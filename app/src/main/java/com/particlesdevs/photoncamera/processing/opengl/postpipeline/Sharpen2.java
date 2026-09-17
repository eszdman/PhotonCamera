package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.hardware.camera2.CaptureResult;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;

public class Sharpen2 extends Node {
    public Sharpen2() {
        super("", "Sharpening");
    }

    @Override
    public void Compile() {
    }
    
    @Tunable(
            title = "Sharp Size", description = "Size parameter for sharpening",
            category = "Sharpening", min = 0.0f, max = 2.0f, defaultValue = 0.8f, step = 0.01f
    )
    float sharpSize;
    
    @Tunable(
            title = "Sharp Min", description = "Minimum sharpening threshold",
            category = "Sharpening", min = 0.0f, max = 2.0f, defaultValue = 0.25f, step = 0.01f
    )
    float sharpMin;
    
    @Tunable(
            title = "Sharp Max", description = "Maximum sharpening threshold",
            category = "Sharpening", min = 0.0f, max = 2.0f, defaultValue = 1.0f, step = 0.01f
    )
    float sharpMax;
    
    @Tunable(
            title = "Denoise Activity", description = "Denoise intensity parameter",
            category = "Sharpening", min = 0.0f, max = 1.0f, defaultValue = 0.0f)
    float denoiseActivity;

    // Applied strength captured for the harness oracle (recomputed nowhere).
    float appliedStrength = 0f;
    // Program bound by the legacy Run, for the T3a tail driver's phase switch.
    int tileProgram = 0;
    
    @Override
    public int halo() {
        return 2; // lsharpening3 5x5 (mirrorCoords2 handles borders in-shader)
    }

    public void Run() {
        PostPipeline pp = (PostPipeline) basePipeline;
        if (pp.tailTiled) {
            // T4 production: the segment driver in CaptureSharpening already
            // rendered and assembled; nothing left to draw here.
            return;
        }
        bindShot(previousNode.WorkingTexture.mSize);
        WorkingTexture = tileActive() ? tileOut : basePipeline.getMain();
        renderTile(previousNode.WorkingTexture, WorkingTexture);
        glProg.closed = true;
        GLTexture.logLive("TiledHarness", "tail-legacy-peak");
        if (pp.debugTiledCompare) {
            verifySharpenRegions();
            proveTail();
        }
    }

    /**
     * Once-per-shot bind: defines, program, computed strength and program id.
     * Shared by the legacy path and the T4 production driver (which binds
     * without drawing; the driver issues draws per band itself).
     * Size is explicit (not read off the chain): the production driver binds
     * sharpen during capture's produce block, before CorrectingFlow has run,
     * so the previous WorkingTexture does not exist yet. INSIZE is the
     * full-frame size either way (mirror bounds are global).
     */
    void bindShot(Point inSize) {
        glProg.setDefine("INTENSE",denoiseActivity);
        glProg.setDefine("INSIZE",inSize);
        glProg.setDefine("SHARPSIZE",sharpSize);
        glProg.setDefine("SHARPMIN",sharpMin);
        glProg.setDefine("SHARPMAX",sharpMax);
        glProg.setDefine("NOISES",basePipeline.noiseS);
        glProg.setDefine("NOISEO",basePipeline.noiseO);
        glProg.useAssetProgram("sharpening/lsharpening3");
        tileProgram = glProg.mCurrentProgramActive;
        float sharpness = Math.max(PreferenceKeys.getSharpnessValue(), 0.0f);
        if (basePipeline.mParameters.isCropped) {
            // Unsharp masks tuned for native detail overshoot on interpolated
            // pixels; the kernelnet reconstruction carries the acutance here.
            sharpness *= ((PostPipeline) basePipeline).upscaleSharpenScale;
        }
        appliedStrength = sharpness;
    }

    /** Walks the static chain for CaptureSharpening (bounded by UpscaleCrop). */
    private CaptureSharpening findCapture() {
        Node n = previousNode;
        while (n != null && !(n instanceof UpscaleCrop)) {
            if (n instanceof CaptureSharpening) {
                return (CaptureSharpening) n;
            }
            n = n.previousNode;
        }
        return null;
    }

    /** Band body shared by production Run(), the oracle and the T3a tail
     * driver: binds (no program rebind — see Initial.renderInitialBinds) and
     * draws inTile into outTile. Defines and the program bind happen once in
     * Run(), never per band. */
    void renderTile(GLTexture inTile, GLTexture outTile) {
        glProg.setVar("size", sharpSize);
        glProg.setVar("strength", appliedStrength);
        glProg.setTexture("InputBuffer", inTile);
        glProg.setTexture("BlurBuffer", inTile);
        WorkingTexture = outTile;
        glProg.drawBlocks(outTile);
    }

    /**
     * Harness oracle (debugTiledCompare, T2c): blits halo-expanded input bands
     * and requires bit-exactness of the provable interior vs the full render.
     * The interior drops the bottom halo rows (their outward stencil taps fall
     * outside the frame and reflect tile-relatively; production keeps the same
     * rows from the same computation, so this is a documented 2-row edge
     * property, not a silent deviation). Uniform and texture re-issue per
     * band, no program rebind (see Initial.renderInitialBinds).
     */
    private void verifySharpenRegions() {
        GLTexture fullOut = WorkingTexture;
        GLTexture fullIn = previousNode.WorkingTexture;
        int imgW = fullOut.mSize.x;
        int imgH = fullOut.mSize.y;
        int halo = halo();
        float worst = 0f;
        for (int[] band : TileDriver.snapBands(imgH, 512)) {
            int b0 = band[0], rows = band[1] - band[0];
            int ye0 = Math.max(0, b0 - halo);
            int ye1 = Math.min(imgH, b0 + rows + halo);
            int erows = ye1 - ye0;
            int off = b0 - ye0;
            GLTexture inTile = new GLTexture(new Point(imgW, erows),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    null, GL_NEAREST, GL_CLAMP_TO_EDGE);
            TileDriver.blitBand(fullIn, inTile, ye0, erows);
            float inDiff = TileDriver.compareBand(fullIn, inTile, imgW, ye0, erows,
                    "TiledHarness-blit");
            if (inDiff != 0f) {
                Log.e("TiledHarness", "sharpen2 blit band [" + ye0 + "," + (ye0 + erows)
                        + ") maxDiff=" + inDiff);
            }
            GLTexture reg = new GLTexture(new Point(imgW, erows),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    null, GL_NEAREST, GL_CLAMP_TO_EDGE);
            tileY0 = ye0;
            tileY1 = ye1;
            tileOut = reg;
            try {
                renderTile(inTile, reg);
                int[] keep = TileDriver.clampInterior(b0, rows, imgH, halo, false);
                float m = 0f;
                if (keep[1] > keep[0]) {
                    m = TileDriver.compareBandRange(fullOut, reg, imgW, keep[0], keep[1] - keep[0],
                            off + (keep[0] - b0), "TiledHarness");
                } else {
                    Log.d("TiledHarness", "sharpen2 band [" + b0 + "," + (b0 + rows)
                            + ") empty interior");
                }
                if (m > worst) {
                    worst = m;
                }
            } catch (Throwable t) {
                Log.e("TiledHarness", "sharpen2 band [" + b0 + "," + (b0 + rows) + ") failed", t);
                worst = Float.POSITIVE_INFINITY;
            } finally {
                try {
                    inTile.close();
                } catch (Exception ignored) {
                }
                try {
                    reg.close();
                } catch (Exception ignored) {
                }
            }
        }
        // T4c column bands (transpose rotations feed the sink from full-height
        // column tiles): axis-swapped mirror of the loop above, same edge
        // rule (no leftSkip, like the row version's topSkip=false).
        float worstC = 0f;
        for (int[] band : TileDriver.snapBands(imgW, 512)) {
            int c0 = band[0], cols = band[1] - band[0];
            int xe0 = Math.max(0, c0 - halo);
            int xe1 = Math.min(imgW, c0 + cols + halo);
            int ecols = xe1 - xe0;
            int coff = c0 - xe0;
            GLTexture inTile = new GLTexture(new Point(ecols, imgH),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    null, GL_NEAREST, GL_CLAMP_TO_EDGE);
            TileDriver.blitColumn(fullIn, inTile, xe0, ecols);
            float inDiff = TileDriver.compareColumn(fullIn, inTile, imgH, xe0, ecols,
                    "TiledHarness-blit");
            if (inDiff != 0f) {
                Log.e("TiledHarness", "sharpen2 blit col [" + xe0 + "," + (xe0 + ecols)
                        + ") maxDiff=" + inDiff);
            }
            GLTexture reg = new GLTexture(new Point(ecols, imgH),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    null, GL_NEAREST, GL_CLAMP_TO_EDGE);
            try {
                renderTile(inTile, reg);
                int[] keep = TileDriver.clampInteriorX(c0, cols, imgW, halo, false);
                float m = 0f;
                if (keep[1] > keep[0]) {
                    m = TileDriver.compareColumnRange(fullOut, reg, imgH, keep[0],
                            keep[1] - keep[0], coff + (keep[0] - c0), "TiledHarness");
                } else {
                    Log.d("TiledHarness", "sharpen2 col [" + c0 + "," + (c0 + cols)
                            + ") empty interior");
                }
                if (m > worstC) {
                    worstC = m;
                }
            } catch (Throwable t) {
                Log.e("TiledHarness", "sharpen2 col [" + c0 + "," + (c0 + cols) + ") failed", t);
                worstC = Float.POSITIVE_INFINITY;
            } finally {
                try {
                    inTile.close();
                } catch (Exception ignored) {
                }
                try {
                    reg.close();
                } catch (Exception ignored) {
                }
            }
        }
        Log.d("TiledHarness", "sharpen2 cols maxDiff=" + worstC);
        Log.d("TiledHarness", "sharpen2 strips maxDiff=" + worst);
        tileY0 = 0;
        tileY1 = -1;
        tileOut = null;
        WorkingTexture = fullOut;
        glProg.setTexture("InputBuffer", fullIn);
        glProg.setTexture("BlurBuffer", fullIn);
    }

    /** T3a A/B proof hook (debugTiledCompare): tiles the proven tail segment
     * against the entry snapshot taken in CaptureSharpening and requires
     * bit-exactness vs this legacy full render. Consumes the snapshot ferry. */
    private void proveTail() {
        PostPipeline pp = (PostPipeline) basePipeline;
        try {
            CaptureSharpening cap = findCapture();
            boolean captureActive = cap != null && cap.csActive;
            if (pp.tailEntryCopy == null) {
                Log.d("TiledHarness", "tailproof skipped (no entry snapshot)");
                return;
            }
            // A/B assembly scratch (freed below after the assembly proof).
            GLTexture assembly = new GLTexture(
                    new android.graphics.Point(WorkingTexture.mSize), WorkingTexture.mFormat);
            try {
                TileDriver.runTailTiled(cap, captureActive, this, pp.tailEntryCopy,
                        WorkingTexture, assembly, true);
            } finally {
                assembly.close();
            }
        } catch (Throwable t) {
            Log.e("TiledHarness", "tailproof hook failed", t);
        } finally {
            if (pp.tailEntryCopy != null) {
                pp.tailEntryCopy.close();
                pp.tailEntryCopy = null;
            }
        }
    }
}
