package com.eszdman.photoncamera.OpenGL;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;
import android.util.Size;
import java.nio.ByteBuffer;

import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;
import static com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing.checkEglError;

public class GLOneScript implements AutoCloseable {
    public GLTexture WorkingTexture;
    public GLOneParams glOne;
    public String Name = "Script";
    public ByteBuffer Output;
    public int Rid;
    private long timestart;
    private final int[] bind = new int[1];
    public Point size;
    public Object additionalParams;
    public GLOneScript(Point size, Bitmap output, GLFormat glFormat, int rid, String name){
        this.size = size;
        glOne = new GLOneParams(size,output,glFormat);
        Name = name;
        Rid = rid;
    }
    public void Run(){
        //glGetIntegerv(GL_FRAMEBUFFER_BINDING, bind, 0);
        checkEglError("glGetIntegerv");
        Compile();
        startT();
        //glBindFramebuffer(GL_FRAMEBUFFER, bind[0]);
        checkEglError("glBindFramebuffer");
        StartScript();
        glOne.glProc.drawBlocksToOutput();
        glOne.glprogram.close();
        endT();
        Output = glOne.glProc.mOutBuffer;
    }
    public void StartScript(){}
    public void startT(){
        timestart = System.currentTimeMillis();
    }
    public void endT(){
        Log.d("OneScript","Name:"+Name+" elapsed:"+(System.currentTimeMillis()-timestart)+ " ms");
    }
    public void Compile(){
        glOne.glprogram.useProgram(Rid);
    }

    @Override
    public void close(){
        glOne.glProc.close();
    }
}
