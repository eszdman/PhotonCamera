package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.opengl.postpipeline.dngprocessor.Histogram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.glReadPixels;

public class Equalization extends Node {
    public Equalization(int rid, String name) {
        super(rid, name);
    }
    private static final float MIN_GAMMA = 0.55f;
    @Override
    public void Compile() {}
    private Histogram Analyze(){
        int resize = 16;
        GLTexture r1 = new GLTexture(previousNode.WorkingTexture.mSize.x/resize,
                previousNode.WorkingTexture.mSize.y/resize,previousNode.WorkingTexture.mFormat);
        glProg.setDefine("BR","("+basePipeline.mSettings.shadows*0.6+")");
        glProg.setDefine("SAMPLING",resize);
        glProg.useProgram(R.raw.analyze);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("step",0);
        glProg.drawBlocks(r1);
        float [] brArr = new float[r1.mSize.x*r1.mSize.y * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(brArr.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.mark();
        glReadPixels(0, 0, r1.mSize.x, r1.mSize.y, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(brArr);
        fb.clear();

        glProg.setVar("stp",1);
        glProg.drawBlocks(r1);
        float [] colArr = new float[r1.mSize.x*r1.mSize.y * 4];
        fb.mark();
        glReadPixels(0, 0, r1.mSize.x, r1.mSize.y, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(colArr);
        //Log.d(Name,"brArr:"+ Arrays.toString(brArr));
        r1.close();
        return new Histogram(brArr,colArr, r1.mSize.x*r1.mSize.y);
    }
    private float EqualizePower = 0.5f;
    @Override
    public void Run() {
        WorkingTexture = basePipeline.getMain();

        Histogram histParser = Analyze();
        Bitmap lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.lut2);
        int wrongHist = 0;
        int brokeHist = 0;
        for(int i =0; i<histParser.hist.length;i++){
            float val = ((float)(i))/histParser.hist.length;
            if(3.f < histParser.hist[i] || val*0.5 > histParser.hist[i]) {
                wrongHist++;
            }
            if(histParser.hist[i] > 10.f){
                brokeHist++;
            }
        }
        if(brokeHist >= 10){
            wrongHist = histParser.hist.length;
        }
        if(wrongHist != 0){
            float wrongP = ((float)wrongHist)/histParser.hist.length;
            wrongP-=0.5f;
            if(wrongP > 0.0) wrongP*=1.6f;
            wrongP+=0.5f;
            wrongP = Math.min(wrongP,1.f);
            Log.d(Name,"WrongHistPercent:"+wrongP);
            histParser.gamma = (1.f-wrongP)*histParser.gamma + 1.f*wrongP;
            for(int i =0; i<histParser.hist.length;i++){
                histParser.hist[i] = (((float)(i))/histParser.hist.length)*wrongP + histParser.hist[i]*(1.f-wrongP);
            }
        }
        Log.d(Name,"hist:"+Arrays.toString(histParser.hist));
        GLTexture histogram = new GLTexture(histParser.hist.length,1,new GLFormat(GLFormat.DataType.FLOAT_16),
                FloatBuffer.wrap(histParser.hist), GL_LINEAR, GL_CLAMP_TO_EDGE);


        float eq = histParser.gamma;
        Log.d(Name,"Gamma:"+eq);
        float minGamma = Math.min(1f, MIN_GAMMA + 3f * (float) Math.hypot(histParser.sigma[0], histParser.sigma[1]));
        eq = Math.max(minGamma, eq < 1.f ? 0.55f + 0.45f * eq : eq);
        eq = (float) Math.pow(eq, 0.6);
        Log.d(Name,"Equalizek:"+eq);
        glProg.setDefine("BL",histParser.BL);
        glProg.setDefine("BLAVR",(histParser.BL[0]+histParser.BL[1]+histParser.BL[2])/3.f);
        Log.d(Name,"BL:"+Arrays.toString(histParser.BL));
        glProg.useProgram(R.raw.equalize);
        glProg.setVar("Equalize",eq);
        glProg.setTexture("Histogram",histogram);
        float bilatHistFactor = Math.max(0.4f, 1f - histParser.gamma * EqualizePower
                - 4f * (float) Math.hypot(histParser.sigma[0], histParser.sigma[1]));
        Log.d(Name,"HistFactor:"+bilatHistFactor*EqualizePower);
        glProg.setVar("HistFactor",bilatHistFactor*EqualizePower);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(WorkingTexture,256);
        histogram.close();
        lutbm.recycle();
        glProg.closed = true;
    }
}
