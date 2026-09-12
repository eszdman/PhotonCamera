package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.ColorCorrectionTransform;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Log;

import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;

/**
 * Sky headroom renderer, replacing the Initial role.
 *
 * Renders the SDR base with matrix-only color (sensor -> ProPhoto -> sRGB,
 * white-point WB; no CCT cubes/CLUTs), the lens shading GainMap and the
 * ExposureFusionBayer2 FusionMap as linear gains, and the sky
 * log-headroom tone curve driven by the {@link LinearExposure} display gain:
 * sceneWhite = clamp(headroomScale*displayGain, 1, sceneWhiteMax).
 */
public class HeadroomRender extends Node {
    @Tunable(title = "Output Exposure", category = "Color & Tone", min = 0.50f, max = 1.20f, defaultValue = 0.80f, step = 0.01f, description = "Global linear output scale (~-0.32 EV at 0.80)")
    float outputExposureScale = 0.80f;

    @Tunable(title = "Headroom Scale", category = "Color & Tone", min = 0.50f, max = 1.20f, defaultValue = 0.90f, step = 0.01f, description = "Fraction of the display gain treated as scene white headroom")
    float headroomScale = 0.90f;

    @Tunable(title = "Headroom Max", category = "Color & Tone", min = 1.0f, max = 20.0f, defaultValue = 14.5f, step = 0.5f, description = "Upper clamp of the scene white headroom; keep above 0.9*Gain Max (14.4 at Gain Max 16) or high-gain scenes clip highlights flat")
    float sceneWhiteMax = 14.5f;

    private GLTexture fallbackGainMap;
    // Per-shot tone state computed once in Run(); rebound (not recomputed)
    // by renderHeadroom() on every harness band.
    private float headroomDisplayGain = 1f;
    private float headroomSceneWhite = 1f;
    private float[] headroomMatrix;

    public HeadroomRender() {
        super("", "HeadroomRender");
    }

    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        // Last consumer of the fusion map (was Initial's duty).
        if (((PostPipeline) basePipeline).FusionMap != null) {
            ((PostPipeline) basePipeline).FusionMap.close();
        }
        if (fallbackGainMap != null) {
            fallbackGainMap.close();
            fallbackGainMap = null;
        }
    }

    @Override
    public int halo() {
        // Bounded log-luma unsharp mask taps +-2 unconditionally; fusion
        // adds its own +-2 (same bound). Halo-2 oracle below proves it.
        return 2;
    }

    /**
     * Shot defines for the headroom program: issued in Run() before the single
     * program bind (useShader consumes the list on each use).
     */
    private void renderHeadroomDefines() {
        PostPipeline pipeline = (PostPipeline) basePipeline;
        glProg.setDefine("FUSION", pipeline.FusionMap != null);
        glProg.setDefine("NEUTRALPOINT", basePipeline.mParameters.whitePoint);
    }

    /**
     * Full texture/uniform re-issue for an input, shared by production Run()
     * and every harness band. The program itself is bound once in Run(), never
     * per band (see Initial.renderInitialBinds: a per-band rebind blacks a
     * later draw on Adreno). Selects the tile target while tileActive.
     */
    private void renderHeadroomBinds(GLTexture input) {
        PostPipeline pipeline = (PostPipeline) basePipeline;
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("u_tileOrigin", 0, tileActive() ? tileY0 : 0);
        android.graphics.Point fullSize = super.previousNode.WorkingTexture.mSize;
        glProg.setVar("u_fullSize", (float) fullSize.x, (float) fullSize.y);
        if (pipeline.FusionMap != null) glProg.setTexture("FusionMap", pipeline.FusionMap);
        glProg.setTexture("GainMap", pipeline.GainMap != null ? pipeline.GainMap : fallbackGainMap);
        glProg.setVar("sensorToIntermediate", basePipeline.mParameters.sensorToProPhoto);
        glProg.setVar("intermediateToSRGB", headroomMatrix);
        glProg.setVar("displayGain", headroomDisplayGain);
        glProg.setVar("sceneWhite", headroomSceneWhite);
        glProg.setVar("outputExposureScale", Math.max(outputExposureScale, 1.0e-2f));
        glProg.setVar("activeSize", 2, 2,
                basePipeline.mParameters.sensorPix.right - basePipeline.mParameters.sensorPix.left - 2,
                basePipeline.mParameters.sensorPix.bottom - basePipeline.mParameters.sensorPix.top - 2);
        Log.d(Name, "displayGain:" + headroomDisplayGain + " sceneWhite:" + headroomSceneWhite
                + " outputExposureScale:" + outputExposureScale
                + " intermediateToSRGB:" + Arrays.toString(headroomMatrix));

        WorkingTexture = tileActive() ? tileOut : basePipeline.getMain();
    }

    public void Run() {
        PostPipeline pipeline = (PostPipeline) basePipeline;

        headroomDisplayGain = Math.max(1.0f, pipeline.linearDisplayGain);
        headroomSceneWhite = Math.max(1.0f,
                Math.min(sceneWhiteMax, headroomScale * headroomDisplayGain));

        // Scene (pre-exposure) luma that this tonemapping maps to SDR display
        // white: the curve reaches 1.0 at exposed = sceneWhite, i.e. raw =
        // sceneWhite / displayGain. The gain-map pass anchors on this so the
        // recoverable highlight headroom is pushed exactly above scene white.
        pipeline.sceneWhiteRaw = headroomSceneWhite / headroomDisplayGain;

        // Matrix-only color: plain matrix for CUBE/CUBES modes (cubes skipped).
        headroomMatrix = basePipeline.mParameters.CCT.matrix;
        if (basePipeline.mParameters.CCT.correctionMode
                == ColorCorrectionTransform.CorrectionMode.MATRIXES) {
            headroomMatrix = basePipeline.mParameters.CCT.combineMatrix(
                    basePipeline.mParameters.whitePoint);
        }

        if (pipeline.GainMap == null && fallbackGainMap == null) {
            fallbackGainMap = new GLTexture(new Point(1, 1),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    BufferUtils.getFrom(new float[]{1.f, 1.f, 1.f, 1.f}),
                    GL_LINEAR, GL_CLAMP_TO_EDGE);
        }

        renderHeadroomDefines();
        glProg.useAssetProgram("headroom/render");
        renderHeadroomBinds(super.previousNode.WorkingTexture);
    }

    @Override
    public void postDrawOracle() {
        // Deferred-draw node: see Initial (same stale-texture hazard).
        if (((PostPipeline) basePipeline).debugTiledCompare) {
            verifyRegions();
        }
    }

    /**
     * Harness oracle (debugTiledCompare): blits halo-expanded input bands
     * into tile textures (exactly as the production driver will) and
     * requires bit-exactness of the interior vs the full render. Skipped on
     * the Fusion path (same +-2 bound, but untested combination).
     */
    private void verifyRegions() {
        if (((PostPipeline) basePipeline).FusionMap != null) {
            Log.d("TiledHarness", "headroom strips skipped (fusion path)");
            return;
        }
        GLTexture fullOut = WorkingTexture;
        GLTexture fullIn = super.previousNode.WorkingTexture;
        int imgW = fullOut.mSize.x;
        int imgH = fullOut.mSize.y;
        int halo = halo();
        // 512-row bands (see Initial): production-scale tiles, no tiny FBOs.
        float worst = 0f;
        for (int[] band : TileDriver.snapBands(imgH, 512)) {
            int b0 = band[0], rows = band[1] - band[0];
            int ye0 = Math.max(0, b0 - halo);
            int ye1 = Math.min(imgH, b0 + rows + halo);
            int erows = ye1 - ye0;
            int off = b0 - ye0;
            GLTexture inTile = new GLTexture(new android.graphics.Point(imgW, erows),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                    TileDriver.blitBand(fullIn, inTile, ye0, erows);
                    float inDiff = TileDriver.compareBand(fullIn, inTile, imgW, ye0, erows,
                            "TiledHarness-blit");
                    if (inDiff != 0f) {
                        Log.e("TiledHarness", "headroom blit band [" + ye0 + "," + (ye0 + erows)
                                + ") maxDiff=" + inDiff);
                    }
                    GLTexture reg = new GLTexture(new android.graphics.Point(imgW, erows),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    null, GL_NEAREST, GL_CLAMP_TO_EDGE);
            tileY0 = ye0;
            tileY1 = ye1;
            tileOut = reg;
            try {
                // Full uniform/texture re-issue per band, no program rebind
                // (see Initial.renderInitialBinds).
                renderHeadroomBinds(inTile);
                glProg.drawBlocks(reg);
                float m = TileDriver.compareBandRange(fullOut, reg, imgW, b0, rows, off, "TiledHarness");
                if (m > worst) {
                    worst = m;
                }
            } catch (Throwable t) {
                Log.e("TiledHarness", "headroom band [" + b0 + "," + (b0 + rows) + ") failed", t);
                worst = Float.POSITIVE_INFINITY;
            } finally {
                inTile.close();
                reg.close();
            }
        }
        Log.d("TiledHarness", "headroom strips maxDiff=" + worst);
        tileY0 = 0;
        tileY1 = -1;
        tileOut = null;
        WorkingTexture = fullOut;
        // This node never sets closed=true, so runAllInternal redraws it
        // after Run returns: restore the legacy origin and input binding or
        // the final draw shifts.
        glProg.setVar("u_tileOrigin", 0, 0);
        glProg.setTexture("InputBuffer", fullIn);
    }
}
