package com.particlesdevs.photoncamera.processing.ml;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Common result type for the KernelNet parameter model, so the ONNX and ncnn
 * backends can be swapped without changing callers.
 */
public interface KernelNetResult {
    /** Half-resolution width. */
    int width();

    /** Half-resolution height. */
    int height();

    /** Channel-major [s1, s2, rho] floats, each channel width*height values. */
    FloatBuffer asFloatBuffer();

    /**
     * Base direct buffer behind {@link #asFloatBuffer} (single owner frees
     * it once uploaded; views don't free).
     */
    ByteBuffer params();
}
