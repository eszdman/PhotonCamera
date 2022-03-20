package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.dngprocessor.Histogram;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Math2;
import com.particlesdevs.photoncamera.util.SplineInterpolator;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static com.particlesdevs.photoncamera.util.Math2.buildCumulativeHist;
import static com.particlesdevs.photoncamera.util.Math2.buildCumulativeHistInv;
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
        glProg.useAssetProgram("exposebayer2",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("InterpolatedCurve",interpolatedCurve);
        //glProg.setVar("factor", str);
        GLTexture outp = new GLTexture(WorkSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.drawBlocks(outp);
        return outp;
    }
    /*
    GLTexture expose2(GLTexture in, float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.setDefine("CURVE",true);
        glProg.setDefine("INVERSE",true);
        glProg.useAssetProgram("exposebayer2",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("InterpolatedCurve",interpolatedCurve);
        glProg.setVar("factor", str);
        GLTexture outp = new GLTexture(WorkSize,new GLFormat(GLFormat.DataType.FLOAT_16,2));
        glProg.drawBlocks(outp);
        return outp;
    }
     */
    GLTexture expose3(GLTexture in, float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.useAssetProgram("exposebayer2",false);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        GLTexture tex = basePipeline.getMain();
        glProg.drawBlocks(tex);
        return tex;
    }
    void getHistogram(GLTexture lowGauss){
        GLTexture vectored = glUtils.convertVec4(lowGauss,"in1.r");
        //GLImage sourceh = glUtils.GenerateGLImage(lowGauss.mSize);
        glHistogram = new GLHistogram(basePipeline.glint.glProcessing);
        glHistogram.Compute(vectored);
        glHistogram.Bc = false;
        glHistogram.Gc = false;
        glHistogram.Ac = false;
        //sourceh.close();
        vectored.close();
    }

    float autoExposureHigh(){
        float avr = 128.f;
        float w = 1.f;
        for(int i = 15; i<128;i++){
            float line = i/255.f;
            float ind = (float)(Math.pow(line, 1./ gammaKSearch))*256.f;
            float mpy = glHistogram.outputArr[0][i]*(ind);
            avr+=mpy;
            w+=glHistogram.outputArr[0][i];
        }
        Log.d(Name,"Overexp pos:"+avr/w);
        return 128/(avr/w + 1);
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
        return (256 - avr/w)/256;
        //return mix(avr/w,min,underExposeMinFusion);
    }

    GLTexture fusionMap(GLTexture in,GLTexture br,float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("FUSIONGAIN",((PostPipeline)(basePipeline)).fusionGain);
        glProg.useAssetProgram("fusionmap",false);
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
    float overExposeMpy = 2.2f;
    float overExposeMaxFusion = 0.9f;
    float underExposeMpy = 1.0f;
    float underExposeMinFusion = 0.0f;
    float gammaKSearch = 1.0f;
    float baseExpose = 1.0f;
    float gaussSize = 0.5f;
    float targetLuma = 0.5f;
    float downScalePerLevel = 2.2f;
    float dehazing = 0.25f;

    float softUpperLevel = 0.1f;
    float softLoverLevel = 0.0f;
    float fusionExpoLowLimit = 1.f/3.f;
    float fusionExpoHighLimit = 5.f;
    int curvePointsCount = 5;
    float[] toneCurveX;
    float[] toneCurveY;
    GLTexture interpolatedCurve;
    boolean disableFusion = false;
    boolean useSymmetricExposureFork = true;
    @Override
    public void Run() {
        disableFusion = getTuning("DisableFusion",disableFusion);
        if(disableFusion){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        useSymmetricExposureFork = getTuning("UseSymmetricExposureFork",useSymmetricExposureFork);
        overExposeMpy =            getTuning("OverExposeMpy", overExposeMpy);
        overExposeMaxFusion =      getTuning("OverExposeMaxFusion", overExposeMaxFusion);
        underExposeMinFusion =     getTuning("UnderExposeMinFusion", underExposeMinFusion);
        underExposeMpy =           getTuning("UnderExposeMpy", underExposeMpy);
        baseExpose =               getTuning("BaseExposure",baseExpose);
        gaussSize =                getTuning("GaussSize",gaussSize);
        targetLuma =               getTuning("TargetLuma",targetLuma);
        dehazing =                 getTuning("Dehazing",dehazing);
        downScalePerLevel =        getTuning("DownScalePerLevel",downScalePerLevel);
        curvePointsCount =         getTuning("CurvePointsCount",curvePointsCount);
        fusionExpoLowLimit =         getTuning("FusionExpoLowLimit",fusionExpoLowLimit);
        fusionExpoHighLimit =         getTuning("FusionExpoHighLimit",fusionExpoHighLimit);

        softUpperLevel = getTuning("HardLevel", softUpperLevel);
        softLoverLevel = getTuning("SoftLevel", softLoverLevel);
        toneCurveX = new float[curvePointsCount];
        toneCurveY = new float[curvePointsCount];
        for(int i = 0; i<curvePointsCount;i++){
            float line = i/((float)(curvePointsCount-1.f));
            toneCurveX[i] = line;
            toneCurveY[i] = 1.0f;
        }

        if(curvePointsCount == 5) {
            toneCurveX[0] = 0.0f;
            toneCurveX[1] = 0.07f;
            toneCurveX[2] = 0.25f;
            toneCurveX[3] = 0.95f;
            toneCurveX[4] = 1.0f;

            toneCurveY[0] = 0.7f;
            toneCurveY[1] = 1.0f;
            toneCurveY[2] = 1.0f;
            toneCurveY[3] = 0.85f;
            toneCurveY[4] = 0.40f;
        }

        toneCurveX = getTuning("TonemapCurveX", toneCurveX);
        toneCurveY = getTuning("TonemapCurveY", toneCurveY);
        ArrayList<Float> curveX = new ArrayList<>();
        ArrayList<Float> curveY = new ArrayList<>();
        float maxC = 0.f;
        for(int i =0; i<curvePointsCount;i++){
            curveX.add(toneCurveX[i]);
            curveY.add(toneCurveY[i]);
            if(toneCurveY[i] > maxC) maxC = toneCurveY[i];
        }

        SplineInterpolator splineInterpolator = SplineInterpolator.createMonotoneCubicSpline(curveX,curveY);
        float[] interpolatedCurveArr = new float[1024];
        for(int i =0 ;i<interpolatedCurveArr.length;i++){
            float line = i/((float)(interpolatedCurveArr.length-1.f));
            interpolatedCurveArr[i] = splineInterpolator.interpolate(line);
        }
        interpolatedCurve = new GLTexture(new Point(interpolatedCurveArr.length,1),
                new GLFormat(GLFormat.DataType.FLOAT_16), BufferUtils.getFrom(interpolatedCurveArr),GL_LINEAR,GL_CLAMP_TO_EDGE);

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
        overexposure*=overExposeMpy;
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
        glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
        glProg.setDefine("GAUSS",gaussSize);
        glProg.setDefine("TARGET",targetLuma);
        glProg.useAssetProgram("fusionbayer2",false);
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
            //GLTexture upsampleWip = (glUtils.interpolate(binnedFuse,normalExpo.sizes[i]));
            //Log.d("ExposureFusion","Before:"+upsampleWip.mSize+" point:"+normalExpo.sizes[i]);
            GLTexture upsampleWip = binnedFuse;
            Log.d(Name,"upsampleWip:"+upsampleWip.mSize);
            glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
            glProg.setDefine("GAUSS",gaussSize);
            glProg.setDefine("TARGET",targetLuma);
            glProg.useAssetProgram("fusionbayer2",false);

            glProg.setTexture("upsampled", upsampleWip);
            glProg.setVar("useUpsampled", 1);
            glProg.setVar("blendMpy",1.0f+dehazing-dehazing*((float)i)/(normalExpo.laplace.length-1.f));
            glProg.setVar("level",i);
            glProg.setVar("upscaleIn",normalExpo.sizes[i]);
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

            upsampleWip.close();
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
        //WorkingTexture = unexpose(binnedFuse,normalExpo.gauss[0], (float)basePipeline.mSettings.gain*((PostPipeline)basePipeline).AecCorr/2.f);
        WorkingTexture = previousNode.WorkingTexture;
        Log.d(Name,"Output Size:"+binnedFuse.mSize);
        glProg.closed = true;

    }
}
