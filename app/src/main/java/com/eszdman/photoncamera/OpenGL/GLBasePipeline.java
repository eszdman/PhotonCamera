package com.eszdman.photoncamera.OpenGL;

import android.graphics.Bitmap;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.Nodes.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;
import static com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing.checkEglError;

public class GLBasePipeline implements AutoCloseable {
    public ArrayList<Node> Nodes = new ArrayList<Node>();
    public GLInterface glint = null;
    private long timestart;
    private static String TAG = "BasePipeline";
    private final int[] bind = new int[1];
    public void startT(){
        timestart = System.currentTimeMillis();
    }
    public void endT(String Name){
        Log.d("Pipeline","Node:"+Name+" elapsed:"+(System.currentTimeMillis()-timestart)+ " ms");
    }
    public void add(Node in){
        if(Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size()-1);
        in.basePipeline = this;
        Nodes.add(in);
        Log.d(TAG,"Added:"+in.Name+" Nodes size:"+Nodes.size());
    }
    private void lasti(){
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, bind, 0);
        checkEglError("glGetIntegerv");
    }
    private void lastr(){
        glBindFramebuffer(GL_FRAMEBUFFER, bind[0]);
        checkEglError("glBindFramebuffer");
    }
    public Bitmap runAll(){
        lasti();
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            if(i == Nodes.size()-1) {
                lastr();
            }
            startT();
            Nodes.get(i).Run();
            endT(Nodes.get(i).Name);
            if(i != Nodes.size()-1) {
                glint.glprogram.drawBlocks(Nodes.get(i).WorkingTexture);
                glint.glprogram.close();
            }
        }
        glint.glProc.drawBlocksToOutput();
        glint.glprogram.close();
        Nodes.clear();
        return glint.glProc.mOut;
    }
    public ByteBuffer runAllRaw(){
        lasti();
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            if(i == Nodes.size()-1) {
                lastr();
            }
            startT();
            Nodes.get(i).Run();
            if(i != Nodes.size()-1) {
                Log.d(TAG,"i:"+i+" size:"+Nodes.size());
                glint.glprogram.drawBlocks(Nodes.get(i).WorkingTexture);
                glint.glprogram.close();
            }
            endT(Nodes.get(i).Name);
        }
        glint.glProc.drawBlocksToOutput();
        glint.glprogram.close();
        Nodes.clear();
        return glint.glProc.mOutBuffer;
    }

    @Override
    public void close() {
        glint.glProc.close();
        glint.inputRaw.clear();
    }
}
