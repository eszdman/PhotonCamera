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

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

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
        glProg.useProgram(R.raw.exposebayer2);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        GLTexture outp = new GLTexture(WorkSize,new GLFormat(GLFormat.DataType.FLOAT_16,2));
        glProg.drawBlocks(outp);
        return outp;
    }
    GLTexture expose2(GLTexture in, float str){
        glProg.setDefine("DH","("+dehaze+")");
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);
        glProg.useProgram(R.raw.exposebayer2);
        glProg.setTexture("InputBuffer",in);
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
        for(int i = 15; i<240;i++){
            float ind = (float)(Math.pow(i/255.f, 1./ gammaKSearch));
            float mpy = histogram.histr[i]/(ind);
            max = Math.max(max,mpy);
        }
        return max;
    }
    float autoExposureLow(){
        float min = 1.f;
        for(int i = 15; i<240;i++){
            float ind = (float)(Math.pow(i/255.f, 1./ gammaKSearch));
            float mpy = histogram.histr[i]/(ind);
            min = Math.min(min,mpy);
        }
        return min;
    }

    GLTexture fusionMap(GLTexture in,GLTexture br,float str){
        glProg.setDefine("DH","("+dehaze+")");
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
    float overExposeMpy = 1.35f;
    float underExposeMpy = 0.6f;
    float gammaKSearch = 1.0f;
    float baseExpose = 1.0f;
    float gaussSize = 0.5f;
    float targetLuma = 0.8f;
    float downScalePerLevel = 2.0f;
    float dehazing = 0.f;
    @Override
    public void Run() {
        overExposeMpy = getTuning("OverExposeMpy", overExposeMpy);
        underExposeMpy = getTuning("UnderExposeMpy", underExposeMpy);
        baseExpose = getTuning("BaseExposure",baseExpose);
        gaussSize = getTuning("GaussSize",gaussSize);
        targetLuma = getTuning("TargetLuma",targetLuma);
        dehazing = getTuning("Dehazing",dehazing);
        downScalePerLevel = getTuning("DownScalePerLevel",downScalePerLevel);
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
        int levelcount = (int)(Math.log10(WorkSize.x)/Math.log10(perlevel))-1;
        if(levelcount <= 0) levelcount = 2;
        Log.d(Name,"levelCount:"+levelcount);


        GLTexture exposureBase = expose3(in,baseExpose);
        GLTexture downscaled = glUtils.interpolate(exposureBase,1.0/4.0);
        getHistogram(downscaled);
        downscaled.close();
        float overexpose = autoExposureHigh()*overExposeMpy;
        float underexposure = autoExposureLow()*underExposeMpy;
        //overexpose = Math.min(10.f,overexpose);
        //underexposure = Math.max(underexposure,0.0008f);
        Log.d(Name,"Overexp:"+overexpose+" , Underexp:"+underexposure);

        GLUtils.Pyramid highExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose(in,overexpose));
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(levelcount,downScalePerLevel, expose2(in,underexposure));


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
        glProg.drawBlocks(binnedFuse,binnedFuse.mSize);
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
            if(normalExpo.laplace[i].mSize.equals(WorkSize)){
                binnedFuse = new GLTexture(normalExpo.laplace[i]);
                wsize = binnedFuse.mSize;
            } else {
                binnedFuse = new GLTexture(normalExpo.laplace[i]);
                wsize = binnedFuse.mSize;
            }

            // Weigh full image.
            glProg.setTexture("normalExpo", normalExpo.gauss[i]);
            glProg.setTexture("highExpo", highExpo.gauss[i]);

            // Blend feature level.
            glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
            glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

            glProg.drawBlocks(binnedFuse,wsize);
            //glUtils.SaveProgResult(binnedFuse.mSize,"ExposureFusion"+i);

            upsampleWip.close();
            if(!normalExpo.gauss[i].mSize.equals(WorkSize)) {
                normalExpo.gauss[i].close();
                highExpo.gauss[i].close();
            }
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
        //WorkingTexture = unexpose(binnedFuse,normalExpo.gauss[0], (float)basePipeline.mSettings.gain*((PostPipeline)basePipeline).AecCorr/2.f);
        WorkingTexture = previousNode.WorkingTexture;
        Log.d(Name,"Output Size:"+binnedFuse.mSize);
        glProg.closed = true;
    }
}
