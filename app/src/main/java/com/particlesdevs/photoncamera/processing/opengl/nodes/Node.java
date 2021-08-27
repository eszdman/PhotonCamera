package com.particlesdevs.photoncamera.processing.opengl.nodes;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;

import java.util.Arrays;
import java.util.Properties;

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
    public Properties mProp;
    private final boolean loggedTuning = true;

    private Node() {
    }

    public Node(int rid, String name) {
        Rid = rid;
        Name = name;
    }

    private void tuningLog(String name, String value){
        if(loggedTuning) Log.d("Tuning",name+" = "+ value);
    }
    public boolean getTuning(String name, boolean Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Boolean.parseBoolean(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public float getTuning(String name,float Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Float.parseFloat(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public float[] getTuning(String name,float[] Default){
        String ins = Arrays.toString(Default).replace("[","").replace("]","");
        tuningLog(Name+"_"+name,ins);
        String inp = mProp.getProperty(Name+"_"+name, ins);
        String[] divided = inp.split(",");
        float[] output = new float[Default.length];
        for(int i = 0; i<divided.length;i++){
            output[i] = Float.parseFloat(divided[i]);
        }
        return output;
    }
    public double getTuning(String name,double Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Double.parseDouble(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public short getTuning(String name,short Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Short.parseShort(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
    }
    public int getTuning(String name,int Default){
        tuningLog(Name+"_"+name,String.valueOf(Default));
        return Integer.parseInt(mProp.getProperty(Name+"_"+name,String.valueOf(Default)));
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
