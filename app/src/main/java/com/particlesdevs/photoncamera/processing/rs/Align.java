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
import com.particlesdevs.photoncamera.processing.opengl.scripts.BoxDown;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GaussianResize;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import static com.particlesdevs.photoncamera.util.Utilities.addP;
import static com.particlesdevs.photoncamera.util.Utilities.div;

public class Align {
    private RUtils rUtils;
    private RenderScript rs;
    private GaussianResize gaussianResize;
    private BoxDown boxDown;
    private ScriptC_align align;

    private Allocation refDown2;
    private Allocation refDown8;
    private Allocation refDown32;
    private Allocation refDown128;

    private ByteBuffer refBuff2;

    private Allocation inputDown2;
    private Allocation inputDown8;
    private Allocation inputDown32;
    private Allocation inputDown128;



    private Allocation align128,align32,align8,align2;

    private Point rawSize;
    public static int TILESIZE = 256;
    private static final String TAG = "AlignRS";
    public Align(Point rawSize,ByteBuffer referenceFrame){
        this.rawSize = rawSize;
        rs = PhotonCamera.getRenderScript();
        GLCoreBlockProcessing glCoreBlockProcessing = new GLCoreBlockProcessing(new Point(1,1),new GLFormat(GLFormat.DataType.FLOAT_16), GLCoreBlockProcessing.Allocate.None);
        rUtils = new RUtils(rs);
        gaussianResize = new GaussianResize(glCoreBlockProcessing);
        boxDown = new BoxDown(glCoreBlockProcessing);
        align = new ScriptC_align(rs);
        Log.d(TAG,"InputBuffPointer->"+referenceFrame.position()+" capacity->"+referenceFrame.capacity()+ " remaining->"+referenceFrame.remaining());
        refDown2 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,2)));
        Log.d(TAG,"InputBuffPointer2->"+refDown2.getByteBuffer().position()+" capacity->"+refDown2.getByteBuffer().capacity()+ " remaining->"+refDown2.getByteBuffer().remaining());
        ByteBuffer buffer = ByteBuffer.allocate(refDown2.getByteBuffer().remaining());
        BoxDown(referenceFrame,buffer);
        PrintVectors(buffer);

        refDown8 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,8)));
        GaussDown(refDown2,refDown8);
        refDown32 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,32)));
        GaussDown(refDown8,refDown32);
        refDown128 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,128)));
        GaussDown(refDown32,refDown128);

        inputDown2 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,2)));
        inputDown8 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,8)));
        inputDown32 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,32)));
        inputDown128 = rUtils.allocateO(rUtils.CreateF16(div(rawSize,128)));

        int added = 1;
        align2 = rUtils.allocateO(rUtils.CreateU16_2(addP(div(rawSize,2*TILESIZE),added)));
        align8 = rUtils.allocateO(rUtils.CreateU16_2(addP(div(rawSize,8*TILESIZE),added)));
        align32 = rUtils.allocateO(rUtils.CreateU16_2(addP(div(rawSize,32*TILESIZE),added)));
        align128 = rUtils.allocateO(rUtils.CreateU16_2(addP(div(rawSize,128*TILESIZE),added)));



    }
    private void GaussDown(Allocation inp, Allocation outp){
        gaussianResize.inputB = inp.getByteBuffer();
        gaussianResize.sizeIn = new Point(inp.getType().getX(),inp.getType().getY());
        gaussianResize.Output = outp.getByteBuffer();
        gaussianResize.Run();
    }
    private void GaussDown(ByteBuffer inp, Allocation outp){
        gaussianResize.inputB = inp;
        gaussianResize.sizeIn = new Point(outp.getType().getX(),outp.getType().getY());
        gaussianResize.Output = outp.getByteBuffer();
        gaussianResize.Run();
    }
    private void BoxDown(ByteBuffer inp, Allocation outp){
        BoxDown(inp,outp.getByteBuffer());
    }
    private void BoxDown(ByteBuffer inp, ByteBuffer outp){
        boxDown.inputB = inp;
        boxDown.sizeIn = rawSize;
        boxDown.Output = outp;
        boxDown.Run();
    }
    private void BoxDown(Allocation inp, Allocation outp){
        boxDown.inputB = inp.getByteBuffer();
        boxDown.sizeIn = new Point(inp.getType().getX(),inp.getType().getY());
        boxDown.Output = outp.getByteBuffer();
        boxDown.Run();
    }
    public ByteBuffer AlignFrame(ByteBuffer inputBuffer){
        Log.d("AlignRs","BoxDown");
        BoxDown(inputBuffer,inputDown2);
        GaussDown(inputDown2,inputDown8);
        GaussDown(inputDown8,inputDown32);
        GaussDown(inputDown32,inputDown128);

        Log.d("AlignRs","RunningAlign128");
        align.set_prevScale(0);
        align.set_inputBuffer(inputDown128);
        align.set_referenceBuffer(refDown128);
        align.set_inputSize(new Int2(inputDown128.getType().getX(),inputDown128.getType().getY()));
        align.forEach_align(rUtils.Range(align128));

        align.set_prevScale(4);
        align.set_alignVectors(align128);
        align.set_inputBuffer(inputDown32);
        align.set_referenceBuffer(refDown32);
        align.set_inputSize(new Int2(inputDown32.getType().getX(),inputDown32.getType().getY()));
        align.forEach_align(rUtils.Range(align32));

        align.set_prevScale(4);
        align.set_alignVectors(align32);
        align.set_inputBuffer(inputDown8);
        align.set_referenceBuffer(refDown8);
        align.set_inputSize(new Int2(inputDown8.getType().getX(),inputDown8.getType().getY()));
        align.forEach_align(rUtils.Range(align8));

        align.set_prevScale(4);
        align.set_alignVectors(align8);
        align.set_inputBuffer(inputDown2);
        align.set_referenceBuffer(refDown2);
        align.set_inputSize(new Int2(inputDown2.getType().getX(),inputDown2.getType().getY()));
        align.forEach_align(rUtils.Range(align2));

        PrintVectors(align2.getByteBuffer());
        return align2.getByteBuffer().duplicate();
    }
    private void PrintVectors(ByteBuffer in){
        ShortBuffer shortBuffer = in.asShortBuffer();
        short[] sh= new short[shortBuffer.capacity()];
        shortBuffer.get(sh);
        Log.d(TAG,"PrintVectors->"+ Arrays.toString(sh));
    }

    public void EndAlign(){
        refDown2.destroy();
        refDown8.destroy();
        refDown32.destroy();
        refDown128.destroy();

        inputDown2.destroy();
        inputDown8.destroy();
        inputDown32.destroy();
        inputDown128.destroy();

        align128.destroy();
        align32.destroy();
        align8.destroy();
        align2.destroy();
    }
}
