package com.particlesdevs.photoncamera.util;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;
public class Allocator{
    static {
        System.loadLibrary("allocator");
    }

    public static boolean binning = false;

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
    public native static ByteBuffer allocateAndCopyConvert(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyConvertBinning(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyBinning(int capacity, ByteBuffer origin, int width, int height, int row_stride);

    public native static void free(ByteBuffer buffer);
    public native static long getMemoryCount();
}
