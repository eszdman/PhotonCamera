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
 * acceleration of Aubry et al. 2011), evaluated on the GPU in a single pass
 * per pyramid level.
 *
 * <p>What makes this a correct LLF, unlike the previous LocalLaplacian:</p>
 * <ul>
 *   <li>The coarse level is remapped <b>before</b> the expansion filter:
 *       each coarse texel passes through the remap curve and only then is
 *       interpolated ({@code expand(remap(G))}, not {@code remap(expand(G))}).
 *       The filter-after-remap structure is what separates detail from edges
 *       at every scale.</li>
 *   <li>The remap anchor of an output pixel is its own Gaussian value, held
 *       fixed across the whole expansion of that pixel - exactly the paper's
 *       interpolation between discrete-anchor remapped pyramids.  Anchoring
 *       per tap instead would telescope the reconstruction into a pointwise
 *       tone curve and cancel the multi-scale behaviour.</li>
 *   <li>The remap curves of all anchors are baked into a small 2D LUT
 *       (intensity x anchor).  Bilinear filtering of the anchor axis performs
 *       the paper's interpolation between neighbouring anchors for free.</li>
 *   <li>The remap curve, its parameter semantics and the 6 anchors at
 *       (k + 0.5)/6 follow the established local-contrast convention: the
 *       shadows slope acts on details brighter than the local average, the
 *       highlights slope on darker ones.</li>
 *   <li>The coarsest Gaussian level is remapped too: it is the base the
 *       Laplacian reconstruction starts from.</li>
 *   <li>Neutral parameters (detail 0, shadows/highlights 1) reproduce the
 *       input bit-exactly, so the filter can be reasoned about as a pure
 *       perturbation.</li>
 * </ul>
 *
 * <p>Cost: N downsample passes, one tiny pointwise pass for the remapped
 * base and N reconstruction passes over a pyramid that shrinks 4x per level
 * - about 2.3 full-resolution pixel passes in total.  Extra storage is one
 * single-channel R16F pyramid plus a 16 KB LUT.</p>
 */
public class LocalLaplacian2 extends Node {
    private static final int MAX_LEVELS = 12;
    private static final int LUT_SAMPLES = 512;
    // 6 anchors at gamma_k = (k + 0.5) / 6.
    private static final int LUT_ANCHORS = 6;

    public LocalLaplacian2() {
        super("", "LocalLaplacian2");
    }

    @Tunable(title = "Enable", description = "Enable Local Laplacian Filter",
            category = "LLF", min = 0, max = 1, defaultValue = 1, step = 1)
    boolean enabled;

    @Tunable(title = "Detail", description = "Local contrast amplification near the local average; 0 is neutral",
            category = "LLF", min = -1.0f, max = 4.0f, defaultValue = 0.25f, step = 0.05f)
    float detail;

    @Tunable(title = "Highlights", description = "Slope for details darker than the local average; below 1 compresses",
            category = "LLF", min = 0.0f, max = 2.0f, defaultValue = 0.5f, step = 0.05f)
    float highlights;

    @Tunable(title = "Shadows", description = "Slope for details brighter than the local average; below 1 compresses",
            category = "LLF", min = 0.0f, max = 2.0f, defaultValue = 0.5f, step = 0.05f)
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
                && (levelInput.mSize.x > 4 || levelInput.mSize.y > 4)) {
            GLTexture next = downsample(levelInput, rgbInput);
            gaussian[++levels] = next;
            levelInput = next;
            rgbInput = false;
        }

        // The reconstruction base is the remapped coarsest level.  The
        // coarsest Gaussian itself stays alive: the first reconstruction
        // pass still samples it through the remap LUT.
        GLTexture reconstructed = remapBase(gaussian[levels], lut);

        for (int level = levels - 1; level >= 0; level--) {
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
