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
 * Builds the KernelNet input luma plane for a cropped single-frame capture
 * (the multi-frame path already exports its params from ESD4D) and starts the
 * parameter-model inference on a worker thread.
 *
 * <p>The GL-thread work here is one packed-luma pass plus one small readback;
 * the ncnn inference then overlaps the rest of the post pipeline and is
 * collected by {@link UpscaleCrop}. Params unavailable paths (RGB layout,
 * not cropped, model failure) simply fall back to bicubic in UpscaleCrop.</p>
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
    public void Run() {
        WorkingTexture = previousNode.WorkingTexture;
        PostPipeline pp = (PostPipeline) basePipeline;
        if (pp.kernelParams != null || pp.kernelNetSingleThread != null) return;
        if (!basePipeline.mParameters.isCropped
                || basePipeline.mParameters.fullRawSize == null) return;
        // RGB layout carries no Bayer quads for the packed luma pass.
        if (basePipeline.mSettings.alignAlgorithm == 2) return;

        Point rawSize = basePipeline.mParameters.rawSize;
        Point packed = new Point(rawSize.x / 2, rawSize.y / 2);
        Point lumaTexSize = new Point((packed.x + 3) / 4, packed.y);
        if (lumaTexSize.x < 1) lumaTexSize.x = 1;
        if (lumaTexSize.y < 1) lumaTexSize.y = 1;

        ByteBuffer stack = pp.stackFrame;
        stack.position(0);
        rawTex = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),
                stack, GL_NEAREST, GL_MIRRORED_REPEAT);

        // Bayer2Float normalizes blackLevel by whiteLevel, so blMean is in
        // [0,1] and the shader divides the raw values by whiteLevel only.
        float[] bl = basePipeline.mParameters.blackLevel;
        float blMean = (bl[0] + bl[1] + bl[2] + bl[3]) * 0.25f;

        glProg.useAssetProgram("upscalecrop/singleluma");
        glProg.setTexture("InputBuffer", rawTex);
        glProg.setVar("blMean", blMean);
        glProg.setVar("whiteLevel", (float) basePipeline.mParameters.whiteLevel);
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
                KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(ctx);
                try {
                    if (processor.isReady()) {
                        pp.kernelNetSingleResult.set(processor.runInference(
                                lumaCPU, lumaCPUSize.x, lumaCPUSize.y, sigma));
                    }
                } finally {
                    processor.close();
                }
            } catch (Throwable t) {
                Log.e(Name, "Single-frame KernelNet worker failed", t);
            }
        }, "KernelNet-single-inference");
        pp.kernelNetSingleThread.start();
        Log.d(Name, "Single-frame KernelNet inference started: " + lumaCPUSize.x
                + "x" + lumaCPUSize.y + " luma, sigma=" + sigma);
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
