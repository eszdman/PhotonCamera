package com.eszdman.photoncamera.OpenGL.Nodes;

import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;

import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;

public class Node {
    public GLTexture WorkingTexture;
    public String Name = "Node";
    public Node previousNode;
    public int Rid;
    private long timestart;
    public BasePipeline basePipeline;
    public boolean LastNode = false;
    private Node(){}
    public Node(int rid, String name){
        Rid = rid;
        Name = name;
    }
    public void Run(){}
    public void Compile(){
        basePipeline.glint.glprogram.useProgram(Rid);
    }
}
