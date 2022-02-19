package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;


import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.rs.HistogramRs;
import com.particlesdevs.photoncamera.util.FileManager;
import com.particlesdevs.photoncamera.util.RANSAC;
import com.particlesdevs.photoncamera.util.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class AWB extends Node {

    public AWB() {
        super(0,"AWB");
    }
    @Override
    public void AfterRun() {
        //previousNode.WorkingTexture.close();
    }
    @Override
    public void Compile() {}

    private final int SIZE = 256;

    private int[][] ChromaHistogram(Bitmap in) {
        int[][] histin = HistogramRs.getHistogram(in);
        return histin;
    }
    private void GenerateCurveBitm(int[] r,int[] g,int[] b){
        Bitmap CurveEQ = Bitmap.createBitmap(256,256, Bitmap.Config.ARGB_8888);
        ((PostPipeline)basePipeline).debugData.add(CurveEQ);
        Utilities.drawArray(r,g,b,CurveEQ);
    }
    private short[][] ChromaHistogram(byte[] in) {
        short[][] colorsMap;
        colorsMap = new short[3][SIZE];
        for(int w = 0; w<in.length;w+=4){
            Log.d(Name,"inputR:"+in[w]);
            int r = ((in[w]))+128;
            int g = ((in[w + 1]))+128;
            int b = ((in[w + 2]))+128;
            int br = (r+g+b);
            if(br/3 > 1 && br/3 < 255) {
                colorsMap[0][(int) ((double) (r) * (255.0) / br)]++;
                colorsMap[1][(int) ((double) (g) * (255.0) / br)]++;
                colorsMap[2][(int) ((double) (b) * (255.0) / br)]++;
            }
        }
        return colorsMap;
    }
    private List<Double> buildCumulativeHist(short[] hist) {
        List<Double> cumulativeHist = new ArrayList<>();
        for (int i = 1; i < SIZE; i++) {
            cumulativeHist.set(i,cumulativeHist.get(i-1) + hist[i - 1]);
        }
        double max = cumulativeHist.get(SIZE);
        for (int i = 0; i < cumulativeHist.size(); i++) {
            cumulativeHist.set(i,cumulativeHist.get(i)/max);
        }
        /*float[] prevH = cumulativeHist.clone();
        cumulativeHist = new float[histSize];
        for(int i =0; i<cumulativeHist.length;i++){
            cumulativeHist[i] = getInterpolated(prevH,i*((float)prevH.length/(cumulativeHist.length)));
        }*/
        return cumulativeHist;
    }
    private List<Double> buildCumulativeHist(int[] hist) {
        List<Double> cumulativeHist = new ArrayList<>();
        cumulativeHist.add(0.0);
        for (int i = 1; i < SIZE; i++) {
            cumulativeHist.add(cumulativeHist.get(i-1) + hist[i - 1]);
        }
        double max = cumulativeHist.get(SIZE-1);
        for (int i = 0; i < cumulativeHist.size(); i++) {
            cumulativeHist.set(i,cumulativeHist.get(i)/max);
        }
        /*float[] prevH = cumulativeHist.clone();
        cumulativeHist = new float[histSize];
        for(int i =0; i<cumulativeHist.length;i++){
            cumulativeHist[i] = getInterpolated(prevH,i*((float)prevH.length/(cumulativeHist.length)));
        }*/
        return cumulativeHist;
    }
    private short[][] Histogram(Bitmap in) {
        short[][] brMap;
        brMap = new short[3][SIZE];
        for (int h = 0; h < in.getHeight(); h++) {
            for (int w = 0; w < in.getWidth(); w++) {
                int rgba = in.getPixel(w, h);
                int r = ((rgba) & 0xff);
                int g = ((rgba >> 8) & 0xff);
                int b = ((rgba >> 16) & 0xff);
                brMap[0][r]++;
                brMap[1][g]++;
                brMap[2][b]++;
            }
        }
        return brMap;
    }
    private float[] CCVRANSAC(int[][] input) {
        int maxHistH = -1;
        float redVector = 0;
        float greenVector = 0;
        float blueVector = 0;
        double maxmpy = 0;
        //short minC = 0;
        List<Double> RC = buildCumulativeHist(input[0]);
        List<Double> GC = buildCumulativeHist(input[0]);
        List<Double> BC = buildCumulativeHist(input[0]);

        List<Double> res = RANSAC.perform(RC, 2, 700, 1, 0.2);
        redVector = res.get(0).floatValue();
        res = RANSAC.perform(GC, 2, 700, 1, 0.2);
        greenVector = res.get(0).floatValue();
        res = RANSAC.perform(BC, 2, 700, 1, 0.2);
        blueVector = res.get(0).floatValue();

        float mean = (float) (redVector + greenVector + blueVector) / 3;
        float[] output = new float[3];
        output[0] = blueVector / mean;
        output[1] = greenVector / mean;
        output[2] = redVector / mean;
        output[0] = 1.f/output[0];
        output[1] = 1.f/output[1];
        output[2] = 1.f/output[2];
        mean = (float) (output[0] + output[1] + output[2]) / 3;
        output[0]/= mean;
        output[1]/= mean;
        output[2]/= mean;

        //if(redVector > greenVector && redVector > blueVector) output[0]*=Math.min(maxmpy,1.05);
        //if(blueVector > redVector && blueVector > greenVector) output[1]*=Math.min(maxmpy,1.05);
        //if(greenVector > redVector && greenVector > blueVector) output[2]*=Math.min(maxmpy,1.05);
        Log.v("AWB", "Color correction vector:" + output[0] + " ," + output[1] + " ," + output[2]);
        return output;
    }
    //Yellow point 0xE3BFA5
    //Averaged white point 0xC2C2C2
    //Color average point 210 192 179
    //Max average point 255 233 217
    private float[] CCV(int[][] input) {
        int maxHistH = -1;
        short redVector = 0;
        short greenVector = 0;
        short blueVector = 0;
        double maxmpy = 0;
        //short minC = 0;

        for (short i = (short)startR; i < endR; i++) {
            for (short j = (short)startG; j < endG; j++) {
                for (short k = (short)startB; k < endB; k++) {
                    //for (short i = 20; i < 200; i++) {
                    //    for (short j = 20; j < 200; j++) {
                    //        for (short k = 20; k < 200; k++) {
                    int min = (short) Math.min(Math.min(input[0][i], input[1][j]), input[2][k]);
                    if (min > maxHistH) {
                        maxHistH = min;
                        redVector = i;
                        greenVector = j;
                        blueVector = k;
                        //maxmpy = (double)Math.max(Math.max(redVector,greenVector),blueVector)/Math.min(Math.min(redVector,greenVector),blueVector);
                        //minC = (short)Math.max(Math.max(redVector,greenVector),blueVector);
                    }
                }
            }
        }
        Log.v("AWB", "Color correction vector original:" + redVector + " ," + greenVector + " ," + blueVector);
        //Use WhiteWorld
        /*
        if(maxmpy > 1.3 || blueVector-10 > redVector) {
            Log.d("AWB", "Use WhiteWorld factor:" + maxmpy);
            maxHistH = -1;
            short rb = redVector;
            short gb = greenVector;
            short bb = blueVector;
            for (short i = 160; i < 254; i++) {
                for (short j = 160; j < 254; j++) {
                    for (short k = 160; k < 254; k++) {
                        int min = (short) Math.min(Math.min(input[0][i], input[1][j]), input[2][k]);
                        //for(short c = (short) (-5); c<5; c++){
                        //min+=(short) Math.min(Math.min(input[0][Math.min(Math.max(i-c,0),SIZE-1)], input[1][Math.min(Math.max(j-c,0),SIZE-1)]), input[2][Math.min(Math.max(k-c,0),SIZE-1)]);
                        //}
                        if (min > maxHistH) {
                            maxHistH = min;
                            redVector = (short) ((i));
                            greenVector = (short) ((j));
                            blueVector = (short) ((k));
                        }
                    }
                }
            }
            Log.v("AWB", "Color correction vector original2:" + redVector + " ," + greenVector + " ," + blueVector);
            if(bb > blueVector){
                redVector = rb;
                greenVector = gb;
                blueVector = bb;
            }
        }*/

        float mean = (float) (redVector + greenVector + blueVector) / 3;
        float[] output = new float[3];
        output[0] = blueVector / mean;
        output[1] = greenVector / mean;
        output[2] = redVector / mean;
        float max = Math.max(output[0],Math.max(output[1],output[2]));
        output[0]/=max;
        output[1]/=max;
        output[2]/=max;
        //if(redVector > greenVector && redVector > blueVector) output[0]*=Math.min(maxmpy,1.05);
        //if(blueVector > redVector && blueVector > greenVector) output[1]*=Math.min(maxmpy,1.05);
        //if(greenVector > redVector && greenVector > blueVector) output[2]*=Math.min(maxmpy,1.05);
        Log.v("AWB", "Color correction vector:" + output[0] + " ," + output[1] + " ," + output[2]);
        return output;
    }

    private float[] CCVBased(short[][] input) {
        int maxHistH = -1;
        short redVector = 0;
        short greenVector = 0;
        short blueVector = 0;
        double maxmpy = 0;
        short minC = 0;
        Parameters parameters = PhotonCamera.getParameters();
        short[]starts = new short[3];
        short[]ends = new short[3];
        for(int i = 0;i<starts.length;i++){
            int mpy = 1;
            int add = 0;
            switch (i){
                case 0:
                    mpy = 120;
                    add=20;
                    break;
                case 1:
                    mpy = 120;
                    break;
                case 2:
                    mpy = 120;
                    add=-5;
                    break;

            }
            starts[i] = (short) Math.max(parameters.whitePoint[i]*mpy - 30 + add,50);
            ends[i] = (short) Math.min(parameters.whitePoint[i]*mpy + 100 + add,250);
            /*if(i == 2) {
                starts[i] = (short) Math.max(parameters.whitePoint[i]*188 - 90,30);
                ends[i] = (short) Math.min(parameters.whitePoint[i]*188 + 40,250);
            }*/
        }
        Log.d(Name,"WP start:"+starts[0]+" WP end:"+ends[0]);
        Log.d(Name,"WP start:"+starts[1]+" WP end:"+ends[1]);
        Log.d(Name,"WP start:"+starts[2]+" WP end:"+ends[2]);
        for (short i = starts[2]; i < ends[2]; i++) {
            for (short j = starts[1]; j < ends[1]; j++) {
                for (short k = starts[0]; k < ends[0]; k++) {
                    //for (short i = 20; i < 200; i++) {
                    //    for (short j = 20; j < 200; j++) {
                    //        for (short k = 20; k < 200; k++) {
                    int min = (short) Math.min(Math.min(input[0][i], input[1][j]), input[2][k]);
                    if (min > maxHistH) {
                        maxHistH = min;
                        redVector = i;
                        greenVector = j;
                        blueVector = k;
                        maxmpy = (double)Math.max(Math.max(redVector,greenVector),blueVector)/Math.min(Math.min(redVector,greenVector),blueVector);
                        minC = (short)Math.max(Math.max(redVector,greenVector),blueVector);
                    }
                }
            }
        }
        Log.v("AWB", "Color correction vector original:" + redVector + " ," + greenVector + " ," + blueVector);
        //Use WhiteWorld

        float mean = (float) (redVector + greenVector + blueVector) / 3;
        float[] output = new float[3];
        output[0] = blueVector / mean;
        output[1] = greenVector / mean;
        output[2] = redVector / mean;
        output[0] = 1.f/output[0];
        output[1] = 1.f/output[1];
        output[2] = 1.f/output[2];
        float max = Math.max(output[0],Math.max(output[1],output[2]));
        output[0]/=max;
        output[1]/=max;
        output[2]/=max;
        //if(redVector > greenVector && redVector > blueVector) output[0]*=Math.min(maxmpy,1.05);
        //if(blueVector > redVector && blueVector > greenVector) output[1]*=Math.min(maxmpy,1.05);
        //if(greenVector > redVector && greenVector > blueVector) output[2]*=Math.min(maxmpy,1.05);
        Log.v("AWB", "Color correction vector:" + output[0] + " ," + output[1] + " ," + output[2]);
        return output;
    }
    private float[] CCVAEC(short[][] brH,float[] CCV){
        double avr = 0.0;
        for(int k =0; k<3;k++)
            for(int i =70; i<SIZE;i++) {
                avr = (avr + brH[k][i]) / 2.0;
            }
        avr/=SIZE/4.0f;
        int ind80 = 204;
        int indo = -1;
        int indmax = -1;
        double tmax = 1000.0;
        for(int i = 253;i>=128;i--){
            double br = (brH[0][i]+brH[1][i]+brH[2][i])/3.0;
            Log.d(Name,"AEC br:"+br);
            if(br > avr){
                indo = i;
                break;
            }
            if(br > tmax){
                tmax = br;
                indmax = i;
            }
        }
        Log.d(Name,"AEC avr:"+avr);
        Log.d(Name,"AEC ind:"+indo);
        if(indo > ind80) return CCV;
        float corr = ((float)ind80)/indo;
        if(indo == -1) corr = ((float)ind80)/indmax;
        CCV[0]*=(float)(corr);
        CCV[1]*=(float)(corr);
        CCV[2]*=(float)(corr);
        return CCV;
    }
    int startR = 40;
    int startG = 40;
    int startB = 40;
    int endR = 255;
    int endG = 255;
    int endB = 255;
    int blur = 1;
    boolean enableAWB = false;
    @Override
    public void Run() {
        enableAWB = getTuning("EnableAWB",enableAWB);
        if(!enableAWB){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        startR = getTuning("StartR",startR);
        startG = getTuning("StartG",startG);
        startB = getTuning("StartB",startB);
        endR = getTuning("EndR",endR);
        endG = getTuning("EndG",endG);
        endB = getTuning("EndB",endB);
        //GLTexture r1 = glUtils.medianpatch(previousNode.WorkingTexture,new Point(400,300));
        GLTexture r1 = glUtils.medianDown(previousNode.WorkingTexture,5);
        //GLTexture r1 = glUtils.patch(r0,new Point(80,80));
        //GLTexture r2 = glUtils.blursmall(r1,3,1.8);
        /*Bitmap preview = Bitmap.createBitmap(r1.mSize.x, r1.mSize.y, bitmapF.getBitmapConfig());
        preview.copyPixelsFromBuffer(glInt.glProcessing.drawBlocksToOutput(r1.mSize, bitmapF));
        if(PhotonCamera.getSettings().DebugData) glUtils.SaveProgResult(r1.mSize,"debAWB");
        r0.close();
        r1.close();*/
        File awblut = new File(FileManager.sPHOTON_TUNING_DIR,"awb_lut.png");
        Bitmap awb_lutbm;
        GLTexture awb_lut = null;
        if(awblut.exists()){
            awb_lutbm = BitmapFactory.decodeFile(awblut.getAbsolutePath());
            awb_lut = new GLTexture(awb_lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
            glProg.setDefine("LUT",true);
        }
        glProg.useProgram(R.raw.awbgetchroma);
        glProg.setTexture("InputBuffer",r1);
        if(awb_lut != null) glProg.setTexture("LookupTable",awb_lut);
        glProg.drawBlocks(basePipeline.main3,r1.mSize);
        Bitmap preview = glUtils.GenerateBitmap(r1.mSize);
        //r0.close();
        int[][] ChromaHist = ChromaHistogram(preview);
        r1.close();
        /*int[][] temp = new int[3][];
        temp[0] = ChromaHist[2];
        temp[1] = ChromaHist[1];
        temp[2] = ChromaHist[0];
        ChromaHist[0] = temp[0];
        ChromaHist[1] = temp[1];
        ChromaHist[2] = temp[2];*/
        //Remove clip
        /*
        ChromaHist[0][255] = 0;
        ChromaHist[1][255] = 0;
        ChromaHist[2][255] = 0;
        ChromaHist[0][0] = 0;
        ChromaHist[1][0] = 0;
        ChromaHist[2][0] = 0;*/
        /*for(int j = 0;j<3;j++)
        for(int i =blur; i<255-blur;i++){
            ChromaHist[j][i] = (int)((ChromaHist[j][i-1]*0.5f+ChromaHist[j][i]*1.2f+ChromaHist[j][i+1]*0.5f)/(0.5f+1.2f+0.5f));
        }*/
        if(basePipeline.mSettings.DebugData) {
            GenerateCurveBitm(ChromaHist[0],ChromaHist[1],ChromaHist[2]);
        }
        float[] CCV = CCV(ChromaHist);

        //CCV = CCVAEC(Histogram(preview),CCV);
        //preview.recycle();

        //WorkingTexture = glUtils.mpy(previousNode.WorkingTexture,CCV,basePipeline.getMain());
        PatchPoint(CCV);
        WorkingTexture = previousNode.WorkingTexture;
        glProg.closed = true;
        preview.recycle();
        if(awb_lut != null) awb_lut.close();
    }
    private void PatchPoint(float[] ccv){
        Parameters parameters = PhotonCamera.getParameters();
        //Rational[] neutral = new Rational[3];
        for(int i =0; i<3;i++)
            Log.d(Name,"Before Patch:"+ PhotonCamera.getParameters().whitePoint[i]);
        //float mpy = ccv[1];
        //neutral[0] = new Rational((int)(mpy*1.f/ccv[0])*1024,1024);
        //neutral[1] = new Rational((int)(mpy*1.f/ccv[1])*1024,1024);
        //neutral[2] = new Rational((int)(mpy*1.f/ccv[2])*1024,1024);
        parameters.customNeutral = new float[ccv.length];
        for(int i =0;i<ccv.length;i++) parameters.customNeutral[i] = ccv[i];
        parameters.ReCalcColor(true, CaptureController.mCaptureResult);
    }
}