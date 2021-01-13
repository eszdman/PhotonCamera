package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.glReadPixels;

public class DynamicBL extends Node {
    public DynamicBL() {
        super(0, "DynamicBL");
    }
    public static int precisionFactor = 64;
    private static float[] findBL(int[] histr,int[] histg,int[] histb) {
        float[] bl = new float[3];
        int integrate = 0;
        int prevW = 0;
        for(int i =0; i<200; i++){
            integrate+=histr[i];
            if(integrate > 9) {
                bl[0] = i/(((float)histr.length));
                prevW = integrate;
                break;
            }
        }
        integrate = 0;
        for(int i =0; i<200; i++){
            integrate+=histg[i];
            if(integrate >= prevW) {
                bl[1] = i/(((float)histr.length));
                prevW = integrate;
                break;
            }
        }
        integrate = 0;
        for(int i =0; i<200; i++){
            integrate+=histb[i];
            if(integrate >= prevW) {
                bl[2] = i/(((float)histr.length));
                prevW = integrate;
                break;
            }
        }
        if(bl[0] == 0.0 || bl[1] == 0.0 || bl[2] == 0.0) return new float[]{0.f,0.f,0.f};
        return bl;
    }
    private float[] getBL(float[] hist){
        int[] histr = new int[256];
        int[] histg = new int[256];
        int[] histb = new int[256];
        new Kernel(){
            @Override
            public void run() {
                int i = getGlobalId(0)*4;
                int bin = (int) (hist[i] * 256 * precisionFactor);
                if (bin < 0) bin = 0;
                if (bin >= 256) bin = 256 - 1;
                histr[bin]++;
                bin = (int) (hist[i + 1] * 256 * precisionFactor);
                if (bin < 0) bin = 0;
                if (bin >= 256) bin = 256 - 1;
                histg[bin]++;
                bin = (int) (hist[i + 2] * 256 * precisionFactor);
                if (bin < 0) bin = 0;
                if (bin >= 256) bin = 256 - 1;
                histb[bin]++;
            }
        }.execute(Range.create(hist.length/4));
        if(histr[0] >= 100){
            histr[0] = 0;
        }
        if(histg[0] >= 100){
            histg[0] = 0;
        }
        if(histb[0] >= 100){
            histb[0] = 0;
        }
        //Log.d(Name,"HistR:"+Arrays.toString(histr));
        //Log.d(Name,"HistG:"+Arrays.toString(histg));
        //Log.d(Name,"HistB:"+Arrays.toString(histb));
        return findBL(histr,histg,histb);
    }
    private float[] AnalyzeBL(){
        int resize = 16;
        if(basePipeline.mSettings.selectedMode == CameraMode.NIGHT) precisionFactor = 32; else precisionFactor = 64;
        GLTexture r1 = new GLTexture(previousNode.WorkingTexture.mSize.x/resize,
                previousNode.WorkingTexture.mSize.y/resize,previousNode.WorkingTexture.mFormat);
        glProg.setDefine("SAMPLING",resize);
        glProg.useProgram(R.raw.analyze);
        glProg.setVar("stp",1);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(r1);
        float [] colArr = new float[r1.mSize.x*r1.mSize.y * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(colArr.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.mark();
        glReadPixels(0, 0, r1.mSize.x, r1.mSize.y, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(colArr);
        fb.reset();
        r1.close();
        return getBL(colArr);
    }
    @Override
    public void Compile() {}

    @Override
    public void Run() {
        ((PostPipeline)basePipeline).analyzedBL = AnalyzeBL();
        Log.d(Name,"AnalyzedBL:"+ Arrays.toString(((PostPipeline) basePipeline).analyzedBL));
        WorkingTexture = previousNode.WorkingTexture;
        glProg.closed = true;
    }
}
