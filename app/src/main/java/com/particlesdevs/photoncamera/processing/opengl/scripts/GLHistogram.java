package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Bitmap;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLBuffer;
import com.particlesdevs.photoncamera.processing.opengl.GLContext;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;

import static android.opengl.GLES31.*;

public class GLHistogram implements AutoCloseable{
    GLContext context;
    GLProg glProg;
    GLBuffer[] buffers = new GLBuffer[4];
    int histSize = 256;
    int[][] outputArr = new int[4][histSize];
    GLFormat histFormat = new GLFormat(GLFormat.DataType.UNSIGNED_32);
    public GLHistogram() {
        context = new GLContext(1,1);
        glProg = context.mProgram;
        buffers[0] = new GLBuffer(histSize,histFormat);
        buffers[1] = new GLBuffer(histSize,histFormat);
        buffers[2] = new GLBuffer(histSize,histFormat);
    }
    public int[][] Compute(Bitmap bitmap){

        GLTexture input = new GLTexture(bitmap);
        input.Bufferize();
        int resize = 2;
        int tile = 8;
        glProg.setDefine("SCALE",resize);
        glProg.setLayout(tile,tile,1);
        glProg.useProgram(R.raw.histogram,true);
        glProg.setTextureCompute("inTexture",input,false);
        glProg.setBufferCompute("histogramRed",buffers[0]);
        glProg.setBufferCompute("histogramGreen",buffers[1]);
        glProg.setBufferCompute("histogramBlue",buffers[2]);
        glProg.computeManual(input.mSize.x/(resize*tile),input.mSize.y/(resize*tile),1);


        outputArr[0] = buffers[0].readBufferIntegers();
        outputArr[1] = buffers[1].readBufferIntegers();
        outputArr[2] = buffers[2].readBufferIntegers();

        input.close();
        return outputArr;
    }

    @Override
    public void close() {
        glProg.close();
        context.close();
    }
}
