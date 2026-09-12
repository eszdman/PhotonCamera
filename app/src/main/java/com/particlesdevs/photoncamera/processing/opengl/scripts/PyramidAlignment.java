package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.BufferUtils;

import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MIRRORED_REPEAT;
import static android.opengl.GLES20.GL_NEAREST;

public class PyramidAlignment implements AutoCloseable {
    public Parameters parameters;
    ArrayList<ImageFrame> images;
    GLProg glProg;
    GLUtils glUtils;
    Point size;
    GLOneScript origin;
    public PyramidAlignment(Point size, ArrayList<ImageFrame> images, GLProg glProg, GLUtils glUtils, GLOneScript origin) {
        this.size = size;
        this.glProg = glProg;
        this.images = images;
        this.glUtils = glUtils;
        this.origin = origin;
    }
    public static Point alignmentShift(Parameters parameters, int f) {
        int shiftX = ((f-1)%parameters.tilesX) * (parameters.alignmentSize.x);
        int shiftY = ((f-1)/parameters.tilesX) * (parameters.alignmentSize.y);
        return new Point(shiftX, shiftY);
    }
    
    /**
     * Find optimal exposure by comparing histograms using brute force approach
     * @param baseHist Base frame histogram (reference)
     * @param alterHist Alter frame histogram (1x exposure)
     * @return Optimal exposure value
     */
    private float findOptimalExposure(int[][] baseHist, int[][] alterHist) {
        float bestExposure = 1.0f;
        double bestScore = Double.MAX_VALUE;
        
        // Stage 1: Coarse search from 0.1x to 10x with step 0.05
        for (float testExposure = 1.0f; testExposure <= 10.0f; testExposure += 0.005f) {
            double score = compareHistograms(baseHist, alterHist, testExposure);
            if (score < bestScore) {
                bestScore = score;
                bestExposure = testExposure;
            }
        }
        Log.d("PyramidAlignment", "Stage 1 best exposure: " + bestExposure + " with score: " + bestScore);
        /*
        // Stage 2: Fine search around best exposure with step 0.005
        float coarseExposure = bestExposure;
        bestScore = Double.MAX_VALUE;
        for (float testExposure = Math.max(0.1f, coarseExposure - 0.1f);
             testExposure <= coarseExposure + 0.1f; testExposure += 0.005f) {
            double score = compareHistograms(baseHist, alterHist, testExposure);
            if (score < bestScore) {
                bestScore = score;
                bestExposure = testExposure;
            }
        }
        Log.d("PyramidAlignment", "Stage 2 best exposure: " + bestExposure + " with score: " + bestScore);
        
        // Stage 3: Ultra-fine search around best exposure with step 0.001
        float fineExposure = bestExposure;
        bestScore = Double.MAX_VALUE;
        for (float testExposure = Math.max(0.1f, fineExposure - 0.02f); 
             testExposure <= fineExposure + 0.02f; testExposure += 0.001f) {
            double score = compareHistograms(baseHist, alterHist, testExposure);
            if (score < bestScore) {
                bestScore = score;
                bestExposure = testExposure;
            }
        }
        
        Log.d("PyramidAlignment", "Stage 3 best exposure: " + bestExposure + " with score: " + bestScore);*/
        return bestExposure;
    }
    /**
     * Compare histograms by applying exposure to alter histogram and computing difference
     * @param baseHist Base frame histogram
     * @param alterHist Alter frame histogram (1x exposure)
     * @param exposure Exposure to apply to alter histogram
     * @return Histogram difference score (lower is better)
     */
    private double compareHistograms(int[][] baseHist, int[][] alterHist, float exposure) {
        double totalDiff = 0.0;
        int numChannels = Math.min(baseHist.length, alterHist.length);
        
        for (int channel = 0; channel < numChannels; channel++) {
            int[] base = baseHist[channel];
            int[] alter = alterHist[channel];
            
            // Calculate total pixels for normalization
            long baseTotalPixels = 0;
            long alterTotalPixels = 0;
            for (int i = 0; i < base.length; i++) {
                baseTotalPixels += base[i];
            }
            for (int i = 0; i < alter.length; i++) {
                alterTotalPixels += alter[i];
            }
            
            if (baseTotalPixels == 0 || alterTotalPixels == 0) continue;
            
            // Create exposure-compensated histogram for alter frame
            double[] alterExposed = new double[alter.length];
            for (int i = 0; i < alter.length; i++) {
                // Map current alter bin to exposed position
                float sourceValue = i / (float)(alter.length - 1);
                float exposedValue = sourceValue / exposure;
                
                if (exposedValue <= 1.0f) {
                    // Map to target bin in exposed space
                    float targetBinFloat = exposedValue * (alter.length - 1);
                    int targetBin = (int)targetBinFloat;
                    float fraction = targetBinFloat - targetBin;
                    
                    // Distribute pixel count with linear interpolation
                    if (targetBin < alter.length) {
                        alterExposed[targetBin] += alter[i] * (1.0f - fraction);
                    }
                    if (targetBin + 1 < alter.length) {
                        alterExposed[targetBin + 1] += alter[i] * fraction;
                    }
                } else {
                    // Clamp overexposed pixels to brightest bin
                    alterExposed[alter.length - 1] += alter[i];
                }
            }
            
            // Compare base histogram with exposure-compensated alter histogram
            for (int i = 0; i < base.length; i++) {
                double baseNormalized = (double)base[i] / baseTotalPixels;
                double alterNormalized = alterExposed[i] / alterTotalPixels;
                
                double diff = baseNormalized - alterNormalized;
                totalDiff += diff * diff;
            }
        }
        
        return totalDiff / numChannels;
    }

    float downScalePerLevel = 2.0f;

    @Tunable(title = "Correction Sharpness", category = "Alignment", min = -1.0f, max = 2.0f, defaultValue = 1.0f)
    float sharpness;

    // Fixed alignment parameters, tuned on real ProRAW bursts with
    // perspective (hand-shake) warps in tools/alignment-bench:
    // - OFFSETS 9: the 8-neighborhood coarse-offset propagation roughly
    //   halves the badly-misaligned tile share vs the plus-shape on every
    //   tested scene (day, dusk, night).
    // - significancy 2.0: significance-gate threshold in noise sigmas (the
    //   canonical 2-sigma test). Freezes only statistically-insignificant
    //   improvements (textureless tiles keep the smooth coarse field instead
    //   of random-walking) while accepting genuine detail matches; 3.0
    //   started rejecting real detail, 1.5 admits more noise locks at night
    //   (measured on real ProRAW bursts, tools/alignment-bench).
    // - prefilter sigma 1.5 quad units (normalize.glsl): centered 5-tap
    //   gaussian, no min/max trim - hot pixels are invisible after this
    //   average and the pyramid above it, trimming only removed detail.
    // - cost: noise-normalized L1 without truncation (see align.glsl).
    private static final int ALIGN_OFFSETS = 9;
    private static final float ALIGN_SIGNIFICANCY = 2.0f;
    private static final float PREFILTER_SIGMA = 1.5f;

    GLTexture inputBase;
    GLTexture base;
    GLTexture alter;
    GLTexture temp;
    GLTexture gainMap;
    public GLTexture Result;
    GLTexture inputAlter;
    GLTexture hotPix;
    GLUtils.Pyramid pyramid;
    GLUtils.Pyramid pyramidAlter;

    public void Run() {
        com.particlesdevs.photoncamera.settings.TunableInjector.inject(this);
        // --- prefilter (normalize.glsl) configuration ---
        // Replicate normalize.glsl's separable 5-tap gaussian weights to get
        // the exact noise-reduction factor for the alignment's noise model:
        // sigma_out = sigma_in * sum(w1d^2), so integralNorm multiplies by
        // 1/sum(w1d^2). Without this the noise-normalized cost and the
        // significance gate overestimate sigma and lose discrimination.
        float blurSigma = PREFILTER_SIGMA;
        double s2 = 2.0 * blurSigma * blurSigma;
        double[] wx = new double[5];
        double wsum = 0;
        for (int i = 0; i < 5; i++) {
            double d = i - 2;
            wx[i] = Math.exp(-d * d / s2);
            wsum += wx[i];
        }
        double sumsq = 0;
        for (int i = 0; i < 5; i++) {
            wx[i] /= wsum;
            sumsq += wx[i] * wx[i];
        }
        float prefilterN = (float) (1.0 / sumsq);
        Log.d("PyramidAlignment", "prefilter: sigma=" + blurSigma + " noiseFactor=" + prefilterN);
        Point rawHalf = new Point(parameters.rawSize.x/2,parameters.rawSize.y/2);
        Result = new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        try (ImageFrame.Upload baseUpload = images.get(0).upload()) {
            inputBase = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16,1),
                    baseUpload.buffer, GL_NEAREST, GL_CLAMP_TO_EDGE);
        }
        // Temporal result
        temp = new GLTexture(rawHalf, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        base = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        alter = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        gainMap = new GLTexture(parameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_32, 4),
                BufferUtils.getFrom(parameters.gainMap), GL_LINEAR, GL_CLAMP_TO_EDGE);

        // Use normalize script to fill base texture
        glProg.setLayout(8, 8, 1);
        glProg.useAssetProgram("alignment/normalize", true);
        glProg.setVar("whiteLevel", (float) (parameters.whiteLevel));
        glProg.setVar("blackLevel", parameters.blackLevel);
        glProg.setVar("blurSigma", blurSigma);
        glProg.setTexture("inTexture", inputBase);
        glProg.setTexture("gainMap", gainMap);
        glProg.setVar("exposure", 1.0f);
        glProg.setTextureCompute("outTexture", temp, true);
        glProg.computeAuto(temp.mSize, 1);

        GLHistogram hist = new GLHistogram(glProg, 1024);
        hist.Rc = true;
        hist.Gc = true;
        hist.Bc = true;
        hist.Ac = true;
        float overexposure = 64.f;
        hist.exposure = new float[]{overexposure, overexposure, overexposure, overexposure};
        int[][] histDataBase = hist.Compute(temp).clone();
        float[] blackLevel = new float[4];
        for (int i = 0; i < 4; i++) {
            long histSum = 0;
            for (int j = 0; j < histDataBase[i].length; j++) {
                histSum += histDataBase[i][j];
            }
            Log.d("PyramidAlignment", "histSum[" + i + "] = " + histSum);
            long integration = 0;
            for (int j = 0; j < histDataBase[i].length; j++) {
                integration += histDataBase[i][j];
                if (integration > histSum * 0.3) {
                    blackLevel[i] = (j / (histDataBase[i].length - 1.f)) / overexposure;
                    Log.d("PyramidAlignment", "blackLevel[" + i + "] = " + blackLevel[i]);
                    break;
                }
            }
        }

        //hist.exposure = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        //histDataBase = hist.Compute(temp).clone();

        // Base black levels are final; the alter-frame histogram branch below
        // is disabled, so this instance has no further readers.
        hist.close();

        glProg.setLayout(8, 8, 1);
        glProg.useAssetProgram("alignment/normalizebl", true);
        glProg.setVar("blackLevel", blackLevel);
        glProg.setVar("whiteLevel", 1.0f);
        glProg.setVar("sharpness", sharpness);
        glProg.setTexture("baseTexture", temp);
        glProg.setTexture("gainMap", gainMap);
        glProg.setTextureCompute("outTexture", base, true);
        glProg.computeAuto(base.mSize, 1);

        //GLTexture histTexture = new GLTexture(new Point(1024,1), new GLFormat(GLFormat.DataType.FLOAT_32), BufferUtils.getFrom(histCurve), GL_LINEAR, GL_CLAMP_TO_EDGE);
        GLTexture histTexture = new GLTexture(new Point(1024,1), new GLFormat(GLFormat.DataType.FLOAT_32), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        GLTexture alterTexture = new GLTexture(new Point(1024,1), new GLFormat(GLFormat.DataType.FLOAT_32), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        int levelcount = (int)(Math.log10(rawHalf.x)/Math.log10(downScalePerLevel))-1;
        if(levelcount <= 0) levelcount = 2;
        int tile = 8;

        pyramid = new GLUtils.Pyramid();
        glUtils.createPyramidStore(levelcount, base, pyramid, false);

        pyramidAlter = new GLUtils.Pyramid();
        NoiseModeler modeler = parameters.noiseModeler;
        float noiseS = modeler.baseModel[0].first.floatValue() +
                modeler.baseModel[1].first.floatValue() +
                modeler.baseModel[2].first.floatValue();
        float noiseO = modeler.baseModel[0].second.floatValue() +
                modeler.baseModel[1].second.floatValue() +
                modeler.baseModel[2].second.floatValue();
        noiseS /= 3.f;
        noiseO /= 3.f;
        double noisempy = Math.pow(2.0, PhotonCamera.getSettings().mergeStrength);
        noiseS = (float)Math.max(noiseS * noisempy,1e-6f);
        noiseO = (float)Math.max(noiseO * noisempy,1e-6f);
        double noise = Math.sqrt(noiseS + noiseO);
        Log.d("PyramidAlignment", "noise: " + Math.sqrt(noiseS + noiseO));
        inputAlter = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 1), null, GL_NEAREST, GL_MIRRORED_REPEAT);

        int alignCount = 0;
        for (int f = 1; f < images.size(); f++) {
            ImageFrame frame = images.get(f);
            Log.d("PyramidAlignment", "load:"+frame.pair.curlayer.name());
            try (ImageFrame.Upload up = frame.upload()) {
                inputAlter.loadData(up.buffer);
            }
            
            // Compute alter frame histogram with exposure = 1.0 for exposure determination
            /*glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("alignment/normalize", true);
            glProg.setVar("whiteLevel", (float) (parameters.whiteLevel));
            glProg.setVar("blackLevel", parameters.blackLevel);
            glProg.setVar("exposure", 1.0f); // Use 1.0 exposure for histogram comparison
            glProg.setVar("noiseS", noiseS);
            glProg.setVar("noiseO", noiseO);
            glProg.setTexture("inTexture", inputAlter);
            glProg.setTexture("gainMap", gainMap);
            glProg.setTextureCompute("outTexture", temp, true);
            glProg.computeAuto(temp.mSize, 1);
            
            // Compute histogram for alter frame with 1x exposure
            int[][] histDataAlter = hist.Compute(temp).clone();*/
            
            // Find optimal exposure using brute force histogram matching
            //float exposure = 1.0f/findOptimalExposure(histDataBase, histDataAlter);
            float exposure = 1.0f/frame.pair.layerMpy;
            //Log.d("PyramidAlignment", "Computed exposure: " + exposure + " reference exposure: " + 1.0f/frame.pair.layerMpy);
            
            // Use normalize script to fill alter texture with computed exposure
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("alignment/normalize", true);
            glProg.setVar("whiteLevel", (float) (parameters.whiteLevel));
            glProg.setVar("blackLevel", parameters.blackLevel);
            glProg.setVar("blurSigma", blurSigma);
            glProg.setVar("exposure", exposure);
            glProg.setTexture("inTexture", inputAlter);
            glProg.setTexture("gainMap", gainMap);
            glProg.setTextureCompute("outTexture", temp, true);
            glProg.computeAuto(temp.mSize, 1);


            /*int[][] histData = hist.Compute(temp);


            for (int i = 0; i < 4; i++) {
                long histSum = 0;
                for (int j = 0; j < histData[i].length; j++) {
                    histSum += histData[i][j];
                }
                Log.d("PyramidAlignment", "histSum[" + i + "] = " + histSum);
                long integration = 0;
                for (int j = 0; j < histData[i].length; j++) {
                    integration += histData[i][j];
                    if (integration > histSum * 0.3) {
                        blackLevel[i] = (j / (histData[i].length - 1.f))/ overexposure;
                        Log.d("PyramidAlignment", "blackLevel[" + i + "] = " + blackLevel[i]);
                        break;
                    }
                }
            }*/

            glProg.setLayout(8, 8, 1);
            glProg.useAssetProgram("alignment/normalizebl", true);
            glProg.setVar("blackLevel", blackLevel);
            glProg.setVar("whiteLevel", 1.0f);
            glProg.setVar("sharpness", sharpness);
            glProg.setTexture("baseTexture", temp);
            glProg.setTexture("gainMap", gainMap);
            glProg.setTextureCompute("outTexture", alter, true);
            glProg.computeAuto(alter.mSize, 1);

            Log.d("PyramidAlignment", "create alter");
            glUtils.createPyramidStore(levelcount, alter, pyramidAlter, false);
            Log.d("PyramidAlignment", "alter created");

            // do pyramid alignment upscaling
            for (int i = pyramidAlter.gauss.length - 2; i >= 0; i--) {

                float integralNorm = (float)rawHalf.x * rawHalf.y/(pyramidAlter.gauss[i+1].mSize.x * pyramidAlter.gauss[i+1].mSize.y);
                glProg.setDefine("TILE_AL", parameters.tile);
                glProg.setDefine("OFFSETS", ALIGN_OFFSETS);
                glProg.setLayout(parameters.tile / 2, parameters.tile / 2, 1);
                glProg.useAssetProgram("alignment/align", true);
                boolean first = (i == pyramidAlter.gauss.length - 2);
                if (!first) {
                    glProg.setTexture("prevAlignment", pyramidAlter.gauss[i + 2]);
                }
                glProg.setTexture("baseTexture", pyramid.gauss[i]);
                glProg.setTexture("alterTexture", pyramidAlter.gauss[i]);
                glProg.setTexture("baseCurve", histTexture);
                glProg.setTexture("alterCurve", alterTexture);
                glProg.setTextureCompute("outTexture", pyramidAlter.gauss[i+1], true);
                glProg.setVar("noiseS", noiseS);
                glProg.setVar("noiseO", noiseO);
                // Noise shrinks by sqrt(pixels averaged) per pyramid level,
                // scaled by the prefilter's noise-reduction factor
                // (normalize.glsl's gaussian: 1/sum(w^2)).
                glProg.setVar("integralNorm", (float) Math.sqrt(integralNorm) * prefilterN);
                glProg.setVar("significancy", ALIGN_SIGNIFICANCY);
                glProg.setVar("first", first ? 1 : 0);
                glProg.setVar("rawHalf", rawHalf);
                glProg.setVar("exposure", exposure);
                //glProg.computeAuto(new Point(alterPyramid.gauss[i].mSize.x/parameters.tile + 1,alterPyramid.gauss[i].mSize.y/parameters.tile + 1), 1);
                glProg.computeManual(pyramidAlter.gauss[i].mSize.x/(parameters.tile/2) + 1,pyramidAlter.gauss[i].mSize.y/(parameters.tile/2) + 1, 1);
            }
            Point shift = alignmentShift(parameters, f);
            // do alignment packing into single texture
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("alignment/pack", true);
            glProg.setTexture("alignTexture", pyramidAlter.gauss[1]);
            glProg.setTextureCompute("outTexture", Result, true);
            glProg.setVar("shift", shift);
            glProg.computeAuto(parameters.alignmentSize, 1);
        }
        histTexture.close();
        alterTexture.close();
    }

    @Override
    public void close() {
        inputBase.close();
        base.close();
        alter.close();
        temp.close();
        for (int i = 0; i < pyramid.gauss.length; i++) {
            pyramid.gauss[i].close();
            pyramidAlter.gauss[i].close();
        }
        inputAlter.close();
        gainMap.close();
        GLTexture.notClosed();
    }
}
