package com.particlesdevs.photoncamera.util;

import java.nio.ByteBuffer;
public class Allocator{
    static {
        System.loadLibrary("allocator");
    }

    public static boolean binning = false;

    public native static ByteBuffer allocate(int capacity);

    public native static ByteBuffer allocateAndCopy(int capacity, ByteBuffer origin, int offset);
    public native static ByteBuffer allocateAndCopyConvert(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyConvertBinning(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyBinning(int capacity, ByteBuffer origin, int width, int height, int row_stride);

    /**
     * Converts a packed uint16 raw frame (width*height, sensor site order
     * (x&1)+(y&1)*2) into a NEW direct ByteBuffer of white/black-level
     * normalized fp16 samples, ready for FLOAT_16 GL texture upload.
     * Implemented by the RawF16 class (rawF16.cpp, NEON-vectorized); free
     * with {@link #free}.
     */
    public native static ByteBuffer createF16(ByteBuffer origin, int width, int height, int whiteLevel, float[] blackLevel);

    /**
     * Inverse of {@link #createF16}: re-encodes a normalized fp16 buffer back
     * into raw uint16 counts (u = round(clamp(f,0,1)*(wl-bl[site])+bl[site]))
     * in a NEW direct ByteBuffer, e.g. for the uint16 DNG save path.
     */
    public native static ByteBuffer createU16FromF16(ByteBuffer origin, int width, int height, int whiteLevel, float[] blackLevel);

    public native static void free(ByteBuffer buffer);
    public native static long getMemoryCount();
}
