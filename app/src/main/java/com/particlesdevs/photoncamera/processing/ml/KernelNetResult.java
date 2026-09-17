package com.particlesdevs.photoncamera.processing.ml;

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

    /** RGBA-interleaved (s1, s2, rho, 1) floats per texel, row-major, width*height*4 values. */
    FloatBuffer asFloatBuffer();
}
