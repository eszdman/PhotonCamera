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
        double[][] vec3 = new double[3][3];
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < SIZE; i++) {
                double x = ((double) i) / (SIZE - 1);
                x -= 0.33 * j + 0.15;
                double pdf = normPDF(x);
                vec3[j][0] += (double) (input[0][i]) * pdf;
                vec3[j][1] += (double) (input[1][i]) * pdf;
                vec3[j][2] += (double) (input[2][i]) * pdf;
            }
            vec3[j][0] = 250 / vec3[j][0];
            vec3[j][1] = 255 / vec3[j][1];
            vec3[j][2] = 247 / vec3[j][2];
            double E = 1. / vec3[j][1];
            vec3[j][0] *= E;
            vec3[j][1] *= E;
            vec3[j][2] *= E;
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 2; k++) {
                    if (vec3[i][k + 1] < vec3[i][k]) {
                        double t = vec3[k + 1][i];
                        vec3[k + 1][i] = vec3[k][i];
                        vec3[k][i] = t;
                    }
                }
            }
        }
        float[] output = new float[3];
        //Log.v("AWB","Color correction vector:"+vec3[1][0]+" ,"+vec3[1][1]+ " ,"+ vec3[1][2]);
        /*output[0] = (float) (250/(vec3[1][0]));
        output[1] = (float) (255/(vec3[1][1]));
        output[2] = (float) (247/(vec3[1][2]));
        float norm = (float)Math.sqrt(output[0]*output[0]+output[1]*output[1]+output[2]*output[2]);
        float E = 1.f/output[1];
        output[0]*=E;
        output[1]*=E;
        output[2]*=E;*/
        output[0] = (float) vec3[1][0];
        output[1] = (float) vec3[1][1];
        output[2] = (float) vec3[1][2];
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
