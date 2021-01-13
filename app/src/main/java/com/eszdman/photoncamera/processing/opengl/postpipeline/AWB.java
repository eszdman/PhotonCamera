package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

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

    private short[][] ChromaHistogram(Bitmap in) {
        short[][] colorsMap;
        colorsMap = new short[3][SIZE];
        for (int h = 0; h < in.getHeight(); h++) {
            for (int w = 0; w < in.getWidth(); w++) {
                int rgba = in.getPixel(w, h);
                int r = ((rgba) & 0xff);
                int g = ((rgba >> 8) & 0xff);
                int b = ((rgba >> 16) & 0xff);
                int br = (r+g+b);
                if(br/3 > 30 && br/3 < 250) {
                    colorsMap[0][(int) ((double) (r) * (255.0) / br)]++;
                    colorsMap[1][(int) ((double) (g) * (255.0) / br)]++;
                    colorsMap[2][(int) ((double) (b) * (255.0) / br)]++;
                }
            }
        }
        return colorsMap;
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

    //Yellow point 0xE3BFA5
    //Averaged white point 0xC2C2C2
    //Color average point 210 192 179
    //Max average point 255 233 217
    private float[] CCV(short[][] input) {
        int maxHistH = -1;
        short redVector = 0;
        short greenVector = 0;
        short blueVector = 0;
        double maxmpy = 0;
        //short minC = 0;

        for (short i = 50; i < 120; i++) {
            for (short j = 40; j < 120; j++) {
                for (short k = 40; k < 120; k++) {
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
                        //minC = (short)Math.max(Math.max(redVector,greenVector),blueVector);
                    }
                }
            }
        }
        Log.v("AWB", "Color correction vector original:" + redVector + " ," + greenVector + " ," + blueVector);
        //Use WhiteWorld
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
        }

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
    private float[] CCVAccel(short[][] input) {
        final int[] maxHistH = {-1};
        final short[] redVector = {0};
        final short[] greenVector = {0};
        final short[] blueVector = {0};
        final double[] maxmpy = {0};
        new Kernel() {
            @Override
            public void run() {
            short i = (short) (getGlobalId(0)+40);
            short j = (short) (getGlobalId(1)+40);
            short k = (short) (getGlobalId(2)+40);
            short min = (short) Math.min(Math.min(input[0][i], input[1][j]), input[2][k]);
            float mpy = (i+j+k)*1.5f/(3.f*240.f);
            if (min*(mpy+0.5f) > maxHistH[0]) {
                maxHistH[0] = min;
                redVector[0] = i;
                greenVector[0] = j;
                blueVector[0] = k;
                maxmpy[0] = (double)Math.max(Math.max(redVector[0], greenVector[0]), blueVector[0])/Math.min(Math.min(redVector[0], greenVector[0]), blueVector[0]);
                }
            }
        }.execute(Range.create3D((240-40),(240-40),(240-40)));
        Log.v("AWB", "Color correction vector original:" + redVector[0] + " ," + greenVector[0] + " ," + blueVector[0]);

        float mean = (float) (redVector[0] + greenVector[0] + blueVector[0]) / 3;
        if(mean <= greenVector[0])
        mean = (float)(greenVector[0]);
        float[] output = new float[3];
        output[0] = blueVector[0] / mean;
        output[1] = greenVector[0] / mean;
        output[2] = redVector[0] / mean;
        output[0] = 1.f/output[0];
        output[1] = 1.f/output[1];
        output[2] = 1.f/output[2];
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

    @Override
    public void Run() {
        GLTexture r0 = glUtils.interpolate(previousNode.WorkingTexture,new Point(previousNode.WorkingTexture.mSize.x/8,previousNode.WorkingTexture.mSize.x/8));
        GLTexture r1 = glUtils.patch(r0,new Point(40,40));
        //GLTexture r2 = glUtils.blursmall(r1,3,1.8);
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, 4);
        Bitmap preview = Bitmap.createBitmap(r1.mSize.x, r1.mSize.y, bitmapF.getBitmapConfig());
        preview.copyPixelsFromBuffer(glInt.glProcessing.drawBlocksToOutput(r1.mSize, bitmapF));

        if(PhotonCamera.getSettings().DebugData) glUtils.SaveProgResult(r1.mSize,"debAWB");
        r0.close();
        r1.close();
        basePipeline.texnum = 1;
        float[] CCV = CCVAccel(ChromaHistogram(preview));
        //CCV = CCVAEC(Histogram(preview),CCV);
        preview.recycle();
        WorkingTexture = glUtils.mpy(previousNode.WorkingTexture,CCV,basePipeline.getMain());
        //PatchPoint(CCVBased(Histogram(preview)));
        //WorkingTexture = previousNode.WorkingTexture;
        glProg.closed = true;
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
        for(int i =0;i<ccv.length;i++) parameters.customNeutral[i] = 1.f/ccv[i];
        parameters.ReCalcColor(true);
    }
}
