package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;

import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.FileManager;

import java.io.File;
import java.io.IOException;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_REPEAT;

public class RotateWatermark extends Node {
    private int rotate;
    private boolean watermarkNeeded;
    private GLImage watermark;
    private GLImage noise;
    // Program bound by Run/bindShot, for the T4b fused driver's per-band
    // rebind (same pattern as CaptureSharpening/Sharpen2 tileProgram).
    int tileProgram = 0;
    // Shader rotation selector (0/3/2/1 for 0/90/180/270) bound by bindShot;
    // the fused driver switches its band mapping on this.
    int tileRot = 0;
    // Sampler textures owned across bands: bound once in bindShot, re-issued
    // per fused band (rebind clears unit assignments). Closed by closeAll.
    GLTexture watermarkTex = null;
    GLTexture noiseTex = null;
    public RotateWatermark(int rotation) {
        super("", "Rotate");
        rotate = rotation;
        watermarkNeeded = PreferenceKeys.isShowWatermarkOn();
    }

    @Override
    public void Compile() {}
    @Override
    public void AfterRun() {
        if(watermark != null) watermark.close();
        if(noise != null) noise.close();
    }

    @Override
    public int halo() {
        return 0; // pointwise remap; tiles use global coords, grid transposes
    }

    public void Run() {
        PostPipeline pp = (PostPipeline) basePipeline;
        if (pp.tailFusedSink) {
            // T4b fused production: the segment driver in CaptureSharpening
            // already rendered bands through this program straight to the
            // sink; nothing left to bind here.
            return;
        }
        bindShot(previousNode.WorkingTexture.mSize);
        if (((PostPipeline) basePipeline).debugTiledCompare) {
            verifyRotateRegions();
        }

    }

    /**
     * Once-per-shot bind: program, watermark/noise upload, const uniforms and
     * program id. Shared by legacy Run() and the T4b fused driver (which binds
     * once, then re-asserts per band itself). Size is explicit (not read off
     * the chain): the fused driver binds during capture's produce block, when
     * downstream WorkingTextures do not exist yet. cropSize/rawSize describe
     * the FULL frame either way (their difference is 0; mirror branches key
     * off full dimensions, which is why fusion is gated to no-mirror).
     */
    void bindShot(Point inSize) {
        //else lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.neutral_lut);
        glProg.setDefine("WATERMARK",watermarkNeeded);

        glProg.useAssetProgram("RotateWatermark/addwatermark_rotate");
        tileProgram = glProg.mCurrentProgramActive;
        try {
            File waterExternal = new File(FileManager.sPHOTON_TUNING_DIR,"watermark.png");
            if (waterExternal.exists()) watermark = new GLImage(waterExternal);
            else watermark = new GLImage(PhotonCamera.getAssetLoader().getInputStream("watermark/photoncamera_watermark.png"));
            noise = new GLImage(PhotonCamera.getAssetLoader().getInputStream("noise.png"));
            watermarkTex = new GLTexture(watermark,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
            noiseTex = new GLTexture(noise,GL_LINEAR,GL_REPEAT,0);
            glProg.setTexture("Watermark", watermarkTex);
            glProg.setTexture("Noise", noiseTex);
        } catch (IOException e) {
            Log.d(Name,"Failed to load watermark or noise texture:" + Log.getStackTraceString(e));
        }

        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        int rot = -1;
        Log.d(Name,"Rotation:"+rotate);
        switch (rotate){
            case 0:
                //WorkingTexture = new GLTexture(size.x,size.y, previousNode.WorkingTexture.mFormat, null);
                rot = 0;
                break;
            case 90:
                //WorkingTexture = new GLTexture(size.y,size.x, previousNode.WorkingTexture.mFormat, null);
                rot = 3;
                break;
            case 180:
                //WorkingTexture = new GLTexture(size, previousNode.WorkingTexture.mFormat, null);
                rot = 2;
                break;
            case 270:
                //WorkingTexture = new GLTexture(size.y,size.x, previousNode.WorkingTexture.mFormat, null);
                rot = 1;
                break;
        }
        Log.d(Name,"selected rotation:"+rot);
        tileRot = rot;
        glProg.setVar("rotate",rot);
        if(basePipeline.mParameters.mirror) {
            glProg.setVar("mirror", 1);
        } else {
            glProg.setVar("mirror", 0);
        }
        // The rotate shader positions samples with (rawSize - cropSize)
        // offsets, which assumed cropSize == crop-buffer size. Since
        // UpscaleCrop expands crops to full-frame size in place, both must
        // describe the actual rotate-input texture (which always matches the
        // pre-rotation output size), so every offset is 0 and the mirror
        // branches operate on full dimensions.
        glProg.setVar("cropSize", inSize);
        glProg.setVar("rawSize", inSize);
    }

    /**
     * Harness oracle (debugTiledCompare, T3b/T4c): renders a harness-only full
     * reference (production streams bands straight to the sink and keeps no
     * output texture, so there is nothing to compare against otherwise) and
     * requires bit-exactness of output bands vs it. No halo mathematics and
     * no edge exclusions (pointwise remap with global coords). Transpose
     * rotations swap the output grid (bands stay output rows, canvas is
     * W/H-swapped); all rotations prove through this same shape. Offset
     * re-issue per band, no program rebind (see Initial.renderInitialBinds).
     * This also validates the yOffset mechanism itself, which the sink replay
     * cannot (a stuck yOffset renders deterministically wrong on both
     * replays).
     */
    private void verifyRotateRegions() {
        GLTexture fullIn = previousNode.WorkingTexture;
        int inW = fullIn.mSize.x;
        int inH = fullIn.mSize.y;
        boolean transposed = rotate == 90 || rotate == 270;
        int imgW = transposed ? inH : inW;
        int imgH = transposed ? inW : inH;
        GLTexture full = new GLTexture(new Point(imgW, imgH), fullIn.mFormat);
        try {
            // Input-content probe (self-compare logs means): a black reference
            // with black input is vacuous, with content input names the draw
            // path. Reads nothing but the input itself.
            TileDriver.compareBand(fullIn, fullIn, inW, 0, Math.min(256, inH),
                    "TiledHarness-rotin");
            glProg.setVar("yOffset", 0);
            glProg.drawBlocks(full);
            TileDriver.logProgramState("TiledHarness", "rot-ref", glProg,
                    "InputBuffer", tileProgram);
            float worst = TileDriver.verifyNodeBands(full, imgW, imgH, 512,
                    "TiledHarness", (b0, rows) -> {
                        GLTexture reg = new GLTexture(new Point(imgW, rows), fullIn.mFormat);
                        glProg.setVar("yOffset", b0);
                        glProg.drawBlocks(reg);
                        TileDriver.logProgramState("TiledHarness", "rot-band" + b0, glProg,
                                "InputBuffer", tileProgram);
                        return reg;
                    });
            Log.d("TiledHarness", "rotate strips rot=" + rotate + " maxDiff=" + worst);
            float worstT = verifyTileFedBands(fullIn, full, imgW, imgH);
            Log.d("TiledHarness", "rotate tilefed rot=" + rotate + " maxDiff=" + worstT);
        } finally {
            full.close();
        }
        glProg.setVar("yOffset", 0);
    }

    /**
     * T4c tile-fed proof: the exact per-band draws the fused driver issues
     * (band-sized inputs blitted exact from the sharpened input, compensated
     * yOffsets — see runTailProduce) vs the same full reference. Tiles are
     * copies, not renders, so any nonzero diff isolates sampling geometry
     * (stage content is covered by the stage oracles). Row tiles for rot 0/2
     * (180 mirrored), full-height column tiles for rot 3/1 (270 mirrored);
     * shapes mirror the driver feed (sharpen-halo-expanded; rotate itself
     * needs no halo). Validates each rotation's compensation formula on
     * device whenever a harness shot of that orientation arrives (rotation 0
     * arrives every shot, retro-proving the shipped formula too).
     */
    private float verifyTileFedBands(GLTexture fullIn, GLTexture full, int imgW, int imgH) {
        String tag = "TiledHarness";
        int inW = fullIn.mSize.x;
        int inH = fullIn.mSize.y;
        float worst = 0f;
        for (int[] band : TileDriver.snapBands(imgH, 512)) {
            int b0 = band[0], rows = band[1] - band[0];
            GLTexture inTile = null, reg = null;
            try {
                int yOff;
                if (tileRot == 0 || tileRot == 2) {
                    int wb0 = tileRot == 2 ? inH - (b0 + rows) : b0;
                    int wb1 = tileRot == 2 ? inH - b0 : b0 + rows;
                    int ws0 = Math.max(0, wb0 - 2);
                    int ws1 = Math.min(inH, wb1 + 2);
                    inTile = new GLTexture(new Point(inW, ws1 - ws0), fullIn.mFormat,
                            null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                    TileDriver.blitBand(fullIn, inTile, ws0, ws1 - ws0);
                    yOff = tileRot == 2 ? b0 + ws1 - inH + 1 : b0 - ws0;
                } else {
                    int wb0 = tileRot == 1 ? inW - (b0 + rows) : b0;
                    int wb1 = tileRot == 1 ? inW - b0 : b0 + rows;
                    int ws0 = Math.max(0, wb0 - 2);
                    int ws1 = Math.min(inW, wb1 + 2);
                    inTile = new GLTexture(new Point(ws1 - ws0, inH), fullIn.mFormat,
                            null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                    TileDriver.blitColumn(fullIn, inTile, ws0, ws1 - ws0);
                    // Buffer-relative like rot 0 (buffer column 0 holds image
                    // column ws0); 270 keeps its mirrored compensation.
                    yOff = tileRot == 1 ? b0 - (inW - ws1) : b0 - ws0;
                }
                reg = new GLTexture(new Point(imgW, rows), fullIn.mFormat);
                // Bind the tile under test (without this the draw samples the
                // stale full-input binding and the proof is vacuous).
                glProg.setTexture("InputBuffer", inTile);
                glProg.setVar("yOffset", yOff);
                glProg.drawBlocks(reg);
                TileDriver.logProgramState("TiledHarness", "rot-tfed" + b0, glProg,
                        "InputBuffer", tileProgram);
                float m = TileDriver.compareBand(full, reg, imgW, b0, rows, tag);
                if (m > worst) {
                    worst = m;
                }
            } catch (Throwable t) {
                Log.e(tag, "rotate tilefed band [" + b0 + "," + (b0 + rows) + ") failed", t);
                return Float.POSITIVE_INFINITY;
            } finally {
                if (inTile != null) {
                    try {
                        inTile.close();
                    } catch (Exception ignored) {
                    }
                }
                if (reg != null) {
                    try {
                        reg.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return worst;
    }
}
