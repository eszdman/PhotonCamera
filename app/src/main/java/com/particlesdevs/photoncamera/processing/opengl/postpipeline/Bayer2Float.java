package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.util.BufferUtils;
import java.io.IOException;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MIRRORED_REPEAT;
import static android.opengl.GLES20.GL_NEAREST;

public class Bayer2Float extends Node {

    public Bayer2Float() {
        super("", "Bayer2Float");
    }

    @Override
    public void Compile() {
    }
    boolean testPattern = false;
    int testPatternIndex = 2;
    @Tunable(title = "Inpaint Opposed Highlights", description = "Enable inpainting of opposed highlights to reconstruct chrominance",
            category = "Bayer2Float", min = 0, max = 1, defaultValue = 1, step = 1)
    boolean hlInpaintOpposed;

    @Tunable(title = "Highlight Clip", description = "Scale of the highlight clip level for inpainting",
            category = "Bayer2Float", min = 0.1f, max = 4.0f, defaultValue = 1.0f, step = 0.1f)
    float hlClip;
    @Override
    public void AfterRun(){
        if(testPattern && testPatternIndex == 0) {
            kodbm.close();
            kod.close();
        }
    }
    GLImage kodbm;
    GLTexture kod;
    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) basePipeline;
        Point rawSize = basePipeline.mParameters.rawSize;

        GLTexture in;
        if(basePipeline.mSettings.alignAlgorithm != 2) {
            in = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),
                    ((PostPipeline) (basePipeline)).stackFrame, GL_NEAREST, GL_MIRRORED_REPEAT);
        } else {
            in = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 3),
                    ((PostPipeline) (basePipeline)).stackFrame, GL_NEAREST, GL_MIRRORED_REPEAT);
        }
        GLTexture GainMapTex = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                BufferUtils.getFrom(basePipeline.mParameters.gainMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
        float[] hlChroma = null;
        if (hlInpaintOpposed && basePipeline.mParameters.cfaPattern != 4) {
            startT();
            try {
                hlChroma = OpposedGL.compute(glProg, in, rawSize, basePipeline.mParameters.cfaPattern,
                        basePipeline.mSettings.alignAlgorithm == 2,
                        basePipeline.mParameters.whiteLevel, basePipeline.mParameters.blackLevel,
                        basePipeline.mParameters.whitePoint, OpposedGL.CLIP_MAGIC * hlClip);
            } catch (Exception e) {
                Log.d(Name, "InpaintOpposed failed, disabling:" + Log.getStackTraceString(e));
                hlChroma = null;
            }
            if (hlChroma != null) {
                endT("InpaintOpposed chroma");
                Log.d(Name, "InpaintOpposed chrominance:" + hlChroma[0] + "," + hlChroma[1] + "," + hlChroma[2]);
                // The reconstruction emits scene-referred values above 1.0 -
                // up to the white-balanced clip extent. Tell downstream nodes
                // (Amaze's saturation bounding) where the real clip level sits
                // so reconstructed photosites are not treated as clipped data.
                float clipLevel = 1.0f;
                float[] wp = basePipeline.mParameters.whitePoint;
                if (wp != null) {
                    for (int c = 0; c < 3; c++) {
                        if (wp[c] > 0.f && wp[c] < 1.f) clipLevel = Math.max(clipLevel, 1.f / wp[c]);
                    }
                }
                postPipeline.rawClipLevel = clipLevel;
                Log.d(Name, "InpaintOpposed clip level:" + clipLevel);
            }
        }

        if (PhotonCamera.getSettings().aspect169) {
            if (rawSize.x > rawSize.y) {
                glProg.setDefine("OFFSET", 0, 2 * (((rawSize.y - rawSize.x * 9 / 16) / 2) / 2));
            } else {
                glProg.setDefine("OFFSET", 2 * (((rawSize.x - rawSize.y * 9 / 16) / 2) / 2), 0);
            }
        }

        float[] BL = new float[3];
        glProg.setDefine("BLR", BL[0]);
        glProg.setDefine("BLG", BL[1]);
        glProg.setDefine("BLB", BL[2]);
        glProg.setDefine("QUAD", basePipeline.mSettings.cfaPattern == -2);
        glProg.setDefine("RGBLAYOUT",basePipeline.mSettings.alignAlgorithm == 2);
        glProg.setDefine("TESTPATTERN",testPattern);
        glProg.setDefine("TP", testPatternIndex);
        glProg.setDefine("HLRECON", hlChroma != null);
        if (hlChroma != null) glProg.setDefine("HLCLIP", OpposedGL.CLIP_MAGIC * hlClip);
        glProg.useAssetProgram("Bayer2Float/tofloat");
        glProg.setTexture("InputBuffer", in);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        glProg.setVar("patSize", 2);
        glProg.setVar("whitePoint", basePipeline.mParameters.whitePoint);
        glProg.setVar("RawSize", basePipeline.mParameters.rawSize);
        glProg.setVar("RawInvSize", 1.0f/basePipeline.mParameters.rawSize.x, 1.0f/basePipeline.mParameters.rawSize.y);
        Log.d(Name, "whitelevel:" + basePipeline.mParameters.whiteLevel);
        glProg.setVarU("whitelevel", (int) basePipeline.mParameters.whiteLevel);
        glProg.setTexture("GainMap", GainMapTex);
        if(testPattern && testPatternIndex == 0) {
            try {
                kodbm = new GLImage(PhotonCamera.getAssetLoader().getInputStream("kodim19.png"));
                kod = new GLTexture(kodbm);
                glProg.setTexture("Kodak", kod);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < 4; i++) {
            basePipeline.mParameters.blackLevel[i] /= basePipeline.mParameters.whiteLevel * postPipeline.regenerationSense;
        }
        glProg.setVar("blackLevel", basePipeline.mParameters.blackLevel);
        if (hlChroma != null) glProg.setVar("Chrominance", hlChroma);
        Log.d(Name, "CfaPattern:" + basePipeline.mParameters.cfaPattern);
        postPipeline.regenerationSense = 10.f;
        int minimal = -1;
        for (int i = 0; i < basePipeline.mParameters.whitePoint.length; i++) {
            if (i == 1) continue;
            if (basePipeline.mParameters.whitePoint[i] < postPipeline.regenerationSense) {
                postPipeline.regenerationSense = basePipeline.mParameters.whitePoint[i];
                minimal = i;
            }
        }
        if (basePipeline.mParameters.cfaPattern == 4) postPipeline.regenerationSense = 1.f;
        postPipeline.regenerationSense = 1.f / postPipeline.regenerationSense;
        postPipeline.regenerationSense = 1.f;
        Log.d(Name, "Regeneration:" + postPipeline.regenerationSense);
        glProg.setVar("Regeneration", postPipeline.regenerationSense);
        glProg.setVar("MinimalInd", minimal);
        Point wsize = new Point(basePipeline.mParameters.rawSize);
        basePipeline.main2 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        WorkingTexture = basePipeline.main2;

        glProg.drawBlocks(WorkingTexture);
        basePipeline.main1 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        basePipeline.main3 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        ((PostPipeline) basePipeline).GainMap = GainMapTex;
        glProg.closed = true;
        in.close();
        //GainMapTex.close();
    }
}
