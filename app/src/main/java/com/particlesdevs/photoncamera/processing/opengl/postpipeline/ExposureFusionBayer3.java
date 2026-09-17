package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Math2;
import com.particlesdevs.photoncamera.util.SplineInterpolator;

import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static com.particlesdevs.photoncamera.util.Math2.mix;

public class ExposureFusionBayer3 extends Node {

    public ExposureFusionBayer3() {
        super("", "FusionBayer");
    }
    @Override
    public void Compile() {}
    private double dehaze = 0.0;
    GLTexture expose(GLTexture in, float strLow,GLTexture strHigh){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.setDefine("STRLOW",strLow);
        //glProg.setDefine("STRHIGH",strHigh);
        glProg.setDefine("CURVE",true);
        glProg.setDefine("OVEREXPOSEMPY", 1.0f);
        glProg.setDefine("RGBLAYOUT",basePipeline.mSettings.alignAlgorithm == 2);
        glProg.setDefine("COMPRESSOR", (float) basePipeline.mSettings.compressor);
        glProg.setDefine("UPPERLIM", overexposedUpperLimit);
        glProg.setDefine("GAMMAFACTOR", gammaFactor);
        Log.d(Name,"Compressor:"+basePipeline.mSettings.compressor);
        glProg.useAssetProgram("ltm/exposebayer31",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("InterpolatedCurve",interpolatedCurve);
        glProg.setTexture("ShadowMap", shadowMap);
        glProg.setTexture("GainMap", ((PostPipeline)basePipeline).GainMap);
        glProg.setVar("neutral", basePipeline.mParameters.whitePoint);
        if (((PostPipeline)basePipeline).FusionMap != null) {
            glProg.setTexture("FusionMap", ((PostPipeline) basePipeline).FusionMap);
            glProg.setVar("useFusion", 1);
        } else
            glProg.setVar("useFusion", 0);
        glProg.setTexture("HighExpo", strHigh);
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
        glProg.setDefine("OVEREXPOSEMPY", 1.0f);
        glProg.setDefine("UPPERLIM", overexposedUpperLimit);
        glProg.setDefine("GAMMAFACTOR", gammaFactor);
        glProg.useAssetProgram("ltm/exposebayer31",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("GainMap", ((PostPipeline)basePipeline).GainMap);
        glProg.setVar("neutral", basePipeline.mParameters.whitePoint);
        //glProg.setVar("factor", str);
        GLTexture tex = basePipeline.getMain();
        glProg.drawBlocks(tex);
        return tex;
    }
    void getHistogram(GLTexture lowGauss){
        GLTexture vectored = glUtils.convertVec4(lowGauss,"in1.r*4.0");
        //GLImage sourceh = glUtils.GenerateGLImage(lowGauss.mSize);
        glHistogram = new GLHistogram(basePipeline.glint.glProcessing);
        glHistogram.Rc = true;
        glHistogram.Bc = false;
        glHistogram.Gc = false;
        glHistogram.Ac = false;
        glHistogram.resize = 2;
        glHistogram.Compute(vectored);

        //sourceh.close();
        vectored.close();
    }

    GLTexture autoExposureHigh(){
        float integrated = 0.f;
        float full = 0.0001f;
        float gaussNorm = 0.0f;
        for(int i = 0; i < 255; i++){
            float line = i/255.f;
            full += glHistogram.outputArr[0][i];
            gaussNorm += (float) Math.exp(-line*line*Math2.mix(0.1f,1.0f,overExposeMpy));
            //Log.d(Name,"Histogram:"+glHistogram.outputArr[0][i]);
        }
        Log.d(Name,"Full histogram:"+full);
        Log.d(Name,"Gauss norm:"+gaussNorm);
        float[] overexposed = new float[255];
        for(int i = 0; i < 255; i++){
            float line = i/255.f;
            float gauss = (float) Math.exp(-line*line*Math2.mix(0.1f,1.0f,overExposeMpy))/gaussNorm;
            float cnt = glHistogram.outputArr[0][i]/full;
            integrated += Math.min(gauss*Math.max(cnt, fusionExpoHighMinStep), fusionExpoHighLimit/256.f);
            //integrated += Math.min(cnt, fusionExpoHighLimit/255.f);
            overexposed[i] = integrated;
        }
        Log.d(Name,"Overexposure integrated:"+integrated);
        for (int i = 0; i < 255; i++){
            overexposed[i] /= integrated;
            Log.d(Name,"Overexposure curve:"+overexposed[i]);
        }
        // apply gaussian smoothing
        int size = 255;
        for (int i = 0; i < 255; i++){
            float sum = 0.f;
            float sumw = 0.f;
            for (int j = 0; j <= size; j++){
                int ind = i+j;
                int ind2 = i-j;
                if (ind < 0 || ind >= 255) continue;
                if (ind2 < 0 || ind2 >= 255) continue;
                float w = (float) Math.exp(-j*j/curveSmooth);
                sum += overexposed[ind]*w;
                sum += overexposed[ind2]*w;
                sumw += w * 2;
            }
            overexposed[i] = sum/sumw;
        }
        // create a curve texture
        return new GLTexture(new Point(overexposed.length,1),new GLFormat(GLFormat.DataType.FLOAT_32),BufferUtils.getFrom(overexposed),GL_LINEAR,GL_CLAMP_TO_EDGE);
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
    @Tunable(title = "Enable", category = "Exposure Fusion", defaultValue = 1, min = 0, max = 1, step = 1,
            description = "Enable Exposure Fusion Post Processing")
    boolean enable = true;

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

    
    /*@Tunable(
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
        title = "Under Expose Min Fusion",
        description = "Minimum fusion factor for underexposed areas",
        category = "Exposure Fusion",
        defaultValue = 0.0f,
        min = 0.0f,
        max = 1.0f,
        step = 0.01f
    )
    float underExposeMinFusion = 0.0f;*/
    
    @Tunable(
        title = "Under Expose Multiplier",
        description = "Multiplier for underexposure adjustment",
        category = "Exposure Fusion",
        defaultValue = 1.0f,
        min = 0.0f,
        max = 2.0f,
        step = 0.01f
    )
    float underExposeMpy = 1.0f;
    
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
        defaultValue = 0.6f,
        min = 0.0f,
        max = 1.0f,
        step = 0.01f
    )
    float targetLuma = 0.6f;
    
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
        title = "DownScale Per Level",
        description = "Downscaling factor per pyramid level",
        category = "Exposure Fusion",
        defaultValue = 2.0f,
        min = 1.5f,
        max = 4.0f,
        step = 0.1f
    )
    float downScalePerLevel;
    
    /*@Tunable(
        title = "Curve Points Count",
        description = "Number of points in tone curve",
        category = "Exposure Fusion",
        defaultValue = 5.0f,
        min = 3.0f,
        max = 10.0f,
        step = 1.0f
    )*/
    int curvePointsCount = 5;
    
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
        defaultValue = 32.f,
        min = 1.0f,
        max = 100.0f,
        step = 0.1f
    )
    float fusionExpoHighLimit = 32.f;
    
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
        title = "Fusion Expo Factor Min",
        description = "Minimum factor for fusion exposure",
        category = "Exposure Fusion",
        defaultValue = 0.01f,
        min = 0.001f,
        max = 0.1f,
        step = 0.001f
    )
    float fusionExpoFactorMin = 0.01f;
    
    @Tunable(
        title = "Fusion Laplace Factor Min",
        description = "Minimum factor for Laplace fusion",
        category = "Exposure Fusion",
        defaultValue = 0.001f,
        min = 0.001f,
        max = 0.1f,
        step = 0.001f
    )
    float fusionLaplaceFactorMin = 0.001f;
    
    @Tunable(
        title = "Fusion Expo High Min Step",
        description = "Minimum step for high exposure fusion",
        category = "Exposure Fusion",
        defaultValue = 1.0f/256.f,
        min = 0.0001f,
        max = 0.01f,
        step = 0.0001f
    )
    float fusionExpoHighMinStep = 1.0f/256.f;
    
    @Tunable(
        title = "Gamma Factor",
        description = "Gamma correction factor for LTM processing",
        category = "Exposure Fusion",
        defaultValue = 0.92f,
        min = 0.5f,
        max = 1.5f,
        step = 0.01f
    )
    float gammaFactor = 0.92f;
    
    /*@Tunable(
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
    float softLoverLevel = 0.0f;*/
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
            title = "Gaussian exposure curve smoothness",
            description = "Smoothness of the Gaussian curve used in exposure fusion",
            category = "Exposure Fusion",
            defaultValue = 64.0f,
            min = 1.0f,
            max = 128.0f,
            step = 1.0f
    )
    float curveSmooth = 64.f;
    float[] toneCurveX;
    float[] toneCurveY;

    float[] shadowCurveX;
    float[] shadowCurveY;
    GLTexture interpolatedCurve;
    GLTexture shadowMap;
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
        basePipeline.getMain3().mSize.x = WorkSize.x;
        basePipeline.getMain3().mSize.y = WorkSize.y;
        //if(PhotonCamera.getManualMode().getCurrentExposureValue() != 0 && PhotonCamera.getManualMode().getCurrentISOValue() != 0) compressor = 1.f;
        float perlevel = downScalePerLevel;
        int levelcount = (int)(Math.log10(WorkSize.x)/Math.log10(perlevel));
        if(levelcount <= 0) levelcount = 2;
        Log.d(Name,"levelCount:"+levelcount);


        GLTexture exposureBase = expose3(in,baseExpose);
        getHistogram(exposureBase);
        GLTexture highExpoTex = autoExposureHigh();
        float underexposure = autoExposureLow();
        underexposure = Math.max(1.f/256.f,underexposure);

        //overexposure*=overExposeMpy;
        underexposure*=underExposeMpy;
        underexposure = Math.max(fusionExpoLowLimit,underexposure);

        ((PostPipeline)basePipeline).fusionGain = 64.f;
        ((PostPipeline)basePipeline).totalGain *= 64.f;

        Log.d(Name,"TotalGain:"+((PostPipeline)basePipeline).totalGain);
        //overexposure = Math.min(10.f,overexposure);
        //underexposure = Math.max(underexposure,0.0008f);
        Log.d(Name,"Underexp:"+underexposure);

        //GLUtils.Pyramid highExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose(in,overexposure));
        long time = System.currentTimeMillis();
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose(in,underexposure, highExpoTex));

        highExpoTex.close();

        Log.d(Name,"Pyramid elapsed:"+(System.currentTimeMillis()-time)+" ms");
        //in.close();

        // select base gauss
        glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
        glProg.setDefine("LAPLACEMIN", fusionLaplaceFactorMin);
        glProg.setDefine("EXPOMIN", fusionExpoFactorMin);
        glProg.useAssetProgram("ltm/fusionbayer3",false);
        glProg.setVar("gauss", gaussSize);
        glProg.setVar("target", targetLuma);
        glProg.setVar("useUpsampled",0);
        int ind = normalExpo.gauss.length - 1;
        GLTexture binnedFuse = new GLTexture(normalExpo.gauss[ind]);
        glProg.setTexture("normalExpo",normalExpo.gauss[ind]);
        //glProg.setTexture("highExpo",highExpo.gauss[ind]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[ind]);
        //glProg.setTexture("highExpoDiff",highExpo.gauss[ind]);
        glProg.setVar("upscaleIn",binnedFuse.mSize);
        glProg.setVar("blendMpy",1.f);

        glProg.drawBlocks(binnedFuse,normalExpo.sizes[ind]);

        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
            //Log.d("ExposureFusion","Before:"+upsample.mSize+" point:"+normalExpo.sizes[i]);
            GLTexture upsample = binnedFuse;
            Log.d(Name,"upsample:"+upsample.mSize);
            glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
            glProg.setDefine("LAPLACEMIN", fusionLaplaceFactorMin);
            glProg.setDefine("EXPOMIN", fusionExpoFactorMin);
            glProg.useAssetProgram("ltm/fusionbayer3",false);

            glProg.setTexture("upsampled", upsample);
            glProg.setVar("useUpsampled", 1);
            glProg.setVar("blendMpy",1.0f+dehazing-dehazing*((float)i)/(normalExpo.laplace.length-1.f));
            glProg.setVar("level",i);
            glProg.setVar("upscaleIn",normalExpo.sizes[i]);
            glProg.setVar("gauss", gaussSize);
            glProg.setVar("target", targetLuma);
            // We can discard the previous work in progress merge.
            //binnedFuse.close();
            binnedFuse = new GLTexture(normalExpo.laplace[i]);

            // Weigh full image.
            glProg.setTexture("normalExpo", normalExpo.gauss[i]);
            //glProg.setTexture("highExpo", highExpo.gauss[i]);

            // Blend feature level.
            glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
            //glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

            glProg.drawBlocks(binnedFuse,normalExpo.sizes[i]);
            //glUtils.SaveProgResult(binnedFuse.mSize,"ExposureFusion"+i);

            upsample.close();
            normalExpo.gauss[i].close();
            //highExpo.gauss[i].close();
            normalExpo.laplace[i].close();
            //highExpo.laplace[i].close();

        }
        //previousNode.WorkingTexture.close();
        normalExpo.gauss[ind].close();
        //highExpo.gauss[ind].close();
        basePipeline.main1.mSize.x = initialSize.x;
        basePipeline.main1.mSize.y = initialSize.y;
        basePipeline.main2.mSize.x = initialSize.x;
        basePipeline.main2.mSize.y = initialSize.y;
        basePipeline.getMain3().mSize.x = initialSize.x;
        basePipeline.getMain3().mSize.y = initialSize.y;
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
