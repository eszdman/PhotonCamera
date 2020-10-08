package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

public class AWB extends Node {
    GLProg glProg;

    public AWB(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Compile() {
    }

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
        short minC = 9999;

        for (short i = 20; i < 120; i++) {
            for (short j = 20; j < 120; j++) {
                for (short k = 20; k < 120; k++) {
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
        //Use WhiteWorld
        if(minC > 85){
            Log.d("AWB","Use WhiteWorld factor:"+minC);
            maxHistH = -1;
            for (short i = 190; i < 254; i++) {
                for (short j = 190; j < 254; j++) {
                    for (short k = 190; k < 254; k++) {
                        int min = (short) Math.min(Math.min(input[0][i], input[1][j]), input[2][k]);
                        //for(short c = (short) (-5); c<5; c++){
                        //min+=(short) Math.min(Math.min(input[0][Math.min(Math.max(i-c,0),SIZE-1)], input[1][Math.min(Math.max(j-c,0),SIZE-1)]), input[2][Math.min(Math.max(k-c,0),SIZE-1)]);
                        //}
                        if (min > maxHistH) {
                            maxHistH = min;
                            redVector = (short)((i));
                            greenVector = (short)((j));
                            blueVector = (short)((k));
                        }
                    }
                }
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
        if(redVector > greenVector && redVector > blueVector) output[0]*=Math.min(maxmpy,1.05);
        if(blueVector > redVector && blueVector > greenVector) output[1]*=Math.min(maxmpy,1.05);
        if(greenVector > redVector && greenVector > blueVector) output[2]*=Math.min(maxmpy,1.05);
        Log.v("AWB", "Color correction vector2:" + output[0] + " ," + output[1] + " ," + output[2]);
        return output;
    }

    @Override
    public void Run() {
        glProg = basePipeline.glint.glProgram;
        GLTexture r0 = down8(previousNode.WorkingTexture);
        GLTexture r1 = down8(r0);
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, 4);
        Bitmap preview = Bitmap.createBitmap(r1.mSize.x, r1.mSize.y, bitmapF.getBitmapConfig());
        /*File debug = new File(imageFileToSave.getAbsolutePath()+"debug.jpg");
        FileOutputStream fOut = null;
        try {
            debug.createNewFile();
            fOut = new FileOutputStream(debug);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        preview.copyPixelsFromBuffer(basePipeline.glint.glProcessing.drawBlocksToOutput(r1.mSize, bitmapF));
        //preview.compress(Bitmap.CompressFormat.JPEG, 97, fOut);
        glProg.useProgram(R.raw.applyvector);
        glProg.setVar("colorvec", CCV(Histogram(preview)));
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        WorkingTexture = new GLTexture(previousNode.WorkingTexture);
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
    }
}
