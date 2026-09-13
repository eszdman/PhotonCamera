package com.particlesdevs.photoncamera.processing.encoder;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.view.Surface;

import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 10-bit HEIC encoder used when the "10-bit HEIC" toggle is on.
 *
 * <p>{@code androidx.heifwriter} hardcodes 8-bit for HEIC, so this encoder
 * drives {@link MediaCodec} directly. It resolves candidates from
 * {@link HeicSupport} for the actual output size: a still-image encoder
 * ({@link HeicSupport#MIME_STILL}) or a video encoder that supports the full
 * frame, otherwise the video encoder in tiles — the largest supported square
 * tile (2048/1024/512) that divides the image, or the one with the least
 * padding, so tile count and boundary overhead stay low. The packed pipeline
 * sink is either copied directly (ABGR1010102, the native RGB10_A2 readback
 * layout), converted to P010 4:2:0 by {@link Allocator#rgb10ToP010Tile}, or
 * rendered into a 10-bit EGL surface for encoders that only take 10-bit
 * frames through an input surface.
 *
 * <p>Muxing mirrors {@code HeifEncoder.HevcEncoderCallback}: the codec output
 * format is rewritten to the HEIC MIME with the full image dimensions and the
 * tile/grid keys before {@link MediaMuxer#addTrack}, which is what makes
 * MPEG4Writer emit a HEIC with a correct grid (the encoder's own format only
 * describes one tile). Each candidate is attempted with every feedable color
 * format it advertises (ABGR1010102 and P010), and each of those with the
 * full format keys, a minimal key set, and finally bitrate mode, because OEM
 * encoders differ in which parts they reject at {@code MediaCodec.start()}.
 * The stream is only accepted if its hvcC says Main10, so an encoder quietly
 * producing 8-bit can never masquerade as 10-bit. Every failure throws so
 * callers fall back to 8-bit HEIC (and Ultra HDR retries with an 8-bit base
 * first).
 */
public final class TenBitHeicEncoder {

    private static final String TAG = "TenBitHeicEncoder";
    private static final int DEQUEUE_TIMEOUT_US = 10_000;
    /** Caps a stalled encoder at ~10 s per queue side instead of hanging the shot. */
    private static final int MAX_STALL_LOOPS = 1000;
    /** Full stack traces per failed attempt are only useful while bring-up. */
    private static final boolean DEBUG_STACKS = false;

    private TenBitHeicEncoder() {}

    /**
     * Encodes {@code pixels} (packed ABGR1010102, {@code width*height*4}
     * bytes) to {@code dest} (must end in {@code .heic}). The buffer is not
     * modified and ownership stays with the caller.
     *
     * @throws UnsupportedOperationException unsupported device/size, so
     *         callers can fall back without having written anything
     */
    public static void encodeToFile(Path dest, ByteBuffer pixels, int width, int height)
            throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw new UnsupportedOperationException("HEIC encode needs API 28+");
        }
        long need = (long) width * height * 4;
        if (pixels == null || pixels.capacity() < need) {
            throw new IllegalArgumentException("10-bit buffer holds "
                    + (pixels == null ? 0 : pixels.capacity()) + " bytes, needs " + need);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Bad size " + width + "x" + height);
        }
        List<HeicSupport.TenBitCandidate> candidates =
                HeicSupport.tenBitCandidatesFor(width, height);
        if (candidates.isEmpty()) {
            throw new UnsupportedOperationException(
                    "No 10-bit HEVC encoder for " + width + "x" + height);
        }
        Throwable last = null;
        for (HeicSupport.TenBitCandidate candidate : candidates) {
            if (sUnsupportedCandidates.contains(candidateKey(candidate))) {
                continue;
            }
            boolean muxed = false;
            try {
                encodeWithCandidate(dest, pixels, width, height, candidate);
                muxed = true;
                // Structural acceptance: the muxed container must declare the
                // full image as its primary (grid items carry the full dims).
                int[] size = UltraHdrHeicContainer.primarySize(dest);
                if (size[0] != width || size[1] != height) {
                    throw new IllegalStateException("Encoded HEIC dimensions "
                            + size[0] + "x" + size[1] + " != " + width + "x" + height);
                }
                Log.d(TAG, "10-bit HEIC written via " + candidate
                        + " (" + width + "x" + height + ")");
                return;
            } catch (Throwable e) {
                last = e;
                if (muxed) {
                    // The codec "encoded" but produced an unusable container
                    // (e.g. P010 buffer input silently drops all samples):
                    // permanent for this encoder+format, don't retry it on
                    // every shot.
                    sUnsupportedCandidates.add(candidateKey(candidate));
                }
                Log.e(TAG, "10-bit candidate failed (" + candidate + "): "
                        + (DEBUG_STACKS ? describeVerbose(e) : describe(e)));
                try {
                    Files.deleteIfExists(dest);
                } catch (Exception ignored) {
                }
            }
        }
        throw new UnsupportedOperationException(
                "All 10-bit HEVC candidates failed for " + width + "x" + height, last);
    }

    /**
     * Injects the Exif item into an already-muxed 10-bit HEIC in place. A
     * failure (unparseable container, structural mismatch) logs and leaves
     * the file without Exif rather than losing the image.
     */
    public static void injectExifToFile(Path dest, byte[] exifPayload,
            int width, int height) {
        if (exifPayload == null) {
            return;
        }
        try {
            byte[] muxed = Files.readAllBytes(dest);
            byte[] withExif = UltraHdrHeicContainer.injectExif(muxed, exifPayload, true);
            if (withExif == muxed) {
                return;
            }
            int[] size = UltraHdrHeicContainer.primarySize(withExif);
            if (size[0] != width || size[1] != height) {
                throw new IllegalStateException("Exif-injected HEIC dimensions "
                        + size[0] + "x" + size[1] + " != " + width + "x" + height);
            }
            Files.write(dest, withExif);
        } catch (Exception e) {
            Log.e(TAG, "EXIF injection failed, keeping HEIC without EXIF: "
                    + Log.getStackTraceString(e));
        }
    }

    private static void encodeWithCandidate(Path dest, ByteBuffer pixels, int width, int height,
            HeicSupport.TenBitCandidate c) throws Exception {
        // OEM encoders reject different parts of the format at start():
        // profile/color-aspect/operating-rate keys, or constant-quality mode.
        // Try progressively simpler configurations before giving up on the
        // encoder.
        Exception firstFailure = null;
        try {
            encodeAttempt(dest, pixels, width, height, c, true, true);
            return;
        } catch (Throwable e) {
            firstFailure = asException(e);
            Log.w(TAG, "Attempt failed (extended cq, " + c + "): " + describe(e));
            deleteQuietly(dest);
        }
        try {
            encodeAttempt(dest, pixels, width, height, c, false, true);
            return;
        } catch (Throwable e) {
            Log.w(TAG, "Attempt failed (minimal cq, " + c + "): " + describe(e));
            deleteQuietly(dest);
        }
        try {
            encodeAttempt(dest, pixels, width, height, c, false, false);
        } catch (Throwable e) {
            if (unsupported(firstFailure) || unsupported(e)) {
                // C2/OMX rejected this encoder+format with ERROR_UNSUPPORTED
                // (1100): remember it so later shots skip it entirely.
                sUnsupportedCandidates.add(candidateKey(c));
            }
            throw new IllegalStateException("All formats failed for " + c
                    + " (first: " + describe(firstFailure) + ")", e);
        }
    }

    private static final java.util.Set<String> sUnsupportedCandidates =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static String candidateKey(HeicSupport.TenBitCandidate c) {
        return c.mime + "|" + c.codecName + "|" + c.colorFormat + "|" + c.grid;
    }

    /** C2 reports unsupported configurations as CodecException code 1100. */
    private static boolean unsupported(Throwable t) {
        return t instanceof MediaCodec.CodecException
                && ((MediaCodec.CodecException) t).getErrorCode() == 1100;
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

    private static void encodeAttempt(Path dest, ByteBuffer pixels, int width, int height,
            HeicSupport.TenBitCandidate c, boolean extendedKeys, boolean allowCq)
            throws Exception {
        if (c.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
            encodeSurfaceAttempt(dest, pixels, width, height, c, extendedKeys, allowCq);
            return;
        }
        int tileW = c.grid ? chooseTile(c, width, height) : width;
        int tileH = c.grid ? tileW : height;
        int cols = c.grid ? (width + tileW - 1) / tileW : 1;
        int rows = c.grid ? (height + tileH - 1) / tileH : 1;
        int numTiles = cols * rows;

        MediaCodec codec = null;
        MediaMuxer muxer = null;
        MuxState state = null;
        boolean muxerStopped = false;
        boolean finished = false;
        try {
            codec = MediaCodec.createByCodecName(c.codecName);
            MediaFormat format = buildFormat(codec, c, tileW, tileH, cols, rows,
                    width, height, extendedKeys, allowCq);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            Log.d(TAG, "Codec started: " + c + " tile=" + tileW + "x" + tileH
                    + " tiles=" + numTiles + " keys=" + (extendedKeys ? "extended" : "minimal")
                    + (allowCq ? " cq" : " bitrate"));

            muxer = new MediaMuxer(dest.toAbsolutePath().toString(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF);
            state = new MuxState();
            state.muxer = muxer;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int tilesQueued = 0;
            boolean inputDone = false;
            int stalls = 0;
            // Interleave feeding and draining: hardware encoders may hold
            // input buffers until their outputs are consumed, so queueing all
            // tiles first can stall (the async HeifWriter path interleaves).
            while (!state.outputDone) {
                boolean progressed = false;
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (inIndex >= 0) {
                        queueTile(codec, c, pixels, width, height, tileW, tileH, cols,
                                tilesQueued, numTiles, inIndex);
                        tilesQueued++;
                        if (tilesQueued >= numTiles) {
                            inputDone = true;
                        }
                        progressed = true;
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (handleOutput(codec, info, outIndex, state, width, height,
                        c, tileW, tileH, cols, rows)) {
                    progressed = true;
                }
                if (!progressed) {
                    if (++stalls > MAX_STALL_LOOPS) {
                        throw new IllegalStateException("Encoder stalled (queued "
                                + tilesQueued + "/" + numTiles + " tiles)");
                    }
                } else {
                    stalls = 0;
                }
            }
            if (!state.started) {
                throw new IllegalStateException("Encoder produced no HEIF track");
            }
            if (!state.main10Checked) {
                Log.w(TAG, "No hvcC available to verify Main10 for this encode");
            }
            muxer.stop();
            muxerStopped = true;
            finished = true;
            Log.d(TAG, "Muxed " + numTiles + " tile(s): "
                    + (Files.size(dest) / 1024) + " KB");
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
                try {
                    Files.deleteIfExists(dest);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Largest supported tile that divides the image exactly (no padded
     * pixels); otherwise the supported tile with the least padding (ties
     * toward larger tiles), so tile count and boundary overhead stay low.
     */
    private static int chooseTile(HeicSupport.TenBitCandidate c, int width, int height) {
        int[] sizes = c.supportedTiles;
        if (sizes == null || sizes.length == 0) {
            return HeicSupport.TILE_SIZE;
        }
        for (int tile : sizes) {
            if (width % tile == 0 && height % tile == 0) {
                return tile;
            }
        }
        int best = sizes[0];
        long bestPadding = Long.MAX_VALUE;
        for (int tile : sizes) {
            long cols = (width + tile - 1) / tile;
            long rows = (height + tile - 1) / tile;
            long padding = cols * tile * rows * tile - (long) width * height;
            if (padding < bestPadding) {
                bestPadding = padding;
                best = tile;
            }
        }
        return best;
    }

    /** Muxer/output state shared by the surface encode loop. */
    private static final class MuxState {
        MediaMuxer muxer;
        int track = -1;
        boolean started;
        boolean main10Checked;
        byte[] csd;
        boolean outputDone;
    }

    /**
     * Handles one {@code dequeueOutputBuffer} result. Returns false when the
     * result was TRY_AGAIN (nothing processed).
     */
    private static boolean handleOutput(MediaCodec codec, MediaCodec.BufferInfo info,
            int outIndex, MuxState s, int width, int height,
            HeicSupport.TenBitCandidate c, int tileW, int tileH, int cols, int rows)
            throws Exception {
        if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }
        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (s.started) {
                throw new IllegalStateException("Encoder format changed twice");
            }
            MediaFormat outFormat = codec.getOutputFormat();
            s.csd = readCsd(outFormat);
            if (s.csd != null) {
                requireMain10(s.csd);
                s.main10Checked = true;
            }
            rewriteTrackFormat(outFormat, width, height, c.grid, tileW, tileH, cols, rows);
            s.track = s.muxer.addTrack(outFormat);
            s.muxer.start();
            s.started = true;
            Log.d(TAG, "Track added (main10=" + s.main10Checked + ", grid=" + c.grid
                    + (c.colorFormat == MediaCodecInfo.CodecCapabilities
                            .COLOR_FormatSurface ? ", surface)" : ", buffer)"));
            return true;
        }
        if (outIndex >= 0) {
            ByteBuffer out = codec.getOutputBuffer(outIndex);
            if (out != null && info.size > 0) {
                boolean codecConfig =
                        (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (codecConfig) {
                    if (!s.main10Checked && s.csd == null) {
                        s.csd = copyBytes(out, info.offset, info.size);
                        requireMain10(s.csd);
                        s.main10Checked = true;
                    }
                } else {
                    if (!s.started) {
                        throw new IllegalStateException("Sample before output format");
                    }
                    out.position(info.offset);
                    out.limit(info.offset + info.size);
                    s.muxer.writeSampleData(s.track, out, info);
                }
            }
            codec.releaseOutputBuffer(outIndex, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                s.outputDone = true;
            }
            return true;
        }
        return true;
    }

    /**
     * Encodes through a 10-bit EGL input surface. Hardware encoders that
     * reject 10-bit buffer formats (QTI V4L2 reports P010 unsupported) accept
     * HDR frames only this way; the encoder does the RGB10 -> P010
     * conversion. Rendering and output draining are interleaved so a full
     * output queue cannot deadlock {@code eglSwapBuffers}.
     */
    private static void encodeSurfaceAttempt(Path dest, ByteBuffer pixels, int width, int height,
            HeicSupport.TenBitCandidate c, boolean extendedKeys, boolean allowCq)
            throws Exception {
        int tileW = c.grid ? chooseTile(c, width, height) : width;
        int tileH = c.grid ? tileW : height;
        int cols = c.grid ? (width + tileW - 1) / tileW : 1;
        int rows = c.grid ? (height + tileH - 1) / tileH : 1;
        int numTiles = cols * rows;

        MediaCodec codec = null;
        MediaMuxer muxer = null;
        Surface inputSurface = null;
        SurfaceInputRenderer renderer = null;
        boolean muxerStopped = false;
        boolean finished = false;
        try {
            codec = MediaCodec.createByCodecName(c.codecName);
            MediaFormat format = buildFormat(codec, c, tileW, tileH, cols, rows,
                    width, height, extendedKeys, allowCq);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = codec.createInputSurface();
            codec.start();
            Log.d(TAG, "Surface codec started: " + c + " tile=" + tileW + "x" + tileH
                    + " tiles=" + numTiles + " keys="
                    + (extendedKeys ? "extended" : "minimal")
                    + (allowCq ? " cq" : " bitrate"));
            renderer = new SurfaceInputRenderer();
            renderer.connect(inputSurface);

            muxer = new MediaMuxer(dest.toAbsolutePath().toString(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF);
            MuxState state = new MuxState();
            state.muxer = muxer;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int i = 0; i < numTiles; i++) {
                int tx = (i % cols) * tileW;
                int ty = (i / cols) * tileH;
                renderer.render(pixels, width, height, tx, ty, tileW, tileH,
                        presentationTimeUs(i, numTiles) * 1000L);
                // Drain whatever is ready so the encoder never runs out of
                // room and blocks the next swap.
                while (!state.outputDone) {
                    int outIndex = codec.dequeueOutputBuffer(info, 0);
                    if (!handleOutput(codec, info, outIndex, state, width, height,
                            c, tileW, tileH, cols, rows)) {
                        break;
                    }
                }
            }
            renderer.release();
            renderer = null;
            codec.signalEndOfInputStream();
            Log.d(TAG, "Rendered " + numTiles + " tile(s), draining");

            int stalls = 0;
            while (!state.outputDone) {
                int outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (!handleOutput(codec, info, outIndex, state, width, height,
                        c, tileW, tileH, cols, rows)) {
                    if (++stalls > MAX_STALL_LOOPS) {
                        throw new IllegalStateException("Encoder stalled (surface)");
                    }
                } else {
                    stalls = 0;
                }
            }
            if (!state.started) {
                throw new IllegalStateException("Encoder produced no HEIF track");
            }
            if (!state.main10Checked) {
                Log.w(TAG, "No hvcC available to verify Main10 for this encode");
            }
            muxer.stop();
            muxerStopped = true;
            finished = true;
            Log.d(TAG, "Muxed " + numTiles + " tile(s): "
                    + (Files.size(dest) / 1024) + " KB");
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
                try {
                    Files.deleteIfExists(dest);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Fills and queues one tile frame (row-major index), EOS on the last.
     */
    private static void queueTile(MediaCodec codec, HeicSupport.TenBitCandidate c,
            ByteBuffer pixels, int width, int height, int tileW, int tileH,
            int cols, int index, int numTiles, int inIndex) throws Exception {
        int tx = (index % cols) * tileW;
        int ty = (index / cols) * tileH;
        int flags = (index == numTiles - 1)
                ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
        if (c.colorFormat == MediaCodecInfo.CodecCapabilities
                .COLOR_Format32bitABGR2101010) {
            ByteBuffer in = codec.getInputBuffer(inIndex);
            long tileBytes = (long) tileW * tileH * 4;
            if (in == null || in.capacity() < tileBytes) {
                throw new UnsupportedOperationException("Encoder input buffer "
                        + (in == null ? -1 : in.capacity()) + " < tile " + tileBytes);
            }
            copyAbgr1010102Tile(pixels, width, height, tx, ty, tileW, tileH, in);
            codec.queueInputBuffer(inIndex, 0, (int) tileBytes,
                    presentationTimeUs(index, numTiles), flags);
        } else {
            // Order matters: MediaCodec.getInputBuffer() invalidates the
            // Image returned by getInputImage(), so the planes must be
            // filled first and the buffer queried only after (HeifWriter
            // does the same).
            Image image = codec.getInputImage(inIndex);
            if (image == null) {
                throw new IllegalStateException("No encoder input image");
            }
            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                throw new IllegalStateException("Encoder input image has "
                        + planes.length + " planes");
            }
            Allocator.rgb10ToP010Tile(pixels, width, height, tx, ty, tileW, tileH,
                    planes[0].getBuffer(), planes[0].getRowStride(),
                    planes[0].getPixelStride(),
                    planes[1].getBuffer(), planes[1].getRowStride(),
                    planes[1].getPixelStride(),
                    planes[2].getBuffer(), planes[2].getRowStride(),
                    planes[2].getPixelStride());
            ByteBuffer in = codec.getInputBuffer(inIndex);
            int size = in != null ? in.capacity() : (int) ((long) tileW * tileH * 3);
            codec.queueInputBuffer(inIndex, 0, size,
                    presentationTimeUs(index, numTiles), flags);
        }
    }

    /**
     * Copies one tile out of the packed ABGR1010102 sink. Edge tiles pad the
     * missing columns/rows by replicating the last valid pixel; the grid item
     * declares the exact output size and crops that padding on decode.
     */
    static void copyAbgr1010102Tile(ByteBuffer src, int fullW, int fullH,
            int tx, int ty, int tileW, int tileH, ByteBuffer dst) {
        ByteBuffer s = src.duplicate();
        dst.clear();
        int validW = Math.min(tileW, fullW - tx);
        byte[] row = new byte[tileW * 4];
        int lastPixel = (validW - 1) * 4;
        for (int r = 0; r < tileH; r++) {
            int y = Math.min(ty + r, fullH - 1);
            int base = (y * fullW + tx) * 4;
            // Widen the limit before positioning: after each row the limit
            // sits at the previous row end, and position() requires <= limit.
            s.limit(base + validW * 4);
            s.position(base);
            s.get(row, 0, validW * 4);
            byte b0 = row[lastPixel];
            byte b1 = row[lastPixel + 1];
            byte b2 = row[lastPixel + 2];
            byte b3 = row[lastPixel + 3];
            for (int x = validW; x < tileW; x++) {
                int p = x * 4;
                row[p] = b0;
                row[p + 1] = b1;
                row[p + 2] = b2;
                row[p + 3] = b3;
            }
            dst.put(row);
        }
        dst.position(0);
    }

    private static MediaFormat buildFormat(MediaCodec codec, HeicSupport.TenBitCandidate c,
            int tileW, int tileH, int cols, int rows, int width, int height,
            boolean extendedKeys, boolean allowCq) {
        int numTiles = cols * rows;
        MediaFormat format = MediaFormat.createVideoFormat(c.mime, tileW, tileH);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, c.colorFormat);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, Math.max(1, numTiles));
        if (c.grid) {
            // Mirror HeifWriter's grid configuration on the codec format:
            // a hint for video encoders, and the muxer uses its own copy.
            format.setInteger(MediaFormat.KEY_TILE_WIDTH, tileW);
            format.setInteger(MediaFormat.KEY_TILE_HEIGHT, tileH);
            format.setInteger(MediaFormat.KEY_GRID_COLUMNS, cols);
            format.setInteger(MediaFormat.KEY_GRID_ROWS, rows);
        }

        // HeifWriter's HEIC recipe over the FULL image (not the tile).
        double quality = SdrHeicEncoder.HEIC_QUALITY / 100.0;
        int bitrate = (int) (width * (double) height * 1.5 * 8 * 0.25 * quality);
        MediaCodecInfo.CodecCapabilities caps;
        try {
            caps = codec.getCodecInfo().getCapabilitiesForType(c.mime);
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

        if (extendedKeys) {
            // SDR display output: full-range BT.709. Required for correctness
            // on the P010 path, where this encoder produces the YCbCr
            // conversion; also hints Main10 on encoders that need it.
            format.setInteger(MediaFormat.KEY_OPERATING_RATE, numTiles > 1 ? 120 : 30);
            format.setInteger(MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10);
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
     * Turns the codec's single-tile output format into the HEIC track format
     * MediaMuxer/MPEG4Writer needs: HEIC mime, full image dimensions, and the
     * grid keys when tiled (mirrors HeifEncoder's output-format callback).
     */
    private static void rewriteTrackFormat(MediaFormat fmt, int width, int height,
            boolean grid, int tileW, int tileH, int cols, int rows) {
        fmt.setString(MediaFormat.KEY_MIME, HeicSupport.MIME_STILL);
        fmt.setInteger(MediaFormat.KEY_WIDTH, width);
        fmt.setInteger(MediaFormat.KEY_HEIGHT, height);
        // MPEG4Writer marks the primary image from "is-default"; without it
        // the HEIC would have no primary item.
        fmt.setInteger("is-default", 1);
        if (grid) {
            fmt.setInteger(MediaFormat.KEY_TILE_WIDTH, tileW);
            fmt.setInteger(MediaFormat.KEY_TILE_HEIGHT, tileH);
            fmt.setInteger(MediaFormat.KEY_GRID_COLUMNS, cols);
            fmt.setInteger(MediaFormat.KEY_GRID_ROWS, rows);
        }
    }

    private static long presentationTimeUs(int index, int numTiles) {
        return 132 + (long) index * 1_000_000 / Math.max(1, numTiles);
    }

    private static byte[] readCsd(MediaFormat format) {
        try {
            ByteBuffer csd = format.getByteBuffer("csd-0");
            if (csd == null || csd.remaining() < 2) {
                return null;
            }
            ByteBuffer dup = csd.duplicate();
            dup.rewind();
            byte[] out = new byte[Math.min(dup.remaining(), 32)];
            dup.get(out);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** hvcC: configurationVersion then general_profile_space/tier/profile_idc. */
    private static void requireMain10(byte[] csd) {
        if (csd == null || csd.length < 2) {
            throw new IllegalStateException("csd too short to verify profile");
        }
        if ((csd[0] & 0xFF) != 1) {
            // Some OEM encoders expose Annex-B / raw NAL data in csd-0 rather
            // than an hvcC record (the QTI video encoder reports profile_idc
            // 0). Format is unverifiable here; the Main10 profile hint and the
            // structural acceptance check still apply.
            Log.w(TAG, "csd-0 is not an hvcC record (first byte "
                    + (csd[0] & 0xFF) + "), skipping Main10 verification");
            return;
        }
        int profileIdc = csd[1] & 0x1F;
        if (profileIdc != MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10) {
            throw new IllegalStateException(
                    "Encoder output is not HEVC Main10 (profile_idc=" + profileIdc + ")");
        }
    }

    private static byte[] copyBytes(ByteBuffer buffer, int offset, int size) {
        ByteBuffer dup = buffer.duplicate();
        dup.position(offset);
        dup.limit(offset + size);
        byte[] out = new byte[size];
        dup.get(out);
        return out;
    }

    /** One-line description of any Throwable (Errors included). */
    private static String describe(Throwable t) {
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
}
