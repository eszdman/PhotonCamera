package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Pair;

import com.particlesdevs.photoncamera.processing.opengl.GLBuffer;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.settings.DynamicNoiseStore;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Math2;

import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MIRRORED_REPEAT;
import static android.opengl.GLES20.GL_NEAREST;
import static com.particlesdevs.photoncamera.processing.processor.ProcessorBase.FAKE_WL;

public class PyramidMerging extends GLOneScript {
    public Parameters parameters;
    ArrayList<ImageFrame> images;
    //ByteBuffer alignment;
    GLProg glProg;
    GLUtils glUtils;
    public PyramidMerging(Point size,ArrayList<ImageFrame> images) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.UNSIGNED_16), GLDrawParams.Allocate.Direct),"", "PyramidMerging", true);
        this.glProg = glOne.glProgram;
        this.images = images;
        //this.alignment = alignment;
    }

    float downScalePerLevel = 2.0f;

    @Override
    public void Compile(){}
    private int baseCnt = 0;

    private GLTexture getBase(){
        if(baseCnt == 0){
            baseCnt++;
            return baseAlter;
        } else {
            baseCnt = 0;
            return base;
        }
    }
    float noiseS;
    float noiseO;
    GLBuffer hotPixelBuffer;
    int hotPixelCount;
    @Tunable(title = "Max hotPixels", category = "Merge", description = "Statistical cpu filtering count threshold", min = 16384, max = 262144, step = 1000, defaultValue = 65535)
    int MAX_HOT_PIXELS;
    @Tunable(title = "Max reasonable hotPixels", category = "Merge", description = "Statistical cpu filtering count threshold", min = 1000, max = 10000, step = 100, defaultValue = 2000)
    int MAX_REASONABLE_HOTPIXELS;

    @Tunable(title = "Enable hotPixel correction", category = "Merge", min = 0, max = 1, step = 1, defaultValue = 0)
    boolean enableHotPixelCorrection;

    /**
     * Averages up to 10 frames (or fewer if not available) into a single rgba16f texture
     * at rawHalf resolution. Uses incremental mix: mix(current, new, 1/(i+1)) which yields
     * a proper running average without overflow.
     */
    private GLTexture buildAveragedFrame(float[] blackLevel, int tile) {
        Point rawHalf = new Point(parameters.rawSize.x / 2, parameters.rawSize.y / 2);
        int maxFrames = Math.min(10, images.size());

        GLTexture avgA     = new GLTexture(rawHalf, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        GLTexture avgB     = new GLTexture(rawHalf, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        GLTexture tempFloat = new GLTexture(rawHalf, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        GLTexture tempRaw  = maxFrames > 1
                ? new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 1), null, GL_NEAREST, GL_CLAMP_TO_EDGE)
                : null;

        GLTexture avgCurrent = avgA;
        GLTexture avgNext    = avgB;

        for (int i = 0; i < maxFrames; i++) {
            GLTexture rawSrc = (i == 0) ? inputBase : tempRaw;
            if (i > 0) {
                tempRaw.loadData(images.get(i).buffer);
            }

            // Convert raw Bayer -> normalized rgba16f vec4 (one texel per 2x2 Bayer quad)
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge00", true);
            glProg.setVar("whiteLevel", (float) parameters.whiteLevel);
            glProg.setVar("blackLevel", blackLevel);
            glProg.setVar("exposure", 1.0f / images.get(0).pair.layerMpy);
            glProg.setVar("createDiff", 0);
            glProg.setVar("cfaPattern", parameters.cfaPattern);
            glProg.setTexture("inTexture", rawSrc);
            glProg.setTextureCompute("outTexture", tempFloat, true);
            glProg.computeAuto(rawHalf, 1);

            // Incremental mix: mix(currentAvg, newFrame, 1/(i+1))
            // i=0 → weight=1.0 copies newFrame wholesale (currentAvg is uninitialised zeros)
            float weight = 1.0f / (i + 1);
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/avermix", true);
            glProg.setTextureCompute("currentTexture", avgCurrent, false);
            glProg.setTextureCompute("newTexture",     tempFloat,  false);
            glProg.setTextureCompute("outTexture",     avgNext,    true);
            glProg.setVar("weight", weight);
            glProg.computeAuto(rawHalf, 1);

            // Ping-pong: avgNext becomes the new accumulator
            GLTexture swap = avgCurrent;
            avgCurrent = avgNext;
            avgNext    = swap;
        }

        avgNext.close();
        tempFloat.close();
        if (tempRaw != null) tempRaw.close();
        Log.d(Name, "Averaged " + maxFrames + " frame(s) for hot pixel detection");
        return avgCurrent; // caller must close
    }

    private GLBuffer detectHotPixels(GLTexture avgTex) {
        GLBuffer res = new GLBuffer(MAX_HOT_PIXELS*4+1, new GLFormat(GLFormat.DataType.UNSIGNED_32));
        glProg.setLayout(8,8,1);
        glProg.useAssetProgram("merge/hotpixeldetect", true);
        glProg.setVar("noiseS", noiseS);
        glProg.setVar("noiseO", noiseO);
        glProg.setVar("detectThr", (float) detectThr);
        glProg.setVar("maxCount", MAX_HOT_PIXELS);
        glProg.setTexture("inTexture", avgTex);
        glProg.setBufferCompute("HotPixelList",res);
        glProg.computeAuto(base.mSize, 1);
        int[] outputArr = res.readBufferIntegers(false);
        int rawCount = Math.min(outputArr[0], MAX_HOT_PIXELS);
        Log.d(Name, "Hot pixels detected (raw):" + rawCount);
        
        hotPixelCount = filterHotPixels(outputArr, rawCount, res);
        Log.d(Name, "Hot pixels after filtering:" + hotPixelCount);
        return res;
    }
    
    private int filterHotPixels(int[] data, int count, GLBuffer buffer) {
        if (count <= 0) return 0;
        
        // Structure: data[0] = count, then for each pixel: x, y, channels, strength
        ArrayList<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = 1 + i * 4;
            int x = data[idx];
            int y = data[idx + 1];
            int ch = data[idx + 2];
            int strength = data[idx + 3];
            candidates.add(new int[]{x, y, ch, strength, i});
        }
        
        // If too many detections, likely false positives - filter by strength
        if (count > MAX_REASONABLE_HOTPIXELS) {
            Log.d(Name, "Too many hot pixels, filtering by strength");
            // Sort by strength (descending)
            candidates.sort((a, b) -> Integer.compare(b[3], a[3]));
            // Keep only the strongest
            while (candidates.size() > MAX_REASONABLE_HOTPIXELS) {
                candidates.remove(candidates.size() - 1);
            }
        }
        
        ArrayList<int[]> filtered = candidates;
        
        // Statistical outlier removal based on strength distribution
        if (filtered.size() > 50) {
            // Calculate mean and stddev of strength
            double sum = 0, sumSq = 0;
            for (int[] c : filtered) {
                sum += c[3];
                sumSq += (double)c[3] * c[3];
            }
            double mean = sum / filtered.size();
            double variance = sumSq / filtered.size() - mean * mean;
            double stddev = Math.sqrt(Math.max(variance, 1));
            
            // Remove weak outliers (strength < mean - 1.5*stddev)
            double threshold = mean - 1.5 * stddev;
            ArrayList<int[]> statistical = new ArrayList<>();
            for (int[] c : filtered) {
                if (c[3] >= threshold) {
                    statistical.add(c);
                }
            }
            Log.d(Name, "Statistical filtering: mean=" + (int)mean + " stddev=" + (int)stddev + " thr=" + (int)threshold);
            Log.d(Name, "Removed " + (filtered.size() - statistical.size()) + " weak detections");
            filtered = statistical;
        }
        
        // Repack filtered results back into buffer
        int finalCount = filtered.size();
        data[0] = finalCount;
        for (int i = 0; i < finalCount; i++) {
            int[] c = filtered.get(i);
            int idx = 1 + i * 4;
            data[idx] = c[0];
            data[idx + 1] = c[1];
            data[idx + 2] = c[2];
            data[idx + 3] = c[3];
        }
        buffer.uploadBuffer(data, finalCount * 4 + 1);
        
        return finalCount;
    }

    private void correctHotPixelsBase(GLBuffer buffer, int count){
        if (count > 0) {
            glProg.setLayout(64, 1, 1);
            glProg.useAssetProgram("merge/hotpixelcorrect", true);
            glProg.setBufferCompute("HotPixelList", buffer);
            glProg.setTextureCompute("inTexture", base, false);
            glProg.setTextureCompute("outTexture", base, true);
            glProg.computeManual((count + 63) / 64, 1, 1);
            Log.d(Name, "Hot pixels corrected in base:" + count);
        }
    }

    private void correctHotPixelsInAlter(GLBuffer buffer, int count){
        if (count > 0) {
            glProg.setLayout(64, 1, 1);
            glProg.useAssetProgram("merge/hotpixelcorrect", true);
            glProg.setBufferCompute("HotPixelList", buffer);
            glProg.setTextureCompute("inTexture", alter, false);
            glProg.setTextureCompute("outTexture", alter, true);
            glProg.computeManual((count + 63) / 64, 1, 1);
            Log.d(Name, "Hot pixels corrected in alter:" + count);
        }
    }

    private void hotPixels(){
        float[] blackLevel = parameters.blackLevel;
        GLTexture avgTex = buildAveragedFrame(blackLevel, 8);
        hotPixelBuffer = detectHotPixels(avgTex);
        avgTex.close();
        correctHotPixelsBase(hotPixelBuffer, hotPixelCount);
    }

    GLTexture inputBase;
    GLTexture baseDiff;
    GLTexture baseDiffOr;
    GLTexture diffFlow;
    GLTexture base;
    GLTexture baseAlter;
    //GLTexture;
    GLTexture brightMap;
    GLTexture result;
    GLTexture inputAlter;
    GLTexture alter;
    GLTexture alignmentTex;
    GLTexture hotPix;
    //GLTexture noiseMap;
    GLUtils.Pyramid pyramid;
    GLUtils.Pyramid pyramidBase;
    @Tunable(title = "HotPixels detect threshold", category = "Merge", description = "Higher multiplier detects less hotpixels", min = 0.5f, max = 5.0f, step = 0.1f, defaultValue = 1.5f)
    double detectThr;

    @Tunable(title = "Enable Adaptive Noise Model", category = "Merge", description = "Creates noise multiplier based on stdev", min = 0, max = 1, step = 1, defaultValue = 1)
    boolean enableAdaptiveNoise;

    @Tunable(title = "Enable Alignment", category = "Merge", description = "Disable to test merging motion filtering without alignment", min = 0, max = 1, step = 1, defaultValue = 1)
    boolean enableAlignment;

    @Tunable(title = "Enable Adaptive Noise Storage", category = "Merge", description = "Persist fitted noise model into the dynamic multisample store", min = 0, max = 1, step = 1, defaultValue = 1)
    boolean enableNoiseStore;

    @Tunable(title = "Merge noise multiplier", category = "Merge", description = "Scales the noise model fed to the merge wiener weights. Default 2 accounts for the diff spanning two frames (2x single-frame variance).", min = 0.1f, max = 20.0f, step = 0.05f, defaultValue = 2.0f)
    float noiseMpy;

    @Tunable(title = "Adaptive Low", category = "Merge", min = 0.0f, max = 1.0f, step = 1.0f/4.0f, defaultValue = 1.0f/3.0f)
    double noiseMpyLow;
    @Tunable(title = "Adaptive High", category = "Merge", min = 1.0f, max = 4.0f, step = 1.0f/2.0f, defaultValue = 3)
    double noiseMpyHigh;

    @Override
    public void Run() {
        com.particlesdevs.photoncamera.settings.TunableInjector.inject(this);
        glUtils = new GLUtils(glOne.glProcessing);
        Point alignmentOutputSize = new Point(parameters.alignmentSize.x * parameters.tilesX,
                parameters.alignmentSize.y * ((images.size()-1)/parameters.tilesX + 1));
        Log.d("Alignment", "alignment pipeline size: " + alignmentOutputSize.x + " " + alignmentOutputSize.y);
        if (enableAlignment) {
            PyramidAlignment pyramidAlignment = new PyramidAlignment(alignmentOutputSize, images, glProg, glUtils, this);
            pyramidAlignment.parameters = parameters;
            long startTime = System.currentTimeMillis();
            pyramidAlignment.Run();
            Log.d("PyramidMerging", "Alignment time: " + (System.currentTimeMillis() - startTime) + "ms");
            alignmentTex = pyramidAlignment.Result;
            pyramidAlignment.close();
        } else {
            alignmentTex = new GLTexture(alignmentOutputSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    BufferUtils.getFrom(new float[alignmentOutputSize.x * alignmentOutputSize.y * 4]),
                    GL_NEAREST, GL_CLAMP_TO_EDGE);
            Log.d("PyramidMerging", "Alignment disabled, using identity alignment");
        }
        Point raw = parameters.rawSize;
        Point rawHalf = new Point(parameters.rawSize.x/2,parameters.rawSize.y/2);
        result = new GLTexture(raw,new GLFormat(GLFormat.DataType.UNSIGNED_16,1), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        inputBase = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16,1),images.get(0).buffer, GL_NEAREST, GL_CLAMP_TO_EDGE);
        // The base frame's CPU buffer is copied into the inputBase texture above
        // (synchronous upload), so it is not needed anymore and can be released.
        images.get(0).close();
        // Pyramid diff
        baseDiff = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        baseDiffOr = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        diffFlow = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        // Temporal result
        base = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        baseAlter = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        alter = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        //avrFrames = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_MIRRORED_REPEAT);
        //noiseMap = new GLTexture(new Point(rawHalf.x/4,rawHalf.y/4),new GLFormat(GLFormat.DataType.FLOAT_32,4));
        brightMap = new GLTexture(new Point(rawHalf.x/4,rawHalf.y/4),new GLFormat(GLFormat.DataType.FLOAT_16,4));
        //hotPix = new GLTexture(rawHalf,new GLFormat(GLFormat.DataType.SIMPLE_8,4));
        //float[] blackLevel = parameters.blackLevel;
        float[] blackLevel = new float[]{parameters.blackLevel[0]*0.5f, parameters.blackLevel[1]*0.5f, parameters.blackLevel[2]*0.5f, parameters.blackLevel[3]*0.5f};
        int levelcount = (int)(Math.log10(rawHalf.x)/Math.log10(downScalePerLevel))-1;
        if(levelcount <= 0) levelcount = 2;
        //float bl = Math.max(Math.max(parameters.blackLevel[0], parameters.blackLevel[1]), Math.max(parameters.blackLevel[2], parameters.blackLevel[3]));
        glOne.glProgram.setDefine("RAWSIZE",parameters.rawSize);
        glOne.glProgram.setDefine("CFAPATTERN",(int)parameters.cfaPattern);

        float[] analogBalance = new float[4];
        switch (parameters.cfaPattern){
            case 0: // RGGB
                analogBalance[0] = 1.0f/parameters.whitePoint[0];
                analogBalance[1] = 1.0f/parameters.whitePoint[1];
                analogBalance[2] = 1.0f/parameters.whitePoint[1];
                analogBalance[3] = 1.0f/parameters.whitePoint[2];
                break;
            case 1: // GRBG
                analogBalance[0] = 1.0f/parameters.whitePoint[1];
                analogBalance[1] = 1.0f/parameters.whitePoint[0];
                analogBalance[2] = 1.0f/parameters.whitePoint[2];
                analogBalance[3] = 1.0f/parameters.whitePoint[1];
                break;
            case 2: // GBRG
                analogBalance[0] = 1.0f/parameters.whitePoint[1];
                analogBalance[1] = 1.0f/parameters.whitePoint[2];
                analogBalance[2] = 1.0f/parameters.whitePoint[0];
                analogBalance[3] = 1.0f/parameters.whitePoint[1];
                break;
            case 3: // BGGR
                analogBalance[0] = 1.0f/parameters.whitePoint[2];
                analogBalance[1] = 1.0f/parameters.whitePoint[1];
                analogBalance[2] = 1.0f/parameters.whitePoint[1];
                analogBalance[3] = 1.0f/parameters.whitePoint[0];
                break;
        }
        NoiseModeler modeler = parameters.noiseModeler;
        noiseS = modeler.baseModel[0].first.floatValue() +
                modeler.baseModel[1].first.floatValue() +
                modeler.baseModel[2].first.floatValue();
        noiseO = modeler.baseModel[0].second.floatValue() +
                modeler.baseModel[1].second.floatValue() +
                modeler.baseModel[2].second.floatValue();
        noiseS /= 3.f;
        noiseO /= 3.f;
        //GLUtils glUtils = new GLUtils(glOne.glProcessing);
        int tile = 8;
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge00",true);
        glProg.setVar("whiteLevel",(float)(parameters.whiteLevel));
        glProg.setVar("blackLevel", blackLevel);
        glProg.setVar("exposure", 1.f/images.get(0).pair.layerMpy);
        glProg.setVar("createDiff", 0);
        glProg.setVar("cfaPattern", parameters.cfaPattern);
        glProg.setVar("analogBalance", analogBalance);
        glProg.setVar("randF", (float)Math.random(), (float)Math.random());
        // Test value if enabled in shader
        //glProg.setVar("noiseS", 0.0013796629f);
        //glProg.setVar("noiseO", 8.3751265E-6f);
        //glProg.setVar("noiseS", 0.05f);
        //glProg.setVar("noiseO", 0.0f);
        glProg.setTexture("inTexture",inputBase);
        glProg.setTextureCompute("outTexture",base, true);
        glProg.computeAuto(new Point(base.mSize.x, base.mSize.y), 1);
        //glUtils.convertVec4(base, "vec4(0.5)", base);
        //var buff = glUtils.GenerateGLImage(base.mSize, 4);
        //Log.d(Name, "Buffer first:" + buff.byteBuffer.get(0) + " " + buff.byteBuffer.get(1));
        //glUtils.Result(base.mSize, "noiseInput", buff.byteBuffer);

        double adaptiveNMpy = 1.0;
        if (enableAdaptiveNoise) {
            // 2D histogram: (brightness_bin * NUM_VARIANCE_BINS + variance_bin) -> count
            // Model: variance = NoiseS * brightness + NoiseO  =>  sigma = sqrt(NoiseS*b + NoiseO)
            final int numBrightnessBins = 64;
            final int numVarianceBins = 64;
            final int noiseScanBins = numBrightnessBins * numVarianceBins; // 1024
            // Variance scale: max variance ~(numVarianceBins-0.5)/scale. Use 160 so we cover up to ~0.2 for noisy sensors.
            final float varianceScale = 64.0f * (float)Math.sqrt(5.0f);
            final float brightnessScale = 64.0f * (float)Math.sqrt(3.0f);
            GLHistogram noiseHist = new GLHistogram(glProg, noiseScanBins);
            noiseHist.Custom = true;
            noiseHist.Rc = true;
            noiseHist.Gc = false;
            noiseHist.Bc = false;
            noiseHist.Ac = false;
            noiseHist.exposure[0] = 1.0f;
            noiseHist.exposure[1] = 1.0f;
            noiseHist.exposure[2] = 1.0f;
            noiseHist.exposure[3] = 1.0f;
            noiseHist.CustomShader = "merge/noisehist";
            noiseHist.input1 = brightnessScale;
            noiseHist.input2 = varianceScale;
            int[][] noiseRes = noiseHist.Compute(base);
            int[] hist = noiseRes[0];
            int varCnt = 0;
            float[] weights = new float[numVarianceBins];
            float wSum = 0.0f;
            float minBr = 0.0f;
            for (int i = 0; i < noiseScanBins; i++) {
                int count = hist[i];
                var bin = i / numVarianceBins;
                var vin = i % numVarianceBins;
                if(vin == 0) {
                    varCnt = 0;
                }
                if (count <= 0 || bin == numBrightnessBins-1 || (varCnt >= 30 && vin == 63) || varCnt > 45) continue;
                varCnt++;
                if(minBr == 0.0f) {
                    minBr = ((float)bin + 0.5f) / brightnessScale;
                    minBr = (float) Math.pow(minBr, 2.0);
                }
                double w = count;
                weights[vin] += (float) w;
                wSum += w;
            }
            float wWindow = Math.max(weights[0], weights[1]);
            for (int i = 0; i < numVarianceBins; i++) {
                wWindow = Math2.mix(Math.max(wWindow, weights[i]), weights[i], 0.025f);
                weights[i] = 0.5f + wWindow / wSum;
                Log.d("DynamicNoise", "Variance weight: " + weights[i]);
            }
            // Weighted linear regression: variance = NoiseS * brightness + NoiseO
            double sumW = 0, sumWb = 0, sumWv = 0, sumWb2 = 0, sumWbv = 0;
            int points = 0;
            varCnt = 0;
            for (int i = 0; i < noiseScanBins; i++) {
                int count = hist[i];
                var bin = i / numVarianceBins;
                var vin = i % numVarianceBins;
                if(vin == 0) {
                    varCnt = 0;
                }
                if (count <= 0 || bin == numBrightnessBins-1 || (varCnt >= 30 && vin == 63) || varCnt > 45) continue;
                varCnt++;
                double brightness = ((double)(bin) + 0.5) / ((double)brightnessScale);
                brightness = Math.pow(brightness, 2.0);
                brightness = (brightness - minBr)/(1.0 - minBr);
                double variance = (vin + 0.5) / varianceScale;
                // Median-of-squared-deviations (shader "var") ≈ 0.6745*sigma, so a
                // single 1.4826 (=1/0.6745) converts it to sigma, then squaring gives
                // the true variance. (Double-multiplying previously biased S/O by ~2.2x.)
                variance *= 1.4826;
                variance = Math.pow(variance, 2.0);

                Log.d("DynamicNoise", "vin:"+ vin + " bin: " + bin + " Variance raw: " + variance + " brightness: " + brightness + " count: " + count);
                double w = count * 1.0f;
                sumW += w;
                sumWb += w * brightness;
                sumWv += w * variance;
                sumWb2 += w * brightness * brightness;
                sumWbv += w * brightness * variance;
                points++;
            }
            //points = 9;
            if (points >= 1) {
                double denom = sumW * sumWb2 - sumWb * sumWb;
                if (denom > 1e-20) {
                    double fitS = (sumW * sumWbv - sumWb * sumWv) / denom;
                    double fitO = (sumWv - fitS * sumWb) / sumW;
                    fitS = Math.max(fitS, 1e-10);
                    Log.d("DynamicNoise",  "Fit S:" + fitS + " O:" + fitO);
                    // Keep at least 5% of original read noise so we don't collapse to zero on noisy sensors
                    double minO = 0.05 * noiseO;
                    fitO = Math.max(fitO, minO);
                    // Read-noise floor: O=S/7 overstates read noise now that the variance
                    // estimator is unbiased (previously S carried a ~2.2x bias that made
                    // S/7 a sane proxy). S/20 keeps a guard against O collapsing while no
                    // longer dominating realistic sensors (O/S is typically < 0.05).
                    fitO = Math.max(fitO, fitS/20);
                    fitS = Math.max(fitS, parameters.noiseModeler.SPlace(parameters.iso));
                    fitO = Math.max(fitO, parameters.noiseModeler.OPlace(parameters.iso)*3.0f);
                    // Commit the fitted S/O to the multisample noise map, then read
                    // back the blended (moving-average) value. Committing before
                    // reading makes the current estimation participate in the
                    // average, while the store's measurement-list guard skips
                    // duplicate scenes (same exposure/iso) to avoid bias. Using the
                    // blended output smooths per-capture estimator fluctuations.
                    double commitS = fitS;
                    double commitO = fitO;
                    DynamicNoiseStore.NoiseEstimate blended = null;
                    if (enableNoiseStore) {
                        blended = DynamicNoiseStore.dynamicNoiseStore.commitAndGet(
                                parameters.physicalID, parameters.iso,
                                parameters.noiseModeler.AnalogueISO,
                                commitS, commitO, parameters.exposureTime);
                    }
                    if (blended != null) {
                        fitS = blended.s;
                        fitO = blended.o;
                        // Re-apply floors defensively on the blended result.
                        //fitS = Math.max(fitS, parameters.noiseModeler.SPlace(parameters.iso));
                        //fitO = Math.max(fitO, parameters.noiseModeler.OPlace(parameters.iso));
                        Log.d("DynamicNoise", "Blended noise model from store: S=" + fitS
                                + " O=" + fitO + " for iso=" + parameters.iso);
                    }
                    fitO += fitS*fitS * 3.0/8.0; // Correction factor
                    noiseS = (float) fitS;
                    noiseO = (float) fitO;
                    Log.d("DynamicNoise",  "Fitted noise model: NoiseS=" + noiseS + " NoiseO=" + noiseO + " Half=" + Math.sqrt(noiseS * 0.5 + noiseO) + " (points=" + points + ")");
                    parameters.noiseModeler.baseModel = new Pair[] {
                            new Pair<>((double) noiseS, (double) noiseO),
                            new Pair<>((double) noiseS, (double) noiseO),
                            new Pair<>((double) noiseS, (double) noiseO)};
                }
                adaptiveNMpy = 1.0;
            } else {
                // Fallback: scale original model to match observed at mid-gray (same as before)
                double modelSigmaMid = Math.sqrt(noiseS * 0.5 + noiseO);
                if (modelSigmaMid > 1e-10) {
                    double sumWeightedSigma = 0, sumWeightedCount = 0;
                    for (int i = 0; i < noiseScanBins; i++) {
                        int count = hist[i];
                        if (count <= 0) continue;
                        double sigma = ((i % numVarianceBins + 0.5) / varianceScale) * 1.4826;
                        sumWeightedSigma += sigma * count;
                        sumWeightedCount += count;
                    }
                    if (sumWeightedCount > 0) {
                        double observedSigma = sumWeightedSigma / sumWeightedCount;
                        adaptiveNMpy = observedSigma / modelSigmaMid;
                        adaptiveNMpy = Math2.clamp(adaptiveNMpy, noiseMpyLow, noiseMpyHigh);
                    }
                }
                Log.d("DynamicNoise", "Adaptive Mpy (fallback): " + adaptiveNMpy + " (insufficient points=" + points + ")");
            }
        }
        parameters.noiseModeler.setAdaptiveMpy(adaptiveNMpy);
        double noisempy = Math.pow(2.0, PhotonCamera.getSettings().mergeStrength);
        //double noiseMin = 1.0/(double)parameters.whiteLevel;
        double noiseMin = 1e-6;
        noiseS = (float)Math.max(noiseS * noisempy * adaptiveNMpy * adaptiveNMpy * noiseMpy,noiseMin);
        noiseO = (float)Math.max(noiseO * noisempy * adaptiveNMpy * adaptiveNMpy * noiseMpy,noiseMin);
        if(enableHotPixelCorrection)
            hotPixels();

        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge02",true);
        glProg.setTextureCompute("inTexture",base, false);
        glProg.setTextureCompute("outTexture",brightMap, true);
        glProg.computeAuto(brightMap.mSize, 1);

        /*glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge00",true);
        glProg.setVar("whiteLevel",(float)(parameters.whiteLevel));
        glProg.setVar("blackLevel", blackLevel);
        glProg.setVar("exposure", 1.f/1.f);
        glProg.setVar("createDiff", 0);
        glProg.setVar("cfaPattern", parameters.cfaPattern);
        glProg.setTexture("inTexture",inputBase);
        glProg.setTextureCompute("outTexture",baseLow, true);
        glProg.computeAuto(new Point(baseLow.mSize.x, baseLow.mSize.y), 1);*/

        /*
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge01",true);
        glProg.setTextureCompute("inTexture",base, false);
        glProg.setTextureCompute("outTexture",noiseMap, true);
        glProg.computeAuto(noiseMap.mSize, 1);

        GLHistogram glHistogram = new GLHistogram(glOne.glProcessing, 64);
        glHistogram.Custom = true;
        glHistogram.resize = 1;
        glHistogram.CustomProgram = "atomicAdd(reds[uint(texColor.r * HISTSIZE)], 1u);" +
                "atomicAdd(greens[uint(texColor.r * HISTSIZE)], uint(texColor.g * 1024.0));" +
                "atomicAdd(blues[uint(texColor.r * HISTSIZE)], uint(texColor.b * 1024.0));" +
                "atomicAdd(alphas[uint(texColor.r * HISTSIZE)], uint(texColor.a * 1024.0));";
        int[][] hist = glHistogram.Compute(noiseMap);
        // print noise map hist
        float[] noise = new float[64];
        float[] brightness = new float[64];
        int cnt = 0;
        for(int i = 0; i < 64; i++){
            int counter = hist[0][i];
            float n = (hist[2][i])/(1.f*1024.f*counter);
            if(counter > 10) {
                noise[cnt] = n;
                brightness[cnt] = (float)(i)/63.f;
                cnt++;
            }
        }
        List<NoiseFitting.DataPoint> data = new ArrayList<>();
        for(int i = 0; i < cnt; i++){
            data.add(new NoiseFitting.DataPoint(brightness[i],noise[i]));
        }
        NoiseFitting.NoiseParameters fitted = NoiseFitting.findParameters(data);
        Log.d(Name, "Fitted parameters: " + fitted.toString());*/
        pyramid = new GLUtils.Pyramid();
        //pyramidBase = new GLUtils.Pyramid();

        //glUtils.createPyramidStore(levelcount, baseLow, pyramidBase);


        //Point aSize = new Point(parameters.rawSize.x/(2*parameters.tile) + 1, parameters.rawSize.y/(2*parameters.tile) + 1);
        Point border = new Point(16,16);
        inputAlter = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 1), null, GL_NEAREST, GL_MIRRORED_REPEAT);
        //alignmentTex = new GLTexture(aSize, new GLFormat(GLFormat.DataType.FLOAT_32, 2), alignment, GL_NEAREST, GL_MIRRORED_REPEAT);

        float minExp = 1.f;
        int minExpIdx = 0;
        int lowCnt = 0;
        for (int i = 1; i < images.size(); i++) {
            ImageFrame frame = images.get(i);
            float exposure = 1.f/frame.pair.layerMpy;
            Log.d("PyramidMerging", "exposure: " + exposure);
            if(exposure < 0.95f) {
                lowCnt++;
            }
            if(exposure < minExp) {
                minExpIdx = i;
                minExp = exposure;
            }
        }
        //counter.put(1.0f,1.0f);
        float cnt1 = 2.0f;

        float cnt2 = 1.0f;
        //Log.d("PyramidMerging", "alignment size: " + aSize.x + " " + aSize.y);
        Log.d("PyramidMerging", "alignment size: " + parameters.alignmentSize.x + " " + parameters.alignmentSize.y);
        float maxBlack = Math.max(blackLevel[0], Math.max(blackLevel[1], Math.max(blackLevel[2], blackLevel[3])));
        float minLevel = (float) (1.0/(double)(parameters.whiteLevel-maxBlack));

        for (int f = 0; f < images.size(); f++) {
            if(f == minExpIdx) continue;
            int ind = f;
            if(ind == 0){
                ind = minExpIdx;
            }
            ImageFrame frame = images.get(ind);
            float exposure = 1.f/frame.pair.layerMpy;
            Point shift = PyramidAlignment.alignmentShift(parameters, ind);
            //int f = 1;
            Log.d("PyramidMerging", "load:"+frame.pair.curlayer.name() + " " + frame.pair.layerMpy);
            inputAlter.loadData(frame.buffer);
            
            // Convert inputAlter to alter (vec4 format)
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge00", true);
            glProg.setVar("whiteLevel", (float)(parameters.whiteLevel));
            glProg.setVar("blackLevel", blackLevel);
            glProg.setVar("exposure", 1.f/images.get(0).pair.layerMpy);
            glProg.setVar("createDiff", 0);
            glProg.setVar("cfaPattern", parameters.cfaPattern);
            glProg.setTexture("inTexture", inputAlter);
            glProg.setTextureCompute("outTexture", alter, true);
            glProg.computeAuto(new Point(alter.mSize.x, alter.mSize.y), 1);
            
            correctHotPixelsInAlter(hotPixelBuffer, hotPixelCount);
            //alignmentTex.loadData(alignment.position((ind-1)*(aSize.x*aSize.y*4*2)));
            glProg.setDefine("TILE_AL", parameters.tile);
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge0", true);
            glProg.setVar("rawHalf", rawHalf);
            glProg.setVar("whiteLevel", (float) (parameters.whiteLevel));
            glProg.setVar("whitePoint", parameters.whitePoint);
            glProg.setVar("blackLevel", blackLevel);
            glProg.setVar("minLevel",minLevel);
            glProg.setVar("exposure", exposure);
            glProg.setVar("analogBalance", analogBalance);
            if(exposure >= 0.95f) {
                if(lowCnt > 1)
                    glProg.setVar("exposureLow", minExp - 0.05f);
                else {
                    glProg.setVar("exposureLow", 0.0f);
                }
            } else {
                glProg.setVar("exposureLow", 0.0f);
            }
            glProg.setVar("createDiff", 1);
            glProg.setVar("noiseS", noiseS);
            glProg.setVar("noiseO", noiseO);
            glProg.setVar("border", border);
            glProg.setVar("shift", shift);
            glProg.setVar("alignmentSize", parameters.alignmentSize);
            glProg.setTexture("inTexture", inputBase);
            glProg.setTexture("alignmentTexture", alignmentTex);
            glProg.setTextureCompute("baseTexture",base, false);
            glProg.setTextureCompute("alterTexture", alter, false);
            //glProg.setTextureCompute("avrTexture", avrFrames, false);
            //glProg.setTextureCompute("hotPixTexture", hotPix, false);
            glProg.setTextureCompute("outTexture", baseDiff, true);
            glProg.computeAuto(baseDiff.mSize, 1);

            // apply optical flow
            //glProg.setLayout(tile, tile, 1);
            //glProg.useAssetProgram("merge/merge03", true);
            //glProg.setTextureCompute("diffTexture", baseDiff, false);
            //glProg.setTextureCompute("baseTexture",base, false);
            //glProg.setTextureCompute("outTexture", diffFlow, true);
            //glProg.setVar("whiteLevel", (float) (parameters.whiteLevel));
            //glProg.setVar("blackLevel", parameters.blackLevel);
            //glProg.setVar("noiseS", noiseS);
            //glProg.setVar("noiseO", noiseO);
            //glProg.setVar("cfaPattern", parameters.cfaPattern);
            //glProg.computeAuto(rawHalf, 1);

            glUtils.convertVec4(baseDiff, "in1", baseDiffOr);
            Log.d("PyramidMerging", "create diff");
            GLUtils.Pyramid diff = glUtils.createPyramidStore(levelcount, baseDiff, pyramid);
            Log.d("PyramidMerging", "diff created");

            Log.d("PyramidMerging", "diff.laplace.length: " + diff.laplace.length + " diff.gauss.length: " + diff.gauss.length);
            // do pyramid upscaling
            for (int i = diff.laplace.length - 1; i >= 0; i--) {
                float integralNorm = (float)rawHalf.x * rawHalf.y/(diff.gauss[i].mSize.x * diff.gauss[i].mSize.y);
                //if(i == diff.laplace.length - 1) integralNorm = 0.f;
                glProg.setLayout(tile, tile, 1);
                glProg.useAssetProgram("merge/merge1", true);
                glProg.setTexture("brTexture", brightMap);
                glProg.setTexture("baseTexture", diff.gauss[i + 1]);
                //glProg.setTexture("baseOriginTexture", pyramidBase.gauss[i + 1]);
                glProg.setTextureCompute("diffTexture", diff.laplace[i], false);
                //glProg.setTextureCompute("diffOriginTexture", pyramidBase.laplace[i], false);
                glProg.setTextureCompute("outTexture", diff.gauss[i], true);
                //glProg.setVar("noiseS", (float) fitted.S);
                glProg.setVar("size", 1.0f/diff.gauss[i].mSize.x, 1.0f/diff.gauss[i].mSize.y);
                glProg.setVar("minLevel",minLevel);
                glProg.setVar("noiseS", noiseS);
                //glProg.setVar("noiseO", (float) fitted.O);
                glProg.setVar("noiseO", noiseO);
                glProg.setVar("cfaPattern", parameters.cfaPattern);
                glProg.setVar("integralNorm", (float) Math.sqrt(integralNorm));
                glProg.setVar("first", (i==diff.laplace.length - 1) ? 1 : 0);
                glProg.computeAuto(diff.gauss[i].mSize, 1);
            }

            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge11", true);
            glProg.setVar("cfaPattern", parameters.cfaPattern);
            glProg.setTexture("inTex", inputBase);
            glProg.setTextureCompute("inTexture", base, false);
            //glProg.setTexture("alterTexture", inputAlter);
            glProg.setTextureCompute("diffTexture", diff.gauss[0], false);
            glProg.setTextureCompute("diffOrTexture", baseDiffOr, false);
            base = getBase();
            glProg.setTextureCompute("outTexture", base, true);
            glProg.setVar("noiseS", noiseS);
            glProg.setVar("noiseO", noiseO);
            glProg.setVar("whiteLevel", (float) (parameters.whiteLevel));
            glProg.setVar("blackLevel", blackLevel);
            glProg.setVar("analogBalance", analogBalance);
            //glProg.setVar("weight",  1.0f/(images.size()));
            //glProg.setVar("weight", 1.0f/(counter.get(exposure)+1.f));
            //glProg.setVar("weight2", 1.0f/(counter.get(exposure)+1.f));
            //glProg.setVar("weight", 1.0f/(f+1.f));
            //glProg.setVar("weight", 1.0f/(counter.get(exposure)));
            if(exposure >= 0.95f){
                glProg.setVar("weight", 1.0f/cnt1);
                glProg.setVar("exposure", minExp);
                cnt1+=1.0f;
            } else {
                glProg.setVar("weight", 1.0f/cnt2);
                glProg.setVar("exposure", 1.0f);
                cnt2+=1.0f;
            }
            //glProg.setVar("exposure", exposure);
            //glProg.setVar("weight",  1.0f);
            glProg.computeAuto(base.mSize, 1);
            // This frame's CPU buffer has been uploaded into inputAlter and merged
            // into the pyramid; release it to keep the peak footprint at roughly one
            // frame instead of N frames held for the whole merge.
            frame.close();
        }

        /*
        // Remove residual noise
        GLUtils.Pyramid full = glUtils.createPyramidStore(levelcount, base, pyramid);
        for (int i = full.laplace.length - 1; i >= 0; i--) {
            float integralNorm = (float)base.mSize.x * base.mSize.y/(full.gauss[i+1].mSize.x * full.gauss[i+1].mSize.y);
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge4", true);
            glProg.setTexture("brTexture", brightMap);
            glProg.setTexture("baseTexture", full.gauss[i + 1]);
            glProg.setTextureCompute("diffTexture", full.laplace[i], false);
            //if(i != 0)
                glProg.setTextureCompute("outTexture", full.gauss[i], true);
            //else {
            //    glProg.setTextureCompute("outTexture", base, true);
            //}
            //glProg.setVar("noiseS", (float) fitted.S);
            glProg.setVar("noiseS", noiseS/256);
            //glProg.setVar("noiseO", (float) fitted.O);
            glProg.setVar("noiseO", noiseO/256);
            glProg.setVar("integralNorm", integralNorm);
            glProg.computeAuto(full.gauss[i].mSize, 1);
        }*/
        float[] bl2 = new float[4];
        for (int i = 0; i < 4; i++) {
            bl2[i] = blackLevel[i]*(FAKE_WL / parameters.whiteLevel);
        }
        glProg.setDefine("WHITE_LEVEL", FAKE_WL);
        glProg.setDefine("BLACK_LEVEL", bl2);
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge2o");
        //glProg.setVar("whiteLevel",65535.f);
        //glProg.setVar("blackLevel", bl2);
        //glProg.setVar("blackLevel", 0.0f);
        glProg.setTexture("inTexture",base);
        glProg.setTexture("alignmentTexture", alignmentTex);
        //glUtils.convertVec4(outputTex,"in1/2.0");
        //glUtils.SaveProgResult(outputTex.mSize,"gainmap");
        result.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        Output = glOne.glProcessing.mOutBuffer;
        AfterRun();
    }

    @Override
    public void AfterRun() {
        if(hotPixelBuffer != null) hotPixelBuffer.close();
        inputAlter.close();
        alter.close();
        inputBase.close();
        baseDiff.close();
        base.close();
        baseAlter.close();
        brightMap.close();
        result.close();
        alignmentTex.close();
        diffFlow.close();
        baseDiffOr.close();
        //noiseMap.close();
        for (int i = 0; i < pyramid.gauss.length; i++) {
            pyramid.gauss[i].close();
        }

        for (int i = 0; i < pyramid.laplace.length; i++) {
            pyramid.laplace[i].close();
        }
        GLTexture.notClosed();
    }
}
