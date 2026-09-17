package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;

public class ESD3D extends Node {
    boolean needClose = false;
    public ESD3D(boolean closing) {
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

    @Override
    public void Run() {
        if (!enable) {
            WorkingTexture = previousNode.WorkingTexture;
            return;
        }
        // Values are automatically injected in BeforeRun()!
        //if(basePipeline.main4 == null)
        //    basePipeline.main4 = glUtils.medianDown(previousNode.WorkingTexture,4);
        //GLTexture grad;
        /*
        if(previousNode.WorkingTexture != basePipeline.getMain3()){
            //grad = basePipeline.getMain3();
            WorkingTexture = basePipeline.getMain();
        }
        else {
            //grad = basePipeline.getMain();
            WorkingTexture = basePipeline.getMain3();
        }*/
        //glUtils.ConvDiff(previousNode.WorkingTexture, grad, 0.f);
        WorkingTexture = basePipeline.getMain();

        {
            Log.d(Name, "NoiseS:" + basePipeline.noiseS + ", NoiseO:" + basePipeline.noiseO);
            glProg.setDefine("NOISES", basePipeline.noiseS);
            glProg.setDefine("NOISEO", basePipeline.noiseO);
            glProg.setDefine("MOIRE", moire);
            glProg.setDefine("LUMA", luma);

            glProg.setDefine("INSIZE", basePipeline.mParameters.rawSize);
            //float ks = 1.0f + Math.min((basePipeline.noiseS+basePipeline.noiseO) * 3.0f * noiseToKernelSize, 34.f);
            //int msize = 7 + (int)ks - (int)ks%2;
            double noiseMpy = Math.max((basePipeline.noiseS+basePipeline.noiseO)/noiseTarget, 0.0000001);
            double kernelSize = 1.0f + Math.sqrt(noiseMpy) * noiseToKernelSize;
            int msize = Math.min(minSize + (int)kernelSize - (int)kernelSize%2, maxSize);
            Log.d("ESD3D", "KernelSize: "+kernelSize+" MSIZE: "+msize);
            glProg.setDefine("KERNELSIZE", (float)(kernelSize));
            glProg.setDefine("MSIZE", msize);
            glProg.useAssetProgram("denoise/esd3d2");
            //glProg.setTexture("NoiseMap", basePipeline.main4);
            glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
            //glProg.setTexture("GradBuffer", grad);
            glProg.drawBlocks(WorkingTexture);
        }
        glProg.closed = true;
        /*if(needClose) {
            basePipeline.main4.close();
            basePipeline.main4 = null;
        }*/
    }
}
