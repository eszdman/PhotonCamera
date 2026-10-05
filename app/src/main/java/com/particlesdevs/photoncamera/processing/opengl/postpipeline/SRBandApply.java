package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.opengl.GLES30;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;

/**
 * Band layer after the reconstruction: where the burst's sub-pixel sampling
 * actually recovered scene content above the raw Nyquist, this replaces the
 * reconstruction's invented content in that band with the real thing.
 *
 * <p>The merge scatters every frame's raw sites onto the output grid and
 * restores the band over [raw Nyquist, 1.5x raw Nyquist] into a
 * (band, confidence) map ({@code merge/srrecover}). This node band-passes the
 * reconstruction's own output over the same range and applies
 * {@code aniso + gate * (restored - anisoBand)}: a differential, so at gate 0
 * the output is bit-exact the native reconstruction, and the recovered band
 * can only replace invented content where the recovery is confident. Nothing
 * below the raw Nyquist is touched (that band is delivered pre-aniso by
 * {@link SRPreResolve}).</p>
 *
 * <p>Null band (single frame, declined expansion, export failure) renders as
 * a passthrough and frees the ferry exactly once.</p>
 */
public final class SRBandApply extends Node {

    private GLTexture bandTex;

    public SRBandApply() {
        super("", "SRBandApply");
    }

    @Override
    public void Compile() {
    }

    @Override
    public int halo() {
        // The band-pass reads a 2 px Gaussian radius of the input.
        return 3;
    }

    /** Frees a malloc-backed result buffer exactly once; null/view-safe. */
    private static void freeBase(ByteBuffer base) {
        if (base != null && base.isDirect()) {
            try {
                Allocator.free(base);
            } catch (Exception ignored) {
            }
        }
    }

    private void releaseFerry(PostPipeline pp) {
        if (pp == null) return;
        freeBase(pp.srBandBase);
        pp.srBandBase = null;
        pp.srBandCPU = null;
        pp.srBandSize = null;
        pp.srBandTexID = 0;
    }

    // Program bound by prepare() for the head driver's per-band phase switch.
    int tileProgram = 0;
    // Frozen band-body uniforms (set in prepare, re-issued by renderTile).
    private float perOutX = 1f, perOutY = 1f;
    private float[] srBlackVal;
    // See prepare: 0 = replace the reconstruction's band (raw-grid path),
    // 1 = deconvolve the reconstruction's own band (drizzle path).
    private boolean bandModeDeconv;
    // Set when the tiled head produce already rendered the band inside its
    // fused pass (it calls prepare/renderTile directly). The node's Run must
    // then pass through: applying it again would double the deconvolution.
    private boolean appliedByHead;

    @Override
    public void Run() {
        GLTexture input = previousNode.WorkingTexture;
        if (appliedByHead) {
            Log.d(Name, "SR band already rendered by the tiled head produce, passing through");
            appliedByHead = false;
            WorkingTexture = input;
            return;
        }
        if (!prepare(input != null ? input.mSize : null)) {
            return; // passthrough; prepare set WorkingTexture
        }
        renderTile(input, tileActive() ? tileOut : basePipeline.getMain(), 0,
                tileActive() ? tileY0 : 0);
        glProg.closed = true;
    }

    /** Called by the head driver once it rendered the band inside its fused
     * pass; see {@link #appliedByHead}. */
    void markAppliedByHead() {
        appliedByHead = true;
    }

    /**
     * One-time setup shared by the legacy Run and the head driver: adopts or
     * uploads the band map, binds the program (capturing {@link #tileProgram})
     * and freezes the band-body uniforms. {@code outSize} is the full output
     * size the raw-per-output ratio is measured against. Returns false when
     * the node passes through, with WorkingTexture already set to the input.
     * Idempotent: a second call reuses the adopted texture.
     */
    boolean prepare(Point outSize) {
        PostPipeline pp = (PostPipeline) basePipeline;
        // The output-grid drizzle already carries the above-raw-Nyquist
        // content, so its reconstruction's band is the real thing: mode 1
        // deconvolves it with the recovery's modeled gain instead of
        // replacing it. Replacement (mode 0) would swap in the RAW deposit's
        // band, which carries that deposit's sampling grid - the "pixelated
        // grid" (bench: 5.6x the artifact on a scene with no HF content).
        bandModeDeconv = pp.srDrizzlePath;
        if (bandTex != null) {
            return true;
        }
        // Fresh adoption: a new shot's band has not been applied by anyone yet.
        appliedByHead = false;
        ShortBuffer band = pp.srBandCPU;
        Point bandSize = pp.srBandSize;
        ByteBuffer ownedBase = pp.srBandBase;
        int sharedBand = pp.srBandTexID;
        Parameters p = basePipeline.mParameters;

        boolean ok = outSize != null && outSize.x > 0 && outSize.y > 0
                && bandSize != null && bandSize.x > 0 && bandSize.y > 0
                && (sharedBand != 0 || (band != null && ownedBase != null))
                && p != null && p.rawSize != null && p.rawSize.x > 0 && p.rawSize.y > 0;
        if (!ok) {
            Log.e(Name, "SR band passthrough: out=" + outSize + " bandSize=" + bandSize
                    + " shared=" + sharedBand + " raw=" + (p != null ? p.rawSize : null));
            releaseFerry(pp);
            WorkingTexture = previousNode.WorkingTexture;
            return false;
        }
        if (sharedBand != 0 && !GLES30.glIsTexture(sharedBand)) {
            Log.e(Name, "SR band: shared map " + sharedBand + " invisible, passing through");
            releaseFerry(pp);
            WorkingTexture = previousNode.WorkingTexture;
            return false;
        }

        if (sharedBand != 0) {
            // Shared-group handoff: adopt the merge's band texture; no upload,
            // no ferry. close() deletes it in this context.
            bandTex = new GLTexture(sharedBand, bandSize,
                    new GLFormat(GLFormat.DataType.UNSIGNED_32, 1));
        } else {
            bandTex = new GLTexture(bandSize,
                    new GLFormat(GLFormat.DataType.UNSIGNED_32, 1), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
            try {
                ownedBase.position(0);
                bandTex.loadRawUint(ownedBase);
            } catch (Throwable t) {
                Log.e(Name, "SR band upload failed, passing through", t);
                closeTextures();
                releaseFerry(pp);
                WorkingTexture = previousNode.WorkingTexture;
                return false;
            }
        }
        // CPU copy served (now on GPU): release so it doesn't ride the render.
        freeBase(pp.srBandBase);
        pp.srBandBase = null;
        pp.srBandCPU = null;
        pp.srBandSize = null;

        // The recovery's expansion (raw px per output px): the merge measured
        // it against the same target this node renders into.
        perOutX = p.rawSize.x / (float) outSize.x;
        perOutY = p.rawSize.y / (float) outSize.y;
        float[] ablc = pp.ablcBlack;
        srBlackVal = ablc != null && ablc.length >= 3
                ? new float[]{ablc[0], ablc[1], ablc[2]}
                : new float[]{0f, 0f, 0f};

        glProg.useAssetProgram("srpre/band");
        tileProgram = glProg.mCurrentProgramActive;
        return true;
    }

    /**
     * Band body shared by the legacy Run and the head driver: rebinds the
     * program, re-issues every uniform/texture (a rebind clears unit
     * assignments) and draws the output band. {@code inOriginY} is the
     * absolute output row the input texture's row 0 maps to;
     * {@code outOriginY} the same for the draw target; the band map stays in
     * absolute output coordinates.
     */
    void renderTile(GLTexture inTile, GLTexture outTile, int inOriginY, int outOriginY) {
        glProg.rebindProgram(tileProgram);
        glProg.setTexture("InputBuffer", inTile);
        glProg.setTexture("BandMap", bandTex);
        glProg.setVar("srPerOut", perOutX, perOutY);
        glProg.setVar("srBlack", srBlackVal);
        glProg.setVar("srBandMode", bandModeDeconv ? 1f : 0f);
        glProg.setVar("u_inOrigin", 0, inOriginY);
        glProg.setVar("u_tileOrigin", 0, outOriginY);
        WorkingTexture = outTile;
        glProg.drawBlocks(outTile);
    }

    @Override
    public void AfterRun() {
        closeTextures();
    }

    private void closeTextures() {
        if (bandTex != null) {
            try {
                bandTex.close();
            } catch (Exception ignored) {
            }
            bandTex = null;
        }
    }
}
