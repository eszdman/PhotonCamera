package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import java.util.Locale;
import static android.opengl.GLES31.*;

/**
 * Banded luminance Richardson-Lucy solver. Requires a current GLES 3.1 context.
 * All textures use RGBA16F. Small kernels pack four adjacent luminance samples
 * per texel; larger kernels use the red channel and shared-memory convolution.
 */
public final class GpuCaptureDeconvolution implements AutoCloseable {
    private final GLProg program;
    private final Point size;
    private final boolean packed;
    private final int iterations, bandRows, halo;
    private final int initialize, divide, multiply, finish, mask, smoothMask;
    private final int horizontal, verticalDivide, verticalMultiply;
    private final GLTexture original, ratio, estimateA, estimateB;

    public static int kernelRadius(float sigma) {
        sigma = Math.max(0.2f, Math.min(2f, sigma));
        return sigma < 0.6f ? 1 : sigma <= 0.84f ? 2 : sigma <= 1.15f ? 3 : sigma <= 1.5f ? 4 : 6;
    }

    public GpuCaptureDeconvolution(GLProg program, Point size, float sigma,
                                   int iterations, int bandRows) {
        if (size.x <= 0 || size.y <= 0 || !Float.isFinite(sigma) || bandRows <= 0) {
            throw new IllegalArgumentException("Invalid deconvolution dimensions or radius");
        }
        this.program = program;
        this.size = new Point(size);
        this.iterations = Math.max(0, Math.min(10, iterations));
        this.bandRows = Math.min(size.y, bandRows);
        sigma = Math.max(0.2f, Math.min(2f, sigma));
        int radius = kernelRadius(sigma);
        packed = radius <= 2;
        // Two convolutions propagate support per iteration. A full dependency halo
        // makes band interiors agree with a whole-image solve, including seams.
        halo = Math.max(6, 2 * radius * this.iterations);
        initialize = compile("capturedeconv/initialize");
        finish = compile("capturedeconv/finish");
        mask = compile("capturedeconv/mask");
        smoothMask = compile("capturedeconv/smoothmask");
        if (!packed && this.iterations > 0) {
            String kernel = kernelExpression(sigma, radius, 16 + 2 * radius, "tile");
            divide = compileIteration(radius, kernel, false);
            multiply = compileIteration(radius, kernel, true);
        } else {
            divide = multiply = 0;
        }
        if (radius <= 2 && this.iterations > 0) {
            double g1 = Math.exp(-1.0 / (2.0*sigma*sigma));
            double g2 = radius == 2 ? Math.exp(-4.0 / (2.0*sigma*sigma)) : 0.0;
            double total = 1.0 + 2.0 * (g1 + g2);
            float centerWeight = (float)(1.0 / total);
            float pairWeight = (float)((g1 + g2) / total);
            float offset = (float)((g1 + 2.0*g2) / (g1 + g2));
            float corner = (float)((g2 / total) * (g2 / total));
            horizontal = compilePackedPass(false, false, centerWeight, pairWeight, offset, corner);
            verticalDivide = compilePackedPass(true, false, centerWeight, pairWeight, offset, corner);
            verticalMultiply = compilePackedPass(true, true, centerWeight, pairWeight, offset, corner);
        } else {
            horizontal = verticalDivide = verticalMultiply = 0;
        }
        Point scratchSize = new Point(packed ? (size.x + 3) / 4 : size.x, Math.min(size.y, this.bandRows + 2 * halo));
        GLFormat scratchFormat = new GLFormat(GLFormat.DataType.FLOAT_16, 4);
        GLTexture o = null, r = null, a = null, b = null;
        try {
            o = new GLTexture(scratchSize, scratchFormat, GL_NEAREST, GL_CLAMP_TO_EDGE);
            r = new GLTexture(scratchSize, scratchFormat, GL_NEAREST, GL_CLAMP_TO_EDGE);
            a = new GLTexture(scratchSize, scratchFormat, GL_NEAREST, GL_CLAMP_TO_EDGE);
            b = new GLTexture(scratchSize, scratchFormat, GL_NEAREST, GL_CLAMP_TO_EDGE);
        } catch (RuntimeException | Error failure) {
            if (o != null) o.close();
            if (r != null) r.close();
            if (a != null) a.close();
            if (b != null) b.close();
            throw failure;
        }
        original = o; ratio = r; estimateA = a; estimateB = b;
    }

    private int compile(String asset) {
        program.setDefine("PACKED", packed ? 1 : 0);
        program.setLayout(16, 16, 1);
        program.useAssetProgram(asset, true);
        return program.mCurrentProgramActive;
    }

    private int compileIteration(int radius, String kernel, boolean update) {
        program.setDefine("RADIUS", radius);
        program.setDefine("KERNEL", kernel);
        program.setDefine("UPDATE", update ? 1 : 0);
        return compile("capturedeconv/iterate");
    }

    private int compilePackedPass(boolean vertical, boolean update,
                                  float center, float pair, float offset, float corner) {
        program.setDefine("VERTICAL", vertical ? 1 : 0);
        program.setDefine("UPDATE", update ? 1 : 0);
        program.setDefine("CENTER_WEIGHT", center);
        program.setDefine("PAIR_WEIGHT", pair);
        program.setDefine("PAIR_OFFSET", offset);
        program.setDefine("CORNER_WEIGHT", corner);
        return compile("capturedeconv/packed");
    }

    private static String kernelExpression(float sigma, int radius, int stride, String plane) {
        // Circular support, normalized once on CPU; no exp() in the pixel loop.
        float support = radius == 1 ? 2f : radius == 2 ? 6.3504f
                : radius == 3 ? 11.9025f : radius == 4 ? 20.25f : 36f;
        double sum = 0;
        for (int y = -radius; y <= radius; y++) for (int x = -radius; x <= radius; x++) {
            if (x*x + y*y <= support) sum += Math.exp(-(x*x+y*y)/(2.0*sigma*sigma));
        }
        StringBuilder expression = new StringBuilder("0.0");
        for (int y = -radius; y <= radius; y++) for (int x = -radius; x <= radius; x++) {
            if (x*x + y*y > support) continue;
            double weight = Math.exp(-(x*x+y*y)/(2.0*sigma*sigma)) / sum;
            expression.append(String.format(Locale.US, " + %.10f * %s[center + (%d)]",
                    weight, plane, y * stride + x));
        }
        return expression.toString();
    }

    /** Includes all bands; never reads from output while writing it. Input/output are RGBA16F. */
    public void render(GLTexture input, GLTexture output, float amount, float contrastRatio, float clipLevel) {
        render(input, output, amount, contrastRatio, clipLevel, 0f, 0f);
    }

    /** Noise variance is noiseS * linear luminance + noiseO, in the input's units. */
    public void render(GLTexture input, GLTexture output, float amount, float contrastRatio,
                       float clipLevel, float noiseS, float noiseO) {
        if (input == output || !size.equals(input.mSize) || !size.equals(output.mSize)
                || input.mGLFormat != GL_RGBA16F || output.mGLFormat != GL_RGBA16F
                || !Float.isFinite(amount) || !Float.isFinite(contrastRatio)
                || !Float.isFinite(noiseS) || !Float.isFinite(noiseO) || noiseS < 0f || noiseO < 0f
                || !Float.isFinite(clipLevel) || clipLevel <= 0f) {
            throw new IllegalArgumentException("Invalid deconvolution textures or blend parameters");
        }
        contrastRatio = Math.max(0f, Math.min(1f, contrastRatio));
        boolean useMask = iterations > 0 && (contrastRatio > 0f || noiseS > 0f || noiseO > 0f);
        glMemoryBarrier(GL_TEXTURE_FETCH_BARRIER_BIT | GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        for (int y = 0; y < size.y; y += bandRows) {
            int rows = Math.min(bandRows, size.y - y);
            int origin = Math.max(0, y - halo);
            int height = Math.min(size.y, y + rows + halo) - origin;
            program.rebindProgram(initialize);
            program.setTexture("InputBuffer", input);
            program.setVar("originY", origin);
            program.setVar("validSize", size.x, height);
            bindWrite(original);
            dispatchPlane(height);
            GLTexture estimate = original;
            for (int i = 0; i < iterations; i++) {
                if (horizontal != 0) {
                    separableBlur(estimate, height);
                    program.rebindProgram(verticalDivide);
                    program.setTexture("SourceBuffer", estimateB);
                    program.setTexture("CornerBuffer", estimate);
                    program.setTexture("OriginalBuffer", original);
                    program.setVar("validSize", size.x, height);
                    bindWrite(ratio);
                    dispatchPlane(height);
                    separableBlur(ratio, height);
                    program.rebindProgram(verticalMultiply);
                    program.setTexture("SourceBuffer", estimateB);
                    program.setTexture("CornerBuffer", ratio);
                    program.setTexture("OriginalBuffer", original);
                    program.setTexture("EstimateBuffer", estimateA);
                    program.setVar("firstIteration", i == 0 ? 1 : 0);
                    program.setVar("validSize", size.x, height);
                    // Each invocation reads/writes only its own estimate; neighbours
                    // come exclusively from the separate ratio and blur textures.
                    glBindImageTexture(0, estimateA.mTextureID, 0, false, 0, GL_WRITE_ONLY, estimateA.mGLFormat);
                    dispatchPlane(height);
                    estimate = estimateA;
                } else {
                    program.rebindProgram(divide);
                    program.setTexture("SourceBuffer", estimate);
                    program.setTexture("OriginalBuffer", original);
                    program.setVar("validSize", size.x, height);
                    bindWrite(ratio);
                    dispatchPlane(height);
                    GLTexture next = estimate == estimateA ? estimateB : estimateA;
                    program.rebindProgram(multiply);
                    program.setTexture("SourceBuffer", ratio);
                    program.setTexture("EstimateBuffer", estimate);
                    program.setVar("validSize", size.x, height);
                    bindWrite(next);
                    dispatchPlane(height);
                    estimate = next;
                }
            }
            if (useMask) {
                // Original and ratio are dead after the solve: reuse them for the mask.
                program.rebindProgram(mask);
                program.setTexture("InputBuffer", input);
                program.setVar("originY", origin);
                program.setVar("validSize", size.x, height);
                program.setVar("contrastRatio", contrastRatio);
                program.setVar("noiseModel", noiseS, noiseO);
                program.setVar("clipLevel", clipLevel);
                bindWrite(original);
                dispatchPlane(height);
                program.rebindProgram(smoothMask);
                program.setTexture("MaskBuffer", original);
                program.setVar("validSize", size.x, height);
                bindWrite(ratio);
                dispatchPlane(height);
            }
            program.rebindProgram(finish);
            program.setTexture("InputBuffer", input);
            program.setTexture("EstimateBuffer", estimate);
            program.setTexture("MaskBuffer", ratio);
            program.setTexture("LocalMaskBuffer", original);
            program.setVar("validSize", size.x, height);
            program.setVar("originY", origin);
            program.setVar("outputY", y);
            program.setVar("outputRows", rows);
            program.setVar("amount", Math.max(0f, Math.min(1f, amount)));
            program.setVar("useMask", useMask ? 1 : 0);
            program.setVar("clipLevel", clipLevel);
            bindWrite(output);
            dispatch(size.x, rows);
        }
        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        program.closed = true;
    }

    private void separableBlur(GLTexture source, int height) {
        program.rebindProgram(horizontal);
        program.setTexture("SourceBuffer", source);
        program.setVar("validSize", size.x, height);
        bindWrite(estimateB);
        dispatchPlane(height);
    }

    private void dispatchPlane(int height) {
        dispatch(packed ? (size.x + 3) / 4 : size.x, height);
    }

    private static void bindWrite(GLTexture texture) {
        glBindImageTexture(0, texture.mTextureID, 0, false, 0, GL_WRITE_ONLY, texture.mGLFormat);
    }
    private static void dispatch(int width, int height) {
        glDispatchCompute((width + 15) / 16, (height + 15) / 16, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
    }
    public long scratchBytes() {
        return (long) original.mSize.x * original.mSize.y * 32;
    }
    @Override public void close() {
        original.close(); ratio.close(); estimateA.close(); estimateB.close();
    }
}
