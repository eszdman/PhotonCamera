package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

import java.nio.ByteBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;

public class CaptureSharpening extends Node {
    public CaptureSharpening() {
        super("", "CaptureSharpening");
    }

    @Override
    public void Compile() {}

    @Override
    public int halo() {
        return 5; // capturesharpening 11x11 (SHARPSIZE is a fixed define)
    }

    // True while the active (non-passthrough) branch rendered this shot.
    boolean csActive = false;
    // Program bound by the legacy Run, for the T3a tail driver's phase switch.
    int tileProgram = 0;

    public void Run() {
        Log.d(Name,"CaptureSharpening specific:"+basePipeline.mParameters.sensorSpecifics);
        csActive = false;
        PostPipeline pp = (PostPipeline) basePipeline;
        if (pp.tailTiled && pp.mParameters.sensorSpecifics != null) {
            // T4 production path: render the segment in bands (see
            // TileDriver.runTailTiled), then return without legacy draws.
            // The fused produce streams bands straight into the sink bitmap:
            // the only main it reads is the entry. Every other main is dead
            // from here through encode (downstream fused nodes early-return,
            // CorrectingFlow passes through without allocating when fused,
            // replay is harness-gated), so release them up front: -384 MB
            // per idle main at 50 MP, off the tail peak and, if the driver
            // reclaims promptly, out of the encode trough. Skipped under the
            // harness (oracles keep full state) and when the entry is unknown
            // (legacy fallback needs intact mains). A failed produce
            // reallocs before falling back to legacy.
            int freedMask = 0;
            GLTexture entry0 = previousNode != null ? previousNode.WorkingTexture : null;
            if (!pp.debugTiledCompare && entry0 != null && entry0.mSize != null) {
                freedMask = freeIdleMains(pp, entry0);
            }
            try {
                runTailTiled(pp);
            } catch (Throwable t) {
                restoreIdleMains(pp, entry0, freedMask);
                Log.e("TiledHarness", "tail produce failed, legacy fallback", t);
                pp.tailTiled = false;
                legacyBody();
            }
            return;
        }
        if (pp.debugTiledCompare) {
            try {
                snapshotTailEntry();
            } catch (Throwable t) {
                Log.e("TiledHarness", "tailproof snapshot hook failed", t);
            }
        }
        if(basePipeline.mParameters.sensorSpecifics == null){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        legacyBody();
    }

    /**
     * Closes + nulls every pipeline main except {@code entry} (bit mask of
     * freed slots: 1 = main1, 2 = main2, 4 = main3). Fused-only: the produce
     * reads exactly one main and everything downstream is a no-op or a
     * pass-through, so the rest are dead weight (384 MB each at 50 MP).
     */
    private int freeIdleMains(PostPipeline pp, GLTexture entry) {
        int mask = 0;
        try {
            com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline bp = basePipeline;
            GLTexture[] mains = {bp.main1, bp.main2, bp.main3};
            for (int i = 0; i < mains.length; i++) {
                GLTexture m = mains[i];
                if (m != null && m != entry && m.mSize != null) {
                    Log.d("TiledHarness", "tail free idle main" + (i + 1) + " "
                            + m.mSize.x + "x" + m.mSize.y);
                    try {
                        m.close();
                    } catch (Throwable ignored) {
                    }
                    mask |= (1 << i);
                }
            }
            if ((mask & 1) != 0) bp.main1 = null;
            if ((mask & 2) != 0) bp.main2 = null;
            if ((mask & 4) != 0) bp.main3 = null;
        } catch (Throwable t) {
            Log.e("TiledHarness", "tail idle-main free failed", t);
            mask = 0;
        }
        return mask;
    }

    /**
     * Reallocs mains freed by {@link #freeIdleMains} so the legacy fallback
     * finds intact ping-pong mains. Fresh (zeroed) storage is fine: legacy
     * overwrites its target full-frame before reading it.
     */
    private void restoreIdleMains(PostPipeline pp, GLTexture entry, int mask) {
        if (mask == 0) return;
        try {
            Point size = entry != null && entry.mSize != null
                    ? new Point(entry.mSize) : new Point(basePipeline.workSize);
            com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline bp = basePipeline;
            GLFormat fmt = new GLFormat(GLFormat.DataType.FLOAT_16,
                    com.particlesdevs.photoncamera.processing.opengl.GLDrawParams.WorkDim);
            if ((mask & 1) != 0 && bp.main1 == null) {
                bp.main1 = new GLTexture(new Point(size), fmt, null,
                        android.opengl.GLES20.GL_LINEAR, GL_CLAMP_TO_EDGE);
            }
            if ((mask & 2) != 0 && bp.main2 == null) {
                bp.main2 = new GLTexture(new Point(size), fmt, null,
                        android.opengl.GLES20.GL_LINEAR, GL_CLAMP_TO_EDGE);
            }
            if ((mask & 4) != 0 && bp.main3 == null) {
                bp.main3 = new GLTexture(new Point(size), fmt, null,
                        android.opengl.GLES20.GL_LINEAR, GL_CLAMP_TO_EDGE);
            }
        } catch (Throwable t) {
            Log.e("TiledHarness", "tail idle-main restore failed", t);
        }
    }

    /** Legacy full-frame path (also the T4 fallback): draws once into a main. */
    private void legacyBody() {        bindShot();
        WorkingTexture = basePipeline.getMain();
        renderTile(previousNode.WorkingTexture, WorkingTexture);

        glProg.closed = true;
        csActive = true;
        if (((PostPipeline) basePipeline).debugTiledCompare) {
            verifyCaptureRegions();
        }
    }

    /**
     * Once-per-shot bind: defines, program and program id. Shared by the
     * legacy path and the T4 production driver (which binds without drawing;
     * the driver issues draws per band itself).
     */
    void bindShot() {
        float str = (0.2f + Math.min(PreferenceKeys.getSharpnessValue(), 0.0f))/0.2f;
        float size = basePipeline.mParameters.sensorSpecifics.captureSharpeningS;
        float strength = basePipeline.mParameters.sensorSpecifics.captureSharpeningIntense*str;
        if (basePipeline.mParameters.isCropped) {
            strength *= ((PostPipeline) basePipeline).upscaleSharpenScale;
        }
        glProg.setDefine("SHARPSTR",strength);
        glProg.setDefine("SHARPSIZEKER",size);
        glProg.setDefine("INSIZE",basePipeline.workSize);
        glProg.useAssetProgram("CaptureSharpening/capturesharpening");
        tileProgram = glProg.mCurrentProgramActive;
    }

    /** T4b fused production orchestration: find Sharpen2 and RotateWatermark
     * downstream, bind all three stages once, stream bands straight to the
     * sink bitmap (no assembly main). Throws on any failure (caller falls
     * back to legacy, which re-renders full-frame from the intact entry and
     * streams via the normal sink; the sink-skipped flag is only set after
     * success, and the bitmap lock is always released). */
    private void runTailTiled(PostPipeline pp) {
        Sharpen2 shp = null;
        RotateWatermark rot = null;
        java.util.List<Node> nodes = pp.Nodes;
        int self = nodes.indexOf(this);
        for (int k = self + 1; k < nodes.size(); k++) {
            if (shp == null && nodes.get(k) instanceof Sharpen2) {
                shp = (Sharpen2) nodes.get(k);
            } else if (nodes.get(k) instanceof RotateWatermark) {
                rot = (RotateWatermark) nodes.get(k);
                break;
            }
        }
        if (shp == null || rot == null) {
            throw new IllegalStateException("tail segment without Sharpen2/Rotate");
        }
        GLTexture entry = previousNode.WorkingTexture;
        if (entry == null || entry.mSize == null) {
            throw new IllegalStateException("tail produce without entry");
        }
        GLCoreBlockProcessing glproc = basePipeline.glint.glProcessing;
        if (glproc == null) {
            throw new IllegalStateException("tail produce without sink proc");
        }
        bindShot();
        shp.bindShot(entry.mSize);
        rot.bindShot(entry.mSize);
        // Transpose rotations stream into a transposed sink bitmap; the entry
        // stays pipeline-shaped. (This check previously compared against the
        // entry dims and wrongly rejected every transposed shot.)
        boolean transposed = rot.tileRot == 1 || rot.tileRot == 3;
        int expW = transposed ? entry.mSize.y : entry.mSize.x;
        int expH = transposed ? entry.mSize.x : entry.mSize.y;
        Bitmap sink = pp.sinkBitmap;
        if (sink == null || sink.getWidth() != expW || sink.getHeight() != expH) {
            throw new IllegalStateException("tail produce without matching sink");
        }
        ByteBuffer wrapped = Allocator.wrapBitmap(sink);
        if (wrapped == null) {
            throw new IllegalStateException("tail produce bitmap lock failed");
        }
        try {
            TileDriver.runTailProduce(this, shp, rot, entry, glproc, wrapped);
        } finally {
            Allocator.unlockBitmap(sink);
        }
        // Fully rendered here (same protocol as legacyBody): suppress
        // runAllInternal's post-node redraw of this node. Without this the
        // shared program (left un-closed by the binds above) would redraw a
        // full frame over the entry placeholder on the way out.
        glProg.closed = true;
        pp.tailFusedSink = true;
        // The entry main is dead from here through encode in fused flow:
        // downstream fused nodes early-return, CorrectingFlow passes through
        // without allocating (fused implies !willCorrect), the sink draw is
        // skipped, and replay is harness-gated. Release it now (384 MB at
        // 50 MP) instead of at Run end so the driver can reclaim before the
        // encode window. Close only, never null: node fields keep dangling
        // (no post-produce draw samples them — see closed flag above) and
        // Run-end closes stay null-safe. Harness keeps full state.
        if (!pp.debugTiledCompare && entry.mSize != null) {
            Log.d("TiledHarness", "tail free entry main "
                    + entry.mSize.x + "x" + entry.mSize.y);
            try {
                entry.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Band body shared by production Run(), the oracle and the T3a tail
     * driver: binds (no program rebind — see Initial.renderInitialBinds) and
     * draws inTile into outTile. Defines and the program bind happen once in
     * Run(), never per band. */
    void renderTile(GLTexture inTile, GLTexture outTile) {
        glProg.setTexture("InputBuffer", inTile);
        WorkingTexture = outTile;
        glProg.drawBlocks(outTile);
    }

    /** T3a A/B entry snapshot: exact full-frame copy of the tail input before
     * legacy draws recycle its main. Size-gated; stale ferry closed first. */
    private void snapshotTailEntry() {
        PostPipeline pp = (PostPipeline) basePipeline;
        if (pp.tailEntryCopy != null) {
            pp.tailEntryCopy.close();
            pp.tailEntryCopy = null;
        }
        GLTexture entry = previousNode.WorkingTexture;
        if (entry == null || entry.mSize == null) {
            return;
        }
        long px = (long) entry.mSize.x * entry.mSize.y;
        if (px > 16L * 1024 * 1024) {
            Log.d("TiledHarness", "tailproof skipped (entry " + px + "px over gate)");
            return;
        }
        GLTexture copy = new GLTexture(new Point(entry.mSize), entry.mFormat);
        TileDriver.blitBand(entry, copy, 0, entry.mSize.y);
        float d = TileDriver.compareBand(entry, copy, entry.mSize.x, 0, entry.mSize.y,
                "TiledHarness-blit");
        if (d != 0f) {
            Log.e("TiledHarness", "tailproof entry snapshot maxDiff=" + d);
        }
        pp.tailEntryCopy = copy;
    }

    /**
     * Harness oracle (debugTiledCompare, T2c): blits halo-expanded input bands
     * and requires bit-exactness of the provable interior vs the full render.
     * The interior drops the bottom halo rows (outward taps fall outside the
     * frame there) and the first row of non-top bands (the shader's
     * {@code <= 0} edge guard keys off tile-relative coords and skips one tap
     * there; same documented edge-property class as Sharpen2). Texture
     * re-issue per band, no program rebind (see Initial.renderInitialBinds).
     * Skipped on the passthrough path (nothing renders).
     */
    private void verifyCaptureRegions() {
        if (!csActive) {
            Log.d("TiledHarness", "capture strips skipped (passthrough)");
            return;
        }
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
                Log.e("TiledHarness", "capture blit band [" + ye0 + "," + (ye0 + erows)
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
                int[] keep = TileDriver.clampInterior(b0, rows, imgH, halo, true);
                float m = 0f;
                if (keep[1] > keep[0]) {
                    m = TileDriver.compareBandRange(fullOut, reg, imgW, keep[0], keep[1] - keep[0],
                            off + (keep[0] - b0), "TiledHarness");
                } else {
                    Log.d("TiledHarness", "capture band [" + b0 + "," + (b0 + rows)
                            + ") empty interior");
                }
                if (m > worst) {
                    worst = m;
                }
            } catch (Throwable t) {
                Log.e("TiledHarness", "capture band [" + b0 + "," + (b0 + rows) + ") failed", t);
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
        // column tiles): axis-swapped mirror of the loop above. The shader's
        // edge guards are x/y symmetric (skip taps at <= 0 both axes), so
        // non-left bands drop their first column (leftSkip, the analogue of
        // topSkip) and right-halo columns are excluded like bottom-halo rows.
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
                Log.e("TiledHarness", "capture blit col [" + xe0 + "," + (xe0 + ecols)
                        + ") maxDiff=" + inDiff);
            }
            GLTexture reg = new GLTexture(new Point(ecols, imgH),
                    new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    null, GL_NEAREST, GL_CLAMP_TO_EDGE);
            try {
                renderTile(inTile, reg);
                int[] keep = TileDriver.clampInteriorX(c0, cols, imgW, halo, true);
                float m = 0f;
                if (keep[1] > keep[0]) {
                    m = TileDriver.compareColumnRange(fullOut, reg, imgH, keep[0],
                            keep[1] - keep[0], coff + (keep[0] - c0), "TiledHarness");
                } else {
                    Log.d("TiledHarness", "capture col [" + c0 + "," + (c0 + cols)
                            + ") empty interior");
                }
                if (m > worstC) {
                    worstC = m;
                }
            } catch (Throwable t) {
                Log.e("TiledHarness", "capture col [" + c0 + "," + (c0 + cols) + ") failed", t);
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
        Log.d("TiledHarness", "capture cols maxDiff=" + worstC);
        Log.d("TiledHarness", "capture strips maxDiff=" + worst);
        tileY0 = 0;
        tileY1 = -1;
        tileOut = null;
        WorkingTexture = fullOut;
        glProg.setTexture("InputBuffer", fullIn);
    }
}
