package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.annotation.SuppressLint;
import android.graphics.Point;

import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.scripts.ABL;

import java.util.Calendar;
import java.util.Locale;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;

public class ABLC extends Node {
    private static final String TAG = "ABLC";

    @Tunable(
            title = "Enable auto black level",
            category = TAG,
            min = 0.0f,
            max = 1.0f,
            defaultValue = 1.0f,
            step = 1.0f
    )
    boolean enable;

    @Tunable(
            title = "Histogram size",
            description = "Histogram bin count",
            category = TAG,
            min = 64,
            max = 16384,
            defaultValue = 256,
            step = 32
    )
    int histSize;

    @Tunable(
            title = "Noise exposure compensation EV",
            description = "Multiply noise for ABL search by selected power of 2",
            category = TAG,
            min = -10.0f,
            max = 10.0f,
            defaultValue = 0.0f,
            step = 0.5f
    )
    double noiseEV;

    @Tunable(
            title = "Min exposure multiplier",
            description = "Min multiplier for black region search",
            category = TAG,
            min = 1.0f,
            max = 32.0f,
            defaultValue = 8.0f,
            step = 1.0f
    )
    double minExposureMpy;

    @Tunable(
            title = "Max exposure compensation",
            description = "Max possible exposure compensation to search dark regions if noise is low",
            category = TAG,
            min = 1.0f,
            max = 16.0f,
            defaultValue = 10.0f,
            step = 1.0f
    )
    double maxEV;

    /** Frozen levels for this run; reused by strip re-renders. */
    private float[] blackLevels;

    public ABLC() {
        super("", "ABLC");
    }

    @Override
    public void Compile() {
    }

    @SuppressLint("DefaultLocale")
    @Override
    public int halo() {
        // Pointwise render, but the black-level compute is a whole-frame
        // histogram: the tile driver must run the compute full-frame first
        // (frozen levels), then tile only the levelcorrection draw (halo 0).
        return 0;
    }

    public void Run() {
        if(!enable){
            WorkingTexture = super.previousNode.WorkingTexture;
            return;
        }
        blackLevels = computeBlackLevels(previousNode.WorkingTexture);
        renderLevels(previousNode.WorkingTexture);
        if (((PostPipeline) basePipeline).debugTiledCompare) {
            verifyRegions();
        }
    }

    /** Whole-frame histogram → frozen levels (driver pre-pass in production). */
    private float[] computeBlackLevels(GLTexture input) {
        ABL abl = new ABL(basePipeline.glint.glProcessing, histSize);

        // Use bruteforce method to find optimal black levels that minimize color shifting
        //float[] blackLevels = bruteforceOptimalBlackLevels(hist);
        double noise = Math.sqrt(basePipeline.noiseS + basePipeline.noiseO);
        noise *= Math.pow(2.0, noiseEV);
        Log.d(TAG, "Noise value:" + noise);
        float[] levels = abl.Compute(
                minExposureMpy,
                maxEV,
                noise,
                input
        );

        Log.d(TAG, String.format("Bruteforce Black Levels - R: %.4f, G: %.4f, B: %.4f",
               levels[0], levels[1], levels[2]));
        return levels;
    }

    /**
     * Pointwise draw, region-capable: with tile fields set, renders rows
     * [tileY0, tileY1) from the given input into tileOut. Inputs are
     * same-layout tiles in production (halo 0), so no coordinate shift.
     */
    private void renderLevels(GLTexture input) {
        // Apply black level correction
        glProg.useAssetProgram("ABLC/levelcorrection");
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("blackLevel", blackLevels);
        WorkingTexture = tileActive() ? tileOut : basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }

    /**
     * Harness oracle (debugTiledCompare): blits input bands into tile
     * textures (exactly as the production driver will) and requires
     * bit-exactness vs the full render.
     */
    private void verifyRegions() {
        GLTexture fullOut = WorkingTexture;
        GLTexture fullIn = previousNode.WorkingTexture;
        int imgW = fullOut.mSize.x;
        int imgH = fullOut.mSize.y;
        float worst = TileDriver.verifyNodeBands(fullOut, imgW, imgH, 2,
                "TiledHarness", (b0, rows) -> {
                    GLTexture inTile = new GLTexture(new Point(imgW, rows),
                            new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                            null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                    TileDriver.blitBand(fullIn, inTile, b0, rows);
                    float inDiff = TileDriver.compareBand(fullIn, inTile, imgW, b0, rows,
                            "TiledHarness-blit");
                    if (inDiff != 0f) {
                        Log.e("TiledHarness", "ablc blit band [" + b0 + "," + (b0 + rows)
                                + ") maxDiff=" + inDiff);
                    }
                    GLTexture reg = new GLTexture(new Point(imgW, rows),
                            new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                            null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                    tileY0 = b0;
                    tileY1 = b0 + rows;
                    tileOut = reg;
                    WorkingTexture = reg;
                    renderLevels(inTile);
                    inTile.close();
                    return reg;
                });
        Log.d("TiledHarness", "ablc strips maxDiff=" + worst);
        tileY0 = 0;
        tileY1 = -1;
        tileOut = null;
        WorkingTexture = fullOut;
    }
}
