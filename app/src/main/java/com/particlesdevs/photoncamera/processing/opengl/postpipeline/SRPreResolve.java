package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.opengl.GLES30;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;

/**
 * Folds the merge-stage multi-frame fused lattice into the demosaiced image,
 * before the reconstruction.
 *
 * <p>The merge scatters every frame's raw sites onto the output grid and
 * reduces that accumulation to the crop-grid lattice (luma, effective
 * weight); this node adds the Wiener-shrunk difference against the post-ABLC
 * demosaiced image, with the lattice's band edge restored. The injection is
 * differential only - it never substitutes the reference's band, so flats and
 * texture stay the reference's (noise and all) and the correction can only
 * add, never soften.</p>
 *
 * <p>Placement is the point: the KernelNet anisotropic upscale and the whole
 * tail then render an SR-corrected input exactly like a native shot. The
 * recovered band above the raw Nyquist is delivered separately, after the
 * reconstruction ({@link SRBandApply}). The node renders on the crop grid
 * (pointwise, halo 0) and passes through when no lattice was exported, freeing
 * the ferry defensively either way.</p>
 */
public final class SRPreResolve extends Node {

    private GLTexture latticeTex;

    public SRPreResolve() {
        super("", "SRPreResolve");
    }

    @Override
    public void Compile() {
    }

    @Override
    public int halo() {
        // Pointwise on its own grid.
        return 0;
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
        freeBase(pp.srLatticeBase);
        pp.srLatticeBase = null;
        pp.srLatticeCPU = null;
        pp.srLatticeSize = null;
        pp.srLatticeTexID = 0;
    }

    @Override
    public void Run() {
        GLTexture input = previousNode.WorkingTexture;
        PostPipeline pp = (PostPipeline) basePipeline;
        ShortBuffer lattice = pp.srLatticeCPU;
        Point latticeSize = pp.srLatticeSize;
        ByteBuffer ownedBase = pp.srLatticeBase;
        int sharedLattice = pp.srLatticeTexID;
        Point rawSize = basePipeline.mParameters != null ? basePipeline.mParameters.rawSize : null;

        // The reference is the demosaiced crop image (a main at the raw
        // buffer's grid); without it, without the lattice, or on a geometry
        // that does not align 1:1, there is nothing to fold.
        boolean ok = input != null && rawSize != null && rawSize.x > 0
                && input.mSize != null && input.mSize.equals(rawSize)
                && latticeSize != null && latticeSize.x > 0 && latticeSize.y > 0
                && (sharedLattice != 0 || (lattice != null && ownedBase != null));
        if (!ok) {
            Log.d(Name, "SR pre-resolve passthrough: lattice=" + (lattice != null)
                    + " shared=" + sharedLattice + " size=" + latticeSize
                    + " in=" + (input != null ? input.mSize : null)
                    + " raw=" + rawSize);
            releaseFerry(pp);
            WorkingTexture = input;
            return;
        }
        if (sharedLattice != 0 && !GLES30.glIsTexture(sharedLattice)) {
            // The shared name is not visible here (the EGL group silently
            // fell back to unshared): skip the SR rather than read garbage.
            Log.e(Name, "SR pre-resolve: shared lattice " + sharedLattice + " invisible, skipping");
            releaseFerry(pp);
            WorkingTexture = input;
            return;
        }

        try {
            if (sharedLattice != 0) {
                // Shared-group handoff: adopt the merge's lattice texture; no
                // upload, no ferry. close() deletes it in this context.
                latticeTex = new GLTexture(sharedLattice, latticeSize,
                        new GLFormat(GLFormat.DataType.FLOAT_16, 4));
            } else {
                latticeTex = new GLTexture(latticeSize,
                        new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                ownedBase.position(0);
                latticeTex.loadRawHalf(ownedBase);
            }
        } catch (Throwable t) {
            Log.e(Name, "SR pre-resolve lattice adopt failed, passing through", t);
            closeTextures();
            releaseFerry(pp);
            WorkingTexture = input;
            return;
        }
        // CPU copies served (now on GPU): release so they don't ride the render.
        releaseFerry(pp);

        // Output-grid drizzle: the fused field is at the target grid, so
        // compose the RGB the reconstruction consumes (luma from the drizzle,
        // chroma from the demosaiced crop) and let UpscaleCrop run at zoom 1.
        // This represents above-raw-Nyquist content instead of folding it into
        // a raw-grid reduce. (The drizzle carries no chroma of its own, so it
        // must never be adopted as the working image directly.)
        if (latticeSize.x > rawSize.x || latticeSize.y > rawSize.y) {
            float[] ablcD = pp.ablcBlack;
            float[] blackD = ablcD != null && ablcD.length >= 3
                    ? new float[]{ablcD[0], ablcD[1], ablcD[2]}
                    : new float[]{0f, 0f, 0f};
            // The compose outputs at the drizzle's target grid: resize the
            // pipeline's ping-pong slots first, or a crop-sized slot clips it
            // and leaves the stale frame (the "smaller crop + emboss"). Same
            // contract as UpscaleCrop.takeOutputMain/rebuildPartnerMain.
            GLTexture outD = basePipeline.getMain();
            if (outD == input) {
                outD = (input == basePipeline.main1) ? basePipeline.main2 : basePipeline.main1;
                basePipeline.texnum = (outD == basePipeline.main2) ? 2 : 1;
            }
            if (outD == null || !latticeSize.equals(outD.mSize)) {
                GLTexture fresh;
                try {
                    fresh = new GLTexture(new Point(latticeSize),
                            new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
                } catch (Throwable t) {
                    Log.e(Name, "drizzle target slot alloc failed, passing through", t);
                    WorkingTexture = input;
                    return;
                }
                if (outD == basePipeline.main1) {
                    basePipeline.main1 = fresh;
                } else if (outD == basePipeline.main2) {
                    basePipeline.main2 = fresh;
                }
                if (outD != null) outD.close();
                outD = fresh;
            }
            basePipeline.workSize = new Point(latticeSize);
            glProg.useAssetProgram("srpre/drizzlecompose");
            glProg.setTexture("InputBuffer", input);
            glProg.setTexture("FusedLuma", latticeTex);
            glProg.setVar("srBlack", blackD);
            glProg.setVar("srPerOut", rawSize.x / (float) latticeSize.x,
                    rawSize.y / (float) latticeSize.y);
            WorkingTexture = outD;
            glProg.drawBlocks(outD);
            glProg.closed = true;
            // The crop input was this node's last reader: rebuild its slot at
            // the target size so the downstream ping-pong never draws into a
            // stale-size main.
            GLTexture partner = (outD == basePipeline.main1) ? basePipeline.main2 : basePipeline.main1;
            if (partner != null && partner == input && !latticeSize.equals(partner.mSize)) {
                try {
                    GLTexture freshP = new GLTexture(new Point(latticeSize),
                            new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
                    if (partner == basePipeline.main1) {
                        basePipeline.main1 = freshP;
                    } else {
                        basePipeline.main2 = freshP;
                    }
                    partner.close();
                } catch (Throwable ignored) {
                }
            }
            pp.srFullInjected = true;
            pp.srDrizzlePath = true;
            Log.d(Name, "SR output-grid drizzle compose: " + latticeSize.x + "x" + latticeSize.y);
            return;
        }

        // Differential injection into the demosaiced image. The output is the
        // ping-pong partner of the input slot.
        glProg.useAssetProgram("srpre/inject");
        float nS = pp.noiseS0 > 0f ? pp.noiseS0 : basePipeline.noiseS;
        float nO = pp.noiseO0 > 0f ? pp.noiseO0 : basePipeline.noiseO;
        float[] ablc = pp.ablcBlack;
        float[] black = ablc != null && ablc.length >= 3
                ? new float[]{ablc[0], ablc[1], ablc[2]}
                : new float[]{0f, 0f, 0f};
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("FusedLuma", latticeTex);
        glProg.setVar("srNoiseS", nS);
        glProg.setVar("srNoiseO", nO);
        glProg.setVar("srBlack", black);
        GLTexture out = basePipeline.getMain();
        if (out == input) {
            // Never draw over the input: take the other slot explicitly and
            // keep texnum coherent for the next drawing node (the same guard
            // UpscaleCrop's takeOutputMain uses).
            out = (input == basePipeline.main1) ? basePipeline.main2 : basePipeline.main1;
            basePipeline.texnum = (out == basePipeline.main2) ? 2 : 1;
        }
        WorkingTexture = out;
        glProg.drawBlocks(out);
        glProg.closed = true;
        pp.srFullInjected = true;
        Log.d(Name, "SR pre-resolve active: crop=" + input.mSize.x + "x" + input.mSize.y
                + " lattice=" + latticeSize.x + "x" + latticeSize.y);
    }

    @Override
    public void AfterRun() {
        closeTextures();
    }

    private void closeTextures() {
        if (latticeTex != null) {
            try {
                latticeTex.close();
            } catch (Exception ignored) {
            }
            latticeTex = null;
        }
    }
}
