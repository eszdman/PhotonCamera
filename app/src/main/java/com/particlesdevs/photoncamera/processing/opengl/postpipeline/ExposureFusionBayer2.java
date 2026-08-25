package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Math2;
import com.particlesdevs.photoncamera.util.SplineInterpolator;
import com.particlesdevs.photoncamera.util.Utilities;

import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MIRRORED_REPEAT;
import static com.particlesdevs.photoncamera.util.Math2.mix;

public class ExposureFusionBayer2 extends Node {

    public ExposureFusionBayer2() {
        super("", "FusionBayer");
    }
    @Override
    public void Compile() {}
    private double dehaze = 0.0;
    GLTexture expose(GLTexture in, float strLow,float strHigh){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.setDefine("STRLOW",strLow);
        glProg.setDefine("STRHIGH",strHigh);
        glProg.setDefine("CURVE",true);
        glProg.setDefine("RGBLAYOUT",basePipeline.mSettings.alignAlgorithm == 2);
        glProg.setDefine("COMPRESSOR", (float) basePipeline.mSettings.compressor);
        glProg.setDefine("UPPERLIM", overexposedUpperLimit);
        // Fusion packing: three exposures + blend factor (well-exposedness x
        // Laplacian contrast, Mertens et al.) in alpha, no separate weight
        // textures (see exposebayer2.glsl).
        glProg.setDefine("FUSEWEIGHTED",true);
        glProg.setDefine("TARGET", targetLuma);
        glProg.setDefine("LAPMIN", fusionLaplaceFactorMin);
        Log.d(Name,"Compressor:"+basePipeline.mSettings.compressor);
        glProg.useAssetProgram("ltm/exposebayer2",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("InterpolatedCurve",interpolatedCurve);
        glProg.setTexture("ShadowMap", shadowMap);
        glProg.setTexture("GainMap", ((PostPipeline)basePipeline).GainMap);
        glProg.setVar("neutral", basePipeline.mParameters.whitePoint);
        //glProg.setVar("factor", str);
        GLTexture outp = new GLTexture(WorkSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.drawBlocks(outp);
        return outp;
    }

    GLTexture expose3(GLTexture in, float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.setDefine("RGBLAYOUT",basePipeline.mSettings.alignAlgorithm == 2);
        glProg.setDefine("STRLOW",str);
        glProg.setDefine("STRHIGH",str);
        glProg.setDefine("UPPERLIM", overexposedUpperLimit);
        glProg.useAssetProgram("ltm/exposebayer2",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("GainMap", ((PostPipeline)basePipeline).GainMap);
        glProg.setVar("neutral", basePipeline.mParameters.whitePoint);
        //glProg.setVar("factor", str);
        GLTexture tex = basePipeline.getMain();
        glProg.drawBlocks(tex);
        return tex;
    }
    void getHistogram(GLTexture lowGauss){
        // The old path copied lowGauss into a half-res RGBA16F buffer with
        // .r*4 broadcast to every channel just so the histogram saw the
        // pre-multiplied red. Folding the factor into exposure[0] measures
        // the same bins directly on lowGauss (~129 MB less GPU traffic).
        glHistogram = new GLHistogram(basePipeline.glint.glProcessing);
        glHistogram.exposure[0] = 4.f;
        glHistogram.Compute(lowGauss);
        glHistogram.Bc = false;
        glHistogram.Gc = false;
        glHistogram.Ac = false;
    }

    float autoExposureHigh(){
        float avr = 0.f;
        float w = 0.01f;
        float full = 1.f;
        for(int i = 0; i < 255; i++){
            full += glHistogram.outputArr[0][i];
        }
        for(int i = 5; i < 255; i++){
            float line = (i-5)/255.f;
            float cnt = glHistogram.outputArr[0][i]*Math.max(1.0f-line*1.6f, 0.0f)/full;
            float ind = (float)(Math.pow(line, 1./ gammaKShadowSearch))*256.f;
            float mpy = cnt*(ind);
            avr+=mpy;
            w+=cnt;
            //Log.d(Name,"Overexp pos:"+ind+" val:"+cnt);
        }
        var gain =  Math.max(128/(avr/w + 1),1.f);
        Log.d(Name,"Overexp pos:"+avr/w);
        float gainNoiseMax = Math.max((float) (noiseMax / Math.sqrt(basePipeline.noiseS * 0.5 + basePipeline.noiseO)), 1.0f);
        if(gain > gainNoiseMax) {
            Log.d(Name, "Clamping gain by noise from " + gain + " to " + gainNoiseMax);
            gain = gainNoiseMax;
        }
        return gain;
        //return mix(avr/w,max, overExposeMaxFusion);
    }
    float autoExposureLow(){
        float avr = 0.f;
        float w = 1.f;
        for(int i = 128; i<240;i++){
            float line = i/255.f;
            float ind = (float)(Math.pow(line, 1./ gammaKSearch))*256.f;
            float mpy = glHistogram.outputArr[0][i]*(ind);
            avr+=mpy;
            w+=glHistogram.outputArr[0][i];
        }
        Log.d(Name,"Underexp pos:"+avr/w);
        //return (256 - avr/w)/256;
        return Math.min(0.7f*256/(avr/w + 1),1.f);
        //return mix(avr/w,min,underExposeMinFusion);
    }

    GLTexture fusionMap(GLTexture in,GLTexture br,float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("FUSIONGAIN",((PostPipeline)(basePipeline)).fusionGain);
        glProg.useAssetProgram("ltm/fusionmap",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("BrBuffer",br);
        glProg.setVar("factor", str);
        GLFormat format = new GLFormat(in.mFormat);
        format.filter = GL_LINEAR;
        format.wrap = GL_CLAMP_TO_EDGE;
        GLTexture out = new GLTexture(in,format);
        glProg.drawBlocks(out);
        return out;
    }
    GLHistogram glHistogram;
    Point initialSize;
    Point WorkSize;
    
    @Tunable(
        title = "Over Expose Multiplier",
        description = "Multiplier for overexposed image pair",
        category = "Exposure Fusion",
        defaultValue = 1.0f,
        min = 0.0f,
        max = 2.0f,
        step = 0.01f
    )
    float overExposeMpy = 1.0f;
    
    @Tunable(
        title = "Over Expose Max Fusion",
        description = "Maximum fusion factor for overexposed areas",
        category = "Exposure Fusion",
        defaultValue = 1.0f,
        min = 0.0f,
        max = 2.0f,
        step = 0.01f
    )
    float overExposeMaxFusion = 1.0f;
    
    @Tunable(
        title = "Under Expose Multiplier",
        description = "Multiplier for underexposure adjustment",
        category = "Exposure Fusion",
        defaultValue = 0.85f,
        min = 0.0f,
        max = 2.0f,
        step = 0.01f
    )
    float underExposeMpy = 0.85f;
    
    @Tunable(
        title = "Under Expose Min Fusion",
        description = "Minimum fusion factor for underexposed areas",
        category = "Exposure Fusion",
        defaultValue = 0.0f,
        min = 0.0f,
        max = 1.0f,
        step = 0.01f
    )
    float underExposeMinFusion = 0.0f;
    
    @Tunable(
        title = "Gamma coefficient",
        description = "Gamma coefficient used in underexposure search calculations",
        category = "Exposure Fusion",
        defaultValue = 1.0f,
        min = 0.1f,
        max = 3.0f,
        step = 0.1f
    )
    float gammaKSearch = 1.0f;
    
    float gammaKShadowSearch = 0.8f;
    
    @Tunable(
        title = "Base Exposure",
        description = "Base exposure value for fusion",
        category = "Exposure Fusion",
        defaultValue = 1.00f,
        min = 0.1f,
        max = 5.0f,
        step = 0.01f
    )
    float baseExpose = 1.00f;
    
    @Tunable(
        title = "Gauss Size",
        description = "Gaussian kernel size for fusion weighting",
        category = "Exposure Fusion",
        defaultValue = 4.0f,
        min = 1.0f,
        max = 10.0f,
        step = 0.1f
    )
    float gaussSize = 4.0f;
    
    @Tunable(
        title = "Target Luma",
        description = "Target luminance value",
        category = "Exposure Fusion",
        defaultValue = 0.5f,
        min = 0.0f,
        max = 1.0f,
        step = 0.01f
    )
    float targetLuma = 0.6f;
    
    @Tunable(
        title = "DownScale Per Level",
        description = "Downscaling factor per pyramid level",
        category = "Exposure Fusion",
        defaultValue = 2.0f,
        min = 1.5f,
        max = 4.0f,
        step = 0.1f
    )
    float downScalePerLevel = 2.0f;
    
    @Tunable(
        title = "Dehazing",
        description = "Dehazing strength",
        category = "Exposure Fusion",
        defaultValue = 0.2f,
        min = 0.0f,
        max = 1.0f,
        step = 0.01f
    )
    float dehazing = 0.2f;

    @Tunable(
        title = "Hard Level",
        description = "Hard level threshold",
        category = "Exposure Fusion",
        defaultValue = 0.1f,
        min = 0.0f,
        max = 1.0f,
        step = 0.01f
    )
    float softUpperLevel = 0.1f;
    
    @Tunable(
        title = "Soft Level",
        description = "Soft level threshold",
        category = "Exposure Fusion",
        defaultValue = 0.0f,
        min = 0.0f,
        max = 1.0f,
        step = 0.01f
    )
    float softLoverLevel = 0.0f;
    
    @Tunable(
        title = "Fusion Expo Low Limit",
        description = "Lower limit for fusion exposure",
        category = "Exposure Fusion",
        defaultValue = 1.f/16.f,
        min = 0.001f,
        max = 1.0f,
        step = 0.001f
    )
    float fusionExpoLowLimit = 1.f/16.f;
    
    @Tunable(
        title = "Fusion Expo High Limit",
        description = "Upper limit for fusion exposure",
        category = "Exposure Fusion",
        defaultValue = 64.f,
        min = 1.0f,
        max = 100.0f,
        step = 0.1f
    )
    float fusionExpoHighLimit = 64.f;
    
    @Tunable(
        title = "Overexposed Upper Limit",
        description = "Upper limit for overexposed areas",
        category = "Exposure Fusion",
        defaultValue = 1.0f,
        min = 0.5f,
        max = 2.0f,
        step = 0.01f
    )
    float overexposedUpperLimit = 1.0f;
    
    @Tunable(
        title = "Fusion Laplace Factor Min",
        description = "Minimum factor for Laplace fusion",
        category = "Exposure Fusion",
        defaultValue = 0.01f,
        min = 0.001f,
        max = 0.1f,
        step = 0.001f
    )
    float fusionLaplaceFactorMin = 0.01f;
    
    @Tunable(
        title = "Fusion Expo Factor Min",
        description = "Minimum factor for fusion exposure",
        category = "Exposure Fusion",
        defaultValue = 0.01f,
        min = 0.001f,
        max = 0.1f,
        step = 0.001f
    )
    float fusionExpoFactorMin = 0.01f;

    @Tunable(title = "Noise Max", category = "Exposure Fusion", max = 1.0f, defaultValue = 0.05f)
    float noiseMax;
    
    float[] toneCurveX;
    float[] toneCurveY;

    float[] shadowCurveX;
    float[] shadowCurveY;
    GLTexture interpolatedCurve;
    GLTexture shadowMap;
    
    @Tunable(title = "Enable", category = "Exposure Fusion", defaultValue = 0, min = 0, max = 1, step = 1,
            description = "Enable Exposure Fusion Post Processing")
    boolean enable;
    
    @Tunable(
        title = "Use Symmetric Exposure Fork",
        description = "Use symmetric exposure fork calculation",
        category = "Exposure Fusion",
        defaultValue = 0,
        min = 0,
        max = 1,
        step = 1
    )
    boolean useSymmetricExposureFork = false;
    
    @Tunable(
        title = "Curve Points Count",
        description = "Number of points in tone curve",
        category = "Exposure Fusion",
        defaultValue = 5.0f,
        min = 3.0f,
        max = 10.0f,
        step = 1.0f
    )
    int curvePointsCount = 5;
    
    @Override
    public void Run() {
        if (!enable) {
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        toneCurveX = new float[curvePointsCount];
        toneCurveY = new float[curvePointsCount];
        shadowCurveX = new float[curvePointsCount];
        shadowCurveY = new float[curvePointsCount];
        for(int i = 0; i<curvePointsCount;i++){
            float line = i/((float)(curvePointsCount-1.f));
            toneCurveX[i] = line;
            toneCurveY[i] = 1.0f;
            shadowCurveX[i] = line;
            shadowCurveY[i] = 1.0f;
        }

        if(curvePointsCount == 5) {
            toneCurveX[0] = 0.0f;
            toneCurveX[1] = 0.07f;
            toneCurveX[2] = 0.25f;
            toneCurveX[3] = 0.95f;
            toneCurveX[4] = 1.0f;

            toneCurveY[0] = 1.0f;
            toneCurveY[1] = 1.0f;
            toneCurveY[2] = 1.0f;
            toneCurveY[3] = 1.0f;
            toneCurveY[4] = 1.0f;

            shadowCurveX[0] = 0.0f;
            shadowCurveX[1] = 0.07f;
            shadowCurveX[2] = 0.2f;
            shadowCurveX[3] = 0.95f;
            shadowCurveX[4] = 1.0f;

            shadowCurveY[0] = 8.0f;
            shadowCurveY[1] = 4.0f;
            shadowCurveY[2] = 2.0f;
            shadowCurveY[3] = 0.0f;
            shadowCurveY[4] = 0.0f;
        }


        overExposeMpy = 1.0f + (float) PhotonCamera.getSettings().compressor;
        // Note: toneCurveX and toneCurveY arrays are not yet supported by Tunable system
        // They are initialized based on curvePointsCount above
        ArrayList<Float> curveX = new ArrayList<>();
        ArrayList<Float> curveY = new ArrayList<>();
        float maxC = 0.f;
        for(int i =0; i<curvePointsCount;i++){
            curveX.add(toneCurveX[i]);
            curveY.add(toneCurveY[i]);
            if(toneCurveY[i] > maxC) maxC = toneCurveY[i];
        }


        SplineInterpolator splineInterpolator = SplineInterpolator.createMonotoneCubicSpline(curveX,curveY);
        SplineInterpolator splineInterpolatorShadows = SplineInterpolator.createMonotoneCubicSpline(curveX,curveY);
        float[] interpolatedCurveArr = new float[1024];
        float[] interpolatedCurveShadowsArr = new float[1024];
        for(int i =0 ;i<interpolatedCurveArr.length;i++){
            float line = i/((float)(interpolatedCurveArr.length-1.f));
            interpolatedCurveArr[i] = splineInterpolator.interpolate(line);
            interpolatedCurveShadowsArr[i] = splineInterpolatorShadows.interpolate(line);
        }
        interpolatedCurve = new GLTexture(new Point(interpolatedCurveArr.length,1),
                new GLFormat(GLFormat.DataType.FLOAT_16), BufferUtils.getFrom(interpolatedCurveArr),GL_LINEAR,GL_CLAMP_TO_EDGE);
        shadowMap = new GLTexture(new Point(interpolatedCurveShadowsArr.length,1),
                new GLFormat(GLFormat.DataType.FLOAT_16), BufferUtils.getFrom(interpolatedCurveShadowsArr),GL_LINEAR,GL_CLAMP_TO_EDGE);

        GLTexture in = previousNode.WorkingTexture;
        initialSize = new Point(previousNode.WorkingTexture.mSize);
        WorkSize = new Point(initialSize.x/2,initialSize.y/2);
        //Size override
        basePipeline.main1.mSize.x = WorkSize.x;
        basePipeline.main1.mSize.y = WorkSize.y;
        basePipeline.main2.mSize.x = WorkSize.x;
        basePipeline.main2.mSize.y = WorkSize.y;
        basePipeline.main3.mSize.x = WorkSize.x;
        basePipeline.main3.mSize.y = WorkSize.y;
        //if(PhotonCamera.getManualMode().getCurrentExposureValue() != 0 && PhotonCamera.getManualMode().getCurrentISOValue() != 0) compressor = 1.f;
        float perlevel = downScalePerLevel;
        int levelcount = (int)(Math.log10(WorkSize.x)/Math.log10(perlevel));
        if(levelcount <= 0) levelcount = 2;
        Log.d(Name,"levelCount:"+levelcount);


        GLTexture exposureBase = expose3(in,baseExpose);
        getHistogram(exposureBase);
        float overexposure = autoExposureHigh();
        float underexposure = autoExposureLow();
        ((PostPipeline)basePipeline).softLight = Math2.smoothstep(softLoverLevel, softUpperLevel,((1.f/overexposure)+underexposure)/2.f);
        Log.d(Name,"SoftLightk:"+((PostPipeline)basePipeline).softLight);


        overexposure = Math.min(256.f,overexposure);
        underexposure = Math.max(1.f/256.f,underexposure);


        if(useSymmetricExposureFork){
            float mpy = overexposure*underexposure;
            overexposure/=mpy;
            underexposure/=mpy;
        }

        //overexposure*=overExposeMpy;
        overexposure = Math2.mix(1.f,overexposure,overExposeMpy);
        underexposure*=underExposeMpy;
        overexposure = Math.min(fusionExpoHighLimit,overexposure);
        underexposure = Math.max(fusionExpoLowLimit,underexposure);

        ((PostPipeline)basePipeline).fusionGain = mix(1.f,overexposure,maxC);
        ((PostPipeline)basePipeline).totalGain *= overexposure;

        Log.d(Name,"TotalGain:"+((PostPipeline)basePipeline).totalGain);
        //overexposure = Math.min(10.f,overexposure);
        //underexposure = Math.max(underexposure,0.0008f);
        Log.d(Name,"Overexp:"+overexposure+" , Underexp:"+underexposure);

        //GLUtils.Pyramid highExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose(in,overexposure));
        long time = System.currentTimeMillis();
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose(in,underexposure,overexposure));
        Log.d(Name,"Pyramid elapsed:"+(System.currentTimeMillis()-time)+" ms");
        //in.close();

        // expose() premultiplied the packed exposure channels with their
        // full-resolution luma shares, so the pyramid channels carry the
        // weighting implicitly and no separate weight textures are needed.
        // The gauss levels are no longer used by the fusion loop.
        int ind = normalExpo.gauss.length - 1;
        for (int i = 0; i < ind; i++) normalExpo.gauss[i].close();

        // select base gauss
        glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
        glProg.setDefine("FUSEWEIGHTED",true);
        glProg.useAssetProgram("ltm/fusionbayer3",false);
        glProg.setVar("useUpsampled",0);
        GLTexture binnedFuse = new GLTexture(normalExpo.gauss[ind]);
        //glProg.setTexture("highExpo",highExpo.gauss[ind]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[ind]);
        //glProg.setTexture("highExpoDiff",highExpo.gauss[ind]);
        glProg.setVar("upscaleIn",1.0f/binnedFuse.mSize.x,1.0f/binnedFuse.mSize.y);
        glProg.setVar("blendMpy",1.f);

        glProg.drawBlocks(binnedFuse,normalExpo.sizes[ind]);
        normalExpo.gauss[ind].close();

        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
            //GLTexture upsampleWip = (glUtils.interpolate(binnedFuse,normalExpo.sizes[i]));
            //Log.d("ExposureFusion","Before:"+upsampleWip.mSize+" point:"+normalExpo.sizes[i]);
            GLTexture upsampleWip = binnedFuse;
            Log.d(Name,"upsampleWip:"+upsampleWip.mSize);
            glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
            glProg.setDefine("FUSEWEIGHTED",true);
            glProg.useAssetProgram("ltm/fusionbayer3",false);

            glProg.setTexture("upsampled", upsampleWip);
            glProg.setVar("useUpsampled", 1);
            glProg.setVar("blendMpy",1.0f+dehazing-dehazing*((float)i)/(normalExpo.laplace.length-1.f));
            glProg.setVar("level",i);
            glProg.setVar("upscaleIn",1.0f/normalExpo.sizes[i].x, 1.0f/normalExpo.sizes[i].y);
            // We can discard the previous work in progress merge.
            //binnedFuse.close();
            binnedFuse = new GLTexture(normalExpo.laplace[i]);

            // Blend feature level.
            glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
            //glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

            glProg.drawBlocks(binnedFuse,normalExpo.sizes[i]);
            //glUtils.SaveProgResult(binnedFuse.mSize,"ExposureFusion"+i);

            upsampleWip.close();
            normalExpo.laplace[i].close();

        }
        //previousNode.WorkingTexture.close();
        //highExpo.gauss[ind].close();
        // Debug dump: the fusion expose packing carries the blend factor t in
        // .a (rgb hold the three exposures). Extract that single scalar via
        // convertVec4 for a grayscale PNG: black = shadows (max boost),
        // white = highlights (base exposure). For a specific share instead,
        // derive it from t: shadow = clamp(2-2t), mid = clamp(2t)-clamp(2t-1),
        // highlight = clamp(2t-1).
        /*GLTexture debugWeights = expose(in, underexposure, overexposure);
        GLTexture debugScalar = glUtils.convertVec4(debugWeights, "vec3(in1.a), 1.0");
        GLImage debugImage = glUtils.GenerateGLImage(debugWeights.mSize);
        Utilities.saveBitmapSaf(debugImage.getBufferedImage(), "FusionWeights");
        debugScalar.close();
        debugWeights.close();*/
        basePipeline.main1.mSize.x = initialSize.x;
        basePipeline.main1.mSize.y = initialSize.y;
        basePipeline.main2.mSize.x = initialSize.x;
        basePipeline.main2.mSize.y = initialSize.y;
        basePipeline.main3.mSize.x = initialSize.x;
        basePipeline.main3.mSize.y = initialSize.y;
        ((PostPipeline)basePipeline).FusionMap =
                fusionMap(binnedFuse,exposureBase, (float)((PostPipeline)basePipeline).AecCorr/2.f);
        //Use EDI to interpolate fusionmap


        basePipeline.getMain();
        //binnedFuse;
        /*if(basePipeline.mSettings.DebugData) {
            glUtils.convertVec4(((PostPipeline)basePipeline).FusionMap,"in1.r*15.0");
            glUtils.SaveProgResult(binnedFuse.mSize,"tonemap");
        }*/

        binnedFuse.close();
        interpolatedCurve.close();
        shadowMap.close();
        //WorkingTexture = unexpose(binnedFuse,normalExpo.gauss[0], (float)basePipeline.mSettings.gain*((PostPipeline)basePipeline).AecCorr/2.f);
        WorkingTexture = previousNode.WorkingTexture;
        Log.d(Name,"Output Size:"+binnedFuse.mSize);
        glProg.closed = true;

    }
}
