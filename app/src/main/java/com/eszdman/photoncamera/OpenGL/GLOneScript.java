package com.eszdman.photoncamera.OpenGL;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import java.nio.ByteBuffer;

import static android.opengl.GLES20.glGetIntegerv;

public class GLOneScript implements AutoCloseable {
    public GLTexture WorkingTexture;
    public GLOneParams glOne;
    public String Name;
    public ByteBuffer Output;
    public int Rid;
    private long timestart;
    public Point size;
    public Object additionalParams;
    public boolean hiddenScript = false;
    public GLOneScript(Point size, Bitmap output, GLFormat glFormat, int rid, String name){
        this.size = size;
        if(glFormat == null){
            hiddenScript = true;
            glFormat = new GLFormat(GLFormat.DataType.UNSIGNED_8,4);
            glOne = new GLOneParams(new Point(1,1),output,glFormat);
        } else {
            glOne = new GLOneParams(size,output,glFormat);
        }

        Name = name;
        Rid = rid;
    }
    public GLOneScript(Point size, GLCoreBlockProcessing glCoreBlockProcessing, int rid, String name){
        this.size = size;
        glOne = new GLOneParams(glCoreBlockProcessing);
        Name = name;
        Rid = rid;
    }
    public void StartScript() {}
    public void Run(){
        Compile();
        startT();
        StartScript();
        if(!hiddenScript) {
            glOne.glProc.drawBlocksToOutput();
        } else {
            glOne.glprogram.drawBlocks(WorkingTexture);
        }
        glOne.glprogram.close();
        endT();
        Output = glOne.glProc.mOutBuffer;
    }
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
