package com.particlesdevs.photoncamera.processing.encoder;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;

import com.particlesdevs.photoncamera.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Capability gates and HEIC encoder selection for the still-image encoder
 * ({@code image/vnd.android.heic}) only.
 *
 * <ul>
 *   <li>SDR HEIC encode needs API 28+ plus a still-image HEIC encoder; a
 *       device without one saves JPEG instead.</li>
 *   <li>HEIC + Ultra HDR gain map additionally needs API 34+, where the
 *       platform {@code Gainmap} / HDR display stack exists to verify and
 *       render the result. Below 34, HEIC save modes degrade to SDR HEIC
 *       (format choice wins) — see the save-mode contract.</li>
 *   <li>Only 8-bit encodes are produced: the still encoder is driven with one
 *       full-size frame per encode. Surface candidates are rendered into an
 *       EGL window surface, buffer candidates are converted to YUV420. Video
 *       HEVC encoders are not used: their single-frame still sizes are far
 *       below still-image resolutions and their tiled fallback loses
 *       detail.</li>
 * </ul>
 *
 * <p>Input formats are offered regardless of what the encoder advertises:
 * image encoders do not reliably report their input formats, so
 * configure/start decides and failures are remembered by
 * {@link StillHeicEncoder}. Capability results are cached per process; the
 * codec query walks the full codec list once, and logs a one-shot probe of
 * every still-image encoder it finds.
 */
public final class HeicSupport {

    /** Still-image HEIC encoder MIME (the only supported encoder). */
    public static final String MIME_STILL = MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC;

    private static final String TAG = "HeicSupport";

    private static volatile Boolean sHeicEncode;
    private static volatile List<HeicCandidate> sCandidates;

    private HeicSupport() {}

    /**
     * One way to run a HEIC encode on this device: a still-image encoder and
     * the input format to feed it. The same encoder yields several candidates.
     */
    public static final class HeicCandidate {
        public final String codecName;
        /** One of the {@code MediaCodecInfo.CodecCapabilities} COLOR_ formats. */
        public final int colorFormat;
        final boolean hardware;
        final boolean constantQuality;

        HeicCandidate(String codecName, int colorFormat, boolean hardware,
                boolean constantQuality) {
            this.codecName = codecName;
            this.colorFormat = colorFormat;
            this.hardware = hardware;
            this.constantQuality = constantQuality;
        }

        @Override
        public String toString() {
            return MIME_STILL + "/" + codecName + " " + formatName(colorFormat);
        }

        static String formatName(int colorFormat) {
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return "surface";
            }
            if (colorFormat == MediaCodecInfo.CodecCapabilities
                    .COLOR_FormatYUV420Flexible) {
                return "YUV420";
            }
            return "fmt=" + colorFormat;
        }
    }

    /** True when a still-image HEIC encoder exists on this device. */
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
     * Every still-image candidate, best first: hardware before software,
     * constant-quality before bitrate modes, surface before buffer formats
     * (the image encoders are surface-first).
     */
    public static List<HeicCandidate> candidates() {
        List<HeicCandidate> cached = sCandidates;
        if (cached != null) {
            return cached;
        }
        List<HeicCandidate> found = queryCandidates();
        sCandidates = found;
        return found;
    }

    /** Test-only hook to override the cached encode probe result. */
    public static void setHeicEncodeSupportedForTest(Boolean value) {
        sHeicEncode = value;
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
                if (info.isEncoder() && supportsStill(info)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static boolean supportsStill(MediaCodecInfo info) {
        for (String type : info.getSupportedTypes()) {
            if (MIME_STILL.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enumerates still-image encoders and offers every feedable input for
     * each. The capabilities object is only used for encoder metadata
     * (hardware/software, constant-quality support). Legacy {@code OMX.*}
     * aliases of a canonical {@code c2.*} still encoder are skipped (they are
     * the same component and would double every attempt).
     */
    private static List<HeicCandidate> queryCandidates() {
        List<HeicCandidate> out = new ArrayList<>();
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            List<MediaCodecInfo> stills = new ArrayList<>();
            boolean hasCanonical = false;
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder() || !supportsStill(info)) {
                    continue;
                }
                stills.add(info);
                if (info.getName().startsWith("c2.")) {
                    hasCanonical = true;
                }
            }
            for (MediaCodecInfo info : stills) {
                if (hasCanonical && !info.getName().startsWith("c2.")) {
                    continue;
                }
                addStillCandidates(info, out);
            }
        } catch (Throwable ignored) {
        }
        out.sort((a, b) -> Integer.compare(rank(b), rank(a)));
        logProbe(out);
        return out;
    }

    private static void addStillCandidates(MediaCodecInfo info, List<HeicCandidate> out) {
        MediaCodecInfo.CodecCapabilities caps;
        try {
            caps = info.getCapabilitiesForType(MIME_STILL);
        } catch (Exception e) {
            return;
        }
        if (caps == null) {
            return;
        }
        MediaCodecInfo.EncoderCapabilities enc = caps.getEncoderCapabilities();
        boolean cq = enc != null && enc.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        boolean hardware = isHardware(info);
        out.add(new HeicCandidate(info.getName(),
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface, hardware, cq));
        out.add(new HeicCandidate(info.getName(),
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible, hardware, cq));
    }

    private static boolean isHardware(MediaCodecInfo info) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || info.isHardwareAccelerated();
    }

    private static int rank(HeicCandidate c) {
        int rank = 0;
        if (c.hardware) {
            rank += 8;
        }
        if (c.constantQuality) {
            rank += 2;
        }
        if (c.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
            rank += 3;
        }
        return rank;
    }

    /**
     * One-shot (first {@link #candidates()} call per process) dump of every
     * still-image encoder and the resolved candidate order. This is the
     * ground truth for on-device bring-up.
     */
    private static void logProbe(List<HeicCandidate> candidates) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            StringBuilder sb = new StringBuilder("HEIC still probe:");
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (info.isEncoder() && supportsStill(info)) {
                    sb.append("\n  ").append(info.getName())
                            .append(" hw=").append(isHardware(info));
                }
            }
            sb.append("\nHEIC still candidates:");
            for (HeicCandidate c : candidates) {
                sb.append("\n  ").append(c)
                        .append(" hw=").append(c.hardware)
                        .append(" cq=").append(c.constantQuality);
            }
            Log.i(TAG, sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "HEIC probe log failed: " + t);
        }
    }
}
