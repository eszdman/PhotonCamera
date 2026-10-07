package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.content.Context;
import android.graphics.Point;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ml.KernelNetNcnnProcessor;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_MIRRORED_REPEAT;
import static android.opengl.GLES20.GL_NEAREST;

/**
 * Builds the KernelNet input luma plane and starts the parameter-model
 * inference on a worker thread.
 *
 * <p>Covers both the legacy cropped path (the multi-frame path already
 * exports its params from ESD4D) and the explicit per-sensor
 * upscale/downscale factor ({@code upscaleFactor} / {@code upscaleFactorQb}
 * on {@link com.particlesdevs.photoncamera.processing.render.Parameters}),
 * which also applies to uncropped shots. The guided aniso reconstruction in
 * {@link UpscaleCrop} is used for both up and down resizes, so inference is
 * started whenever a resize will happen.
 *
 * <p>The GL-thread work here is one packed-luma pass plus one small readback;
 * the ncnn inference then overlaps the rest of the post pipeline and is
 * collected by {@link UpscaleCrop}. Params unavailable paths (RGB layout,
 * no resize, model failure) fall back to bicubic in UpscaleCrop.</p>
 */
public final class KernelNetPrep extends Node {

    @Tunable(title = "Single-frame KernelNet sigma multiplier", category = "Upscale", description = "Scales the noise sigma fed to KernelNet when params are inferred for a single-frame crop (multi-frame crops reuse the merge params)", min = 0.1f, max = 20.0f, step = 0.05f, defaultValue = 1.0f)
    float singleSigmaMpy;

    private GLTexture rawTex;
    private GLTexture lumaTex;

    public KernelNetPrep() {
        super("", "KernelNetPrep");
    }

    @Override
    public void Compile() {
    }

    @Override
    public int halo() {
        return 0; // passthrough; side inference stays full-frame by design
    }

    @Override
    public void Run() {
        WorkingTexture = previousNode.WorkingTexture;
        PostPipeline pp = (PostPipeline) basePipeline;
        try {
            if (pp.kernelParams != null || pp.kernelNetSingleThread != null) return;
            com.particlesdevs.photoncamera.processing.render.Parameters params =
                    basePipeline.mParameters;
            if (params == null) return;
            float factor = params.getActiveUpscaleFactor();
            boolean explicitResize =
                    !com.particlesdevs.photoncamera.processing.render.Parameters.isResizeDisabled(factor)
                            && Math.abs(factor - 1.0f) > 1e-4f;
            // No consumer in UpscaleCrop when neither zoom-expanded nor
            // explicitly resized: skip inference (1.0x is a size no-op).
            if (!params.isCropped && !explicitResize) return;
            // Cropped path still needs the full-frame size to expand into.
            if (params.isCropped && params.fullRawSize == null && !explicitResize) return;
            // RGB layout carries no Bayer quads for the packed luma pass.
            if (basePipeline.mSettings.alignAlgorithm == 2) return;

            Point rawSize = basePipeline.mParameters.rawSize;
            if (rawSize == null || rawSize.x <= 0 || rawSize.y <= 0) return;
            Point packed = new Point(rawSize.x / 2, rawSize.y / 2);
            Point lumaTexSize = new Point((packed.x + 3) / 4, packed.y);
            if (lumaTexSize.x < 1) lumaTexSize.x = 1;
            if (lumaTexSize.y < 1) lumaTexSize.y = 1;

            ByteBuffer stack = pp.stackFrame;
            stack.position(0);
            rawTex = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.FLOAT_16),
                    null, GL_NEAREST, GL_MIRRORED_REPEAT);
            // stackFrame is white/black-level normalized fp16 (see
            // HdrxProcessor / UnlimitedProcessor) - upload as raw halves; the
            // shader then works in the normalized domain (black 0, white 1).
            rawTex.loadRawHalf(stack);

            glProg.useAssetProgram("upscalecrop/singleluma");
            glProg.setTexture("InputBuffer", rawTex);
            glProg.setVar("blMean", 0.f);
            glProg.setVar("whiteLevel", 1.f);
            lumaTex = new GLTexture(lumaTexSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4));
            glProg.drawBlocks(lumaTex);
            glProg.closed = true;

            lumaTex.BufferLoad();
            ByteBuffer raw = lumaTex.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32, 4), true);
            raw.order(ByteOrder.nativeOrder());
            final FloatBuffer lumaCPU = raw.asFloatBuffer();
            final Point lumaCPUSize = new Point(lumaTexSize.x * 4, lumaTexSize.y);

            final float sigma = (float) (Math.sqrt(basePipeline.noiseS * 0.5 + basePipeline.noiseO) * singleSigmaMpy);
            pp.kernelNetSingleThread = new Thread(() -> {
                try {
                    Context ctx = PhotonCamera.getAppContext();
                    if (ctx == null) return;
                    // Shared instance (see ESD4D): do NOT close it here.
                    KernelNetNcnnProcessor processor = KernelNetNcnnProcessor.start(ctx);
                    if (processor.isReady()) {
                        pp.kernelNetSingleResult.set(processor.runInference(
                                lumaCPU, lumaCPUSize.x, lumaCPUSize.y, sigma));
                    }
                } catch (Throwable t) {
                    Log.e(Name, "Single-frame KernelNet worker failed", t);
                }
            }, "KernelNet-single-inference");
            pp.kernelNetSingleThread.start();
            Log.d(Name, "Single-frame KernelNet inference started: " + lumaCPUSize.x
                    + "x" + lumaCPUSize.y + " luma, sigma=" + sigma);
        } finally {
            // Last reader of the merged raw in every path: Bayer2Float
            // uploaded it, the multi-frame params already came from ESD4D,
            // and the single-frame luma pass above has consumed it. Free it
            // here so it doesn't ride through Amaze/tail/gain-map.
            pp.releaseStackFrame();
        }
    }

    @Override
    public void AfterRun() {
        if (rawTex != null) {
            rawTex.close();
            rawTex = null;
        }
        if (lumaTex != null) {
            lumaTex.close();
            lumaTex = null;
        }
    }
}
