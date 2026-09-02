package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.ml.KernelNetNcnnProcessor;
import com.particlesdevs.photoncamera.processing.ml.KernelNetResult;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

/**
 * Expands a cropped capture to the requested full-frame output size.
 *
 * <p>When KernelNet params are available (exported by the merge pass, or
 * inferred on the single-frame path by {@link KernelNetPrep}), the crop is
 * reconstructed with a locally anisotropic Gaussian kernel (Wronski et al.,
 * "Procedural Kernel Networks", section 4.3) steered by those params -
 * edge-aligned interpolation at no additional inference cost, plus an
 * optional edge-aligned unsharp term for acutance at extreme zoom. Otherwise
 * the pipeline's existing bicubic GPU interpolation path is used.</p>
 */
public final class UpscaleCrop extends Node {

    @Tunable(title = "KernelNet upscale sigma scale", category = "Upscale", description = "Fine trim on the KernelNet map sigmas after the automatic map-to-crop rescaling (1.0 = calibrated)", min = 0.1f, max = 4.0f, step = 0.05f, defaultValue = 0.9f)
    float sigmaScale;

    @Tunable(title = "KernelNet upscale abs min sigma", category = "Upscale", description = "Absolute floor on the reconstruction kernel sigma in crop pixels (numerical guard against tap-weight collapse)", min = 0.05f, max = 1.0f, step = 0.01f, defaultValue = 0.25f)
    float absMinPx;

    @Tunable(title = "KernelNet upscale min sigma (output px)", category = "Upscale", description = "Additional sigma floor measured in output pixels - keeps the reconstruction equally crisp at every zoom factor (lower = sharper at extreme zoom)", min = 0.1f, max = 8.0f, step = 0.05f, defaultValue = 1.25f)
    float outFloorPx;

    @Tunable(title = "KernelNet upscale max sigma", category = "Upscale", description = "Cap on the reconstruction kernel sigma in crop pixels (kept below radius/2 so the window rim never clips the kernel)", min = 0.5f, max = 6.0f, step = 0.1f, defaultValue = 1.2f)
    float sigmaMaxPx;

    @Tunable(title = "KernelNet upscale blend", category = "Upscale", description = "Mix between bicubic (0) and the anisotropic KernelNet reconstruction (1)", min = 0.0f, max = 1.0f, step = 0.05f, defaultValue = 1.0f)
    float anisoStrength;

    @Tunable(title = "KernelNet upscale radius", category = "Upscale", description = "Half-width of the anisotropic reconstruction window in crop pixels (5 = 11x11 taps). Must stay above sharpWide*sigmaMax*2 so the wide unsharp pass fits the window", min = 1, max = 5, step = 1, defaultValue = 5)
    int kernelRadius;

    @Tunable(title = "KernelNet upscale max elongation", category = "Upscale", description = "Caps the sigma ratio max(s1,s2)/min(s1,s2) - prevents knife-thin edge kernels", min = 1.0f, max = 8.0f, step = 0.5f, defaultValue = 7.0f)
    float maxElong;

    @Tunable(title = "KernelNet upscale acutance", category = "Upscale", description = "Edge-aligned unsharp-mask amount: sharpened = aniso + amt*gate*(aniso - wider aniso), where gate is the used fraction of maxElong (0 in flats, 1 on strong edges); 0 keeps the output strictly convex", min = 0.0f, max = 1.5f, step = 0.05f, defaultValue = 0.75f)
    float sharpAmt;

    @Tunable(title = "KernelNet upscale acutance width", category = "Upscale", description = "Sigma multiplier of the wide pass used by the unsharp term (higher = softer wide pass, stronger bandpass). Effective value is capped at radius/(2*sigmaMax) so the wide kernel fits the window", min = 1.1f, max = 4.0f, step = 0.05f, defaultValue = 2.2f)
    float sharpWide;

    @Tunable(title = "Debug: dump kernelnet params", category = "Upscale", description = "Renders the KernelNet params map (s1, s2, rho as RGB) into the debug overlay", min = 0, max = 1, step = 1, defaultValue = 0)
    int debugParams;

    @Tunable(title = "Debug: bilinear vs reconstruction", category = "Upscale", description = "0=full anisotropic reconstruction (default), 1=plain bilinear upscale of input", min = 0, max = 1, step = 1, defaultValue = 0)
    int debugUpscale;

    private GLTexture kernelsMapTex;

    public UpscaleCrop() {
        super("", "UpscaleCrop");
    }

    @Override
    public void Compile() {
    }

    /**
     * Downstream nodes (LocalLaplacian, CaptureSharpening, CorrectingFlow,
     * Sharpen2) draw into the pipeline's main ping-pong textures, which
     * Bayer2Float created at crop size. Once the crop has been expanded to
     * the full-frame output size, those targets must be rebuilt to match.
     */
    private void resizeMainTextures(Point size) {
        GLFormat fmt = new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim);
        GLTexture[] mains = {basePipeline.main1, basePipeline.main2, basePipeline.main3};
        for (int i = 0; i < mains.length; i++) {
            if (mains[i] != null) {
                mains[i].close();
            }
            mains[i] = new GLTexture(size, fmt, null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        }
        basePipeline.main1 = mains[0];
        basePipeline.main2 = mains[1];
        basePipeline.main3 = mains[2];
    }

    /** Renders the interleaved (s1, s2, rho, 1) param buffer as a debug bitmap. */
    private void dumpParams(FloatBuffer params, Point paramsSize) {
        if (debugParams == 0 || params == null || paramsSize == null) return;
        PostPipeline pp = (PostPipeline) basePipeline;
        int[] pix = new int[paramsSize.x * paramsSize.y];
        params.position(0);
        for (int i = 0; i < pix.length; i++) {
            float s1 = Math.min(params.get(i * 4) * 0.5f, 1.0f);
            float s2 = Math.min(params.get(i * 4 + 1) * 0.5f, 1.0f);
            float rho = Math.min(Math.max((params.get(i * 4 + 2) + 1.0f) * 0.5f, 0.0f), 1.0f);
            int r = (int) (s1 * 255.0f);
            int g = (int) (s2 * 255.0f);
            int b = (int) (rho * 255.0f);
            pix[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        Bitmap bmp = Bitmap.createBitmap(paramsSize.x, paramsSize.y, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pix, 0, paramsSize.x, 0, 0, paramsSize.x, paramsSize.y);
        pp.debugData.add(bmp);
    }

    @Override
    public void Run() {
        GLTexture input = previousNode.WorkingTexture;

        if (input == null) {
            WorkingTexture = null;
            return;
        }

        if (basePipeline.mParameters.fullRawSize == null ||
                !basePipeline.mParameters.isCropped) {
            WorkingTexture = input;
            return;
        }

        Point fullSize = basePipeline.mParameters.fullRawSize;

        if (fullSize.x <= 0 ||
                fullSize.y <= 0 ||
                input.mSize.x <= 0 ||
                input.mSize.y <= 0) {
            WorkingTexture = input;
            return;
        }

        /*
         * Keep output dimensions divisible by four, matching the crop/output
         * sizing convention already used by PostPipeline.
         */
        Point target = new Point(
                fullSize.x & ~3,
                fullSize.y & ~3);

        if (target.x < 4) {
            target.x = 4;
        }

        if (target.y < 4) {
            target.y = 4;
        }

        if (target.equals(input.mSize)) {
            WorkingTexture = input;
            return;
        }

        PostPipeline pp = (PostPipeline) basePipeline;
        FloatBuffer params = pp.kernelParams;
        Point paramsSize = pp.kernelParamsSize;
        if (params == null && pp.kernelNetSingleThread != null) {
            // Collect the single-frame inference started by KernelNetPrep.
            try {
                pp.kernelNetSingleThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pp.kernelNetSingleThread = null;
            KernelNetResult result = pp.kernelNetSingleResult.getAndSet(null);
            if (result != null) {
                float[] rgba = KernelNetNcnnProcessor.toInterleavedRGBA(result);
                if (rgba != null) {
                    params = FloatBuffer.wrap(rgba);
                    paramsSize = new Point(result.width(), result.height());
                }
            }
        }

        boolean hasParams = params != null && paramsSize != null && paramsSize.x > 0 && paramsSize.y > 0;
        if (hasParams) {
            // Map-health sanity check: if the first param texel is NaN or out
            // of the model's range, the map is garbage and the reconstruction
            // would mirror it - fall back to the bicubic path instead.
            params.position(0);
            float s1 = params.get(0);
            float s2 = params.get(1);
            float rho = params.get(2);
            if (Float.isNaN(s1) || Float.isNaN(s2) || Float.isNaN(rho)
                    || s1 < 0.0f || s1 > 4.0f || s2 < 0.0f || s2 > 4.0f
                    || rho < -2.0f || rho > 2.0f) {
                hasParams = false;
            }
        }

        if (hasParams) {
            kernelsMapTex = new GLTexture(paramsSize,
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
            params.position(0);
            kernelsMapTex.loadData(params);
            dumpParams(params, paramsSize);

            /*
             * Per-axis sigma floor in crop pixels: a constant floor in output
             * pixels keeps the reconstruction equally crisp at every zoom
             * factor, with an absolute crop-pixel floor so the tap weights
             * never collapse numerically. The floor is capped at sigmaMaxPx:
             * clamp(s, min, max) with min > max is undefined in GLSL.
             */
            float zoomX = input.mSize.x / (float) target.x;
            float zoomY = input.mSize.y / (float) target.y;
            float minX = Math.min(Math.max(absMinPx, outFloorPx * zoomX), sigmaMaxPx);
            float minY = Math.min(Math.max(absMinPx, outFloorPx * zoomY), sigmaMaxPx);

            GLTexture out = new GLTexture(target, input.mFormat);
            glProg.useAssetProgram("upscalecrop/anisoupscale");
            glProg.setVar("fullSize", target);
            glProg.setVar("scaleRatio", 1.0f/zoomX, 1.0f/zoomY);
            glProg.setVar("sigmaScale", sigmaScale);
            glProg.setVar("sigmaMinPx", minX, minY);
            glProg.setVar("sigmaMaxPx", sigmaMaxPx);
            glProg.setVar("strength", anisoStrength);
            glProg.setVar("kernelRadius", kernelRadius);
            glProg.setVar("sharpAmt", sharpAmt);
            glProg.setVar("sharpWide", sharpWide);
            glProg.setVar("maxElong", maxElong);
            glProg.setVar("debugMode", debugUpscale);
            glProg.setTexture("InputBuffer", input);
            glProg.setTexture("KernelsMap", kernelsMapTex);
            glProg.drawBlocks(out);
            glProg.closed = true;
            WorkingTexture = out;
        } else {
            WorkingTexture = glUtils.interpolate(input, target);
        }

        /*
         * Downstream nodes and the final GL output need to use the new texture
         * dimensions rather than the original crop dimensions. (pp.cropSize
         * intentionally keeps the crop-region size: RotateWatermark sizes its
         * sampling from its actual input texture now.)
         */
        resizeMainTextures(target);
        basePipeline.workSize = new Point(target);
    }

    @Override
    public void AfterRun() {
        if (kernelsMapTex != null) {
            kernelsMapTex.close();
            kernelsMapTex = null;
        }
    }
}
