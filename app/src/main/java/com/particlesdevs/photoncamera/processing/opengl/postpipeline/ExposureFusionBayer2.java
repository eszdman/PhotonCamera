package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

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
    Point initialSize;
    Point WorkSize;
    @Override
    public void Run() {
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
        int perlevel = 4;
        int levelcount = (int)(Math.log10(previousNode.WorkingTexture.mSize.x)/Math.log10(perlevel))+1;
        if(levelcount <= 0) levelcount = 2;
        Log.d(Name,"levelCount:"+levelcount);
        GLUtils.Pyramid highExpo = glUtils.createPyramid(levelcount,0, expose(in,3.f));
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(levelcount,0, expose2(in,(float)(0.7f)));
        //in.close();
        glProg.useProgram(R.raw.fusionbayer2);
        glProg.setVar("useUpsampled",0);
        int ind = normalExpo.gauss.length - 1;
        GLTexture wip = new GLTexture(normalExpo.gauss[ind]);
        glProg.setTexture("normalExpo",normalExpo.gauss[ind]);
        glProg.setTexture("highExpo",highExpo.gauss[ind]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[ind]);
        glProg.setTexture("highExpoDiff",highExpo.gauss[ind]);
        glProg.setVar("upscaleIn",wip.mSize);
        //normalExpo.gauss[ind].close();
        //highExpo.gauss[ind].close();
        glProg.drawBlocks(wip,wip.mSize);
        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
            //GLTexture upsampleWip = (glUtils.interpolate(wip,normalExpo.sizes[i]));
            //Log.d("ExposureFusion","Before:"+upsampleWip.mSize+" point:"+normalExpo.sizes[i]);
            GLTexture upsampleWip = wip;
            Log.d(Name,"upsampleWip:"+upsampleWip.mSize);
            glProg.useProgram(R.raw.fusionbayer2);

            glProg.setTexture("upsampled", upsampleWip);
            glProg.setVar("useUpsampled", 1);
            glProg.setVar("level",i);
            glProg.setVar("upscaleIn",normalExpo.sizes[i]);
            // We can discard the previous work in progress merge.
            //wip.close();
            Point wsize;
            if(normalExpo.laplace[i].mSize.equals(WorkSize)){
                wip = new GLTexture(normalExpo.laplace[i]);
                wsize = wip.mSize;
            } else {
                wip = new GLTexture(normalExpo.laplace[i]);
                wsize = wip.mSize;
            }

            // Weigh full image.
            glProg.setTexture("normalExpo", normalExpo.gauss[i]);
            glProg.setTexture("highExpo", highExpo.gauss[i]);

            // Blend feature level.
            glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
            glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

            glProg.drawBlocks(wip,wsize);
            //glUtils.SaveProgResult(wip.mSize,"ExposureFusion"+i);

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
                fusionMap(wip,normalExpo.gauss[0], (float)((PostPipeline)basePipeline).AecCorr/2.f);
                //wip;
        /*if(basePipeline.mSettings.DebugData) {
            glUtils.convertVec4(((PostPipeline)basePipeline).FusionMap,"in1.r*15.0");
            glUtils.SaveProgResult(wip.mSize,"tonemap");
        }*/
        wip.close();
        //WorkingTexture = unexpose(wip,normalExpo.gauss[0], (float)basePipeline.mSettings.gain*((PostPipeline)basePipeline).AecCorr/2.f);
        WorkingTexture = previousNode.WorkingTexture;
        Log.d(Name,"Output Size:"+wip.mSize);
        glProg.closed = true;
    }
}
