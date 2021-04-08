package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.dngprocessor.Histogram;
import com.particlesdevs.photoncamera.processing.render.Converter;
import com.particlesdevs.photoncamera.util.Utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.glReadPixels;
import static com.particlesdevs.photoncamera.util.Math.mix;

public class Equalization extends Node {
    public Equalization() {
        super(0,"Equalization");
    }
    private static final float MIN_GAMMA = 0.55f;
    private final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private void GenerateCurveBitm(float[] curve){
        Bitmap CurveEQ = Bitmap.createBitmap(512,512, Bitmap.Config.ARGB_8888);
        ((PostPipeline)basePipeline).debugData.add(CurveEQ);
        Utilities.drawArray(curve,CurveEQ);
    }
    private void GenerateCurveBitm(float[] r,float[] g,float[] b){
        Bitmap CurveEQ = Bitmap.createBitmap(512,512, Bitmap.Config.ARGB_8888);
        ((PostPipeline)basePipeline).debugData.add(CurveEQ);
        Utilities.drawArray(r,g,b,CurveEQ);
    }
    @Override
    public void Compile() {}
    private Histogram Analyze(){
        int resize = 16;
        GLTexture r1 = new GLTexture(previousNode.WorkingTexture.mSize.x/resize,
                previousNode.WorkingTexture.mSize.y/resize,previousNode.WorkingTexture.mFormat);
        double shadowW = (basePipeline.mSettings.shadows);
        glProg.setDefine("BR",(float)shadowW*0.4f);
        glProg.setDefine("SAMPLING",resize);
        glProg.useProgram(R.raw.analyze);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("stp",0);
        glProg.drawBlocks(r1);
        float [] brArr = new float[r1.mSize.x*r1.mSize.y * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(brArr.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.mark();
        glReadPixels(0, 0, r1.mSize.x, r1.mSize.y, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(brArr);
        fb.reset();
        r1.close();
        return new Histogram(brArr, r1.mSize.x*r1.mSize.y);
    }
    private float pdf(float x,float sigma){
        return (float) (0.39894*Math.exp(-0.5*x*x/(sigma*sigma))/sigma);
    }
    private float gauss(float[] in,int ind){
        float sum = 0.f;
        float pdf = 0.f;
        for(int i =-8;i<=8;i++){
            int cind = ind+i;
            float w = pdf(i,5.5f);
            if(cind < 0) cind = 0;
            sum+=w*in[cind];
            pdf+=w;
        }
        return sum/pdf;
    }
    private float[] bezier(float in1, float in2, float in3,float in4,int size){
        float[] output = new float[size];
        for(int i =0; i<size;i++){
            float s = (float)(i)/size;
            float p0 = mix(in1,in2,s);
            float p1 = mix(in2,in3,s);
            float p2 = mix(in3,in4,s);
            float p3 = mix(p0,p1,s);
            float p4 = mix(p1,p2,s);
            output[i] = mix(p3,p4,s);
        }
        return output;
    }
    private float[] bezierIterate(float[] input, int iterations){
        float[] inchanging = input.clone();
        float[] bezier = bezier(input[0],input[85],input[168],input[input.length-1],input.length);
        for(int j = 0; j<iterations;j++){
            for(int i =0; i<inchanging.length;i++){
                inchanging[i] += (float)i/inchanging.length - bezier[i];
            }
            float[] bezier2 = bezier(inchanging[0],inchanging[85],inchanging[168],inchanging[input.length-1],input.length);
            for(int i =0; i<inchanging.length;i++){
                bezier[i] -=(float)i/inchanging.length - bezier2[i];
            }
        }
        return bezier;
    }
    /*private float[] bezier(float[]in,int size){
        float[] output = new float[size];
        float[] reduct = new float[in.length];
        for(int i =0; i<size;i++){
            for(int reduction = 0;reduction<in.length;reduction++){
                for(int j =0; j<reduction;j++){
                    reduct[j] =
                }
            }
        }
        return output;
    }*/
    private float EqualizePower = 0.9f;
    @Override
    public void Run() {
        WorkingTexture = basePipeline.getMain();

        Histogram histParser = Analyze();
        Bitmap lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.lut2);
        int wrongHist = 0;
        int brokeHist = 0;
        for(int i =0; i<histParser.hist.length;i++){
            float val = ((float)(i))/histParser.hist.length;
            //if(3.f < histParser.hist[i] || val*0.25 > histParser.hist[i]) {
                //wrongHist++;
            //}
            if(histParser.hist[i] > 15.f){
                brokeHist++;
            }
            if(Float.isNaN(histParser.hist[i])){
                brokeHist+=2;
            }
        }
        if(brokeHist >= 10){
            wrongHist = histParser.hist.length;
        }
        Log.d(Name,"WrongHistFactor:"+wrongHist);
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
        //Log.d(Name,"hist:"+Arrays.toString(histParser.hist));

        /*histParser.hist[0] = 0.f;
        float prev = histParser.hist[0];
        for(int i = 0; i<histParser.hist.length;i++){
            float prevh = histParser.hist[i];
            histParser.hist[i] = prev-Math.min(histParser.hist[i]-prev,0.001f);
            prev = prevh;
        }
        */
        /*float eq = histParser.gamma;
        eq = Math.min(eq,1.f);
        Log.d(Name,"Gamma:"+eq);
        float minGamma = Math.min(1f, MIN_GAMMA + 3f * (float) Math.hypot(histParser.sigma[0], histParser.sigma[1]));
        eq = Math.max(minGamma, eq < 1.f ? 0.55f + 0.45f * eq : eq);
        eq = (float) Math.pow(eq, 0.6);
        Log.d(Name,"Equalizek:"+eq);*/


        /*for(int j =0; j<2;j++) {
            histParser.hist[0] = 0.f;
            for (int i = 0; i < histParser.hist.length - 8; i++) {
                histParser.hist[i] = gauss(histParser.hist, i);
                histParser.histr[i] = gauss(histParser.histr, i);
                histParser.histg[i] = gauss(histParser.histg, i);
                histParser.histb[i] = gauss(histParser.histb, i);
            }
        }*/

        //Log.d(Name,"Hist:"+Arrays.toString(histParser.hist));

        //Use kx+b prediction for curve start
        float[] BLPredict = new float[3];
        float[] BLPredictShift = new float[3];
        int cnt = 0;
        for(int i =5; i<30;i++){
            float x = i/256.f;
            BLPredict[0]+= histParser.histr[i]/x;
            BLPredict[1]+= histParser.histg[i]/x;
            BLPredict[2]+= histParser.histb[i]/x;
            cnt++;
        }
        BLPredict[0]/=cnt;
        BLPredict[1]/=cnt;
        BLPredict[2]/=cnt;
        cnt = 0;
        for(int i =5; i<30;i++){
            float x = i/256.f;
            BLPredictShift[0]+=histParser.histr[i]-x*BLPredict[0];
            BLPredictShift[1]+=histParser.histg[i]-x*BLPredict[1];
            BLPredictShift[2]+=histParser.histb[i]-x*BLPredict[2];
            cnt++;
        }
        BLPredictShift[0]/=cnt;
        BLPredictShift[1]/=cnt;
        BLPredictShift[2]/=cnt;
        float mins = Math.min(BLPredictShift[0],Math.min(BLPredictShift[1],BLPredictShift[2]));
        if(mins < 0.0) {
            BLPredictShift[0]-=mins;
            BLPredictShift[1]-=mins;
            BLPredictShift[2]-=mins;
        }
        BLPredictShift[0]*=0.8;
        BLPredictShift[1]*=0.8;
        BLPredictShift[2]*=0.8;
        float[] averageCurve = new float[histParser.hist.length];
        for(int i =0; i<averageCurve.length;i++){
            averageCurve[i] = (histParser.histr[i]+histParser.histg[i]+histParser.histb[i])/3.f;
        }
        if(basePipeline.mSettings.DebugData) {
            GenerateCurveBitm(histParser.histr,histParser.histg,histParser.histb);
            GenerateCurveBitm(averageCurve);
            GenerateCurveBitm(histParser.hist);
        }
        float[] bezierArr = bezierIterate(averageCurve,0);
        for(int i =0; i<bezierArr.length;i++){
            float shadow = (float)i*4.f/bezierArr.length;
            shadow = Math.min(shadow,1.f);
            float high = (((float)i/bezierArr.length)-0.6f)*0.8f;
            high = Math.max(high,0.f);
            float prev = histParser.hist[i];
            histParser.hist[i] = Math.min(mix(histParser.hist[i],bezierArr[i],shadow),bezierArr[i]);
            histParser.hist[i] = Math.max(mix(histParser.hist[i],prev,high),histParser.hist[i]);
        }
        Log.d(Name,"PredictedShift:"+Arrays.toString(BLPredictShift));
        if(basePipeline.mSettings.DebugData) GenerateCurveBitm(histParser.hist);

        /*float[] equalizingCurve = new float[histParser.hist.length];
        for(int i =0; i<histParser.hist.length;i++){
            equalizingCurve[i] = (float)(Math.pow(((double)i)/histParser.hist.length,eq));
        }
        if(basePipeline.mSettings.DebugData) GenerateCurveBitm(equalizingCurve);
        GLTexture equalizing = new GLTexture(histParser.hist.length,1,new GLFormat(GLFormat.DataType.FLOAT_16),
                FloatBuffer.wrap(equalizingCurve), GL_LINEAR, GL_CLAMP_TO_EDGE);
        Log.d(Name,"Equalizing:"+Arrays.toString(equalizingCurve));*/

        GLTexture histogram = new GLTexture(histParser.hist.length,1,new GLFormat(GLFormat.DataType.FLOAT_16),
                FloatBuffer.wrap(histParser.hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        glProg.setDefine("BL2",BLPredictShift);
        glProg.useProgram(R.raw.equalize);
        //glProg.setVar("Equalize",eq);
        //glProg.setTexture("Equalizing",equalizing);
        glProg.setTexture("Histogram",histogram);
        GLTexture TonemapCoeffs = new GLTexture(new Point(256,1),new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(basePipeline.mSettings.toneMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("TonemapTex",TonemapCoeffs);
        glProg.setVar("toneMapCoeffs", Converter.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        float bilatHistFactor = Math.max(0.4f, 1f - histParser.gamma * EqualizePower
                - 4f * (float) Math.hypot(histParser.sigma[0], histParser.sigma[1]));
        Log.d(Name,"HistFactor:"+bilatHistFactor*EqualizePower);
        glProg.setVar("HistFactor",bilatHistFactor*EqualizePower);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(WorkingTexture);
        histogram.close();
        lutbm.recycle();
        TonemapCoeffs.close();
        glProg.closed = true;
    }
}
