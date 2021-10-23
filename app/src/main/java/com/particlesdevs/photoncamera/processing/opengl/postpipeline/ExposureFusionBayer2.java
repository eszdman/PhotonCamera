package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.dngprocessor.Histogram;
import com.particlesdevs.photoncamera.util.Math2;
import com.particlesdevs.photoncamera.util.SplineInterpolator;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static com.particlesdevs.photoncamera.util.Math2.mix;

public class ExposureFusionBayer2 extends Node {

    public ExposureFusionBayer2() {
        super(0, "FusionBayer");
    }
    @Override
    public void Compile() {}
    private double dehaze = 0.0;
    GLTexture expose(GLTexture in, float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.setDefine("CURVE",true);
        glProg.useProgram(R.raw.exposebayer2);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("InterpolatedCurve",interpolatedCurve);
        glProg.setVar("factor", str);
        GLTexture outp = new GLTexture(WorkSize,new GLFormat(GLFormat.DataType.FLOAT_16,2));
        glProg.drawBlocks(outp);
        return outp;
    }
    GLTexture expose2(GLTexture in, float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.setDefine("CURVE",true);
        glProg.setDefine("INVERSE",true);
        glProg.useProgram(R.raw.exposebayer2);
        glProg.setTexture("InputBuffer",in);
        glProg.setTexture("InterpolatedCurve",interpolatedCurve);
        glProg.setVar("factor", str);
        GLTexture outp = new GLTexture(WorkSize,new GLFormat(GLFormat.DataType.FLOAT_16,2));
        glProg.drawBlocks(outp);
        return outp;
    }
    GLTexture expose3(GLTexture in, float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.useProgram(R.raw.exposebayer2);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        GLTexture tex = basePipeline.getMain();
        glProg.drawBlocks(tex);
        return tex;
    }
    void getHistogram(GLTexture lowGauss){
        glUtils.convertVec4(lowGauss,"in1.r");
        Bitmap sourceh = glUtils.GenerateBitmap(lowGauss.mSize);
        histogram = new Histogram(sourceh,lowGauss.mSize.x*lowGauss.mSize.y,256);
        sourceh.recycle();
    }
    float autoExposureHigh(){
        float max = 0.f;
        float avr = 0.f;
        float w = 0.f;
        for(int i = 15; i<240;i++){
            float line = i/255.f;
            float ind = (float)(Math.pow(line, 1./ gammaKSearch));
            float mpy = histogram.histr[i]/(ind);
            max = Math.max(max,mpy);
            avr+=mpy;
            w+=1.0f;
        }
        return mix(avr/w,max, overExposeMaxFusion);
    }
    float autoExposureLow(){
        /*float min = 1.f;
        for(int i = 15; i<240;i++){
            float ind = (float)(Math.pow(i/255.f, 1./ gammaKSearch));
            float mpy = histogram.histr[i]/(ind);
            min = Math.min(min,mpy);
        }
        return max;
        */
        float min = 0.f;
        float avr = 0.f;
        float w = 0.f;
        for(int i = 15; i<240;i++){
            float line = i/255.f;
            float ind = (float)(Math.pow(line, 1./ gammaKSearch));
            float mpy = histogram.histInvr[i]/(ind);
            min = Math.min(min,mpy);
            avr+=mpy;
            w+=1.0f;
        }
        return mix(avr/w,min,underExposeMinFusion);
    }

    GLTexture fusionMap(GLTexture in,GLTexture br,float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("FUSIONGAIN",((PostPipeline)(basePipeline)).fusionGain);
        glProg.useProgram(R.raw.fusionmap);
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
    Histogram histogram;
    Point initialSize;
    Point WorkSize;
    float overExposeMpy = 1.8f;
    float overExposeMaxFusion = 0.9f;
    float underExposeMpy = 1.0f;
    float underExposeMinFusion = 0.0f;
    float gammaKSearch = 1.0f;
    float baseExpose = 1.0f;
    float gaussSize = 0.5f;
    float targetLuma = 0.5f;
    float downScalePerLevel = 2.2f;
    float dehazing = 0.5f;

    float softUpperLevel = 0.1f;
    float softLoverLevel = 0.0f;
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
                new GLFormat(GLFormat.DataType.FLOAT_16), FloatBuffer.wrap(interpolatedCurveArr),GL_LINEAR,GL_CLAMP_TO_EDGE);

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
        GLTexture downscaled = glUtils.interpolate(exposureBase,1.0/4.0);
        getHistogram(downscaled);
        downscaled.close();
        float overexposure = autoExposureHigh();
        float underexposure = autoExposureLow();
        ((PostPipeline)basePipeline).softLight = Math2.smoothstep(softLoverLevel, softUpperLevel,((1.f/overexposure)+underexposure)/2.f);
        Log.d(Name,"SoftLightk:"+((PostPipeline)basePipeline).softLight);

        overexposure*=overExposeMpy;
        underexposure*=underExposeMpy;
        overexposure = Math.min(1024.f,overexposure);
        underexposure = Math.max(1.f/1024.f,underexposure);
        if(useSymmetricExposureFork){
            float mpy = overexposure*underexposure;
            overexposure/=mpy;
            underexposure/=mpy;
        }

        ((PostPipeline)basePipeline).fusionGain = mix(1.f,overexposure,maxC);
        //overexposure = Math.min(10.f,overexposure);
        //underexposure = Math.max(underexposure,0.0008f);
        Log.d(Name,"Overexp:"+overexposure+" , Underexp:"+underexposure);

        GLUtils.Pyramid highExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose(in,overexposure));
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose2(in,underexposure));

        //glUtils.convertVec4(highExpo.laplace[2],"in1.r");
        //glUtils.SaveProgResult(highExpo.laplace[2].mSize,"laplace");

        //in.close();
        glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
        glProg.setDefine("GAUSS",gaussSize);
        glProg.setDefine("TARGET",targetLuma);
        glProg.useProgram(R.raw.fusionbayer2);
        glProg.setVar("useUpsampled",0);
        int ind = normalExpo.gauss.length - 1;
        GLTexture binnedFuse = new GLTexture(normalExpo.gauss[ind]);
        glProg.setTexture("normalExpo",normalExpo.gauss[ind]);
        glProg.setTexture("highExpo",highExpo.gauss[ind]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[ind]);
        glProg.setTexture("highExpoDiff",highExpo.gauss[ind]);
        glProg.setVar("upscaleIn",binnedFuse.mSize);
        glProg.setVar("blendMpy",1.f);
        //normalExpo.gauss[ind].close();
        //highExpo.gauss[ind].close();
        glProg.drawBlocks(binnedFuse,normalExpo.sizes[ind]);
        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
            //GLTexture upsampleWip = (glUtils.interpolate(binnedFuse,normalExpo.sizes[i]));
            //Log.d("ExposureFusion","Before:"+upsampleWip.mSize+" point:"+normalExpo.sizes[i]);
            GLTexture upsampleWip = binnedFuse;
            Log.d(Name,"upsampleWip:"+upsampleWip.mSize);
            glProg.setDefine("MAXLEVEL",normalExpo.laplace.length - 1);
            glProg.setDefine("GAUSS",gaussSize);
            glProg.setDefine("TARGET",targetLuma);
            glProg.useProgram(R.raw.fusionbayer2);

            glProg.setTexture("upsampled", upsampleWip);
            glProg.setVar("useUpsampled", 1);
            glProg.setVar("blendMpy",1.0f+dehazing-dehazing*((float)i)/(normalExpo.laplace.length-1.f));
            glProg.setVar("level",i);
            glProg.setVar("upscaleIn",normalExpo.sizes[i]);
            // We can discard the previous work in progress merge.
            //binnedFuse.close();
            Point wsize;
            binnedFuse = new GLTexture(normalExpo.laplace[i]);

            // Weigh full image.
            glProg.setTexture("normalExpo", normalExpo.gauss[i]);
            glProg.setTexture("highExpo", highExpo.gauss[i]);

            // Blend feature level.
            glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
            glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

            glProg.drawBlocks(binnedFuse,normalExpo.sizes[i]);
            //glUtils.SaveProgResult(binnedFuse.mSize,"ExposureFusion"+i);

            upsampleWip.close();
            normalExpo.gauss[i].close();
            highExpo.gauss[i].close();
            normalExpo.laplace[i].close();
            highExpo.laplace[i].close();

        }
        //previousNode.WorkingTexture.close();
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
