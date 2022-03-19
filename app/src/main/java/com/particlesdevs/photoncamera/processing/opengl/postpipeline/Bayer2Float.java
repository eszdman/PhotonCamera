package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.util.BufferUtils;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;

public class Bayer2Float extends Node {

    public Bayer2Float() {
        super("", "Bayer2Float");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline)basePipeline;
        GLTexture in = new GLTexture(basePipeline.mParameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),
                ((PostPipeline)(basePipeline)).stackFrame,GL_NEAREST,GL_CLAMP_TO_EDGE);
        GLTexture GainMapTex = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4),
                BufferUtils.getFrom(basePipeline.mParameters.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        float[] BL = new float[3];
        /*BL[0] = basePipeline.mParameters.noiseModeler.computeModel[0].second.floatValue();
        BL[1] = basePipeline.mParameters.noiseModeler.computeModel[1].second.floatValue();
        BL[2] = basePipeline.mParameters.noiseModeler.computeModel[2].second.floatValue();*/
        glProg.setDefine("BLR",BL[0]);
        glProg.setDefine("BLG",BL[1]);
        glProg.setDefine("BLB",BL[2]);
        glProg.setDefine("QUAD", basePipeline.mSettings.cfaPattern == -2);
        glProg.useAssetProgram("tofloat");
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("CfaPattern",basePipeline.mParameters.cfaPattern);
        glProg.setVar("patSize",2);
        glProg.setVar("whitePoint",basePipeline.mParameters.whitePoint);
        glProg.setVar("RawSize",basePipeline.mParameters.rawSize);
        Log.d(Name,"whitelevel:"+basePipeline.mParameters.whiteLevel);
        glProg.setVarU("whitelevel",(basePipeline.mParameters.whiteLevel));
        glProg.setTexture("GainMap",GainMapTex);
        for(int i =0; i<4;i++){
            basePipeline.mParameters.blackLevel[i]/=basePipeline.mParameters.whiteLevel*postPipeline.regenerationSense;
        }
        glProg.setVar("blackLevel",basePipeline.mParameters.blackLevel);
        Log.d(Name,"CfaPattern:"+basePipeline.mParameters.cfaPattern);
        postPipeline.regenerationSense = 10.f;
        int minimal = -1;
        for(int i =0; i<basePipeline.mParameters.whitePoint.length;i++){
            if(i == 1) continue;
            if(basePipeline.mParameters.whitePoint[i] < postPipeline.regenerationSense){
                postPipeline.regenerationSense = basePipeline.mParameters.whitePoint[i];
                minimal = i;
            }
        }
        if(basePipeline.mParameters.cfaPattern == 4) postPipeline.regenerationSense = 1.f;
        postPipeline.regenerationSense = 1.f/postPipeline.regenerationSense;
        postPipeline.regenerationSense = 1.f;
        Log.d(Name,"Regeneration:"+postPipeline.regenerationSense);
        glProg.setVar("Regeneration",postPipeline.regenerationSense);
        glProg.setVar("MinimalInd",minimal);
        Point wsize = new Point(basePipeline.mParameters.rawSize);
        basePipeline.main2 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        WorkingTexture = basePipeline.main2;
        /*glProg.drawBlocks(basePipeline.main3);
        glProg.useAssetProgram("demosaicantiremosaic);
        glProg.setTexture("RawBuffer",basePipeline.main3);*/


        //glUtils.convertVec4(WorkingTexture,"in1.rgb,1.0");
        //glUtils.SaveProgResult(in.mSize,"bayer",4,".jpg");

        glProg.drawBlocks(WorkingTexture);
        /*if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT){
            wsize.x/=2;
            wsize.y/=2;
            basePipeline.main2 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        }*/
        basePipeline.main1 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        basePipeline.main3 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        ((PostPipeline)basePipeline).GainMap = GainMapTex;
        glProg.closed = true;
        in.close();
        GainMapTex.close();
    }
}
