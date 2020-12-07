package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLDrawParams;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.GLUtils;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.parameters.ResolutionSolution;

public class ExposureFusionFast2 extends Node {

    public ExposureFusionFast2(String name) {
        super(0, name);
    }
    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        previousNode.WorkingTexture.close();
    }
    GLTexture exposing1 = null;
    GLTexture exposing2 = null;
    GLTexture expose(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        if(exposing1 == null) exposing1 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        glProg.drawBlocks(exposing1);
        return exposing1;
    }
    GLTexture expose2(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        if(exposing2 == null) exposing2 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        glProg.drawBlocks(exposing2);
        return exposing2;
    }
    GLTexture unexpose(GLTexture in,float str){
        glProg.useProgram(R.raw.unexpose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        glProg.drawBlocks(exposing1);
        return exposing1;
    }
    @Override
    public void Run() {
        double compressor = 1.f;
        //if(PhotonCamera.getManualMode().getCurrentExposureValue() != 0 && PhotonCamera.getManualMode().getCurrentISOValue() != 0) compressor = 1.f;
        int split = 2;
        if(PhotonCamera.getParameters().rawSize.x*PhotonCamera.getParameters().rawSize.y >= ResolutionSolution.highRes) split = 3;
        GLTexture input = new GLTexture(
                previousNode.WorkingTexture.mSize.x/split,
                previousNode.WorkingTexture.mSize.y/split,
                new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        GLTexture prevOut = basePipeline.getMain();
        GLUtils.Pyramid highExpo = null;
        GLUtils.Pyramid normalExpo = null;
        GLTexture[] wipa = null;

        float fact2 = (float) (1.0f / compressor) * 3.5f;
        for(int j = 0; j<split*split;j++) {
            int perlevel = 4;
            int levelcount = (int) (Math.log10(input.mSize.x) / Math.log10(perlevel)) + 1;
            if (levelcount <= 0) levelcount = 2;
            Log.d(Name, "levelCount:" + levelcount);


            glUtils.splitby(previousNode.WorkingTexture, input, split, j);

            if(highExpo == null) highExpo = glUtils.createPyramid(levelcount, 0, expose(input, fact2)); else
                highExpo.fillPyramid(expose(input, fact2));
            if(normalExpo == null) normalExpo = glUtils.createPyramid(levelcount, 0, expose2(input, (float) (1.0f))); else
                normalExpo.fillPyramid(expose2(input, (float) (1.0f)));
            if(wipa == null) wipa  = new GLTexture[normalExpo.laplace.length + 1];


            glProg.useProgram(R.raw.fusion);
            glProg.setVar("useUpsampled", 0);
            int ind = normalExpo.gauss.length - 1;
            if (wipa[normalExpo.laplace.length] == null)
                wipa[normalExpo.laplace.length] = new GLTexture(normalExpo.gauss[ind]);
            glProg.setTexture("normalExpo", normalExpo.gauss[ind]);
            glProg.setTexture("highExpo", highExpo.gauss[ind]);
            glProg.setTexture("normalExpoDiff", normalExpo.gauss[ind]);
            glProg.setTexture("highExpoDiff", highExpo.gauss[ind]);
            glProg.setVar("upscaleIn", wipa[normalExpo.laplace.length].mSize);
            glProg.drawBlocks(wipa[normalExpo.laplace.length]);

            for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
                GLTexture upsampleWip = wipa[i + 1];
                Log.d(Name, "upsampleWip:" + upsampleWip.mSize);
                glProg.useProgram(R.raw.fusion);
                glProg.setTexture("upsampled", upsampleWip);
                glProg.setVar("useUpsampled", 1);
                glProg.setVar("upscaleIn", normalExpo.sizes[i]);
                if (wipa[i] == null) wipa[i] = new GLTexture(normalExpo.laplace[i]);
                // Weigh full image.
                glProg.setTexture("normalExpo", normalExpo.gauss[i]);
                glProg.setTexture("highExpo", highExpo.gauss[i]);
                // Blend feature level.
                glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
                glProg.setTexture("highExpoDiff", highExpo.laplace[i]);
                glProg.drawBlocks(wipa[i]);
                //glUtils.SaveProgResult(wip.mSize,"ExposureFusion"+i);
            }
            GLTexture unexposed = unexpose(wipa[0], (float) PhotonCamera.getSettings().gain);
            GLTexture out = basePipeline.getMain();
            glUtils.conglby(unexposed,out,prevOut,split,j);
            prevOut = out;
        }
        assert normalExpo != null;
        normalExpo.releasePyramid();
        highExpo.releasePyramid();
        for(GLTexture wip : wipa) wip.close();
        exposing1.close();
        exposing2.close();
        Log.d(Name,"Output Size:"+prevOut.mSize);
        WorkingTexture = prevOut;
        glProg.closed = true;
    }
}
