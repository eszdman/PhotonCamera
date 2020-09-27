package com.eszdman.photoncamera.processing.opengl.nodes;

import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLBasePipeline;
import com.eszdman.photoncamera.processing.opengl.GLTexture;

public class Node {
    public GLTexture WorkingTexture;
    public String Name = "Node";
    public Node previousNode;
    public int Rid;
    private long timestart;
    public GLBasePipeline basePipeline;
    public boolean LastNode = false;
    private Node(){}
    public Node(int rid, String name){
        Rid = rid;
        Name = name;
    }
    public void startT(){
        timestart = System.currentTimeMillis();
    }
    public void endT(String name){
        Log.d(Name,name+" elapsed:"+(System.currentTimeMillis()-timestart)+" ms");
    }
    public void Run(){}
    public void Compile(){
        basePipeline.glint.glprogram.useProgram(Rid);
    }
    public GLTexture GetProgTex(){
        return WorkingTexture;
    }
}
