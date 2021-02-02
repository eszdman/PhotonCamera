package com.particlesdevs.photoncamera.processing.opengl.nodes;

import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;

public class Node {
    public GLTexture WorkingTexture;
    public String Name = "Node";
    public Node previousNode;
    public int Rid;
    private long timeStart;
    public GLBasePipeline basePipeline;
    public GLInterface glInt;
    public GLUtils glUtils;
    public GLProg glProg;
    public boolean LastNode = false;

    private Node() {
    }

    public Node(int rid, String name) {
        Rid = rid;
        Name = name;
    }

    public void startT() {
        timeStart = System.currentTimeMillis();
    }

    public void endT(String name) {
        Log.d(Name, name + " elapsed:" + (System.currentTimeMillis() - timeStart) + " ms");
    }
    public void BeforeRun(){}
    public void Run() {}
    public void AfterRun(){}
    public void BeforeCompile(){}
    public void Compile() {
        basePipeline.glint.glProgram.useProgram(Rid);
    }

    public GLTexture GetProgTex() {
        return WorkingTexture;
    }
}
