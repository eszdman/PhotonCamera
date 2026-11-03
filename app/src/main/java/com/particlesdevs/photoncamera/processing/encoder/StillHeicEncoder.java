package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * HEIC item encoder for every Ultra HDR HEIC piece: the SDR base, the Ultra
 * HDR base and the gain map. The base defaults to 8-bit; an opt-in Main10
 * path feeds P010 when the vendor still encoder exposes it. The gain map is
 * always 8-bit.
 *
 * <p>The QTI still encoder pads its input to a 512x512 grid, so the 10-bit
 * path allocates the codec frame at the grid-aligned size and writes a HEIF
 * {@code clap} clean aperture back to the requested dimensions. The kernel
 * rejects a P010 plane shorter than that grid size, which is why the input
 * buffer is not the nominal width x height.
 *
 * <p>{@code androidx.heifwriter} cannot use the still-image HEIC encoder on
 * devices where {@code getVideoCapabilities()} is null for the image MIME
 * (it throws and falls back to video HEVC in 512px tiles), so this encoder
 * drives {@link MediaCodec} directly and resolves candidates from
 * {@link HeicSupport}. Only the still-image encoder
 * ({@link HeicSupport#MIME_STILL}) is used, always fed one full-size frame.
 * The codec may itself emit a tile grid (the QTI still encoder declares
 * 512px tiles plus grid dimensions in its output format, which is passed to
 * the muxer); no video-encoder or tiled-video fallback is attempted. A device
 * whose still encoder cannot encode the frame saves JPEG instead.
 *
 * <p>Sources: an ARGB_8888/RGBA1010102 software bitmap converted to YUV420 or
 * P010 by {@link Allocator#rgbaToYuv420Tile} /
 * {@link Allocator#rgbaToP010Tile} for buffer candidates, or rendered into an
 * EGL window surface by {@link SurfaceInputRenderer}.
 *
 * <p>Muxing mirrors {@code HeifEncoder.HevcEncoderCallback}: the codec output
 * format is rewritten to the HEIC MIME with the full image dimensions before
 * {@link MediaMuxer#addTrack}. Every candidate is attempted with the full
 * format keys, a minimal key set, and finally bitrate mode, because OEM
 * encoders differ in which parts they reject at {@code MediaCodec.start()}.
 * Every failure throws so callers fall back to JPEG; a structurally invalid
 * output cannot be accepted because the container's primary ispe is checked.
 * Failures that cannot improve on retry (unsupported configuration, unusable
 * input/output) are remembered per process so later shots skip them.
 */
public final class StillHeicEncoder {

    private static final String TAG = "StillHeicEncoder";
    /** HEVC quality matching {@code ImageSaver.JPG_QUALITY} (98). */
    public static final int HEIC_QUALITY = 98;
    private static final int DEQUEUE_TIMEOUT_US = 10_000;
    /** Caps a stalled encoder at ~10 s per queue side instead of hanging the shot. */
    private static final int MAX_STALL_LOOPS = 1000;
    /** Full stack traces per failed attempt are only useful while bring-up. */
    private static final boolean DEBUG_STACKS = false;
    /** C2 reports unsupported configurations as CodecException code 1100. */
    private static final int ERROR_UNSUPPORTED = 1100;
    /** Single-frame presentation time; HeifWriter uses 132us for stills. */
    private static final long PRESENTATION_TIME_US = 132;
    /** Format key levels, most descriptive first (OEM encoders reject subsets). */
    private static final int KEYS_EXTENDED = 1;
    private static final int KEYS_MINIMAL = 0;
    /** The Qualcomm still HEIC encoder pads input to this tile grid. */
    private static final int HEIC_GRID = 512;
    /** HEVC Main10 level 6.2, matching the vendor still encoder's caps. */
    private static final int HEVC_LEVEL_62 = 0x1000000;

    private StillHeicEncoder() {}

    /**
     * Encodes one full-size 8-bit HEIC item.
     *
     * @param sdr         source (required); never recycled here
     * @param exifPayload raw {@code Exif\0\0 + TIFF} payload for the injected
     *                   Exif item, or null (pixi/colr are still written)
     * @param attachExif true for SDR items; false when a merge adds the Exif
     * @throws UnsupportedOperationException when no candidate could encode
     */
    public static void encodeToFile(Path dest, Bitmap sdr, int width, int height,
            byte[] exifPayload, boolean attachExif, boolean displayP3) throws Exception {
        encodeToFile(dest, sdr, width, height, exifPayload, attachExif, displayP3, false);
    }

    /**
     * Encodes one HEIC item. In 10-bit mode the codec input is padded to the
     * vendor's 512x512 grid and the container gets a {@code clap} clean
     * aperture for the requested size, so the visible image remains
     * {@code width x height}.
     */
    public static void encodeToFile(Path dest, Bitmap sdr, int width, int height,
            byte[] exifPayload, boolean attachExif, boolean displayP3,
            boolean tenBit) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw new UnsupportedOperationException("HEIC encode needs API 28+");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Bad size " + width + "x" + height);
        }
        if (sdr == null || sdr.isRecycled()) {
            throw new IllegalArgumentException("Null/recycled SDR bitmap");
        }

        int encodeW = tenBit ? align(width, HEIC_GRID) : width;
        int encodeH = tenBit ? align(height, HEIC_GRID) : height;

        List<HeicSupport.HeicCandidate> candidates = HeicSupport.candidates();
        if (candidates.isEmpty()) {
            throw new UnsupportedOperationException(
                    "No HEIC still encoder for " + width + "x" + height);
        }

        Throwable last = null;
        boolean attempted = false;
        for (HeicSupport.HeicCandidate candidate : candidates) {
            if (candidate.tenBit != tenBit) {
                continue;
            }
            attempted = true;
            if (sUnsupportedCandidates.contains(candidateKey(candidate))) {
                Log.i(TAG, "skipping known-unsupported " + candidate);
                continue;
            }
            boolean muxed = false;
            try {
                encodeWithCandidate(dest, sdr, encodeW, encodeH, width, height,
                        candidate, exifPayload, attachExif, displayP3, tenBit);
                muxed = true;
                // Structural acceptance: the coded size is the grid-aligned
                // size; the display size must be the requested image size.
                int[] size = UltraHdrHeicContainer.primarySize(dest);
                if (size[0] != encodeW || size[1] != encodeH) {
                    throw new IllegalStateException("Encoded HEIC dimensions "
                            + size[0] + "x" + size[1] + " != " + encodeW + "x" + encodeH);
                }
                if (tenBit) {
                    int[] display = UltraHdrHeicContainer.displaySize(
                            Files.readAllBytes(dest));
                    if (display[0] != width || display[1] != height) {
                        throw new IllegalStateException("HEIC clean aperture "
                                + display[0] + "x" + display[1] + " != "
                                + width + "x" + height);
                    }
                }
                Log.d(TAG, "HEIC written via " + candidate + " (" + width + "x"
                        + height + ", " + (tenBit ? "10-bit" : "8-bit") + ")");
                return;
            } catch (Throwable e) {
                last = e;
                if (muxed) {
                    // The codec "encoded" but produced an unusable container
                    // (e.g. buffer input silently drops all samples):
                    // permanent for this encoder+format, don't retry it on
                    // every shot.
                    markUnsupported(candidate);
                }
                Log.e(TAG, "HEIC candidate failed (" + candidate + "): "
                        + (DEBUG_STACKS ? describeVerbose(e) : describe(e)));
                deleteQuietly(dest);
            }
        }
        if (!attempted) {
            throw new UnsupportedOperationException(
                    (tenBit ? "No 10-bit" : "No 8-bit") + " HEIC candidate for "
                            + width + "x" + height);
        }
        throw new UnsupportedOperationException(
                "All HEIC candidates failed for " + width + "x" + height, last);
    }

    private static int align(int value, int alignment) {
        return ((value + alignment - 1) / alignment) * alignment;
    }

    private static void encodeWithCandidate(Path dest, Bitmap sdr, int width, int height,
            int actualWidth, int actualHeight, HeicSupport.HeicCandidate c,
            byte[] exifPayload, boolean attachExif, boolean displayP3,
            boolean tenBit) throws Exception {
        try (BitmapPixels pixels = BitmapPixels.open(sdr)) {
            encodeSequence(dest, pixels, width, height, actualWidth, actualHeight,
                    c, exifPayload, attachExif, displayP3, tenBit);
        }
    }

    /**
     * Encode sequence: the extended key set, a minimal set, and finally
     * bitrate mode, because OEM encoders differ in which parts they reject.
     */
    private static void encodeSequence(Path dest, BitmapPixels pixels, int width, int height,
            int actualWidth, int actualHeight, HeicSupport.HeicCandidate c,
            byte[] exifPayload, boolean attachExif, boolean displayP3,
            boolean tenBit) throws Exception {
        Exception firstFailure = null;
        try {
            encodeAttempt(dest, pixels, width, height, actualWidth, actualHeight,
                    c, exifPayload, attachExif, displayP3, tenBit,
                    KEYS_EXTENDED, true);
            return;
        } catch (Throwable e) {
            firstFailure = asException(e);
            Log.w(TAG, "Attempt failed (extended cq, " + c + "): " + describe(e));
            deleteQuietly(dest);
        }
        try {
            encodeAttempt(dest, pixels, width, height, actualWidth, actualHeight,
                    c, exifPayload, attachExif, displayP3, tenBit,
                    KEYS_MINIMAL, true);
            return;
        } catch (Throwable e) {
            Log.w(TAG, "Attempt failed (minimal cq, " + c + "): " + describe(e));
            deleteQuietly(dest);
        }
        try {
            encodeAttempt(dest, pixels, width, height, actualWidth, actualHeight,
                    c, exifPayload, attachExif, displayP3, tenBit,
                    KEYS_MINIMAL, false);
        } catch (Throwable e) {
            if (permanentFailure(firstFailure) || permanentFailure(e)) {
                markUnsupported(c);
            }
            throw new IllegalStateException("All formats failed for " + c
                    + " (first: " + describe(firstFailure) + ")", e);
        }
    }

    private static final java.util.Set<String> sUnsupportedCandidates =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static String candidateKey(HeicSupport.HeicCandidate c) {
        return c.codecName + "|" + c.colorFormat;
    }

    private static void markUnsupported(HeicSupport.HeicCandidate c) {
        if (sUnsupportedCandidates.add(candidateKey(c))) {
            Log.w(TAG, "Marked unsupported for this process: " + c);
        }
    }

    /**
     * True when retrying this failure cannot plausibly succeed: a rejected
     * configuration, unusable input, or an encoder that produced no output.
     */
    private static boolean permanentFailure(Throwable t) {
        if (t instanceof UnusableOutput
                || t instanceof IllegalArgumentException
                || t instanceof UnsupportedOperationException) {
            return true;
        }
        return t instanceof MediaCodec.CodecException
                && ((MediaCodec.CodecException) t).getErrorCode() == ERROR_UNSUPPORTED;
    }

    /** The encoder started but could not produce a usable stream. */
    private static final class UnusableOutput extends IllegalStateException {
        UnusableOutput(String message) {
            super(message);
        }
    }

    private static Exception asException(Throwable t) {
        return t instanceof Exception ? (Exception) t : new Exception(t);
    }

    private static void deleteQuietly(Path dest) {
        try {
            Files.deleteIfExists(dest);
        } catch (Exception ignored) {
        }
    }

    private static void encodeAttempt(Path dest, BitmapPixels pixels, int width, int height,
            int actualWidth, int actualHeight, HeicSupport.HeicCandidate c,
            byte[] exifPayload, boolean attachExif, boolean displayP3,
            boolean tenBit, int keys, boolean allowCq) throws Exception {
        if (c.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
            encodeSurfaceAttempt(dest, pixels, width, height, c,
                    exifPayload, attachExif, displayP3, keys, allowCq);
            return;
        }

        MediaCodec codec = null;
        MediaMuxer muxer = null;
        MuxState state = null;
        boolean muxerStopped = false;
        boolean finished = false;
        try {
            codec = MediaCodec.createByCodecName(c.codecName);
            MediaFormat format = buildFormat(codec, c, width, height, keys, allowCq, tenBit);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            Log.d(TAG, "Codec started: " + c + " " + width + "x" + height
                    + " keys=" + keysName(keys)
                    + (tenBit ? " 10-bit" : "")
                    + (allowCq ? " cq" : " bitrate"));

            muxer = new MediaMuxer(dest.toAbsolutePath().toString(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF);
            state = new MuxState();
            state.muxer = muxer;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            int stalls = 0;
            // Interleave feeding and draining: hardware encoders may hold
            // input buffers until their outputs are consumed.
            while (!state.outputDone) {
                boolean progressed = false;
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (inIndex >= 0) {
                        queueFrame(codec, pixels, width, height, actualWidth,
                                actualHeight, inIndex, tenBit);
                        inputDone = true;
                        progressed = true;
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (handleOutput(codec, info, outIndex, state, width, height, c, tenBit)) {
                    progressed = true;
                }
                if (!progressed) {
                    if (++stalls > MAX_STALL_LOOPS) {
                        throw new UnusableOutput("Encoder stalled (input queued="
                                + inputDone + ")");
                    }
                } else {
                    stalls = 0;
                }
            }
            requireTrack(state);
            muxer.stop();
            muxerStopped = true;
            if (attachExif || tenBit) {
                // A 10-bit base needs its pixi/clap even when the Ultra HDR
                // merge injects the Exif item; exifPayload may be null.
                injectMetadata(dest, exifPayload, width, height, displayP3,
                        tenBit, actualWidth, actualHeight);
            }
            finished = true;
            Log.d(TAG, "Muxed 1 frame(s): " + (Files.size(dest) / 1024) + " KB");
        } finally {
            if (muxer != null) {
                if (state != null && state.started && !muxerStopped) {
                    try {
                        muxer.stop();
                    } catch (Exception ignored) {
                    }
                }
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                try {
                    codec.release();
                } catch (Exception ignored) {
                }
            }
            if (!finished) {
                deleteQuietly(dest);
            }
        }
    }

    /** Muxer/output state for the encode loops. */
    private static final class MuxState {
        MediaMuxer muxer;
        int track = -1;
        boolean started;
        boolean outputDone;
    }

    /**
     * Handles one {@code dequeueOutputBuffer} result. Returns false when the
     * result was TRY_AGAIN (nothing processed).
     */
    private static boolean handleOutput(MediaCodec codec, MediaCodec.BufferInfo info,
            int outIndex, MuxState s, int width, int height,
            HeicSupport.HeicCandidate c, boolean tenBit) throws Exception {
        if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }
        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (s.started) {
                throw new IllegalStateException("Encoder format changed twice");
            }
            MediaFormat outFormat = codec.getOutputFormat();
            Log.d(TAG, "Output format: " + outFormat);
            rewriteTrackFormat(outFormat, width, height);
            s.track = s.muxer.addTrack(outFormat);
            s.muxer.start();
            s.started = true;
            Log.d(TAG, "Track added (" + (tenBit ? "10-bit" : "8-bit")
                    + (c.colorFormat == MediaCodecInfo.CodecCapabilities
                            .COLOR_FormatSurface ? ", surface)" : ", buffer)"));
            return true;
        }
        if (outIndex >= 0) {
            ByteBuffer out = codec.getOutputBuffer(outIndex);
            if (out != null && info.size > 0
                    && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                if (!s.started) {
                    throw new IllegalStateException("Sample before output format");
                }
                out.position(info.offset);
                out.limit(info.offset + info.size);
                s.muxer.writeSampleData(s.track, out, info);
            }
            codec.releaseOutputBuffer(outIndex, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                s.outputDone = true;
            }
            return true;
        }
        return true;
    }

    private static void requireTrack(MuxState state) {
        if (!state.started) {
            throw new UnusableOutput("Encoder produced no HEIF track");
        }
    }

    /**
     * Encodes through an EGL input surface with the bitmap's pixels (the
     * encoder does the YCbCr conversion). Surface passthrough is the only
     * accepted input for some hardware encoders.
     */
    private static void encodeSurfaceAttempt(Path dest, BitmapPixels pixels,
            int width, int height, HeicSupport.HeicCandidate c,
            byte[] exifPayload, boolean attachExif, boolean displayP3,
            int keys, boolean allowCq) throws Exception {
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        android.view.Surface inputSurface = null;
        SurfaceInputRenderer renderer = null;
        boolean muxerStopped = false;
        boolean finished = false;
        try {
            codec = MediaCodec.createByCodecName(c.codecName);
            MediaFormat format = buildFormat(codec, c, width, height, keys, allowCq, false);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = codec.createInputSurface();
            codec.start();
            Log.d(TAG, "Surface codec started: " + c + " " + width + "x" + height
                    + " keys=" + keysName(keys)
                    + (allowCq ? " cq" : " bitrate"));
            renderer = new SurfaceInputRenderer();
            renderer.connect(inputSurface);

            muxer = new MediaMuxer(dest.toAbsolutePath().toString(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF);
            MuxState state = new MuxState();
            state.muxer = muxer;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long ptsNs = PRESENTATION_TIME_US * 1000L;
            renderer.renderRgba(pixels.rgba, width, height, ptsNs);
            renderer.release();
            renderer = null;
            codec.signalEndOfInputStream();
            Log.d(TAG, "Rendered frame, draining");

            int stalls = 0;
            while (!state.outputDone) {
                int outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (!handleOutput(codec, info, outIndex, state, width, height, c, false)) {
                    if (++stalls > MAX_STALL_LOOPS) {
                        throw new UnusableOutput("Encoder stalled (surface)");
                    }
                } else {
                    stalls = 0;
                }
            }
            requireTrack(state);
            muxer.stop();
            muxerStopped = true;
            if (attachExif) {
                injectMetadata(dest, exifPayload, width, height, displayP3,
                        false, width, height);
            }
            finished = true;
            Log.d(TAG, "Muxed 1 frame(s): " + (Files.size(dest) / 1024) + " KB");
        } finally {
            if (renderer != null) {
                renderer.release();
            }
            if (inputSurface != null) {
                try {
                    inputSurface.release();
                } catch (Exception ignored) {
                }
            }
            if (muxer != null) {
                if (!muxerStopped) {
                    try {
                        muxer.stop();
                    } catch (Exception ignored) {
                    }
                }
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                try {
                    codec.release();
                } catch (Exception ignored) {
                }
            }
            if (!finished) {
                deleteQuietly(dest);
            }
        }
    }

    /** Fills and queues the single full-size frame, EOS. */
    private static void queueFrame(MediaCodec codec, BitmapPixels pixels, int width,
            int height, int actualWidth, int actualHeight, int inIndex,
            boolean tenBit) throws Exception {
        if (tenBit) {
            fillP010(codec, pixels, width, height, inIndex,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return;
        }
        fillYuv420(codec, pixels.rgba, width, height, inIndex,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    }

    /**
     * Fills a P010 input Image from the 10-bit (RGBA1010102) or 8-bit source.
     * The codec buffer is grid-aligned and larger than the visible image; the
     * source's actual size is replicated into the padding so the encoder never
     * reads uninitialized pixels. The full buffer capacity is queued because
     * the kernel validates the plane length against the 512-grid size.
     */
    private static void fillP010(MediaCodec codec, BitmapPixels pixels, int width,
            int height, int inIndex, int flags) {
        Image image = codec.getInputImage(inIndex);
        if (image == null) {
            throw new UnsupportedOperationException("No P010 encoder input image");
        }
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3) {
            throw new UnsupportedOperationException("P010 input image has "
                    + planes.length + " planes");
        }
        Allocator.rgbaToP010Tile(pixels.rgba, pixels.tenBit,
                pixels.width, pixels.height, 0, 0, width, height,
                planes[0].getBuffer(), planes[0].getRowStride(),
                planes[0].getPixelStride(),
                planes[1].getBuffer(), planes[1].getRowStride(),
                planes[1].getPixelStride(),
                planes[2].getBuffer(), planes[2].getRowStride(),
                planes[2].getPixelStride());
        ByteBuffer in = codec.getInputBuffer(inIndex);
        int size = in != null ? in.capacity() : width * height * 3;
        codec.queueInputBuffer(inIndex, 0, size, PRESENTATION_TIME_US, flags);
    }

    private static void fillYuv420(MediaCodec codec, ByteBuffer rgba, int width, int height,
            int inIndex, int flags) {
        Image image = codec.getInputImage(inIndex);
        if (image == null) {
            throw new UnsupportedOperationException("No encoder input image");
        }
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3) {
            throw new UnsupportedOperationException("Encoder input image has "
                    + planes.length + " planes");
        }
        Allocator.rgbaToYuv420Tile(rgba, width, height, 0, 0, width, height,
                planes[0].getBuffer(), planes[0].getRowStride(),
                planes[0].getPixelStride(),
                planes[1].getBuffer(), planes[1].getRowStride(),
                planes[1].getPixelStride(),
                planes[2].getBuffer(), planes[2].getRowStride(),
                planes[2].getPixelStride());
        ByteBuffer in = codec.getInputBuffer(inIndex);
        int size = in != null ? in.capacity() : (int) ((long) width * height * 3 / 2);
        codec.queueInputBuffer(inIndex, 0, size, PRESENTATION_TIME_US, flags);
    }

    private static MediaFormat buildFormat(MediaCodec codec, HeicSupport.HeicCandidate c,
            int width, int height, int keys, boolean allowCq, boolean tenBit) {
        MediaFormat format = MediaFormat.createVideoFormat(
                HeicSupport.MIME_STILL, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, c.colorFormat);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 1);
        if (tenBit) {
            format.setInteger(MediaFormat.KEY_PROFILE,
                    HeicSupport.HEVC_PROFILE_MAIN10_STILL);
            format.setInteger(MediaFormat.KEY_LEVEL, HEVC_LEVEL_62);
            // Graphic input buffers are allocated from width/height (already
            // grid-aligned); do not add a max-input-size key that could make
            // the framework choose a different buffer path.
        }

        // The platform HEIC quality recipe over the FULL image:
        // bitrate = w*h*1.5*8*maxCompressRatio(0.25)*quality/100.
        double quality = HEIC_QUALITY / 100.0;
        int bitrate = (int) (width * (double) height * 1.5 * 8 * 0.25 * quality);
        MediaCodecInfo.CodecCapabilities caps;
        try {
            caps = codec.getCodecInfo().getCapabilitiesForType(HeicSupport.MIME_STILL);
        } catch (Exception e) {
            caps = null;
        }
        MediaCodecInfo.VideoCapabilities video =
                caps == null ? null : caps.getVideoCapabilities();
        if (video != null) {
            try {
                bitrate = video.getBitrateRange().clamp(bitrate);
            } catch (Exception ignored) {
            }
        }

        if (keys == KEYS_EXTENDED) {
            // SDR display output: full-range BT.709 matrix coefficients. The
            // RGB primaries/transfer are written at the container level
            // (BT.709/sRGB or Display P3) by injectMetadata/merge.
            format.setInteger(MediaFormat.KEY_OPERATING_RATE, 30);
            format.setInteger(MediaFormat.KEY_COLOR_STANDARD,
                    MediaFormat.COLOR_STANDARD_BT709);
            format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
            format.setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                    MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        }

        MediaCodecInfo.EncoderCapabilities enc =
                caps == null ? null : caps.getEncoderCapabilities();
        boolean cq = allowCq && enc != null && enc.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        if (cq) {
            try {
                android.util.Range<Integer> qr = enc.getQualityRange();
                int q = (int) (qr.getLower()
                        + (qr.getUpper() - qr.getLower()) * quality);
                format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
                format.setInteger(MediaFormat.KEY_QUALITY, q);
                return format;
            } catch (Exception e) {
                Log.e(TAG, "CQ mode setup failed, using bitrate: "
                        + Log.getStackTraceString(e));
            }
        }
        if (enc != null && enc.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        } else {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        }
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        return format;
    }

    /**
     * Turns the codec's output format into the HEIC track format
     * MediaMuxer/MPEG4Writer needs: HEIC mime, full image dimensions, and the
     * "is-default" flag (mirrors HeifEncoder's output-format callback).
     */
    private static void rewriteTrackFormat(MediaFormat fmt, int width, int height) {
        fmt.setString(MediaFormat.KEY_MIME, HeicSupport.MIME_STILL);
        fmt.setInteger(MediaFormat.KEY_WIDTH, width);
        fmt.setInteger(MediaFormat.KEY_HEIGHT, height);
        // MPEG4Writer marks the primary image from "is-default"; without it
        // the HEIC would have no primary item.
        fmt.setInteger("is-default", 1);
        // The still encoder is grid-native: it declares tile-width/height and
        // grid-cols/rows in this output format and emits one sample per tile.
        // Those keys must reach MPEG4Writer, which assembles the grid; removing
        // them makes it drop every tile ("encoded 0 frames") and the merged
        // file carries no image payload.
    }

    /**
     * Adds the Exif item (when a payload exists) plus an 8-bit {@code pixi} and
     * a full-range SDR {@code colr} (BT.709/sRGB or Display P3) to the primary
     * item. A failure
     * (unparseable container, structural mismatch) logs and leaves the file
     * without the metadata rather than losing the image.
     */
    private static void injectMetadata(Path dest, byte[] exifPayload,
            int width, int height, boolean displayP3, boolean tenBit,
            int displayW, int displayH) throws Exception {
        try {
            byte[] muxed = Files.readAllBytes(dest);
            byte[] tagged = UltraHdrHeicContainer.injectExif(muxed, exifPayload,
                    displayP3, tenBit, displayW, displayH);
            int[] size = UltraHdrHeicContainer.primarySize(tagged);
            if (size[0] != width || size[1] != height) {
                throw new IllegalStateException("Metadata-injected HEIC dimensions "
                        + size[0] + "x" + size[1] + " != " + width + "x" + height);
            }
            if (tenBit) {
                int[] display = UltraHdrHeicContainer.displaySize(tagged);
                if (display[0] != displayW || display[1] != displayH) {
                    throw new IllegalStateException("Metadata-injected HEIC aperture "
                            + display[0] + "x" + display[1] + " != "
                            + displayW + "x" + displayH);
                }
            }
            Files.write(dest, tagged);
        } catch (Exception e) {
            if (displayP3 || tenBit) {
                // A P3 pixel stream tagged as BT.709 would display badly, and
                // a 10-bit stream without its clean aperture would show the
                // 512-grid padding. Fail the candidate so StillEncoder can
                // fall back to a correctly tagged 8-bit encode.
                throw new IllegalStateException("HEIC metadata injection failed", e);
            }
            Log.e(TAG, "HEIC metadata injection failed, keeping the image: "
                    + Log.getStackTraceString(e));
        }
    }

    private static String keysName(int keys) {
        return keys == KEYS_EXTENDED ? "extended" : "minimal";
    }

    /** One-line description of any Throwable (Errors included). */
    private static String describe(Throwable t) {
        if (t == null) {
            return "none";
        }
        if (t instanceof MediaCodec.CodecException) {
            MediaCodec.CodecException ce = (MediaCodec.CodecException) t;
            return "CodecException code=" + ce.getErrorCode()
                    + " transient=" + ce.isTransient()
                    + " recoverable=" + ce.isRecoverable()
                    + " diag=" + ce.getDiagnosticInfo()
                    + " msg=" + ce.getMessage();
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** Full stack of any Throwable; enable with {@link #DEBUG_STACKS}. */
    private static String describeVerbose(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Tightly packed RGBA8888 view of a bitmap. Prefers a zero-copy lock via
     * {@link Allocator#wrapBitmap}; falls back to a software copy plus
     * {@code copyPixelsToBuffer} for hardware or padded bitmaps.
     */
    private static final class BitmapPixels implements AutoCloseable {
        final ByteBuffer rgba;
        final int width;
        final int height;
        /** True when the source pixels are packed RGBA1010102. */
        final boolean tenBit;
        private final Bitmap locked;
        private final ByteBuffer owned;
        private final Bitmap softwareCopy;

        private BitmapPixels(ByteBuffer rgba, int width, int height, boolean tenBit,
                Bitmap locked, ByteBuffer owned, Bitmap softwareCopy) {
            this.rgba = rgba;
            this.width = width;
            this.height = height;
            this.tenBit = tenBit;
            this.locked = locked;
            this.owned = owned;
            this.softwareCopy = softwareCopy;
        }

        static BitmapPixels open(Bitmap bitmap) {
            long need = (long) bitmap.getWidth() * bitmap.getHeight() * 4;
            boolean tenBit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && bitmap.getConfig() != null
                    && "RGBA_1010102".equals(bitmap.getConfig().name());
            ByteBuffer wrapped = Allocator.wrapBitmap(bitmap);
            if (wrapped != null && wrapped.capacity() >= need) {
                wrapped.position(0);
                return new BitmapPixels(wrapped, bitmap.getWidth(), bitmap.getHeight(),
                        tenBit, bitmap, null, null);
            }
            Bitmap soft = bitmap;
            Bitmap copy = null;
            if (bitmap.getConfig() == null) {
                // Hardware bitmap: materialize a software copy first. The
                // 10-bit pipeline sink is always software, so this is only a
                // safety path for unusual callers.
                copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                soft = copy;
                tenBit = false;
            }
            if (soft == null) {
                throw new UnsupportedOperationException("Cannot read bitmap pixels");
            }
            ByteBuffer owned = Allocator.allocate(
                    (int) ((long) soft.getWidth() * soft.getHeight() * 4));
            if (owned == null) {
                if (copy != null) {
                    copy.recycle();
                }
                throw new UnsupportedOperationException("No pixel buffer");
            }
            soft.copyPixelsToBuffer(owned);
            owned.position(0);
            return new BitmapPixels(owned, soft.getWidth(), soft.getHeight(), tenBit,
                    null, owned, copy);
        }

        @Override
        public void close() {
            if (locked != null) {
                try {
                    Allocator.unlockBitmap(locked);
                } catch (Exception ignored) {
                }
            }
            if (owned != null) {
                Allocator.free(owned);
            }
            if (softwareCopy != null) {
                softwareCopy.recycle();
            }
        }
    }
}
