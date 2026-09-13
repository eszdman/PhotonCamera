package com.particlesdevs.photoncamera.processing.encoder;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Capability gates and 10-bit HEIC encoder selection.
 *
 * <ul>
 *   <li>SDR HEIC encode needs API 28+ plus a hardware HEVC image encoder
 *       ({@code androidx.heifwriter} has the same requirement and throws on
 *       emulators / encoder-less devices).</li>
 *   <li>HEIC + Ultra HDR gain map additionally needs API 34+, where the
 *       platform {@code Gainmap} / HDR display stack exists to verify and
 *       render the result. Below 34, HEIC save modes degrade to SDR HEIC
 *       (format choice wins) — see the save-mode contract.</li>
 *   <li>10-bit HEIC needs HEVC Main10 encoders with a buffer color format the
 *       pipeline can feed: {@code ABGR1010102} (native sink layout) or
 *       {@code YUVP010} (converted on the fly). Candidates are resolved per
 *       output size at encode time, because video encoders cap single-frame
 *       sizes far below still-image resolutions; the fallback for those is
 *       HeifWriter-style 512px tiling.</li>
 * </ul>
 *
 * <p>Capability results are cached per process; the codec query walks the
 * full codec list once.
 */
public final class HeicSupport {

    /** Still-image HEIC encoder MIME (what HeifWriter prefers). */
    public static final String MIME_STILL = MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC;
    /** Video HEVC encoder MIME (HeifWriter's own fallback). */
    public static final String MIME_VIDEO = MediaFormat.MIMETYPE_VIDEO_HEVC;
    /**
     * Baseline tile size (mirrors HeifWriter's HEIC grid) and the first entry
     * of {@link #TILE_PREFERENCES} fallback.
     */
    public static final int TILE_SIZE = 512;
    /**
     * Tiled-candidate tile sizes in preference order (largest first). Larger
     * tiles mean fewer samples and less tile-boundary overhead, so the
     * encoder picks the largest supported size that divides the image.
     */
    public static final int[] TILE_PREFERENCES = {2048, 1024, TILE_SIZE};

    private static volatile Boolean sHeicEncode;
    private static volatile List<TenBitCandidate> sTenBitCandidates;

    private HeicSupport() {}

    /**
     * One way to run a 10-bit HEIC encode on this device: an encoder + MIME,
     * the buffer color format to feed it, and whether the encode must be
     * tiled. The same encoder can yield several candidates (single-frame and
     * tiled).
     */
    public static final class TenBitCandidate {
        public final String mime;
        public final String codecName;
        /** {@code COLOR_Format32bitABGR2101010} or {@code COLOR_FormatYUVP010}. */
        public final int colorFormat;
        /** True when the encode must be tiled ({@link #TILE_PREFERENCES}). */
        public final boolean grid;
        /** Square tile sizes this encoder supports for {@link #grid}, largest first. */
        public final int[] supportedTiles;
        final boolean hardware;
        final boolean constantQuality;
        private final MediaCodecInfo.VideoCapabilities videoCaps;

        TenBitCandidate(String mime, String codecName, int colorFormat, boolean grid,
                int[] supportedTiles, boolean hardware, boolean constantQuality,
                MediaCodecInfo.VideoCapabilities videoCaps) {
            this.mime = mime;
            this.codecName = codecName;
            this.colorFormat = colorFormat;
            this.grid = grid;
            this.supportedTiles = supportedTiles;
            this.hardware = hardware;
            this.constantQuality = constantQuality;
            this.videoCaps = videoCaps;
        }

        /** True when this candidate can encode the requested output size. */
        public boolean supportsSize(int width, int height) {
            if (grid) {
                // Tile support was verified when the candidate was built.
                return true;
            }
            if (videoCaps == null) {
                return false;
            }
            try {
                return videoCaps.isSizeSupported(width, height);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String toString() {
            return mime + "/" + codecName + (grid ? " grid" : " single")
                    + " " + formatName(colorFormat);
        }

        static String formatName(int colorFormat) {
            if (colorFormat == MediaCodecInfo.CodecCapabilities
                    .COLOR_Format32bitABGR2101010) {
                return "ABGR1010102";
            }
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010) {
                return "P010";
            }
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return "surface";
            }
            return "fmt=" + colorFormat;
        }
    }

    /** True when SDR HEIC encode is expected to work on this device. */
    public static boolean isHeicEncodeSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        Boolean cached = sHeicEncode;
        if (cached != null) {
            return cached;
        }
        boolean supported = queryHevcImageEncoder();
        sHeicEncode = supported;
        return supported;
    }

    /**
     * True when HEIC + Ultra HDR gain-map mux is enabled on this device
     * (API 34+ with working SDR HEIC encode).
     */
    public static boolean isUltraHdrHeicSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && isHeicEncodeSupported();
    }

    /**
     * True when any 10-bit HEIC candidate exists (API 28+, SDR HEIC encode,
     * Main10 encoder with a supported input format). Per-size availability is
     * resolved by {@link #tenBitCandidatesFor(int, int)}; callers fall back
     * to 8-bit HEIC when no candidate can encode the output size.
     */
    public static boolean isTenBitHeicSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        return isHeicEncodeSupported() && !tenBitCandidates().isEmpty();
    }

    /**
     * Every 10-bit candidate, best first, size-independent. Order: still
     * encoder before video, hardware before software, single-frame before
     * tiled, constant-quality before bitrate modes, ABGR1010102 before P010.
     */
    public static List<TenBitCandidate> tenBitCandidates() {
        List<TenBitCandidate> cached = sTenBitCandidates;
        if (cached != null) {
            return cached;
        }
        List<TenBitCandidate> found = queryTenBitCandidates();
        sTenBitCandidates = found;
        return found;
    }

    /** Candidates able to encode {@code width}x{@code height}, best first. */
    public static List<TenBitCandidate> tenBitCandidatesFor(int width, int height) {
        List<TenBitCandidate> out = new ArrayList<>();
        for (TenBitCandidate c : tenBitCandidates()) {
            if (c.supportsSize(width, height)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Test-only hook to override the cached SDR encode probe result. */
    public static void setHeicEncodeSupportedForTest(Boolean value) {
        sHeicEncode = value;
    }

    /** Test-only hook to override the cached 10-bit candidate list. */
    public static void setTenBitCandidatesForTest(List<TenBitCandidate> value) {
        sTenBitCandidates = value;
    }

    /** Pure-logic helper: is this save-mode value one of the HEIC modes? */
    public static boolean isHeicSaveMode(int saveMode) {
        return saveMode == ImageFormatConfig.SAVE_HEIC
                || saveMode == ImageFormatConfig.SAVE_HEIC_RAW;
    }

    private static boolean queryHevcImageEncoder() {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (MIME_STILL.equalsIgnoreCase(type)
                            || MIME_VIDEO.equalsIgnoreCase(type)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    /**
     * Enumerates Main10 encoders that accept a feedable input. Each encoder's
     * capabilities are queried once per MIME; the still MIME yields
     * single-frame candidates, the video MIME single-frame (usable only when
     * the image size is supported) and {@link #TILE_SIZE}-tiled candidates.
     */
    private static List<TenBitCandidate> queryTenBitCandidates() {
        List<TenBitCandidate> out = new ArrayList<>();
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) {
                    continue;
                }
                addMimeCandidates(info, MIME_STILL, out);
                addMimeCandidates(info, MIME_VIDEO, out);
            }
        } catch (Throwable ignored) {
        }
        out.sort((a, b) -> Integer.compare(rank(b), rank(a)));
        return out;
    }

    private static void addMimeCandidates(MediaCodecInfo info, String mime,
            List<TenBitCandidate> out) {
        MediaCodecInfo.CodecCapabilities caps;
        try {
            caps = info.getCapabilitiesForType(mime);
        } catch (Exception e) {
            return;
        }
        if (caps == null || caps.profileLevels == null || caps.colorFormats == null) {
            return;
        }
        if (!main10(caps.profileLevels)) {
            return;
        }
        addModeCandidates(info, caps, mime, false, null, out);
        if (MIME_VIDEO.equals(mime)) {
            int[] tiles = supportedTiles(caps.getVideoCapabilities());
            if (tiles.length > 0) {
                addModeCandidates(info, caps, mime, true, tiles, out);
            }
        }
    }

    private static boolean main10(MediaCodecInfo.CodecProfileLevel[] profiles) {
        for (MediaCodecInfo.CodecProfileLevel pl : profiles) {
            if (pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    || pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                    || pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus) {
                return true;
            }
        }
        return false;
    }

    private static int rank(TenBitCandidate c) {
        int rank = 0;
        if (MIME_STILL.equals(c.mime)) {
            rank += 16;
        }
        if (c.hardware) {
            rank += 8;
        }
        if (!c.grid) {
            rank += 4;
        }
        if (c.constantQuality) {
            rank += 2;
        }
        // P010 is the format hardware encoders usually accept for 10-bit
        // buffer input; surface input is the fallback when buffer formats are
        // rejected (QTI video encoders); ABGR1010102 last (often listed but
        // unallocatable).
        if (c.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010) {
            rank += 2;
        } else if (c.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
            rank += 1;
        }
        return rank;
    }

    /**
     * Adds one candidate per feedable color format for this encoder/MIME/mode
     * (P010 first, then surface, then ABGR1010102): OEM encoders often list a
     * format whose input is rejected at start, so every format must be
     * attempted instead of only the preferred one.
     */
    private static void addModeCandidates(MediaCodecInfo info,
            MediaCodecInfo.CodecCapabilities caps, String mime, boolean grid,
            int[] tiles, List<TenBitCandidate> out) {
        MediaCodecInfo.EncoderCapabilities enc = caps.getEncoderCapabilities();
        boolean cq = enc != null && enc.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        boolean hardware = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || info.isHardwareAccelerated();
        MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
        addFormatCandidate(info.getName(), caps, mime, grid, tiles, hardware, cq, video,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010, out);
        addFormatCandidate(info.getName(), caps, mime, grid, tiles, hardware, cq, video,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface, out);
        addFormatCandidate(info.getName(), caps, mime, grid, tiles, hardware, cq, video,
                MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010, out);
    }

    private static void addFormatCandidate(String codecName,
            MediaCodecInfo.CodecCapabilities caps, String mime, boolean grid,
            int[] tiles, boolean hardware, boolean cq,
            MediaCodecInfo.VideoCapabilities video, int wanted,
            List<TenBitCandidate> out) {
        boolean supported = false;
        for (int fmt : caps.colorFormats) {
            if (fmt == wanted) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            return;
        }
        out.add(new TenBitCandidate(mime, codecName, wanted, grid,
                tiles == null ? new int[0] : tiles, hardware, cq, video));
    }

    /** Supported tiled frame sizes in preference order (largest first). */
    private static int[] supportedTiles(MediaCodecInfo.VideoCapabilities video) {
        if (video == null) {
            return new int[0];
        }
        List<Integer> found = new ArrayList<>();
        for (int tile : TILE_PREFERENCES) {
            try {
                if (video.isSizeSupported(tile, tile)) {
                    found.add(tile);
                }
            } catch (Exception ignored) {
            }
        }
        int[] out = new int[found.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = found.get(i);
        }
        return out;
    }
}
