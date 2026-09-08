package com.particlesdevs.photoncamera.util;

import android.graphics.Bitmap;
import android.os.Debug;

import java.nio.ByteBuffer;
public class Allocator{
    static {
        System.loadLibrary("allocator");
    }

    public static volatile boolean binning = false;

    public native static ByteBuffer allocate(int capacity);

    /**
     * Locks a software ARGB_8888 bitmap and wraps its pixel memory in a
     * direct ByteBuffer for direct GL readback. Returns null if the bitmap
     * cannot be wrapped (wrong config/stride or lock failure). The buffer
     * must NOT be passed to {@link #free}; release it with
     * {@link #unlockBitmap} instead.
     */
    public native static ByteBuffer wrapBitmap(Bitmap bitmap);

    public native static boolean unlockBitmap(Bitmap bitmap);

    public native static ByteBuffer allocateAndCopy(int capacity, ByteBuffer origin, int offset);
    /**
     * Copies a rectangular sub-region of a 2D image row by row, supporting an
     * arbitrary XY offset (unlike {@link #allocateAndCopy(int, ByteBuffer, int)}).
     * Output is tightly packed (no stride padding).
     *
     * @param cropWidthBytes bytes to copy per row (cropWidth * bytesPerPixel)
     * @param cropHeight     number of rows to copy
     * @param origin         source buffer (full frame)
     * @param row_stride     source row stride in bytes
     * @param offset         byte offset to the crop's top-left corner
     */
    public native static ByteBuffer allocateAndCopyCrop(int cropWidthBytes, int cropHeight, ByteBuffer origin, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyCropBinning(int cropWidth, int cropHeight, ByteBuffer origin, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyConvert(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyConvertBinning(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyBinning(int capacity, ByteBuffer origin, int width, int height, int row_stride);

    public native static void free(ByteBuffer buffer);
    public native static long getMemoryCount();

    /**
     * Logs current native memory totals with a pipeline-stage label so peak
     * usage can be compared across stages, frame counts and resolutions.
     * Logging only; zero behavior change.
     *
     * <p>Two numbers: the Allocator-tracked malloc total (burst buffers,
     * snapshots, staging) and the whole-process native heap (which also
     * covers Bitmap pixels and other native allocations that bypass
     * Allocator). GPU texture (VRAM) usage is tracked by neither.
     */
    public static void logStage(String logTag, String stage) {
        long tracked = getMemoryCount();
        long heap = Debug.getNativeHeapAllocatedSize();
        long dalvik = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        Log.d(logTag, "MemStage[" + stage + "] tracked=" + (tracked / 1048576)
                + "MB nativeHeap=" + (heap / 1048576)
                + "MB dalvikHeap=" + (dalvik / 1048576) + "MB");
    }
}
