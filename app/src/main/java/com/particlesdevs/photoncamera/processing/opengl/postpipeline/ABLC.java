package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.annotation.SuppressLint;

import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.scripts.ABL;

import java.util.Calendar;
import java.util.Locale;

public class ABLC extends Node {
    private static final String TAG = "ABLC";

    @Tunable(
            title = "Enable auto black level",
            category = TAG,
            min = 0.0f,
            max = 1.0f,
            defaultValue = 1.0f,
            step = 1.0f
    )
    boolean enable;

    @Tunable(
            title = "Histogram size",
            description = "Histogram bin count",
            category = TAG,
            min = 64,
            max = 16384,
            defaultValue = 256,
            step = 32
    )
    int histSize;

    @Tunable(
            title = "Noise exposure compensation EV",
            description = "Multiply noise for ABL search by selected power of 2",
            category = TAG,
            min = -10.0f,
            max = 10.0f,
            defaultValue = -1.0f,
            step = 0.5f
    )
    double noiseEV;

    @Tunable(
            title = "Min exposure multiplier",
            description = "Min multiplier for black region search",
            category = TAG,
            min = 1.0f,
            max = 32.0f,
            defaultValue = 8.0f,
            step = 1.0f
    )
    double minExposureMpy;

    @Tunable(
            title = "Max exposure compensation",
            description = "Max possible exposure compensation to search dark regions if noise is low",
            category = TAG,
            min = 1.0f,
            max = 16.0f,
            defaultValue = 10.0f,
            step = 1.0f
    )
    double maxEV;

    
    public ABLC() {
        super("", "ABLC");
    }

    @Override
    public void Compile() {
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void Run() {
        if(!enable){
            WorkingTexture = super.previousNode.WorkingTexture;
            return;
        }
        ABL abl = new ABL(basePipeline.glint.glProcessing, histSize);

        // Use bruteforce method to find optimal black levels that minimize color shifting
        //float[] blackLevels = bruteforceOptimalBlackLevels(hist);
        double noise = Math.sqrt(basePipeline.noiseS + basePipeline.noiseO);
        noise *= Math.pow(2.0, noiseEV);
        Log.d(TAG, "Noise value:" + noise);
        float[] blackLevels = abl.Compute(
                minExposureMpy,
                maxEV,
                noise,
                previousNode.WorkingTexture
        );

        Log.d(TAG, String.format("Bruteforce Black Levels - R: %.4f, G: %.4f, B: %.4f", 
               blackLevels[0], blackLevels[1], blackLevels[2]));

        // Apply black level correction
        glProg.useAssetProgram("ABLC/levelcorrection");
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        glProg.setVar("blackLevel", blackLevels);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
