package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Vector;

import static com.eszdman.photoncamera.processing.ImageSaver.imageFileToSave;

public class AWB extends Node {
    GLProg glProg;
    public AWB(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Compile() {}
    public GLTexture down8(GLTexture input){
        glProg.useProgram(R.raw.gaussdown884);
        glProg.setTexture("InputBuffer",input);
        GLTexture Output = new GLTexture(input.mSize.x/8,input.mSize.y/8,input.mFormat,null);
        glProg.drawBlocks(Output);
        glProg.close();
        return Output;
    }
    private int SIZE = 256;
    private int maxY = 0;
    private short[][] Histogram(Bitmap in){
        short[][] colorsMap;
        colorsMap = new short[3][SIZE];
        maxY = 0;
        for(int h = 0; h<in.getHeight();h++){
            for(int w = 0; w<in.getWidth();w++){
                int rgba = in.getPixel(w,h);
                colorsMap[0][((rgba) & 0xff)]++;
                colorsMap[1][((rgba >>  8) & 0xff)]++;
                colorsMap[2][((rgba >>  16) & 0xff)]++;
            }
        }
        //Find max
        for(int i =0; i<SIZE;i++){
            if(maxY < colorsMap[0][i]){
                maxY = colorsMap[0][i];
            }
            if(maxY < colorsMap[1][i]){
                maxY = colorsMap[1][i];
            }
            if(maxY < colorsMap[2][i]){
                maxY = colorsMap[2][i];
            }
        }
        in.recycle();
        return colorsMap;
    }
    private double normPDF(double dist,double sigma){
        return (0.39894*Math.exp(-0.5*dist*dist/(sigma*sigma))/sigma);
    }
    //Yellow point 0xE3BFA5
    //Averaged white point 0xC2C2C2
    //Color average point 210 192 179
    //Max average point 255 233 217
    private float[] CCV(short[][] input){
        double[] vec3 = new double[3];
        for(int i =0; i<SIZE;i++){
            double x = ((double)i)/(SIZE-1);
            x-=0.5;
            double pdf = normPDF(x,0.3);
            vec3[0] += (double)(input[0][i])*pdf;
            vec3[1] += (double)(input[1][i])*pdf;
            vec3[2] += (double)(input[2][i])*pdf;
        }
        float[] output = new float[3];
        Log.v("AWB","Color correction vector:"+vec3[0]+" ,"+vec3[1]+ " ,"+ vec3[2]);
        output[0] = (float) (255/(vec3[0]));
        output[1] = (float) (243/(vec3[1]));
        output[2] = (float) (233/(vec3[2]));
        float norm = (float)Math.sqrt(output[0]*output[0]+output[1]*output[1]+output[2]*output[2]);
        output[0]/=norm;
        output[1]/=norm;
        output[2]/=norm;
        return output;
    }
    @Override
    public void Run() {
        glProg = basePipeline.glint.glprogram;
        GLTexture r0 = down8(previousNode.WorkingTexture);
        GLTexture r1 = down8(r0);
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8,4);
        Bitmap preview = Bitmap.createBitmap(r1.mSize.x,r1.mSize.y,bitmapF.getBitmapConfig());
        /*File debug = new File(imageFileToSave.getAbsolutePath()+"debug.jpg");
        FileOutputStream fOut = null;
        try {
            debug.createNewFile();
            fOut = new FileOutputStream(debug);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        preview.copyPixelsFromBuffer(basePipeline.glint.glProc.drawBlocksToOutput(r1.mSize,bitmapF));
        //preview.compress(Bitmap.CompressFormat.JPEG, 97, fOut);
        glProg.useProgram(R.raw.applyvector);
        glProg.setvar("colorvec",CCV(Histogram(preview)));
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        WorkingTexture = new GLTexture(previousNode.WorkingTexture);
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
    }
}
