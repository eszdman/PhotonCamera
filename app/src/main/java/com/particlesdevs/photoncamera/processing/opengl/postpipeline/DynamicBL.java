package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

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
    private float[] findBL(int[] histr,int[] histg,int[] histb) {
        float[] bl = new float[3];
        int integmax = 450;
        int start = 20;
        int integrate = 0;
        int shift = 400;
        int prevW = 0;
        int min = 1000;
        int minind = 0;

        for(int i =start; i<200; i++){
            integrate+=(int)((float)histr[i]);
            if(min > histr[i] && histr[i] != 0) {
                minind = i;
                min = histr[i];
            }
            //if(histr[i] > shift) {
            if(integrate > integmax) {
                bl[0] = i/(((float)histr.length));
                prevW = integrate;
                break;
            }
        }
        integrate = 0;
        for(int i =start; i<200; i++){
            integrate+=(int)((float)histg[i]);
            if(min > histg[i] && histg[i] != 0) {
                minind = i;
                min = histg[i];
            }
            //if(histg[i] > shift) {
            if(integrate > integmax) {
                bl[1] = i/(((float)histr.length));
                prevW = integrate;
                break;
            }
        }
        integrate = 0;
        for(int i =start; i<200; i++){
            integrate+=(int)((float)histb[i]);
            if(min > histb[i] && histb[i] != 0) {
                minind = i;
                min = histb[i];
            }
            //if(histb[i] > shift) {
            if(integrate > integmax) {
                bl[2] = i/(((float)histr.length));
                prevW = integrate;
                break;
            }
        }

        /*int con = 25;
        int min = 500;
        for(int i = -10;i<10;i++){
            for(int j = -10;j<10;j++){
                for(int k = -10;k<10;k++){
                    int avr = (histr[35+i]+histg[35+j]+histb[35+k])/3;
                    int val = Math.abs(avr-histr[35+i])+Math.abs(avr-histr[35+j])+Math.abs(avr-histr[35+k]);
                    if(min > val && avr > 20){
                        min = val;
                        Log.d(Name,"Found:"+min+" ijk:"+i+","+j+","+k);
                        bl[0] = 35+i;
                        bl[1] = 35+j;
                        bl[2] = 35+k;
                    }
                }
            }
        }
        bl[0]/=(((float)histr.length));
        bl[1]/=(((float)histg.length));
        bl[2]/=(((float)histb.length));*/
        Log.d(Name,"ShadowFinder:"+Arrays.toString(bl));
        if(bl[0] == 0.0 || bl[1] == 0.0 || bl[2] == 0.0) return new float[]{0.f,0.f,0.f};
        float minf = ((float)minind)/((float)histr.length);
        float blmin = minf/((bl[0]+bl[1]+bl[2])/3.f);
        bl[0]*=blmin;
        bl[1]*=blmin;
        bl[2]*=blmin;
        return bl;
    }
    private float[] getBL(float[] hist){
        int[] histr = new int[256];
        int[] histg = new int[256];
        int[] histb = new int[256];
        float min = 0.015f;
        boolean notfound = true;
        /*for (float v : hist) {
            if (min > v && v != 0.0) {
                min = v;
                notfound = false;
            }
        }*/
        //if(notfound) return new float[]{0.f,0.f,0.f};
        //precisionFactor = (int)(0.1f/min);
        Log.d(Name,"Minimum:"+min);
        Log.d(Name,"precisionFactor:"+precisionFactor);
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
        histr[0] = 0;
        histg[0] = 0;
        histb[0] = 0;
        Log.d(Name,"HistR:"+Arrays.toString(histr));
        Log.d(Name,"HistG:"+Arrays.toString(histg));
        Log.d(Name,"HistB:"+Arrays.toString(histb));
        return findBL(histr,histg,histb);
    }
    private float[] getBL2(float[] hist){
        float[] min = new float[3];
        min[0] = hist[0];
        min[1] = hist[1];
        min[2] = hist[2];
        new Kernel(){
            @Override
            public void run() {
                int i = getGlobalId(0)*4;
                if(min[0] < hist[i]) min[0] = hist[i];
                if(min[1] < hist[i+1]) min[1] = hist[i+1];
                if(min[2] < hist[i+2]) min[2] = hist[i+2];
            }
        }.execute(Range.create(hist.length/4));
        return min;
    }
    private float[] getFL(Point size){
        float [] colArr = new float[size.x*size.y * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(colArr.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.mark();
        glReadPixels(0, 0, size.x, size.y, GL_RGBA, GL_FLOAT, fb.reset());
        fb.asReadOnlyBuffer().get(colArr);
        fb.reset();
        return colArr;
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
        float[] colArr = getFL(r1.mSize);
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
