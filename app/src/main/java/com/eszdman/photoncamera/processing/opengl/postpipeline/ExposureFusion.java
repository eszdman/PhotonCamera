package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.GLUtils;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

public class ExposureFusion extends Node {

    public ExposureFusion(String name) {
        super(0, name);
    }
    @Override
    public void Compile() {}

    GLTexture expose(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        GLTexture out = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,3),null);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }
    GLTexture unexpose(GLTexture in){
        glProg.useProgram(R.raw.unexpose);
        glProg.setTexture("InputBuffer",in);
        //glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        GLTexture out = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,3),null);
        glProg.drawBlocks(out);
        glProg.close();
        return out;
    }

    @Override
    public void Run() {
        GLTexture in = previousNode.WorkingTexture;
        double compressor = (PhotonCamera.getSettings().compressor);
        if(PhotonCamera.getManualMode().getCurrentExposureValue() != -1 && PhotonCamera.getManualMode().getCurrentISOValue() != -1) compressor = 1.f;

        GLUtils.Pyramid highExpo = glUtils.createPyramid(4,3, expose(in,(float)(1.0/compressor)*2.0f));
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(4,3, expose(in,(float)(1.0/compressor)/5.f));
        in.close();
        glProg.useProgram(R.raw.fusion);
        glProg.setVar("useUpsampled",0);
        GLTexture wip = new GLTexture(normalExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("normalExpo",normalExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("highExpo",highExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[normalExpo.gauss.length - 1]);
        glProg.setTexture("highExpoDiff",highExpo.gauss[highExpo.gauss.length - 1]);
        glProg.drawBlocks(wip);
        glProg.close();
        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
                //GLTexture upsampleWip = (glUtils.interpolate(wip,normalExpo.sizes[i]));
                //Log.d("ExposureFusion","Before:"+upsampleWip.mSize+" point:"+normalExpo.sizes[i]);
                GLTexture upsampleWip = wip;
                Log.d(Name,"upsampleWip:"+upsampleWip.mSize);
                glProg.useProgram(R.raw.fusion);

                glProg.setTexture("upsampled", upsampleWip);
                glProg.setVar("useUpsampled", 1);
                glProg.setVar("upscaleIn",normalExpo.sizes[i]);
                // We can discard the previous work in progress merge.
                //wip.close();
                wip = new GLTexture(normalExpo.laplace[i]);

                // Weigh full image.
                glProg.setTexture("normalExpo", normalExpo.gauss[i]);
                glProg.setTexture("highExpo", highExpo.gauss[i]);

                // Blend feature level.
                glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
                glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

                glProg.drawBlocks(wip);
                glProg.close();
                upsampleWip.close();
                normalExpo.gauss[i].close();
                highExpo.gauss[i].close();
                normalExpo.laplace[i].close();
                highExpo.laplace[i].close();

        }
        previousNode.WorkingTexture.close();
        WorkingTexture = unexpose(wip);
        Log.d(Name,"Output Size:"+wip.mSize);
        wip.close();
        glProg.close();
        highExpo.releasePyramid();
        normalExpo.releasePyramid();
    }
}
