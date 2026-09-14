package com.particlesdevs.photoncamera.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Java wrapper around the native MediaCinemaRAW (.mcraw) encoder
 * (see src/main/cpp/mediacinemaraw).
 *
 * Frames flow through two stages so the camera Image can be released as soon
 * as encoding finishes, without waiting for disk IO:
 *   {@link #encode}: reads the direct ByteBuffer backing an open Image's RAW
 *   plane in place (zero copy) and writes the compressed frame into a
 *   caller-provided scratch slot, returning its size.
 *   {@link #writeFrame}: appends one encoded slot to the container.
 * The caller closes the Image right after {@link #encode} returns, recycling
 * the buffer into the ImageReader's circular pool.
 */
public class McrawWriter implements Closeable {
    private static final String TAG = "McrawWriter";

    static {
        System.loadLibrary("mcraw");
    }

    private long nativePtr;
    private FileOutputStream fallbackStream;

    private McrawWriter(long nativePtr) {
        this.nativePtr = nativePtr;
    }

    /**
     * Creates the output file and writes the container header.
     * File creation goes through SAF first (required on Android 11+ for
     * DCIM locations) with a plain FileOutputStream fallback.
     * Must be called at most ONCE per output path: SAF deletes and
     * recreates an existing file, so a second open discards everything
     * written so far.
     *
     * @param path              absolute output file path (should end in .mcraw)
     * @param containerMetadata JSON metadata stored in the container header
     */
    public static McrawWriter open(String path, String containerMetadata) throws IOException {
        int fd = SimpleStorageHelper.openFdForWrite(path);
        if (fd >= 0) {
            long ptr = nativeCreate(fd, containerMetadata);
            if (ptr == 0) throw new IOException("mcraw container create failed for " + path);
            return new McrawWriter(ptr);
        }
        // Fallback for app-specific paths or older APIs.
        File file = new File(path);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(file);
        try {
            Field field = FileDescriptor.class.getDeclaredField("descriptor");
            field.setAccessible(true);
            int rawFd = (int) field.get(fos.getFD());
            long ptr = nativeCreate(rawFd, containerMetadata);
            if (ptr == 0) throw new IOException("mcraw container create failed for " + path);
            McrawWriter writer = new McrawWriter(ptr);
            writer.fallbackStream = fos;
            return writer;
        } catch (IOException e) {
            fos.close();
            throw e;
        } catch (ReflectiveOperationException e) {
            fos.close();
            throw new IOException("fd extraction failed", e);
        }
    }

    public boolean isOpen() {
        return nativePtr != 0;
    }

    /**
     * Encodes one RAW frame (lossless, compression type 7) from the camera
     * plane into {@code outputSlot}.
     *
     * @param rawPlane   direct buffer of an open Image plane (read in place, not copied)
     * @param width      visible width in pixels (RAW16: any even, RAW10: multiple of 4)
     * @param height     full plane height in pixels
     * @param stride     plane row stride in bytes
     * @param raw10      true for Android packed RAW10 (4 pixels / 5 bytes)
     * @param cropTop    first output row (even, preserves Bayer phase)
     * @param cropHeight output height (multiple of 4; of 8 when binning)
     * @param bin        2x2 same-colour average downscale
     * @param outputSlot direct buffer sized to hold a worst-case frame
     * @return encoded frame size in bytes
     */
    public static int encode(ByteBuffer rawPlane, int width, int height, int stride,
                             boolean raw10, int cropTop, int cropHeight, boolean bin,
                             ByteBuffer outputSlot) throws IOException {
        if (!rawPlane.isDirect() || !outputSlot.isDirect()) {
            throw new IllegalArgumentException("raw plane and output slot must be direct buffers");
        }
        rawPlane.rewind();
        return nativeEncode(rawPlane, width, height, stride, raw10,
                cropTop, cropHeight, bin, outputSlot);
    }

    /**
     * Appends one encoded frame (produced by {@link #encode}) to the container.
     *
     * @param timestampNs strictly increasing frame timestamp
     * @return encoded frame size in bytes
     */
    public int writeFrame(ByteBuffer encoded, int length, long timestampNs,
                          String frameMetadata) throws IOException {
        if (nativePtr == 0) {
            throw new IllegalStateException("mcraw writer is closed");
        }
        return nativeWriteFrame(nativePtr, encoded, length, timestampNs, frameMetadata);
    }

    /** Number of frames successfully appended so far. */
    public long frameCount() {
        return nativePtr == 0 ? 0 : nativeFrameCount(nativePtr);
    }

    /** Appends one PCM16 audio chunk (interleaved channels, camera-time timestamp). */
    public void writeAudio(short[] samples, long timestampNs) throws IOException {
        if (nativePtr == 0) {
            throw new IllegalStateException("mcraw writer is closed");
        }
        nativeWriteAudio(nativePtr, samples, samples.length, timestampNs);
    }

    /**
     * Appends the gyro sample block (timestamps in the camera time domain).
     * Only the first {@code count} array entries are written - the backing
     * arrays may be larger than the recorded sample count.
     */
    public void writeGyro(long[] timestampsNs, float[] x, float[] y, float[] z,
                          int count) throws IOException {
        if (nativePtr == 0) {
            throw new IllegalStateException("mcraw writer is closed");
        }
        if (count <= 0 || count > timestampsNs.length) return;
        nativeWriteGyro(nativePtr, timestampsNs, x, y, z, count);
    }

    /**
     * Finalizes the container (frame index and footer) and closes the file.
     * Safe to call once; later calls are no-ops.
     */
    @Override
    public void close() throws IOException {
        if (nativePtr != 0) {
            long ptr = nativePtr;
            nativePtr = 0;
            nativeClose(ptr); // flushes indexes and footers, fsyncs
        }
        if (fallbackStream != null) {
            fallbackStream.close();
            fallbackStream = null;
        }
    }

    private static native long nativeCreate(int fd, String metadata);

    private static native int nativeEncode(ByteBuffer plane, int width, int height, int stride,
                                           boolean raw10, int cropTop, int cropHeight,
                                           boolean bin, ByteBuffer outputSlot);

    private static native int nativeWriteFrame(long ptr, ByteBuffer encoded, int length,
                                               long timestampNs, String frameMetadata);

    private static native long nativeFrameCount(long ptr);

    private static native void nativeWriteAudio(long ptr, short[] samples, int count, long timestampNs);

    private static native void nativeWriteGyro(long ptr, long[] timestamps, float[] x, float[] y, float[] z,
                                               int count);

    private static native void nativeClose(long ptr);
}
