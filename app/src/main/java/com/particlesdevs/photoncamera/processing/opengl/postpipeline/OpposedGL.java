package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLBuffer;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;

import static android.opengl.GLES31.GL_ALL_BARRIER_BITS;
import static android.opengl.GLES31.glMemoryBarrier;

/**
 * GPU half of the "inpaint opposed" chrominance reduction, replacing the CPU
 * scan: three compute dispatches over the raw texture that is already
 * uploaded for Bayer2Float's fragment pass.
 *
 * <ul>
 *   <li>pass 1 (opposedmask): one invocation per 3x3 cell marks per-channel
 *       clip bits and atomically counts clipped cells - the count readback
 *       decides whether the reconstruction engages at all</li>
 *   <li>pass 2 (opposeddilate): radius-3 octagon dilation of the mask bits</li>
 *   <li>pass 3 (opposedaccum): one workgroup per row accumulates
 *       (value - refavg) per channel into shared memory and writes six ints
 *       per row (Q16.16 sums + counts) - no float atomics anywhere</li>
 * </ul>
 *
 * The only CPU-GPU traffic is one uint after pass 1 and six ints per row
 * after pass 3; the final chrominance averages are computed on the CPU.
 * The per-pixel reconstruction itself stays in tofloat.glsl (HLRECON path).
 */
final class OpposedGL {
    /** darktable highlights_clip_magics[DT_IOP_HIGHLIGHTS_OPPOSED]. */
    static final float CLIP_MAGIC = 0.987f;
    private static final float LO_CLIP_FACTOR = 0.2f;
    private static final int MIN_SAMPLES = 100;

    private OpposedGL() {
    }

    /**
     * @param raw         the merged raw texture (uint16, 1 or 3 channels) Bayer2Float samples
     * @param blackLevel  per-CFA-position black level (R, G1, G2, B), raw counts
     * @param whitePoint  camera neutral point (unused for rgbLayout)
     * @param clip        clip point in white-balanced normalized space, incl. {@link #CLIP_MAGIC}
     * @return null when no photosite is clipped, otherwise the vec3 uniform for tofloat.glsl
     */
    static float[] compute(GLProg prog, GLTexture raw, Point size, int cfaPattern, boolean rgbLayout,
                           float whiteLevel, float[] blackLevel, float[] whitePoint, float clip) {
        final int mw = size.x / 3, mh = size.y / 3;
        if (size.x < 16 || size.y < 16 || mw < 8 || mh < 8 || whiteLevel <= 0.f) return null;

        float[] lvl = {
                blackLevel[0] / whiteLevel,
                (blackLevel[1] + blackLevel[2]) * 0.5f / whiteLevel,
                blackLevel[3] / whiteLevel};
        float[] wp = {1.f, 1.f, 1.f};
        if (!rgbLayout && whitePoint != null) {
            for (int c = 0; c < 3; c++) wp[c] = whitePoint[c] > 0.f ? whitePoint[c] : 1.f;
        }
        float[] clipThr = new float[3];
        float[] loClip = new float[3];
        for (int c = 0; c < 3; c++) {
            clipThr[c] = whiteLevel * (lvl[c] + clip * wp[c] * (1.f - lvl[c]));
            loClip[c] = LO_CLIP_FACTOR * clip;
        }

        GLFormat fmt = new GLFormat(GLFormat.DataType.UNSIGNED_32);
        GLBuffer maskA = new GLBuffer(mw * mh, fmt);
        GLBuffer maskB = new GLBuffer(mw * mh, fmt);
        GLBuffer counter = new GLBuffer(1, fmt);
        GLBuffer rows = new GLBuffer(size.y * 6, fmt);
        try {
            // pass 1: per-cell clip mask + clipped-cell count
            prog.setLayout(8, 8, 1);
            prog.setDefine("QUAD", cfaPattern == -2);
            prog.setDefine("RGBLAYOUT", rgbLayout);
            prog.useAssetProgram("Bayer2Float/opposedmask", true);
            prog.setTexture("u_raw", raw);
            prog.setVar("u_size", size.x, size.y);
            prog.setVar("u_msize", mw, mh);
            prog.setVar("u_level", lvl);
            prog.setVar("u_clipthr", clipThr);
            prog.setVar("CfaPattern", cfaPattern);
            prog.setBufferCompute("MaskOut", maskA);
            prog.setBufferCompute("ClipCount", counter);
            prog.computeAuto(new Point(mw, mh), 1);
            glMemoryBarrier(GL_ALL_BARRIER_BITS);
            int[] clipped = counter.readBufferIntegers(false);
            if (clipped == null || clipped.length == 0 || clipped[0] == 0) return null;

            // pass 2: dilate the mask bits so the statistics cover the ring around clips
            prog.setLayout(8, 8, 1);
            prog.useAssetProgram("Bayer2Float/opposeddilate", true);
            prog.setVar("u_msize", mw, mh);
            prog.setBufferCompute("MaskIn", maskA);
            prog.setBufferCompute("MaskDil", maskB);
            prog.computeAuto(new Point(mw, mh), 1);
            glMemoryBarrier(GL_ALL_BARRIER_BITS);

            // pass 3: accumulate (value - refavg) per row
            prog.setLayout(64, 1, 1);
            prog.setDefine("QUAD", cfaPattern == -2);   // useProgram clears the define list per program
            prog.setDefine("RGBLAYOUT", rgbLayout);
            prog.useAssetProgram("Bayer2Float/opposedaccum", true);
            prog.setTexture("u_raw", raw);
            prog.setVar("u_size", size.x, size.y);
            prog.setVar("u_msize", mw, mh);
            prog.setVar("u_whitelevel", whiteLevel);
            prog.setVar("u_level", lvl);
            prog.setVar("u_whitepoint", wp);
            prog.setVar("u_loclip", loClip);
            prog.setVar("u_clip", clip);
            prog.setVar("CfaPattern", cfaPattern);
            prog.setBufferCompute("Mask", maskB);
            prog.setBufferCompute("RowSums", rows);
            prog.computeAuto(new Point(1, size.y), 1);
            glMemoryBarrier(GL_ALL_BARRIER_BITS);

            int[] r = rows.readBufferIntegers(false);
            double[] sum = {0., 0., 0.};
            long[] cnt = {0, 0, 0};
            if (r != null) {
                for (int y = 0; y < size.y; y++) {
                    for (int c = 0; c < 3; c++) {
                        sum[c] += (double) r[y * 6 + c] / 65536.0;
                        cnt[c] += r[y * 6 + 3 + c];
                    }
                }
            }
            float[] chrominance = new float[3];
            for (int c = 0; c < 3; c++)
                chrominance[c] = cnt[c] > MIN_SAMPLES ? (float) (sum[c] / cnt[c]) : 0.f;
            return chrominance;
        } finally {
            maskA.close();
            maskB.close();
            counter.close();
            rows.close();
        }
    }
}
