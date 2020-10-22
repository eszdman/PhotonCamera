package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.hardware.camera2.CaptureResult;
import android.util.Log;
import android.util.Rational;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraReflectionApi;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

public class AWB extends Node {

    public AWB(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void AfterRun() {
        //previousNode.WorkingTexture.close();
    }
    @Override
    public void Compile() {}

    public GLTexture down8(GLTexture input) {
        glProg.useProgram(R.raw.gaussdown884);
        glProg.setTexture("InputBuffer", input);
        GLTexture Output = new GLTexture(input.mSize.x / 8, input.mSize.y / 8, input.mFormat, null);
        glProg.drawBlocks(Output);
        glProg.close();
        return Output;
    }

    private int SIZE = 256;

    private short[][] Histogram(Bitmap in) {
        short[][] colorsMap;
        colorsMap = new short[3][SIZE];
        int maxY = 0;
        for (int h = 0; h < in.getHeight(); h++) {
            for (int w = 0; w < in.getWidth(); w++) {
                int rgba = in.getPixel(w, h);
                int r = ((rgba) & 0xff);
                int g = ((rgba >> 8) & 0xff);
                int b = ((rgba >> 16) & 0xff);
                colorsMap[0][(int)((double)(r)*(255.0)/(r+g+b))]++;
                colorsMap[1][(int)((double)(g)*(255.0)/(r+g+b))]++;
                colorsMap[2][(int)((double)(b)*(255.0)/(r+g+b))]++;
                //colorsMap[0][r]++;
                //colorsMap[1][g]++;
                //colorsMap[2][b]++;
            }
        }
        //Find max
        for (int i = 0; i < SIZE; i++) {
            if (maxY < colorsMap[0][i]) {
                maxY = colorsMap[0][i];
            }
            if (maxY < colorsMap[1][i]) {
                maxY = colorsMap[1][i];
            }
            if (maxY < colorsMap[2][i]) {
                maxY = colorsMap[2][i];
            }
        }
        in.recycle();
        return colorsMap;
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
        short minC = 0;

        for (short i = 50; i < 170; i++) {
            for (short j = 40; j < 150; j++) {
                for (short k = 40; k < 150; k++) {
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
        if(maxmpy > 1.3 || blueVector-10 > redVector) {
            Log.d("AWB", "Use WhiteWorld factor:" + maxmpy);
            maxHistH = -1;
            short rb = redVector;
            short gb = greenVector;
            short bb = blueVector;
            for (short i = 100; i < 254; i++) {
                for (short j = 100; j < 254; j++) {
                    for (short k = 100; k < 254; k++) {
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
            /*if(bb > blueVector){
                redVector = rb;
                greenVector = gb;
                blueVector = bb;
            }*/
        }

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


    @Override
    public void Run() {
        GLTexture r0 = down8(previousNode.WorkingTexture);
        GLTexture r1 = down8(r0);
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, 4);
        Bitmap preview = Bitmap.createBitmap(r1.mSize.x, r1.mSize.y, bitmapF.getBitmapConfig());
        preview.copyPixelsFromBuffer(glInt.glProcessing.drawBlocksToOutput(r1.mSize, bitmapF));
        if(PhotonCamera.getSettings().aFDebugData) glUtils.SaveProgResult(r1.mSize,"debAWB");
        WorkingTexture = glUtils.mpy( previousNode.WorkingTexture,CCV(Histogram(preview)));
        //PatchPoint(CCVBased(Histogram(preview)));
        //WorkingTexture = previousNode.WorkingTexture;
        glProg.close();
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
