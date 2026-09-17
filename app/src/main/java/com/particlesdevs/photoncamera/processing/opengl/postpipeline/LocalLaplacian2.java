package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.BufferUtils;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

/**
 * Fast Local Laplacian filter (Paris, Hasinoff, Kautz 2011, with the LUT
 * acceleration of Aubry et al. 2011), evaluated on the GPU.  The two
 * scale regimes use the two equivalent readings of the algorithm, each in
 * the place where it is exact:
 *
 * <ul>
 *   <li><b>Fine levels (single pyramid + 2D LUT):</b> each output pixel is
 *       rebuilt as expand(reconstruction) + r(G_l(p); anchor) -
 *       expand(r(G_{l+1}; anchor)).  Fine-scale windows only ever contain
 *       values near the anchor, where the remap curve is locally linear, so
 *       remapping the pyramid values directly is accurate here and costs
 *       one pass per level.</li>
 *   <li><b>Coarse levels (packed per-anchor pyramids):</b>
 *       reconstruction out = expand(out) + mix(lap_lo, lap_hi, a) with
 *       lap_k = R_k[l] - expand(R_k[l+1]) computed from pyramids of
 *       <i>remapped</i> images.  The distinction matters: the tone
 *       compression must be averaged INTO the reduction.  Remapping the
 *       already-reduced values instead does nothing at these scales (the
 *       blurred-in contrast is never far enough from the anchor to be
 *       compressed) and measurably pushes blacks down.  Following Aubry et
 *       al., the per-anchor pyramids are built from a downscaled Gaussian
 *       (all levels with extent &gt; {@link #COARSE_MAX} are above the seam
 *       and exact), packed as one texture column per anchor.</li>
 * </ul>
 *
 * <p>Shared properties:</p>
 * <ul>
 *   <li>The remap curves of all anchors are baked into a small 2D LUT
 *       (intensity x anchor, 24 anchors at (k + 0.5)/24).
 *       Bilinear filtering of the anchor axis performs the
 *       interpolation between neighbouring anchors for free.  Denser
 *       anchoring is not cosmetic: a pixel farther than half the anchor
 *       spacing from every row centre is remapped by one fixed curve, so
 *       flat areas turn into a pointwise tone map - at 6 rows the bottom
 *       ~12% of the range lost up to 36% brightness.</li>
 *   <li>The remap curve and its parameter semantics follow the established
 *       local-contrast convention: the shadows slope acts on details
 *       brighter than the local average, the highlights slope on darker
 *       ones.</li>
 *   <li>Neutral parameters (detail 0, shadows/highlights 1) make the curve
 *       the identity, every per-anchor pyramid the Gaussian itself, and so
 *       reproduce the input bit-exactly - the filter is a pure
 *       perturbation.</li>
 * </ul>
 *
 * <p>Cost: N downsample passes, 1 remap + N/2 reductions of a packed
 * coarse image (a few hundred KB), and one reconstruction pass per level -
 * about 2.5 full-resolution pixel passes in total.  Extra storage is one
 * single-channel R16F pyramid plus the packed coarse pyramids and a
 * 24 KB LUT.</p>
 */
public class LocalLaplacian2 extends Node {
    private static final int MAX_LEVELS = 12;
    private static final int LUT_SAMPLES = 512;
    // 24 anchors at gamma_k = (k + 0.5) / 24.  A
    // pixel farther than half the anchor spacing from every row center is
    // remapped by a single fixed curve, so sparse rows turn flat areas into
    // a pointwise tone map - at 6 rows the bottom ~12% of the range lost
    // up to 36% brightness and crushed to black below ~1%.
    private static final int LUT_ANCHORS = 24;
    // Coarsest downscaled extent that still carries the per-anchor
    // pyramids; levels coarser than this are represented by them.  64
    // keeps the packed texture within the 2048-texel GLES minimum
    // (24 anchors per row) while the within-cell variance the remap has
    // to see stays small.
    private static final int COARSE_MAX = 64;

    public LocalLaplacian2() {
        super("", "LocalLaplacian2");
    }

    @Tunable(title = "Enable", description = "Enable Local Laplacian Filter",
            category = "LLF", min = 0, max = 1, defaultValue = 1, step = 1)
    boolean enabled;

    @Tunable(title = "Detail", description = "Local contrast amplification near the local average; 0 is neutral",
            category = "LLF", min = -1.0f, max = 4.0f, defaultValue = 0.15f, step = 0.05f)
    float detail;

    @Tunable(title = "Highlights", description = "Slope for details darker than the local average; below 1 compresses",
            category = "LLF", min = 0.0f, max = 2.0f, defaultValue = 0.0f, step = 0.05f)
    float highlights;

    @Tunable(title = "Shadows", description = "Slope for details brighter than the local average; below 1 compresses",
            category = "LLF", min = 0.0f, max = 2.0f, defaultValue = 0.0f, step = 0.05f)
    float shadows;

    @Tunable(title = "Mid-tone Range", description = "Width of the tone band around the local average treated as mid-tones; highlights/shadows act only outside 2x this width. 0.5 spans the whole tonal range",
            category = "LLF", min = 0.001f, max = 1.0f, defaultValue = 0.5f, step = 0.01f)
    float midtone;

    @Override
    public void Compile() {
    }

    /**
     * Remap curve r(x; anchor).  The |c| &lt; 2*sigma branch is a quadratic
     * Bezier blending slope 1 at the anchor into the tail slope; "detail"
     * adds a mid-tone bump, giving total slope 1 + detail at the anchor
     * (detail &gt; 0 amplifies).  The shadows slope applies to deviations
     * BRIGHTER than the anchor, the highlights slope to darker ones.
     * Identity when detail = 0 and both slopes = 1.
     */
    private float remapCurve(float x, float anchor) {
        final float sigma = Math.min(1.f, Math.max(0.001f, midtone));
        final float c = x - anchor;
        float val;
        if (c > 2.f * sigma) {
            val = anchor + sigma + shadows * (c - sigma);
        } else if (c < -2.f * sigma) {
            val = anchor - sigma + highlights * (c + sigma);
        } else if (c > 0.f) {
            final float t = Math.min(c / (2.f * sigma), 1.f);
            val = anchor + sigma * 2.f * (1.f - t) * t + t * t * (sigma + sigma * shadows);
        } else {
            final float t = Math.min(-c / (2.f * sigma), 1.f);
            val = anchor - sigma * 2.f * (1.f - t) * t + t * t * (-sigma - sigma * highlights);
        }
        val += detail * c * (float) Math.exp(-c * c / (2.f * sigma * sigma / 3.f));
        return val;
    }

    private GLTexture buildRemapLut() {
        float[] values = new float[LUT_SAMPLES * LUT_ANCHORS];
        int i = 0;
        for (int anchorIdx = 0; anchorIdx < LUT_ANCHORS; anchorIdx++) {
            float anchor = (anchorIdx + 0.5f) / LUT_ANCHORS;
            for (int s = 0; s < LUT_SAMPLES; s++) {
                values[i++] = remapCurve(s / (float) (LUT_SAMPLES - 1), anchor);
            }
        }
        // FLOAT_16 storage is uploaded through direct GL_FLOAT byte buffers
        // in this framework (GLTexture casts pixels to ByteBuffer).
        return new GLTexture(new Point(LUT_SAMPLES, LUT_ANCHORS),
                new GLFormat(GLFormat.DataType.FLOAT_16, 1),
                BufferUtils.getFrom(values), GL_LINEAR, GL_CLAMP_TO_EDGE);
    }

    private GLTexture downsample(GLTexture input, boolean rgbInput) {
        Point size = new Point(Math.max(1, (input.mSize.x + 1) / 2),
                Math.max(1, (input.mSize.y + 1) / 2));
        GLTexture output = new GLTexture(size, new GLFormat(GLFormat.DataType.FLOAT_16));
        glProg.setDefine("INPUT_RGB", rgbInput ? 1 : 0);
        glProg.useAssetProgram("local_laplacian2/downsample");
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("inputSize", input.mSize);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    private GLTexture remapBase(GLTexture input, GLTexture lut) {
        GLTexture output = new GLTexture(input.mSize, new GLFormat(GLFormat.DataType.FLOAT_16));
        glProg.useAssetProgram("local_laplacian2/remap");
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("RemapLut", lut);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    /** Packed remap of a coarse Gaussian: column k = r(g; anchor_k). */
    private GLTexture remapPacked(GLTexture level, GLTexture lut) {
        Point size = new Point(LUT_ANCHORS * level.mSize.x, level.mSize.y);
        GLTexture output = new GLTexture(size, new GLFormat(GLFormat.DataType.FLOAT_16));
        glProg.setDefine("ANCHORS", LUT_ANCHORS);
        glProg.useAssetProgram("local_laplacian2/remappacked");
        glProg.setTexture("InputBuffer", level);
        glProg.setTexture("RemapLut", lut);
        glProg.setVar("columnSize", level.mSize);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    /** Column-wise Gaussian reduction of a packed pyramid level. */
    private GLTexture reducePacked(GLTexture packedIn, Point columnSize) {
        Point size = new Point(LUT_ANCHORS * Math.max(1, (columnSize.x + 1) / 2),
                Math.max(1, (columnSize.y + 1) / 2));
        GLTexture output = new GLTexture(size, new GLFormat(GLFormat.DataType.FLOAT_16));
        glProg.useAssetProgram("local_laplacian2/reducepacked");
        glProg.setTexture("InputBuffer", packedIn);
        glProg.setVar("inSize", columnSize);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    @Override
    public void Run() {
        final GLTexture input = previousNode.WorkingTexture;
        if (!enabled || input.mSize.x < 8 || input.mSize.y < 8) {
            WorkingTexture = input;
            glProg.close();
            return;
        }

        final GLTexture lut = buildRemapLut();

        // gaussian[1..levels]: luminance Gaussian pyramid, [0] is the RGB input.
        GLTexture[] gaussian = new GLTexture[MAX_LEVELS + 1];
        int levels = 0;
        GLTexture levelInput = input;
        boolean rgbInput = true;
        while (levels < MAX_LEVELS
                && (levelInput.mSize.x > 2 || levelInput.mSize.y > 2)) {
            GLTexture next = downsample(levelInput, rgbInput);
            gaussian[++levels] = next;
            levelInput = next;
            rgbInput = false;
        }

        // The packed per-anchor pyramids represent every level from
        // coarseStart up, so the seam sits where the remap-vs-reduce order
        // stops mattering.  The scan starts at 1: gaussian[0] stays null,
        // level 0 is the RGB input itself.
        int coarseStart = 1;
        while (coarseStart < levels
                && Math.max(gaussian[coarseStart].mSize.x,
                        gaussian[coarseStart].mSize.y) > COARSE_MAX) {
            coarseStart++;
        }
        coarseStart = Math.max(1, Math.min(coarseStart, levels - 1));

        GLTexture reconstructed;
        if (coarseStart <= levels - 1) {
            GLTexture[] packed = new GLTexture[levels + 1];
            packed[coarseStart] = remapPacked(gaussian[coarseStart], lut);
            for (int level = coarseStart + 1; level <= levels; level++) {
                packed[level] = reducePacked(packed[level - 1], gaussian[level - 1].mSize);
            }
            // reconstructs from the unremapped coarsest Gaussian
            // and adds remapped Laplacians on the way down.
            reconstructed = gaussian[levels];
            for (int level = levels - 1; level >= coarseStart; level--) {
                GLTexture output = new GLTexture(gaussian[level].mSize,
                        new GLFormat(GLFormat.DataType.FLOAT_16));
                glProg.setDefine("ANCHORS", LUT_ANCHORS);
                glProg.useAssetProgram("local_laplacian2/coarserecon");
                glProg.setTexture("PrevRecon", reconstructed);
                glProg.setTexture("PackedFine", packed[level]);
                glProg.setTexture("PackedCoarse", packed[level + 1]);
                glProg.setTexture("FineBuffer", gaussian[level]);
                glProg.setVar("fineSize", gaussian[level].mSize);
                glProg.setVar("coarseSize", gaussian[level + 1].mSize);
                glProg.drawBlocks(output);
                glProg.close();

                packed[level + 1].close();
                reconstructed.close();
                if (level > coarseStart) {
                    gaussian[level].close();
                }
                reconstructed = output;
            }
            packed[coarseStart].close();
        } else {
            // Degenerate: image too small for a packed stage, keep the
            // single-pyramid behaviour with the remapped base.
            reconstructed = remapBase(gaussian[levels], lut);
        }

        for (int level = coarseStart - 1; level >= 0; level--) {
            boolean finest = level == 0;
            GLTexture fine = finest ? input : gaussian[level];
            GLTexture coarse = gaussian[level + 1];
            GLTexture output = finest
                    ? basePipeline.getMain()
                    : new GLTexture(fine.mSize, new GLFormat(GLFormat.DataType.FLOAT_16));

            glProg.setDefine("FINE_RGB", finest ? 1 : 0);
            glProg.setDefine("FINAL_OUTPUT", finest ? 1 : 0);
            glProg.useAssetProgram("local_laplacian2/reconstruct");
            glProg.setTexture("FineBuffer", fine);
            glProg.setTexture("CoarseBuffer", coarse);
            glProg.setTexture("ReconstructedBuffer", reconstructed);
            glProg.setTexture("RemapLut", lut);
            glProg.setVar("coarseSize", coarse.mSize);
            glProg.drawBlocks(output);
            glProg.close();

            reconstructed.close();
            coarse.close();
            reconstructed = output;
        }

        lut.close();
        WorkingTexture = reconstructed;
    }
}
