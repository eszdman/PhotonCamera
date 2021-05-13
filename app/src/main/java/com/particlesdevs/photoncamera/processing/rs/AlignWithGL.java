package com.particlesdevs.photoncamera.processing.rs;

import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Int2;
import android.renderscript.RenderScript;
import android.util.Log;

import com.particlesdevs.photoncamera.ScriptC_align;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.rawpipeline.AlignAndMergeHybrid;
import com.particlesdevs.photoncamera.processing.opengl.scripts.BoxDown;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GaussianResize;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import static com.particlesdevs.photoncamera.util.Utilities.addP;
import static com.particlesdevs.photoncamera.util.Utilities.div;

public class AlignWithGL {
    private RUtils rUtils;
    private ScriptC_align align;

    public GlAllocation refDown2;
    public GlAllocation refDown8;
    public GlAllocation refDown32;
    public GlAllocation refDown128;


    public GlAllocation inputDown2;
    public GlAllocation inputDown8;
    public GlAllocation inputDown32;
    public GlAllocation inputDown128;



    public GlAllocation align128,align32,align8,align2;
    public static RenderScript rs;

    private static final String TAG = "AlignRS";
    public AlignWithGL(){
        rs = PhotonCamera.getRenderScript();
        rUtils = new RUtils(rs);
        align = new ScriptC_align(rs);
    }
    public void AlignFrame(){

        Log.d("AlignRs","RunningAlign128");
        Log.d("AlignRs","inputDown128"+inputDown128.glTexture.mSize);
        Log.d("AlignRs","align128"+align128.glTexture.mSize);

        align128.createAllocation();
        align32.createAllocation();
        align8.createAllocation();
        align2.createAllocation();

        inputDown128.getAllocation();
        inputDown32.getAllocation();
        inputDown8.getAllocation();
        inputDown2.getAllocation();

        refDown128.getAllocation();
        refDown32.getAllocation();
        refDown8.getAllocation();
        refDown2.getAllocation();

        align.set_TILESIZE(AlignAndMergeHybrid.tileSize);

        align.set_prevScale(0);
        align.set_inputBuffer(inputDown128.allocation);
        align.set_referenceBuffer(refDown128.allocation);
        align.set_inputSize(new Int2(inputDown128.allocation.getType().getX(),inputDown128.allocation.getType().getY()));
        align.set_alignOutput(align128.allocation);
        align.forEach_align(rUtils.Range(align128.allocation));


        Log.d("AlignRs","RunningAlign32");

        align.set_prevScale(0);
        align.set_alignVectors(align128.allocation);
        align.set_inputBuffer(inputDown32.allocation);
        align.set_referenceBuffer(refDown32.allocation);
        align.set_inputSize(new Int2(inputDown32.allocation.getType().getX(),inputDown32.allocation.getType().getY()));
        align.set_prevSize(new Int2(align128.allocation.getType().getX(),align128.allocation.getType().getY()));
        align.set_alignOutput(align32.allocation);
        align.forEach_align(rUtils.Range(align32.allocation));

        Log.d("AlignRs","RunningAlign8");
        align.set_prevScale(4);
        align.set_alignVectors(align32.allocation);
        align.set_inputBuffer(inputDown8.allocation);
        align.set_referenceBuffer(refDown8.allocation);
        align.set_inputSize(new Int2(inputDown8.allocation.getType().getX(),inputDown8.allocation.getType().getY()));
        align.set_prevSize(new Int2(align32.allocation.getType().getX(),align32.allocation.getType().getY()));
        align.set_alignOutput(align8.allocation);
        align.forEach_align(rUtils.Range(align8.allocation));

        Log.d("AlignRs","RunningAlign2");
        align.set_prevScale(4);
        align.set_alignVectors(align8.allocation);
        align.set_inputBuffer(inputDown2.allocation);
        align.set_referenceBuffer(refDown2.allocation);
        align.set_inputSize(new Int2(inputDown2.allocation.getType().getX(),inputDown2.allocation.getType().getY()));
        align.set_prevSize(new Int2(align8.allocation.getType().getX(),align8.allocation.getType().getY()));
        align.set_alignOutput(align2.allocation);
        align.forEach_align(rUtils.Range(align2.allocation));

        align2.pushToTexture();
    }
}
