package com.eszdman.photoncamera.OpenGL.Nodes;

import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;

public class Node {
    public GLTexture WorkingTexture;
    public String Name = "Node";
    public Node previousNode;
    public int Rid;
    private long timestart;
    public void startT(){
        timestart = System.currentTimeMillis();
    }
    public void endT(){
        Log.d("Pipeline","Node:"+Name+" elapsed:"+(System.currentTimeMillis()-timestart)+ " ms");
    }
    private Node(){}
    public Node(int rid, String name){
        Rid = rid;
        Name = name;
    }
    public void Run(BasePipeline basePipeline){}
    public void Compile(){
        GLInterface.i.glprogram.useProgram(Rid);
    }
}
