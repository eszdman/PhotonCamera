package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;

/**
 * Separable Bilateral Filter Node
 * Performs two-pass bilateral filtering: vertical (Y) then horizontal (X)
 * More efficient than full 2D bilateral filtering for larger kernel sizes
 */
public class BilateralSeparable extends Node {

    public BilateralSeparable() {
        super("", "BilateralSeparable");
    }

    @Tunable(title = "Enable", category = "BilateralSeparable", defaultValue = 1, min = 0, max = 1, step = 1)
    boolean enable;

    @Tunable(title = "Kernel Size", category = "BilateralSeparable", 
             description = "Size of the filter kernel (larger = more smoothing)", 
             defaultValue = 15, min = 5, max = 31, step = 2)
    int kernelSize;

    @Tunable(title = "Spatial Sigma", category = "BilateralSeparable", 
             description = "Spatial sigma for Gaussian weighting", 
             defaultValue = 5.0f, min = 1.0f, max = 20.0f, step = 0.5f)
    float spatialSigma;

    @Tunable(title = "Intensity Multiplier", category = "BilateralSeparable", 
             description = "Multiplier for noise-based intensity sigma", 
             defaultValue = 1.0f, min = 0.1f, max = 5.0f, step = 0.1f)
    float intensityMultiplier;

    @Override
    public void Compile() {
    }

    @Override
    public void Run() {
        if (!enable) {
            WorkingTexture = previousNode.WorkingTexture;
            return;
        }

        // Get noise model parameters
        NoiseModeler modeler = basePipeline.mParameters.noiseModeler;
        float noiseS = modeler.computeModel[0].first.floatValue() +
                modeler.computeModel[1].first.floatValue() +
                modeler.computeModel[2].first.floatValue();
        float noiseO = modeler.computeModel[0].second.floatValue() +
                modeler.computeModel[1].second.floatValue() +
                modeler.computeModel[2].second.floatValue();
        noiseS /= 3.f;
        noiseO /= 3.f;

        Log.d(Name, "NoiseS:" + noiseS + ", NoiseO:" + noiseO);
        Log.d(Name, "KernelSize:" + kernelSize + ", SpatialSigma:" + spatialSigma);

        // Calculate kernel half-size
        int ksize = (kernelSize - 1) / 2;
        
        // For separable bilateral, we need to adjust sigma to approximate the 2D filter
        // Using sigma/sqrt(2) for each 1D pass approximates the 2D Gaussian spatial component
        float adjustedSpatialSigma = spatialSigma / (float)Math.sqrt(2.0);

        // Allocate temporary texture for intermediate result
        GLTexture tempTexture = basePipeline.getMain3();

        // ========================================
        // PASS 1: Vertical filtering (Y direction)
        // ========================================
        glProg.setDefine("NOISES", noiseS);
        glProg.setDefine("NOISEO", noiseO);
        glProg.setDefine("INTENSE", (float) basePipeline.mSettings.noiseRstr);
        glProg.setDefine("KSIZE", ksize);
        glProg.setDefine("SPATIAL_SIGMA", adjustedSpatialSigma);
        glProg.setDefine("INTENSITY_MPY", intensityMultiplier);
        glProg.setDefine("DIRECTION", 0); // 0 = vertical
        glProg.setDefine("LAST_PASS", 0); // 0 = not last pass
        
        glProg.useAssetProgram("denoise/bilateralsep");
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        glProg.setTexture("OriginalBuffer", previousNode.WorkingTexture);
        glProg.drawBlocks(tempTexture);
        glProg.closed = true;

        Log.d(Name, "Vertical pass complete");

        // ========================================
        // PASS 2: Horizontal filtering (X direction)
        // ========================================
        glProg.setDefine("NOISES", noiseS);
        glProg.setDefine("NOISEO", noiseO);
        glProg.setDefine("INTENSE", (float) basePipeline.mSettings.noiseRstr);
        glProg.setDefine("KSIZE", ksize);
        glProg.setDefine("SPATIAL_SIGMA", adjustedSpatialSigma);
        glProg.setDefine("INTENSITY_MPY", intensityMultiplier);
        glProg.setDefine("DIRECTION", 1); // 1 = horizontal
        glProg.setDefine("LAST_PASS", 1); // 1 = last pass (can apply brightness preservation here)
        
        glProg.useAssetProgram("denoise/bilateralsep");
        glProg.setTexture("InputBuffer", tempTexture);
        glProg.setTexture("OriginalBuffer", previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;

        Log.d(Name, "Horizontal pass complete");
    }
}

