package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.opengl.GLES31;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;

/**
 * AMaZE demosaicing (Emil Martineac / Ingo Weyrich, GPL-3.0) as 13 GLES 3.1
 * compute dispatches per tile, ported from the verified GLSL compute pipeline
 * in amazeGLSL (amaze_glsl/compute.py + compute_shaders.py):
 *
 * pad -> gradcd -> bound -> hvwt (+ nyquist test values) -> nyq2 -> area ->
 * green -> nyqref -> rbpm -> pmrbint -> gcorr -> chroma -> final
 *
 * Like the original amaze.cc, the demosaic runs tile by tile: the image is
 * cut into TILE x TILE interiors and every tile carries a BORDER px skirt of
 * real pixels.  BORDER covers the longest stage chain back to the CFA
 * (final 2 + chroma 4 + gcorr 3 + nyqref 3 + green 2 + area 8 + hvwt 4 +
 * gradcd 4 = 30), so the stored interiors match the whole-image run.  All
 * intermediates live in one fixed (TILE + 2*BORDER + 32)^2 padded window
 * allocated once and reused for every tile (~130 MB at TILE 1024,
 * independent of sensor resolution; the previous whole-image scratch peaked
 * around 1.8 GB for a 50 MP frame).
 *
 * The input is the Bayer2Float CFA (rgba16f, mosaic in .r, normalized to
 * [0,1]); tofloat.glsl re-anchors every sensor cfaPattern to a fixed RGGB
 * output phase, so u_fc is the constant (R,G,G,B) cell.  The crop pass
 * mirror-pads each window by 16 px (period 32, Bayer phase preserving) while
 * clamping to the whole-image padded domain, so tiles reaching past the
 * image edge reproduce the whole-image border behaviour; the final pass
 * crops the skirt while assembling RGB into the pipeline's rgba16f working
 * texture.
 *
 * The nyquist machinery runs unconditionally on every tile: the per-site
 * flag checks inside nyq2/area/nyqref already skip the heavy math wherever
 * no nyquist pattern was detected, and the bounding-box test the original
 * uses to decide whether to run those passes at all almost never said "no"
 * on real content, so it is disabled here.  The stages are ordered with
 * memory barriers only (no per-dispatch glFinish, no buffer maps - each is
 * a full pipeline stall, hundreds per shot), so the whole node runs without
 * a single CPU-GPU sync.  Like the reference (and any Bayer CFA), the
 * input width and height must be even; the window size is always even.
 */
public class Amaze extends Node {
    private static final int PAD = 16;
    private static final int LW = 8;
    private static final int LH = 8;
    private static final int BORDER = 32;     // tile skirt, >= the 30 px stage chain
    private static final int TILE = 1024;     // interior; window = TILE + 2*BORDER + 2*PAD

    public Amaze() {
        super("", "Amaze");
    }

    @Override
    public void Compile() {
    }

    private GLTexture alloc(Point size, int channels) {
        // 4-channel stages: rgba16f; scalar stages: r32f (smallest valid option)
        GLFormat.DataType type = channels == 1
                ? GLFormat.DataType.FLOAT_32 : GLFormat.DataType.FLOAT_16;
        return new GLTexture(size, new GLFormat(type, channels),
                null, GL_NEAREST, GL_CLAMP_TO_EDGE);
    }

    private void dispatch(Point size) {
        // dispatch + memory barrier only: producer->consumer ordering
        // between dispatches never needs glFinish, which stalls the CPU
        // until the whole GPU drains (computeManual pays it per dispatch)
        GLES31.glDispatchCompute((size.x + LW - 1) / LW, (size.y + LH - 1) / LH, 1);
        GLES31.glMemoryBarrier(GLES31.GL_ALL_BARRIER_BITS);
    }

    // fixed-size tile scratch, allocated once in Run() and reused per tile
    private GLTexture cfa, grad, cdA, cdB, cd2, hvwt, nyqTest, nyq2, hvwt2,
            hvwt3, greenD, greenD2, rbpm, pmrbint, greenD3, dgrb01;
    private Point window;   // padded window size shared by every tile
    private Point inner;    // interior origin in window coords (PAD + BORDER)
    private GLTexture inTex;
    private int imgW, imgH;

    private void setAmazeUniforms() {
        glProg.setVar("u_size", window.x, window.y);
        // Bayer2Float re-anchors every sensor pattern to an RGGB output phase
        glProg.setVar("u_fc", 0, 1, 1, 2);
        glProg.setVar("u_clip", 1.0f);
    }

    @Override
    public void Run() {
        inTex = previousNode.WorkingTexture;
        imgW = inTex.mSize.x;
        imgH = inTex.mSize.y;
        window = new Point(TILE + 2 * BORDER + 2 * PAD, TILE + 2 * BORDER + 2 * PAD);
        inner = new Point(PAD + BORDER, PAD + BORDER);
        WorkingTexture = basePipeline.getMain3();

        cfa = alloc(window, 1);
        grad = alloc(window, 4);
        cdA = alloc(window, 4);
        cdB = alloc(window, 4);
        cd2 = alloc(window, 4);
        hvwt = alloc(window, 1);
        nyqTest = alloc(window, 1);
        nyq2 = alloc(window, 1);
        hvwt2 = alloc(window, 1);
        hvwt3 = alloc(window, 1);
        greenD = alloc(window, 4);
        greenD2 = alloc(window, 4);
        rbpm = alloc(window, 4);
        pmrbint = alloc(window, 4);
        greenD3 = alloc(window, 4);
        dgrb01 = alloc(window, 4);

        startT();
        for (int ty = 0; ty < (imgH + TILE - 1) / TILE; ty++) {
            for (int tx = 0; tx < (imgW + TILE - 1) / TILE; tx++) {
                runTile(tx * TILE, ty * TILE);
            }
        }
        glProg.close();
        GLES31.glFinish();   // one sync per shot: honest timing + safe scratch close
        endT("amaze_tiles");

        cfa.close();
        grad.close();
        cdA.close();
        cdB.close();
        cd2.close();
        hvwt.close();
        nyqTest.close();
        nyq2.close();
        hvwt2.close();
        hvwt3.close();
        greenD.close();
        greenD2.close();
        rbpm.close();
        pmrbint.close();
        greenD3.close();
        dgrb01.close();

        WorkingTexture = basePipeline.swap3();
    }

    private void runTile(int ox, int oy) {
        int tw = Math.min(TILE, imgW - ox);
        int th = Math.min(TILE, imgH - oy);

        // pass 0: crop the tile's padded window out of the full CFA; texels
        // past the image edge reproduce the whole-image pad + staging clamp
        glProg.useAssetProgram("amaze/pad", true);
        glProg.setTexture("u_in", inTex);
        glProg.setVar("u_insize", imgW, imgH);
        glProg.setVar("u_off", ox - BORDER - PAD, oy - BORDER - PAD);
        glProg.setVar("u_size", window.x, window.y);
        glProg.setTextureCompute("img_out", cfa, true);
        dispatch(window);

        // pass 1+2 (fused): gradients + directional colour differences;
        // the cfa rides in grad.a from here on
        glProg.useAssetProgram("amaze/gradcd", true);
        setAmazeUniforms();
        glProg.setTexture("u_cfa", cfa);
        glProg.setTextureCompute("img_grad", grad, true);
        glProg.setTextureCompute("img_a", cdA, true);
        glProg.setTextureCompute("img_b", cdB, true);
        dispatch(window);

        // pass 3: variance selection + saturation bounding
        glProg.useAssetProgram("amaze/bound", true);
        setAmazeUniforms();
        glProg.setTexture("u_cda", cdA);
        glProg.setTexture("u_grad", grad);
        glProg.setTextureCompute("img_out", cd2, true);
        dispatch(window);

        // pass 4+5 (fused): hvwt weights + the per-site nyquist test values
        // that flag pixels for the nyquist correction passes
        glProg.useAssetProgram("amaze/hvwt", true);
        setAmazeUniforms();
        glProg.setTexture("u_cd2", cd2);
        glProg.setTexture("u_cdb", cdB);
        glProg.setTexture("u_grad", grad);
        glProg.setTextureCompute("img_hvwt", hvwt, true);
        glProg.setTextureCompute("img_nyq", nyqTest, true);
        dispatch(window);

        // pass 6: nyquist flag dilate/erode
        glProg.useAssetProgram("amaze/nyq2", true);
        setAmazeUniforms();
        glProg.setTexture("u_nyqtest", nyqTest);
        glProg.setTextureCompute("img_out", nyq2, true);
        dispatch(window);

        // pass 7: area interpolation in nyquist regions
        glProg.useAssetProgram("amaze/area", true);
        setAmazeUniforms();
        glProg.setTexture("u_grad", grad);
        glProg.setTexture("u_nyq2", nyq2);
        glProg.setTexture("u_hvwt", hvwt);
        glProg.setTextureCompute("img_out", hvwt2, true);
        dispatch(window);

        // pass 8: populate G at R/B sites + hvwt firming
        glProg.useAssetProgram("amaze/green", true);
        setAmazeUniforms();
        glProg.setTexture("u_grad", grad);
        glProg.setTexture("u_hvwt", hvwt2);
        glProg.setTexture("u_cd2", cd2);
        glProg.setTexture("u_nyq2", nyq2);
        glProg.setTextureCompute("img_gd", greenD, true);
        glProg.setTextureCompute("img_hv", hvwt3, true);
        dispatch(window);

        // pass 8b: nyquist refinement using G curvatures
        glProg.useAssetProgram("amaze/nyqref", true);
        setAmazeUniforms();
        glProg.setTexture("u_grad", grad);
        glProg.setTexture("u_gd", greenD);
        glProg.setTexture("u_cd2", cd2);
        glProg.setTexture("u_nyq2", nyq2);
        glProg.setTextureCompute("img_out", greenD2, true);
        dispatch(window);

        // pass 9+10 (fused): diagonal gradients + diagonal chroma interpolation
        glProg.useAssetProgram("amaze/rbpm", true);
        setAmazeUniforms();
        glProg.setTexture("u_grad", grad);
        glProg.setTextureCompute("img_out", rbpm, true);
        dispatch(window);

        // pass 11: pmwt firming + R+B interpolation
        glProg.useAssetProgram("amaze/pmrbint", true);
        setAmazeUniforms();
        glProg.setTexture("u_grad", grad);
        glProg.setTexture("u_rbpm", rbpm);
        glProg.setTextureCompute("img_out", pmrbint, true);
        dispatch(window);

        // pass 12: G via R+B + Dgrb split
        glProg.useAssetProgram("amaze/gcorr", true);
        setAmazeUniforms();
        glProg.setTexture("u_pmrbint", pmrbint);
        glProg.setTexture("u_gd2", greenD2);
        glProg.setTexture("u_hvwt", hvwt3);
        glProg.setTexture("u_grad", grad);
        glProg.setTextureCompute("img_out", greenD3, true);
        dispatch(window);

        // pass 14: fancy chroma smoothing
        glProg.useAssetProgram("amaze/chroma", true);
        setAmazeUniforms();
        glProg.setTexture("u_gd3", greenD3);
        glProg.setTextureCompute("img_out", dgrb01, true);
        dispatch(window);

        // pass 15: final RGB assembly; folds the tile skirt crop, stores the
        // interior at (ox, oy) in the full-size output
        glProg.useAssetProgram("amaze/final", true);
        glProg.setVar("u_size", window.x, window.y);
        glProg.setVar("u_fc", 0, 1, 1, 2);
        glProg.setVar("u_inner", inner.x, inner.y);
        glProg.setVar("u_outsize", tw, th);
        glProg.setVar("u_outoff", ox, oy);
        glProg.setTexture("u_chroma", dgrb01);
        glProg.setTexture("u_hvwt", hvwt3);
        glProg.setTextureCompute("img_out", WorkingTexture, true);
        dispatch(new Point(tw, th));
    }
}
