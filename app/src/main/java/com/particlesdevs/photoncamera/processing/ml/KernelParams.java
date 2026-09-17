package com.particlesdevs.photoncamera.processing.ml;

import java.nio.FloatBuffer;

/**
 * Helpers for ferrying KernelNet parameter maps to the GPU in row bands.
 *
 * <p>The model emits channel-major (s1, s2, rho) floats, but the shaders
 * sample them as interleaved RGBA. Interleaving the whole map at once costs
 * a full {@code float[4*w*h]} (~245 MB at 64 MP) just to cross GL contexts;
 * banding keeps the transient to a few MB while uploading byte-identical
 * texels (the driver converts FLOAT-&gt;HALF per texel, independent of
 * sub-rect tiling).
 */
public final class KernelParams {

    /** Rows interleaved+uploaded per band (~4.7 MB band buffer at 64 MP). */
    public static final int BAND_ROWS = 64;

    private KernelParams() {}

    /**
     * Interleaves rows {@code [y0, y0+rows)} of a channel-major map into
     * RGBA floats {@code (s1, s2, rho, 1)}, element-identical to interleaving
     * the whole map. Absolute access only; positions of both buffers are
     * left untouched. {@code dst} must hold at least {@code w*rows*4}.
     */
    public static void interleaveBand(FloatBuffer channelMajor, int w, int plane,
                                      int y0, int rows, FloatBuffer dst) {
        for (int y = 0; y < rows; y++) {
            int i = (y0 + y) * w;
            int o = y * w * 4;
            for (int x = 0; x < w; x++) {
                dst.put(o++, channelMajor.get(i));
                dst.put(o++, channelMajor.get(plane + i));
                dst.put(o++, channelMajor.get(2 * plane + i));
                dst.put(o++, 1.0f);
                i++;
            }
        }
    }
}
