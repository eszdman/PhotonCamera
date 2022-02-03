package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.dngprocessor.Histogram;
import com.particlesdevs.photoncamera.util.FileManager;
import com.particlesdevs.photoncamera.util.RANSAC;
import com.particlesdevs.photoncamera.util.SplineInterpolator;
import com.particlesdevs.photoncamera.util.Utilities;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static com.particlesdevs.photoncamera.util.Math2.MirrorCoords;
import static com.particlesdevs.photoncamera.util.Math2.mix;
import static com.particlesdevs.photoncamera.util.Math2.pdf;

public class Equalization extends Node {
    public Equalization() {
        super(0,"Equalization");
    }
    private final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private void GenerateCurveBitmWB(float[] curve, float[] BL, float[] WB){
        Bitmap CurveEQ = Bitmap.createBitmap(256,256, Bitmap.Config.ARGB_8888);
        ((PostPipeline)basePipeline).debugData.add(CurveEQ);
        Utilities.drawArray(curve,CurveEQ);
        Utilities.drawBL(BL,CurveEQ);
        Utilities.drawWB(WB,CurveEQ);//Test WL draw
    }
    private void GenerateCurveBitm(int[] curve){
        Bitmap CurveEQ = Bitmap.createBitmap(256,256, Bitmap.Config.ARGB_8888);
        ((PostPipeline)basePipeline).debugData.add(CurveEQ);
        Utilities.drawArray(curve,CurveEQ);
    }
    private void GenerateCurveBitm(float[] r,float[] g,float[] b){
        Bitmap CurveEQ = Bitmap.createBitmap(256,256, Bitmap.Config.ARGB_8888);
        ((PostPipeline)basePipeline).debugData.add(CurveEQ);
        Utilities.drawArray(r,g,b,CurveEQ);
    }
    private void GenerateCurveBitm(int[] r,int[] g,int[] b){
        Bitmap CurveEQ = Bitmap.createBitmap(256,256, Bitmap.Config.ARGB_8888);
        ((PostPipeline)basePipeline).debugData.add(CurveEQ);
        Utilities.drawArray(r,g,b,CurveEQ);
    }
    @Override
    public void Compile() {}
    private Histogram Analyze(){

        int resize = 6;
        GLTexture r1 = new GLTexture(previousNode.WorkingTexture.mSize.x/resize,
                previousNode.WorkingTexture.mSize.y/resize,previousNode.WorkingTexture.mFormat);
        //glProg.setDefine("BR",(float)shadowW*0.4f);
        glProg.setDefine("SAMPLING",resize);
        glProg.setDefine("ANALYZEINTENSE", analyzeIntensity);
        glProg.setDefine("LUT",true);
        glProg.useProgram(R.raw.analyze);
        File customAnalyzelut = new File(FileManager.sPHOTON_TUNING_DIR,"analyze_lut.png");
        Bitmap analyze_lutbm;
        GLTexture analyze_lut;
        if(customAnalyzelut.exists()){
            analyze_lutbm = BitmapFactory.decodeFile(customAnalyzelut.getAbsolutePath());
            analyze_lut = new GLTexture(analyze_lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
        } else {
            analyze_lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(),R.drawable.analyze_lut);
            analyze_lut = new GLTexture(analyze_lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
        }
        glProg.setTexture("LookupTable",analyze_lut);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("stp",0);
        glProg.drawBlocks(r1);
        Bitmap bmp = glUtils.GenerateBitmap(r1.mSize);
        analyze_lut.close();
        analyze_lutbm.recycle();
        /*float [] brArr = new float[r1.mSize.x*r1.mSize.y * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(brArr.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.mark();
        glReadPixels(0, 0, r1.mSize.x, r1.mSize.y, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(brArr);
        fb.reset();
        r1.close();*/
        endT("Equalization Part 01");
        startT();
        Histogram histogram = new Histogram(bmp, r1.mSize.x*r1.mSize.y,histSize);
        bmp.recycle();
        endT("Equalization Part 02");
        r1.close();

        return histogram;
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
    private float[] bezier(float in1, float in2, float in3,float in4,int size,int size2){
        float[] output = new float[size];
        float k = (float)(size)/size2;
        for(int i =0; i<size;i++){
            float s = (float)(i)*k/size;
            float p0 = mix(in1,in2,s);
            float p1 = mix(in2,in3,s);
            float p2 = mix(in3,in4,s);
            float p3 = mix(p0,p1,s);
            float p4 = mix(p1,p2,s);
            output[i] = mix(p3,p4,s);
        }
        return output;
    }
    private float[] bezier1(float in1,float in3,float in4,int size){
        float[] output = new float[size];
        for(int i =0; i<size;i++){
            float s = (float)(i)/size;
            float p0 = mix(in1,in3,s);
            float p2 = mix(in3,in4,s);
            float p3 = mix(p0,p2,s);
            float p4 = mix(p2,p3,s);
            output[i] = mix(p3,p4,s);
        }
        return output;
    }
    private float findWL(float[] inputR,float[] inputG, float[] inputB){
        boolean nightMode = PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
        float wlind = inputG.length-1;
        for(int i =0; i<inputR.length;i++){
            if(inputR[i] > 0.99) {
                wlind = i;
                //wlind = (i*8.f+wlind)/(8.0f + 1.f);
                break;
            }
        }
        for(int i =0; i<inputG.length;i++){
            if(inputG[i] > 0.99) {
                wlind = i;
                //wlind = (i*8.f+wlind)/(8.0f + 1.f);
                break;
            }
        }
        for(int i =0; i<inputB.length;i++){
            if(inputB[i] > 0.99) {
                wlind = i;
                //wlind = (i*8.f+wlind)/(8.0f + 1.f);
                break;
            }
        }
        /*if(!nightMode){
            wlind = (wlind + (input.length-1.f))/(1.f + 1.f);
        } else {
            wlind = (wlind*8.f+(input.length-1.f))/(8.0f + 1.f);
        }*/
        wlind = Math.min(wlind+128,inputG.length-1);
        return wlind;
    }
    private float findBL(float[] inputR,float[] inputG, float[] inputB){
        boolean nightMode = PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
        float blind = 0;
        for(int i =inputR.length-1; i>=0;i--){
            if(inputR[i] < 0.01) {
                blind = i;
                //wlind = (i*8.f+wlind)/(8.0f + 1.f);
                break;
            }
        }
        for(int i =inputG.length-1; i>=0;i--){
            if(inputG[i] < 0.01) {
                blind = i;
                //wlind = (i*8.f+wlind)/(8.0f + 1.f);
                break;
            }
        }
        for(int i =inputB.length-1; i>=0;i--){
            if(inputB[i] < 0.01) {
                blind = i;
                //wlind = (i*8.f+wlind)/(8.0f + 1.f);
                break;
            }
        }
        /*if(!nightMode){
            blind = (blind + (input.length-1.f))/(1.f + 1.f);
        } else {
            blind = (blind*8.f+(input.length-1.f))/(8.0f + 1.f);
        }*/
        blind = Math.max(blind-16,0);
        return blind;
    }
    private float contrastLevel(float[] input, int wlind){
        double integin = 0.0;
        double intege = 0.0;
        for(int i =0; i<wlind;i++){
            intege +=(double)(i)/(wlind-1.0);
            integin += input[i];
        }
        return (float)(intege/integin);
    }
    private float[] bSpline(float[] input, float[] blwl){
        boolean nightMode = PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
        float bilateralk = edgesBilateralSmooth;
        if(nightMode) bilateralk = edgesBilateralSmoothNight;
        blwl[0] = pdf(blwl[0]/input.length,bilateralk)*blwl[0]*edgesStretchShadows;
        blwl[1] = input.length - pdf(1.f - blwl[1]/input.length,bilateralk*highLightSmoothAmplify)*(input.length - blwl[1])*edgesStretchHighLight;
        blwl[1] = Math.min(blwl[1],input.length-1);
        ArrayList<Float> my,mx;
        my = new ArrayList<>();
        mx = new ArrayList<>();
        int wlind = (int)blwl[1];
        float[] output = new float[wlind];
        float clevel = contrastLevel(input,wlind);
        Log.d(Name,"ContrastLevel:"+clevel);

        int count = Math.min((int)(13 - Math.max(clevel,1.0/clevel)*3),10);
        count = Math.max(count,2);
        Log.d(Name,"Count:"+count);
        float aggressiveness = 1.2f;
        float k = (wlind-1.f)/(count-1.f);
        for(int xi = 0; xi<count; xi++){
            int x = (int)(xi*k);
            mx.add((float)xi/(float)(count-1));
            my.add(input[x]);
        }
        float k2 = (my.get(my.size()-1))/(my.size()-1);
        if(wlind <= input.length-16){
            float wl = my.get(my.size()-1);
            for(int i =0; i<my.size();i++){
                float ik = i*k2;
                if(my.get(i) < ik){
                    my.set(i,ik);
                }
            }
            my.set(my.size()-1,wl);
        }
        for(int xi = 1; xi<count-1; xi++){
            my.set(xi,(my.get(xi-1)+my.get(xi)*aggressiveness+my.get(xi+1))/(aggressiveness+2.f));
        }
        my.set(0,0.0f);
        SplineInterpolator splineInterpolator = SplineInterpolator.createMonotoneCubicSpline(mx,my);
        for(int i =0; i<output.length;i++){
            output[i] = splineInterpolator.interpolate(i/(float)(output.length-1));
        }
        return output;
    }
    private float[] bilateralSmoothCurve(float[]input, float[] blwl){
        boolean nightMode = PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
        float bilateralk = edgesBilateralSmooth;
        if(nightMode) bilateralk = edgesBilateralSmoothNight;
        ArrayList<Float> my,mx;
        my = new ArrayList<>();
        mx = new ArrayList<>();
        Log.d(Name,"BL0:"+blwl[0]);
        Log.d(Name,"WL0:"+blwl[1]);
        blwl[0] = pdf(blwl[0]/input.length,bilateralk)*blwl[0]*edgesStretchShadows;
        blwl[1] = input.length - pdf(1.f - blwl[1]/input.length,bilateralk*highLightSmoothAmplify)*(input.length - blwl[1])*edgesStretchHighLight;
        blwl[1] = Math.min(blwl[1],input.length-1);
        float centerY = 0.f;
        float msum = 0.f;
        float centerX = mix(blwl[0],blwl[1],curveCenter);
        for(int i =(int)blwl[0]; i<(int)blwl[1];i++){
            float k = pdf((i-mix(blwl[0],blwl[1],analyzeCenter))/blwl[1],1.f);
            centerY+=k*input[i];
            msum+=k;
        }
        centerY = (centerY+0.0001f)/(msum+0.0001f);

        mx.add(blwl[0]);
        //mx.add(blwl[0]+0.01f);
        mx.add(centerX);
        mx.add(blwl[1]);
        //mx.add(blwl[1]+0.01f);


        my.add(0.f);
        //my.add(0.f);
        my.add(centerY);
        my.add(1.f);
        //my.add(1.f);
        Log.d(Name,"blwl[0]:"+blwl[0]);
        Log.d(Name,"blwl[1]:"+blwl[1]);
        Log.d(Name,"Mx:"+mx.toString());
        Log.d(Name,"My:"+my.toString());
        float[]output = new float[input.length];
        SplineInterpolator splineInterpolator = SplineInterpolator.createMonotoneCubicSpline(mx,my);
        for(int i =0; i<output.length;i++){
            output[i] = splineInterpolator.interpolate(i);
            if(i < blwl[0]) output[i] = 0.f;
        }
        return output;
    }
    private float[] SmoothCurve(float[]input, float BL,float WL){
        boolean nightMode = PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
        float bilateralk = edgesBilateralSmooth;
        if(nightMode) bilateralk = edgesBilateralSmoothNight;
        Log.d(Name,"BL0:"+BL);
        Log.d(Name,"WL0:"+WL);
        BL = pdf(BL/input.length,bilateralk)*BL*edgesStretchShadows;
        WL = input.length - pdf(1.f - WL/input.length,bilateralk*highLightSmoothAmplify)*(input.length - WL)*edgesStretchHighLight;
        WL = Math.min(WL,input.length-1);
        int size;
        float[]output = input.clone();
        for(int k = 0; k<1;k++) {
            input = output.clone();
            for (int i = 0; i < output.length; i++) {
                if (i >= (int) BL && i < (int) WL) {
                    size = Math.min(i-(int)BL,(int)WL - i);
                    float temp = 0.f;
                    float pdf = 0.f;
                    for (int j = -size; j < size; j++) {
                        if (j + i >= (int) BL && j + i < (int) WL) {
                            float ker = pdf(j / 512.f, 1.f);
                            temp += ker * input[i + j];
                            pdf += ker;
                        }
                    }
                    output[i] = (temp + 0.001f) / (pdf + 0.001f);
                } else if (i <= BL) output[i] = 0.f;
                else output[i] = 1.f;
            }
        }
        return output;
    }
    private float[] bezierIterate(float[] input, int iterations){
        float[] inchanging = input.clone();
        float wlind = findWL(input,input,input);
        float[] params = new float[]{input[0],input[(int)(wlind/3.f)],input[(int)(wlind/1.5f)],input[(int)wlind]};
        float k = (params[3])/(params.length-1);

        if(wlind <= input.length-16){
            float wl = params[3];
            for(int i =0; i<params.length;i++){
                float ik = i*k;
                if(params[i] < ik){
                    params[i] = ik;
                }
            }
            params[3] = wl;
        }
        float[] bezier = bezier(params[0],params[1],params[2],params[3],input.length,(int)wlind);

        for(int j = 0; j<iterations;j++){
            for(int i =0; i<inchanging.length;i++){
                inchanging[i] += (float)i/inchanging.length - bezier[i];
            }
            float[] bezier2 = bezier(inchanging[0],inchanging[(int)(wlind/3.f)],inchanging[(int)(wlind/1.5f)],inchanging[(int)wlind],input.length,(int)wlind);
            for(int i =0; i<inchanging.length;i++){
                bezier[i] -=(float)i/inchanging.length - bezier2[i];
            }
        }
        return bezier;
    }
    static class Point2D{
        float x,y;
    }
    private Point2D mixp(Point2D in, Point2D in2, float t){
        Point2D outp = new Point2D();
        outp.x = in.x*(1.f-t) + in2.x*t;
        outp.y = in.y*(1.f-t) + in2.y*t;
        return outp;
    }
    private void ApplyLaplace(float[] currentCurve, float[] eqCurve){
        float laplacianAMP = 1.5f;
        int laplaceSize = 128;
        float[] laplaceArr = new float[eqCurve.length];
        for(int i =0; i<laplaceArr.length;i++){
            float blur = 0.f;
            float pdf = 0.f;
            for(int j = -laplaceSize/2; j<=laplaceSize/2;j++){
                float mp = pdf((float)j/(laplaceSize/2.f),1.5f);
                blur+=eqCurve[MirrorCoords(i+j,eqCurve.length)]*mp;
                pdf+=mp;
            }
            blur/=pdf;
            laplaceArr[i] = eqCurve[i]-blur;
        }
        for(int i =0; i<currentCurve.length-1;i++){
            float mp1 = Math.min(1.f,i*10.f/(currentCurve.length-1.f));
            float nc = currentCurve[i]+laplaceArr[i]*laplacianAMP*(Math.min(i,400)/400.f)*mp1;
            //if(nc > currentCurve[i+1]) nc = currentCurve[i];
            currentCurve[i] = nc;
        }
    }
    private float[] bezier2(float[] input){
        return input;
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
    class Minindexes implements Comparable<Minindexes>{
        public float r,g,b;
        public int ind;
        public double dist(){
            float r2,g2,b2;
            r2 = r/(r+g+b);
            g2 = g/(r+g+b);
            b2 = b/(r+g+b);
            return Math.sqrt(r2*r2 + g2*g2 + b2*b2);
        }
        public Minindexes(float r, float g, float b, int ind){
            this.r = r+0.0001f;
            this.g = g+0.0001f;
            this.b = b+0.0001f;
            this.ind = ind;
        }
        @Override
        public int compareTo(Minindexes o) {
            double out = ((dist()-o.dist())*1000.);
            return Double.compare(out, 0.0);
        }
    }
    private float[] getWB(float[] histr, float[] histg, float[] histb, float[] blwl){
        int searchMax = (int) mix(blwl[0],blwl[1],whiteBalanceSearch/((float)histSize));
        float rk = 0.f;
        float gk = 0.f;
        float bk = 0.f;
        float cnt = 0.f;
        float mindist = 1000.f;
        /*Minindexes[] minindexes = new Minindexes[histSize];
        for(int i =0; i<histSize;i++){
            minindexes[i] = new Minindexes(histr[i],histg[i],histb[i],i);
        }
        Arrays.sort(minindexes);
        Log.d(Name,"0:"+minindexes[0].dist()+",1:"+minindexes[histSize-1].dist());
         */
        List<Double> dataR = new ArrayList<>();
        List<Double> dataG = new ArrayList<>();
        List<Double> dataB = new ArrayList<>();
        for(int i = (int)mix(blwl[0],blwl[1],0.05); i<(int)mix(blwl[0],blwl[1],0.9);i++){
            //Minindexes minindexes1 = minindexes[i];

            dataR.add((histr[i]+0.0001)/(histr[i]+histb[i]+histr[i]+0.0001));
            dataG.add((histg[i]+0.0001)/(histr[i]+histb[i]+histr[i]+0.0001));
            dataB.add((histb[i]+0.0001)/(histr[i]+histb[i]+histr[i]+0.0001));
            //cnt+=1.f;
        }
        List<Double> res = RANSAC.perform(dataR, 2, 1500, 1, 0.2);
        rk = res.get(1).floatValue();
        res = RANSAC.perform(dataG, 2, 1500, 1, 0.2);
        gk = res.get(1).floatValue();
        res = RANSAC.perform(dataB, 2, 1500, 1, 0.2);
        bk = res.get(1).floatValue();
        /*
        for(int i = 0; i<searchMax;i++){
            rk+=
            cnt+=1.f;
        }*/
        //float rk = Utilities.linearRegressionK(Arrays.copyOfRange(histr,histr.length-1-searchMax,histr.length))+0.0001f;
        //float gk = Utilities.linearRegressionK(Arrays.copyOfRange(histg,histg.length-1-searchMax,histg.length))+0.0001f;
        //float bk = Utilities.linearRegressionK(Arrays.copyOfRange(histb,histb.length-1-searchMax,histb.length))+0.0001f;
        float[] outp = new float[]{rk+0.0001f,gk+0.0001f,bk+0.0001f};
        float mink = Math.min(Math.min(outp[0],outp[1]),outp[2]);
        outp[0]/=mink;
        outp[1]/=mink;
        outp[2]/=mink;
        //outp = Utilities.saturate(outp,whiteBalanceSaturation);
        Log.d(Name,"WBK:"+Arrays.toString(outp));
        mink = Math.min(Math.min(outp[0],outp[1]),outp[2]);
        outp[0]/=mink;
        outp[1]/=mink;
        outp[2]/=mink;
        return new float[]{outp[0],outp[1],outp[2]};
    }
    GLTexture lut;
    Bitmap lutbm;
    float analyzeIntensity = -2.0f;
    float analyzeCenter = 0.5f;
    float curveCenter = 0.5f;
    float edgesStretchShadows = 2.25f;
    float edgesStretchHighLight = 0.0f;
    int histSize = 4096;
    int blackLevelSearch = 384;
    float edgesBilateralSmooth = 3.5f;
    float edgesBilateralSmoothNight = 3.0f;
    float highLightSmoothAmplify = 2.5f;
    float shadowsSensitivity = 0.5f;
    float blackLevelSensitivity = 1.0f;
    int whiteBalanceSearch = 400;
    float whiteBalanceSaturation = 1.35f;
    float[] tonemapCoeffs = new float[]{-0.78360f / 1.0063f, 0.84690f / 1.0063f, 0.9430f / 1.0063f, 0f};
    boolean disableEqualization = false;
    boolean enableTonemap = true;
    float highlightCompress = 0.4f;
    float contrast = 0.2f;
    boolean useOldEqualization = false;
    boolean removeUnderexpose = true;
    @Override
    public void Run() {
        startT();
        disableEqualization = getTuning("DisableEqualization",disableEqualization);
        if(disableEqualization){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        highlightCompress = getTuning("HighlightCompress",highlightCompress);
        contrast = getTuning("Contrast",contrast);
        enableTonemap = getTuning("EnableTonemap",enableTonemap);
        removeUnderexpose = getTuning("RemoveUnderexpose",removeUnderexpose);
        useOldEqualization = getTuning("UseOldEqualization",useOldEqualization);
        analyzeIntensity = getTuning("AnalyzeIntensity", analyzeIntensity);
        edgesStretchShadows = getTuning("EdgesStretchShadows", edgesStretchShadows);
        edgesStretchHighLight = getTuning("EdgesStretchHighLight", edgesStretchHighLight);
        edgesBilateralSmooth = getTuning("EdgesBilateralSmooth", edgesBilateralSmooth);
        edgesBilateralSmoothNight = getTuning("EdgesBilateralSmoothNight", edgesBilateralSmoothNight);
        highLightSmoothAmplify = getTuning("HighLightSmoothAmplify", highLightSmoothAmplify);
        analyzeCenter = getTuning("AnalyzeCenter", analyzeCenter);
        curveCenter = getTuning("CurveCenter", curveCenter);
        shadowsSensitivity = getTuning("ShadowsSensitivity", shadowsSensitivity);
        histSize = getTuning("HistSize", histSize);
        blackLevelSearch = getTuning("BlackLevelSearch", blackLevelSearch);
        blackLevelSensitivity = getTuning("BlackLevelSensitivity", blackLevelSensitivity);
        whiteBalanceSearch = getTuning("WhiteBalanceSearch", whiteBalanceSearch);
        tonemapCoeffs = getTuning("TonemapCoeffs", tonemapCoeffs);
        WorkingTexture = basePipeline.getMain();
        float rmax = (float)(Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[0].second) + Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[0].first));
        float gmax = (float)(Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[1].second) + Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[1].first));
        float bmax = (float)(Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[2].second) + Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[2].first));
        Log.d("Equalization","rgb max shift:"+rmax+","+gmax+","+bmax);
        endT("Equalization Part 00");
        Histogram histParser = Analyze();
        startT();
        //Bitmap lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.lut2);
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




        float[] averageCurve = new float[histParser.hist.length];
        for(int i =0; i<averageCurve.length;i++){
            averageCurve[i] = (histParser.histr[i]+histParser.histg[i]+histParser.histb[i])/3.f;
        }
        endT("Equalization Part 1");
        startT();
        if(basePipeline.mSettings.DebugData) {
            GenerateCurveBitm(histParser.histr,histParser.histg,histParser.histb);
        }
        float max = 0.f;
        float WL = findWL(histParser.histr,histParser.histg,histParser.histb);
        float BL = findBL(histParser.histr,histParser.histg,histParser.histb);
        float[] blwl = new float[]{BL,WL};
        double compensation = averageCurve.length/WL;
        if(useOldEqualization){
            histParser.hist = bSpline(histParser.hist,blwl);
        } else
        histParser.hist = bilateralSmoothCurve(histParser.hist,blwl);


        float[] WB = new float[]{1.0f,1.0f,1.0f};// = getWB(histParser.histr,histParser.histg,histParser.hist,blwl);

        //Use kx+b prediction for curve start
        //Depurple Degreen
        float[] BLPredict = new float[3];
        float[] BLPredictShift = new float[3];
        //int maxshift = (int) (blwl[0] + blwl[1]*blackLevelSearch/4096.f);
        int maxshift = (int) mix(blwl[0],WL,blackLevelSearch/((float)histSize));
        maxshift = Math.max(maxshift,10);
        Log.d(Name,"BlSearch:"+maxshift);
        int cnt = 0;
        for(int i =5; i<maxshift;i++){
            float x = (float)(i)/histSize;
            BLPredict[0]+= histParser.histr[i]/x;
            BLPredict[1]+= histParser.histg[i]/x;
            BLPredict[2]+= histParser.histb[i]/x;
            cnt++;
        }
        BLPredict[0]/=cnt;
        BLPredict[1]/=cnt;
        BLPredict[2]/=cnt;
        cnt = 0;
        for(int i =5; i<maxshift;i++){
            float x = (float)(i)/histSize;
            BLPredictShift[0]+=histParser.histr[i]-x*BLPredict[0];
            BLPredictShift[1]+=histParser.histg[i]-x*BLPredict[1];
            BLPredictShift[2]+=histParser.histb[i]-x*BLPredict[2];
            cnt++;
        }
        BLPredictShift[0]/=cnt;
        BLPredictShift[1]/=cnt;
        BLPredictShift[2]/=cnt;

        //Saturate shift
        float avr = (BLPredictShift[0]+BLPredictShift[1]+BLPredictShift[2])/3.f;
        float saturation = 0.0f;
        BLPredictShift[0] = -(BLPredictShift[0]-avr*saturation) / (1.f-avr*saturation);
        BLPredictShift[1] = -(BLPredictShift[1]-avr*saturation) / (1.f-avr*saturation);
        BLPredictShift[2] = -(BLPredictShift[2]-avr*saturation) / (1.f-avr*saturation);

        float mins = Math.min(BLPredictShift[0],Math.min(BLPredictShift[1],BLPredictShift[2]));
        if(mins < 0.0) {
            BLPredictShift[0]-=mins;
            BLPredictShift[1]-=mins;
            BLPredictShift[2]-=mins;
        }
        if(PhotonCamera.getSettings().selectedMode != CameraMode.NIGHT) {
            float oldr = BLPredictShift[0];
            float oldb = BLPredictShift[2];
            BLPredictShift[2] = Math.min(BLPredictShift[0],BLPredictShift[2]);
            BLPredictShift[0] = BLPredictShift[2];
            BLPredictShift[0] += oldr*0.15f;
            BLPredictShift[2] += oldb*0.15f;
        }

        //Limit blacklevel to 10% of detected range
        float length  = (float) Math.sqrt(BLPredictShift[0]*BLPredictShift[0] + BLPredictShift[1]*BLPredictShift[1] + BLPredictShift[2]*BLPredictShift[2])+0.0001f;
        float length2 = Math.min(length,WL-blwl[0])/length;
        BLPredictShift[0]*=blackLevelSensitivity*length2;
        BLPredictShift[1]*=blackLevelSensitivity*length2;
        BLPredictShift[2]*=blackLevelSensitivity*length2;



        /*float[] smoothArr = bilateralSmoothCurve(averageCurve,BL,WL);
        for(int i =0; i<smoothArr.length;i++){
            float t = ((float)i)/smoothArr.length;
            float shadow = (float)i*4.f/smoothArr.length;
            shadow = Math.min(shadow,1.f);
            float high = (((float)i/smoothArr.length)-0.6f)*0.8f;
            high = Math.max(high,0.f);
            float prev = histParser.hist[i];
            averageCurve[i] = Math.min(mix(averageCurve[i],smoothArr[i],shadow),smoothArr[i]);
            averageCurve[i] = Math.max(mix(averageCurve[i],prev,high),averageCurve[i]);
            averageCurve[i] = mix(prev,averageCurve[i],Math.min(t*1.3f,0.4f) + 0.6f);
            averageCurve[i] = Math.min(averageCurve[i],1.f);
            if(max < averageCurve[i]) max = averageCurve[i];
        }
        for(int i =0; i<histParser.hist.length;i++){
            if(i<smoothArr.length){
                //histParser.hist[i] = bezierArr[i];
                histParser.hist[i] = averageCurve[i]*(1.f/max);
            } else
            histParser.hist[i] = 1.f;
        }*/
        Log.d(Name,"PredictedBLShift:"+Arrays.toString(BLPredictShift));
        Log.d(Name,"PredictedWBKoeff:"+Arrays.toString(WB));
        if(basePipeline.mSettings.DebugData)GenerateCurveBitm(histParser.histInr,histParser.histIng,histParser.histInb);


        /*float[] equalizingCurve = new float[histParser.hist.length];
        for(int i =0; i<histParser.hist.length;i++){
            equalizingCurve[i] = (float)(Math.pow(((double)i)/histParser.hist.length,eq));
        }
        if(basePipeline.mSettings.DebugData) GenerateCurveBitm(equalizingCurve);
        GLTexture equalizing = new GLTexture(histParser.hist.length,1,new GLFormat(GLFormat.DataType.FLOAT_16),
                FloatBuffer.wrap(equalizingCurve), GL_LINEAR, GL_CLAMP_TO_EDGE);
        Log.d(Name,"Equalizing:"+Arrays.toString(equalizingCurve));*/
        /*float[] shadowCurve = new float[histParser.histr.length*3];
        for(int i =0; i<shadowCurve.length;i+=3){
            shadowCurve[i] = (histParser.histr[i/3]);
            shadowCurve[i+1] = (histParser.histg[i/3]);
            shadowCurve[i+2] = (histParser.histb[i/3]);
        }*/
        double shadowW = (basePipeline.mSettings.shadows);
        double avrbr = 0.0;

        for(int i =0; i<histParser.hist.length;i++){
            float line = i/(histParser.hist.length-1.f);
            double linepi = line*Math.PI - Math.PI/2.0;
            double contrastCurve = (Math.sin(linepi) + 1.0)/2.0;
            if(removeUnderexpose) histParser.hist[i] = Math.max(histParser.hist[i],line);
            if(shadowW != 0.f) {
                if(shadowW > 0.f)
                histParser.hist[i] = (float)mix(histParser.hist[i],Math.sqrt(histParser.hist[i]),(shadowW)*shadowsSensitivity);
                else histParser.hist[i] = (float)mix(histParser.hist[i],(histParser.hist[i])*(histParser.hist[i]),-(shadowW)*shadowsSensitivity);
            }

            histParser.hist[i] = mix(histParser.hist[i],line,line*line*highlightCompress);
            histParser.hist[i] = (float) mix(histParser.hist[i],histParser.hist[i]*contrastCurve,contrast);
            avrbr+=histParser.hist[i];
        }
        avrbr/=histParser.hist.length;
        float desaturate = 0.5f/(float)avrbr;
        desaturate = Math.max(1.f,desaturate);
        desaturate*=1.0f;
        desaturate-=1.0f;
        if(basePipeline.mSettings.DebugData) GenerateCurveBitmWB(histParser.hist,BLPredictShift,new float[]{WL,WL,WL});
        GLTexture histogram = new GLTexture(histParser.hist.length,1,new GLFormat(GLFormat.DataType.FLOAT_16),
                FloatBuffer.wrap(histParser.hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        //GLTexture shadows = new GLTexture(histParser.hist.length,1,new GLFormat(GLFormat.DataType.FLOAT_16,3),
        //        FloatBuffer.wrap(shadowCurve), GL_LINEAR, GL_CLAMP_TO_EDGE);
        glProg.setDefine("BL2",BLPredictShift);
        glProg.setDefine("BR",(float)(shadowW)*shadowsSensitivity);
        File customlut = new File(FileManager.sPHOTON_TUNING_DIR,"lut.png");
        glProg.setDefine("TONEMAP",enableTonemap);
        glProg.setDefine("DESAT",desaturate);
        if(customlut.exists()){
            lutbm = BitmapFactory.decodeFile(customlut.getAbsolutePath());
            lut = new GLTexture(lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
            glProg.setDefine("LUT",true);
        }
        endT("Equalization Part 2");
        startT();
        glProg.useProgram(R.raw.equalize);
        if(lut != null) glProg.setTexture("LookupTable",lut);
        glProg.setTexture("Histogram",histogram);
        //glProg.setTexture("Shadows",shadows);
        GLTexture TonemapCoeffs = new GLTexture(new Point(256, 1),
                new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(basePipeline.mSettings.toneMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("TonemapTex",TonemapCoeffs);
        glProg.setVar("toneMapCoeffs", tonemapCoeffs);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(WorkingTexture);
        histogram.close();
        if(lutbm != null) lutbm.recycle();
        if(lut != null) lut.close();
        TonemapCoeffs.close();
        glProg.closed = true;
        endT("Equalization Part 3");
    }
}
