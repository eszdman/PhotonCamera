package com.eszdman.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.glBindAttribLocation;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;
import static com.eszdman.photoncamera.processing.opengl.GLCoreBlockProcessing.checkEglError;

public class GLBasePipeline implements AutoCloseable {
    public final ArrayList<Node> Nodes = new ArrayList<>();
    public GLInterface glint = null;
    private long timeStart;
    private static final String TAG = "BasePipeline";
    private final int[] bind = new int[1];
    public GLTexture main1,main2,main3;
    private String currentProg;
    public int texnum = 0;

    public GLTexture getMain(){
        if(texnum == 1) {
            texnum = 2;
            return main2;
        }
        if(texnum != 1) {
            texnum = 1;
            return main1;
        }
        return main1;
    }

    /*public GLTexture newTexture(GLTexture in) {
        return new GLTexture(in.mSize,in.mFormat,null,in.filter,in.wrap);
    }
    public GLTexture newTexture(int sizeX, int sizeY, GLFormat glFormat, Buffer pixels) {
        return new GLTexture(new Point(sizeX, sizeY), new GLFormat(glFormat), pixels, GL_NEAREST, GL_CLAMP_TO_EDGE);
    }
    public GLTexture newTexture(int sizeX, int sizeY, GLFormat glFormat, Buffer pixels,int textureFilter, int textureWrapper) {
        return new GLTexture(new Point(sizeX, sizeY), new GLFormat(glFormat), pixels, textureFilter, textureWrapper);
    }
    public GLTexture newTexture(Point size, GLFormat glFormat, Buffer pixels) {
        return new GLTexture(new Point(size), new GLFormat(glFormat), pixels, GL_NEAREST, GL_CLAMP_TO_EDGE);
    }
    public GLTexture newTexture(int sizeX, int sizeY, GLFormat glFormat) {
        return new GLTexture(new Point(sizeX, sizeY), new GLFormat(glFormat), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
    }
    public GLTexture newTexture(int sizeX, int sizeY, GLFormat glFormat,int textureFilter, int textureWrapper) {
        return new GLTexture(new Point(sizeX, sizeY), new GLFormat(glFormat), null, textureFilter, textureWrapper);
    }
    public GLTexture newTexture(Point size, GLFormat glFormat) {
        return new GLTexture(new Point(size), new GLFormat(glFormat), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
    }*/
    public void startT() {
        timeStart = System.currentTimeMillis();
    }

    public void endT(String Name) {
        Log.d("Pipeline", "Node:" + Name + " elapsed:" + (System.currentTimeMillis() - timeStart) + " ms");
    }

    public void add(Node in) {
        if (Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size() - 1);
        in.basePipeline = this;
        in.glInt = glint;
        in.glUtils = glint.glUtils;
        in.glProg = glint.glProgram;
        Nodes.add(in);
    }

    private void lastI() {
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, bind, 0);
        checkEglError("glGetIntegerv");
    }

    private void lastR() {
        glBindFramebuffer(GL_FRAMEBUFFER, bind[0]);
        checkEglError("glBindFramebuffer");
    }

    public Bitmap runAll() {
        lastI();
        for (int i = 0; i < Nodes.size(); i++) {
            Nodes.get(i).Compile();
            Nodes.get(i).BeforeRun();
            if (i == Nodes.size() - 1) {
                lastR();
            }
            startT();
            Nodes.get(i).Run();
            endT(Nodes.get(i).Name);
            if (i != Nodes.size() - 1) {
                if(!glint.glProgram.closed) {
                    glint.glProgram.drawBlocks(Nodes.get(i).GetProgTex());
                    glint.glProgram.closed = true;
                }
            }
            Nodes.get(i).AfterRun();
        }
        if(texnum == 1){
            if (main2 != null) main2.close();
        }else {
            if (main1 != null) main1.close();
        }
        glint.glProcessing.drawBlocksToOutput();
        if(texnum == 1){
            if (main1 != null) main1.close();
        }else {
            if (main2 != null) main2.close();
        }
        if (main3 != null) main3.close();
        glint.glProgram.close();
        Nodes.clear();
        return glint.glProcessing.mOut;
    }

    public ByteBuffer runAllRaw() {
        lastI();
        for (int i = 0; i < Nodes.size(); i++) {
            Nodes.get(i).Compile();
            Nodes.get(i).BeforeRun();
            if (i == Nodes.size() - 1) {
                lastR();
            }
            startT();
            Nodes.get(i).Run();
            if (i != Nodes.size() - 1) {
                Log.d(TAG, "i:" + i + " size:" + Nodes.size());
                if(!glint.glProgram.closed) {
                    glint.glProgram.drawBlocks(Nodes.get(i).GetProgTex());
                    glint.glProgram.closed = true;
                }
            }
            Nodes.get(i).AfterRun();
            endT(Nodes.get(i).Name);
        }
        glint.glProgram.drawBlocks(Nodes.get(Nodes.size() - 1).GetProgTex());
        if(texnum == 1){
            if (main2 != null) main2.close();
        }else {
            if (main1 != null) main1.close();
        }
        glint.glProcessing.drawBlocksToOutput();
        if(texnum == 1){
            if (main1 != null) main1.close();
        }else {
            if (main2 != null) main2.close();
        }
        glint.glProgram.close();
        Nodes.clear();
        return glint.glProcessing.mOutBuffer;
    }

    @Override
    public void close() {
        if (glint.glProcessing != null) glint.glProcessing.close();
        if (glint.glContext != null) glint.glContext.close();
        if (glint.glProgram != null) glint.glProgram.close();
    }
}
