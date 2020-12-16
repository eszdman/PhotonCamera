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

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class Equalization extends Node {
    public Equalization(int rid, String name) {
        super(rid, name);
    }
    private short[][] Histogram(Bitmap in) {
        short[][] brMap;
        brMap = new short[3][256];
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
    private float EqualizeF(short[][] brH){
        double avr = 0.0;
        int cnt = 0;
        for(int j =0;j<3;j++)
        for(int i =0; i<128;i++){
            avr+=brH[j][i];
            cnt+=brH[j][i];
        }
        avr/=128.0;
        cnt = 0;
        double avr2 = 0.0;
        for(int j =0;j<3;j++)
            for(int i =128; i<256;i++){
                avr2+=brH[j][i];
                if(i == 255) avr2-=brH[j][i]/3.0;
                cnt+=brH[j][i];
            }
        avr2/=128.0;
        Log.d(Name,"Avr:"+avr);
        Log.d(Name,"Avr2:"+avr2);
        return (float)(Math.min(avr/avr2,5.0));
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        WorkingTexture = basePipeline.getMain();
        GLTexture r0 = glUtils.interpolate(previousNode.WorkingTexture,new Point(previousNode.WorkingTexture.mSize.x/8,previousNode.WorkingTexture.mSize.x/8));
        GLTexture r1 = glUtils.interpolate(r0,new Point(40,40));
        //GLTexture r2 = glUtils.blursmall(r1,3,1.8);
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, 4);
        Bitmap preview = Bitmap.createBitmap(r1.mSize.x, r1.mSize.y, bitmapF.getBitmapConfig());
        preview.copyPixelsFromBuffer(glInt.glProcessing.drawBlocksToOutput(r1.mSize, bitmapF));
        EqualizeF(Histogram(preview));
        Bitmap lutbm = BitmapFactory.decodeResource(PhotonCamera.getCameraActivity().getResources(), R.drawable.lut2);
        GLTexture lutT = new GLTexture(lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);

        float eq = EqualizeF(Histogram(preview));
        eq = (2.f+eq)/(3.f);
        eq-=0.85;
        eq*=2.3;
        eq = Math.max(0.4f,eq);
        eq = Math.min(2.5f,eq);
        Log.d(Name,"Equalizek:"+eq);
        glProg.useProgram(R.raw.equalize);
        glProg.setVar("Equalize",eq);
        glProg.setTexture("LookupTable",lutT);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(WorkingTexture);
        preview.recycle();
        lutT.close();
        lutbm.recycle();
        glProg.closed = true;
    }
}
