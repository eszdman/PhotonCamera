package com.particlesdevs.photoncamera.processing.ml;

import java.nio.ByteBuffer;

/**
 * Common result type for the KernelNet parameter model, so the ONNX and ncnn
 * backends can be swapped without changing callers.
 */
public interface KernelNetResult {
    /** Half-resolution width. */
    int width();

    /** Half-resolution height. */
    int height();

    /**
     * Direct native-order RGBA-interleaved (s1, s2, rho, 1) fp16 halves per
     * texel, row-major, width*height*4 halves — the exact GL_RGBA16F layout.
     * Single owner frees it once uploaded; views don't free.
     */
    ByteBuffer params();
}
