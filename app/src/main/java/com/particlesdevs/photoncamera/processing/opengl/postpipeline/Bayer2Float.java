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
    /** Draw inputs shared by the full render and strip re-renders. */
    private GLTexture inTex;
    private GLTexture gainMapTex;
    private float[] hlChromaResult;
    @Override
    public int halo() {
        return hlInpaintOpposed ? 1 : 0;
    }

    public void Run() {
        PostPipeline postPipeline = (PostPipeline) basePipeline;
        Point rawSize = basePipeline.mParameters.rawSize;

        if(basePipeline.mSettings.alignAlgorithm != 2) {
            inTex = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),
                    ((PostPipeline) (basePipeline)).stackFrame, GL_NEAREST, GL_MIRRORED_REPEAT);
        } else {
            inTex = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 3),
                    ((PostPipeline) (basePipeline)).stackFrame, GL_NEAREST, GL_MIRRORED_REPEAT);
        }
        GLTexture GainMapTex = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                BufferUtils.getFrom(basePipeline.mParameters.gainMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
        gainMapTex = GainMapTex;
        float[] hlChroma = null;
        if (hlInpaintOpposed && basePipeline.mParameters.cfaPattern != 4) {
            startT();
            try {
                hlChroma = OpposedGL.compute(glProg, inTex, rawSize, basePipeline.mParameters.cfaPattern,
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
        hlChromaResult = hlChroma;

        // Defines/uniforms/draw live in drawMain() below (shared full + strip).
        // One-time sensor calibration (must run exactly once per shot — NOT
        // in drawMain, which re-runs per strip region).
        for (int i = 0; i < 4; i++) {
            basePipeline.mParameters.blackLevel[i] /= basePipeline.mParameters.whiteLevel * postPipeline.regenerationSense;
        }
        Point wsize = new Point(basePipeline.mParameters.rawSize);
        basePipeline.main2 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        WorkingTexture = basePipeline.main2;

        drawMain();
        // main1 must stay eagerly allocated here: deferring it moved the
        // GLTexture construction (whose constructor leaves the active texture
        // unit at GL_TEXTURE1 + name) to a different point in the render,
        // which clobbered a sampler on the fused tail and corrupted the
        // output. Do not make this lazy until GLTexture restores the active
        // unit it found.
        basePipeline.main1 = new GLTexture(wsize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        // main3 is demand-allocated via GLBasePipeline.getMain3(): only
        // demosaic-stage nodes use it, so it must not occupy ~514 MB (64 MP)
        // from here through the whole render.
        ((PostPipeline) basePipeline).GainMap = GainMapTex;
        if (((PostPipeline) basePipeline).debugTiledCompare) {
            verifyRegions();
        }
        glProg.closed = true;
        inTex.close();
        //GainMapTex.close();
    }

    /**
     * Binds every define/uniform the tofloat program needs and draws
     * WorkingTexture. Pure re-issue, no mutations: safe to call for the full
     * render and again per strip region. {@code yOffset} shifts sampling to
     * absolute coords (0 on the legacy path, identical output).
     */
    private void drawMain() {
        PostPipeline postPipeline = (PostPipeline) basePipeline;
        Point rawSize = basePipeline.mParameters.rawSize;
        // When the buffer is already pre-cropped by digital zoom, no extra
        // 16:9 offset should be applied (the crop region is already final).
        boolean zoomed = PhotonCamera.getCaptureController() != null
                && PhotonCamera.getCaptureController().zoomController.isZoomed();
        if (PhotonCamera.getSettings().aspect169 && !zoomed) {
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
        glProg.setDefine("RGBLAYOUT", basePipeline.mSettings.alignAlgorithm == 2);
        glProg.setDefine("TESTPATTERN", testPattern);
        glProg.setDefine("TP", testPatternIndex);
        glProg.setDefine("HLRECON", hlChromaResult != null);
        if (hlChromaResult != null) glProg.setDefine("HLCLIP", OpposedGL.CLIP_MAGIC * hlClip);
        if (basePipeline.mParameters.isCropped
                && basePipeline.mParameters.cropOrigin != null
                && basePipeline.mParameters.fullRawSize != null) {
            glProg.setDefine("HAS_GAIN_OFFSET", 1);
        }
        glProg.useAssetProgram("Bayer2Float/tofloat");
        glProg.setTexture("InputBuffer", inTex);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        glProg.setVar("patSize", 2);
        glProg.setVar("whitePoint", basePipeline.mParameters.whitePoint);
        glProg.setVar("RawSize", basePipeline.mParameters.rawSize);
        glProg.setVar("RawInvSize", 1.0f / basePipeline.mParameters.rawSize.x, 1.0f / basePipeline.mParameters.rawSize.y);
        glProg.setVarU("whitelevel", (int) basePipeline.mParameters.whiteLevel);
        glProg.setTexture("GainMap", gainMapTex);
        // The lens-shading map covers the full frame; when the buffer is cropped,
        // sample the gain map at (cropOrigin + xy) / fullSize instead of over the
        // whole map, so vignette correction stays aligned to the crop region.
        if (basePipeline.mParameters.isCropped
                && basePipeline.mParameters.cropOrigin != null
                && basePipeline.mParameters.fullRawSize != null) {
            glProg.setVar("GainOffset",
                    basePipeline.mParameters.cropOrigin.x
                            / (float) basePipeline.mParameters.fullRawSize.x,
                    basePipeline.mParameters.cropOrigin.y
                            / (float) basePipeline.mParameters.fullRawSize.y);
            glProg.setVar("GainFullInvSize",
                    1.0f / basePipeline.mParameters.fullRawSize.x,
                    1.0f / basePipeline.mParameters.fullRawSize.y);
        }
        if (testPattern && testPatternIndex == 0) {
            if (kod == null) {
                try {
                    kodbm = new GLImage(PhotonCamera.getAssetLoader().getInputStream("kodim19.png"));
                    kod = new GLTexture(kodbm);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (kod != null) glProg.setTexture("Kodak", kod);
        }
        glProg.setVar("blackLevel", basePipeline.mParameters.blackLevel);
        if (hlChromaResult != null) glProg.setVar("Chrominance", hlChromaResult);
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
        glProg.setVar("Regeneration", postPipeline.regenerationSense);
        glProg.setVar("MinimalInd", minimal);
        glProg.setVar("yOffset", tileActive() ? tileY0 : 0);
        glProg.drawBlocks(WorkingTexture);
    }

    /**
     * Harness oracle (debugTiledCompare): re-renders strips into region
     * textures and requires bit-exactness vs the full render. The input
     * texture stays full-res by design, so no halo is needed here.
     */
    private void verifyRegions() {
        GLTexture fullOut = WorkingTexture;
        int imgH = fullOut.mSize.y;
        int imgW = fullOut.mSize.x;
        float worst = TileDriver.verifyNodeBands(fullOut, imgW, imgH, 2,
                "TiledHarness", (b0, rows) -> {
                    GLTexture reg = new GLTexture(new Point(imgW, rows),
                            new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim),
                            null, GL_LINEAR, GL_CLAMP_TO_EDGE);
                    tileY0 = b0;
                    tileY1 = b0 + rows;
                    tileOut = reg;
                    WorkingTexture = reg;
                    drawMain();
                    return reg;
                });
        Log.d("TiledHarness", "bayer strips maxDiff=" + worst);
        tileY0 = 0;
        tileY1 = -1;
        tileOut = null;
        WorkingTexture = fullOut;
    }
}
