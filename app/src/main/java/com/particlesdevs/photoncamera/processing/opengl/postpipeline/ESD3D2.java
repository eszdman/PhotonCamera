package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Math2;

import org.w3c.dom.Text;

public class ESD3D2 extends Node {
    boolean needClose = false;
    public ESD3D2(boolean closing) {
        super("", "ES3D");
        needClose = closing;
    }

    @Override
    public void Compile() {
    }
    @Tunable(title = "Enable", category = "Denoise", defaultValue = 1, min = 0, max = 1, step = 1, description = "Enable ESD3D Denoising")
    boolean enable;

    @Tunable(title = "Noise To Kernel Size", category = "Denoise", max = 50.0f, defaultValue = 24.0f)
    float noiseToKernelSize = 24.0f;

    @Tunable(title = "Noise Target", category = "Denoise", max = 0.1f, defaultValue = 0.00390625f, step = 0.0001f,
            description = "Target noise level to map to minimum kernel size (1/256 = 0.00390625)"
    )
    float noiseTarget = 1.0f/256.f;

    @Tunable(title = "Luma", category = "Denoise", max = 2.0f, defaultValue = 0.8f,
            description = "Luma strength multiplier for denoising"
    )
    float luma = 0.8f;

    @Tunable(title = "Max Kernel", category = "Denoise", min = 1.0f, max = 51.0f, defaultValue = 21.0f, step = 1.0f,
            description = "Maximum kernel size for denoising"
    )
    int maxSize = 21;

    @Tunable(title = "Min Kernel", category = "Denoise", min = 1.0f, max = 21.0f, defaultValue = 7.0f, step = 1.0f,
            description = "Minimum kernel size for denoising"
    )
    int minSize = 7;

    @Tunable(title = "Moire Reduction", category = "Denoise", max = 5.0f, defaultValue = 1.5f, step = 0.1f,
            description = "Moire reduction strength"
    )
    float moire = 1.5f;

    @Tunable(title = "Use Color Denoising", category = "Denoise", defaultValue = 1, min = 0, max = 1, step = 1,
            description = "Whether to apply subsampling denoising to color channels (in addition to luma)"
    )
    boolean useColorDenoising;

    @Tunable(title = "ESD3D Version", category = "Denoise", defaultValue = 0,
            entries = {"Steered", "Steered Prod"},
            entryValues = {"steered", "steered_prod"},
            description = "Steered Prod adds consensus weights (stricter edge preservation) on top of tensor steering"
    )
    String version = "steered";

    private String esd3dProgram() {
        if ("steered_prod".equals(version)) return "denoise/esd3d2_steered_prod";
        return "denoise/esd3d2_steered";
    }

    void ESD3DRun(GLTexture inputTexture, GLTexture outputTexture, GLTexture gradBuffer, float moire, float scale) {
        {
            float NoiseS = basePipeline.noiseS;
            float NoiseO = basePipeline.noiseO;
            NoiseS /= scale;
            NoiseO /= scale;
            Log.d(Name, "NoiseS:" + NoiseS + ", NoiseO:" + NoiseO);
            glProg.setDefine("NOISES", NoiseS);
            glProg.setDefine("NOISEO", NoiseO);
            glProg.setDefine("MOIRE", moire);
            glProg.setDefine("LUMA", luma);

            glProg.setDefine("INSIZE", basePipeline.mParameters.rawSize);
            //float ks = 1.0f + Math.min((basePipeline.noiseS+basePipeline.noiseO) * 3.0f * noiseToKernelSize, 34.f);
            //int msize = 7 + (int)ks - (int)ks%2;
            double noiseMpy = Math.max((NoiseS+NoiseO)/noiseTarget, 0.0000001);
            double kernelSize = 1.0f + Math.sqrt(noiseMpy) * noiseToKernelSize;
            int msize = Math.min(minSize + (int)kernelSize - (int)kernelSize%2, maxSize);
            Log.d("ESD3D", "KernelSize: "+kernelSize+" MSIZE: "+msize);
            glProg.setDefine("KERNELSIZE", (float)(kernelSize));
            glProg.setDefine("MSIZE", msize);
            glProg.useAssetProgram(esd3dProgram());
            glProg.setTexture("InputBuffer", inputTexture);
            glProg.setTexture("GradBuffer", gradBuffer);
            glProg.drawBlocks(outputTexture);
        }
    }
    public void guidedUpsample(GLTexture lowresInput, GLTexture guide, GLTexture guideHigh, GLTexture output, int scaling) {
        //glProg.setDefine("USE_GUIDE_BRIGHTNESS", useGuideBrightness);
        glProg.setDefine("SCALE", scaling);
        glProg.useAssetProgram("denoise/guidedupsample", false);
        glProg.setTexture("LowresInput", lowresInput);
        glProg.setTexture("Guide", guide);
        glProg.setTexture("GuideHigh", guideHigh);
        glProg.setVar("noiseS", basePipeline.noiseS);
        glProg.setVar("noiseO", basePipeline.noiseO);
        glProg.drawBlocks(output, output.mSize);
        glProg.closed = true;
    }

    @Override
    public int halo() {
        // Steered kernel up to ~10px + grad + pyramid slop; overestimated on
        // purpose (safe direction) — harness-proven, lower if seams allow.
        return 16;
    }

    public void Run() {
        if (!enable) {
            WorkingTexture = previousNode.WorkingTexture;
            return;
        }
        float N = (float) Math.sqrt(0.5 * basePipeline.noiseS + basePipeline.noiseO);
        float targetN = noiseTarget;
        float scaleF = Math2.clamp(N/targetN, 1.0f, 4.0f);
        int scale = (int)(scaleF + 0.5f);
        GLTexture outp;
        // Gradient buffers (gx, gy) required by the steered kernel, one per filter input resolution
        GLTexture grad = null;
        GLTexture gradLow = null;
        Log.d(Name, "Scaling factor:" + scale);
        if(!useColorDenoising){
            outp = previousNode.WorkingTexture;
        } else {
            if (scale != 1) {
                basePipeline.main4 = glUtils.gaussdown(previousNode.WorkingTexture, scale);
                // Denoise runs at low resolution, so the intermediate has to match main4's size
                basePipeline.main5 = new GLTexture(basePipeline.main4.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
                gradLow = new GLTexture(basePipeline.main4.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
                glUtils.ConvDiff(basePipeline.main4, gradLow, 0.f);
                ESD3DRun(basePipeline.main4, basePipeline.main5, gradLow, 0.0f, scaleF * 0.75f);
                WorkingTexture = basePipeline.getMain();
                outp = basePipeline.getMain();
                guidedUpsample(basePipeline.main5, basePipeline.main4, previousNode.WorkingTexture, outp, scale);
            } else {
                WorkingTexture = basePipeline.getMain();
                grad = new GLTexture(previousNode.WorkingTexture.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
                glUtils.ConvDiff(previousNode.WorkingTexture, grad, 0.f);
                ESD3DRun(previousNode.WorkingTexture, WorkingTexture, grad, 0.0f, 1.0f);
                outp = basePipeline.getMain();
                guidedUpsample(WorkingTexture, previousNode.WorkingTexture, previousNode.WorkingTexture, outp, scale);
            }
        }
        WorkingTexture = basePipeline.getMain();
        if (grad == null) {
            grad = new GLTexture(outp.mSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        }
        glUtils.ConvDiff(outp, grad, 0.f);
        ESD3DRun(outp, WorkingTexture, grad, moire, 1.0f);
        glProg.closed = true;
        grad.close();
        if (gradLow != null) {
            gradLow.close();
        }
        if(basePipeline.main4 != null){
            basePipeline.main4.close();
            basePipeline.main4 = null;
        }
        if(basePipeline.main5 != null){
            basePipeline.main5.close();
            basePipeline.main5 = null;
        }
    }
}
