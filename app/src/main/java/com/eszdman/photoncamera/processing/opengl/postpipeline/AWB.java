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
                colorsMap[0][((rgba) & 0xff)]++;
                colorsMap[1][((rgba >> 8) & 0xff)]++;
                colorsMap[2][((rgba >> 16) & 0xff)]++;
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

    private double normPDF(double dist) {
        return (0.39894 * Math.exp(-0.5 * dist * dist / (0.3 * 0.3)) / 0.3);
    }

    //Yellow point 0xE3BFA5
    //Averaged white point 0xC2C2C2
    //Color average point 210 192 179
    //Max average point 255 233 217
    private float[] CCV(short[][] input) {
        short maxRed = -1;
        short maxGreen = -1;
        short maxBlue = -1;
        short maxRedMean = 0;
        short maxGreenMean = 0;
        short maxBlueMean = 0;

        short maxCount = -1;
        for (short i = 0; i < 255; i++) {
            maxRedMean += input[0][i];
            if (input[0][i] > maxCount) {
                maxCount = input[0][i];
                maxRed = i;
            }
        }
        maxRedMean /= 255;
        maxCount = -1;
        for (short i = 0; i < 255; i++) {
            maxGreenMean += input[0][i];
            if (input[1][i] > maxCount) {
                maxCount = input[1][i];
                maxGreen = i;
            }
        }
        maxGreenMean /= 255;
        maxCount = -1;
        for (short i = 0; i < 255; i++) {
            maxBlueMean += input[0][i];
            if (input[2][i] > maxCount) {
                maxCount = input[2][i];
                maxBlue = i;
            }
        }
        maxBlueMean /= 255;

        float[] output = new float[3];

        float maxMean = (float) (maxRed + maxGreen + maxBlue) / 3;
        float mean = (float) ((maxRed - maxRedMean) + (maxGreen - maxGreenMean) + (maxBlue - maxBlueMean)) / 3;

//        output[0] = maxRed / mean - maxRedMean / mean;
//        output[1] = maxGreen / mean - maxGreenMean / mean;
//        output[2] = maxBlue / mean - maxBlueMean / mean;
        output[0] = (maxRed - maxRedMean) / mean;
        output[1] = (maxGreen - maxGreenMean) / mean;
        output[2] = (maxBlue - maxBlueMean) / mean;
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
