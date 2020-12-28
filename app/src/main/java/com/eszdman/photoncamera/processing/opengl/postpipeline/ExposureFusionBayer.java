package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.GLUtils;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

public class ExposureFusionBayer extends Node {

    public ExposureFusionBayer(String name) {
        super(0, name);
    }
    @Override
    public void Compile() {}

    private double Gamma = 1.5;
    private double Dehaze = 0.0;
    private float Strength = 4.5f;
    private double ExposeMin = 0.5;
    private double ExposeMax = 1.0;
    private double ExposeMpy = 1.3;
    private double ExposeShift = 0.7;
    GLTexture expose(GLTexture in, float str){
        glProg.setDefine("DR","("+ Gamma +")");
        glProg.setDefine("DH","("+ Dehaze +")");
        glProg.setDefine("EXPOSESHIFT","("+ ExposeShift +")");
        glProg.setDefine("EXPOSEMPY","("+ ExposeMpy +")");
        glProg.setDefine("EXPOSEMIN","("+ ExposeMin +")");
        glProg.setDefine("EXPOSEMAX","("+ ExposeMax +")");
        glProg.useProgram(R.raw.exposebayer);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint",basePipeline.mParameters.whitePoint);
        GLTexture outp = new GLTexture(WorkSize,basePipeline.main1.mFormat);
        glProg.drawBlocks(outp);
        return outp;
    }
    GLTexture expose2(GLTexture in, float str){
        glProg.setDefine("DR","("+ Gamma +")");
        glProg.setDefine("DH","("+ Dehaze +")");
        glProg.setDefine("EXPOSESHIFT","("+ ExposeShift +")");
        glProg.setDefine("EXPOSEMPY","("+ ExposeMpy +")");
        glProg.setDefine("EXPOSEMIN","("+ ExposeMin +")");
        glProg.setDefine("EXPOSEMAX","("+ ExposeMax +")");
        glProg.useProgram(R.raw.exposebayer);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", basePipeline.mParameters.whitePoint);
        GLTexture outp = new GLTexture(WorkSize,basePipeline.main1.mFormat);
        glProg.drawBlocks(outp);
        return outp;
    }
    GLTexture unexpose(GLTexture in,float str){
        glProg.setDefine("DR","("+ Gamma +")");
        glProg.setDefine("DH","("+ Dehaze +")");
        glProg.useProgram(R.raw.unexposebayer);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", basePipeline.mParameters.whitePoint);
        glProg.drawBlocks(basePipeline.main2,initialSize);
        return basePipeline.main2;
    }
    Point initialSize;
    Point WorkSize;
    @Override
    public void Run() {
        Gamma = getTuning("Gamma",Gamma);
        Dehaze = getTuning("Dehaze",Dehaze);
        Strength = getTuning("Strength",Strength);
        ExposeMin = getTuning("ExposeMin",ExposeMin);
        ExposeMax = getTuning("ExposeMax",ExposeMax);
        ExposeShift = getTuning("ExposeShift",ExposeShift);
        ExposeMpy = getTuning("ExposeMpy",ExposeMpy);

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
        GLUtils.Pyramid highExpo = glUtils.createPyramid(levelcount,0, expose(in, Strength));
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(levelcount,0, expose2(in,(float)(1.0f)));
        //in.close();
        glProg.useProgram(R.raw.fusionbayer);
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
            glProg.useProgram(R.raw.fusionbayer);

            glProg.setTexture("upsampled", upsampleWip);
            glProg.setVar("useUpsampled", 1);
            glProg.setVar("level",i);
            glProg.setVar("upscaleIn",normalExpo.sizes[i]);
            // We can discard the previous work in progress merge.
            //wip.close();
            Point wsize;
            if(normalExpo.laplace[i].mSize.equals(WorkSize)){
                wip = basePipeline.main1;
                wsize = WorkSize;
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

            /*upsampleWip.close();
            if(!normalExpo.gauss[i].mSize.equals(WorkSize)) {
                normalExpo.gauss[i].close();
                highExpo.gauss[i].close();
            }
            normalExpo.laplace[i].close();
            highExpo.laplace[i].close();*/

        }
        //previousNode.WorkingTexture.close();

        basePipeline.main1.mSize.x = initialSize.x;
        basePipeline.main1.mSize.y = initialSize.y;
        basePipeline.main2.mSize.x = initialSize.x;
        basePipeline.main2.mSize.y = initialSize.y;
        basePipeline.main3.mSize.x = initialSize.x;
        basePipeline.main3.mSize.y = initialSize.y;

        WorkingTexture = unexpose(wip, (float)basePipeline.mSettings.gain*((PostPipeline)basePipeline).AecCorr/2.f);
        Log.d(Name,"Output Size:"+wip.mSize);
        glProg.closed = true;
    }
}
