package com.eszdman.photoncamera.OpenGL.Nodes;

import android.graphics.Bitmap;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;
import static com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing.checkEglError;

public class BasePipeline implements AutoCloseable {
    ArrayList<Node> Nodes = new ArrayList<Node>();
    GLInterface glint = null;
    private static String TAG = "BasePipeline";
    private final int[] bind = new int[1];
    void add(Node in){
        if(Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size()-1);
        in.basePipeline = this;
        Nodes.add(in);
        Log.d(TAG,"Added:"+in.Name+"Nodes size:"+Nodes.size());
    }
    private void lasti(){
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, bind, 0);
        checkEglError("glGetIntegerv");
    }
    private void lastr(){
        glBindFramebuffer(GL_FRAMEBUFFER, bind[0]);
        checkEglError("glBindFramebuffer");
    }
    Bitmap runAll(){
        lasti();
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            if(i == Nodes.size()-1) {
                lastr();
            }
            Nodes.get(i).Run();
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
    ByteBuffer runAllRaw(){
        lasti();
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            if(i == Nodes.size()-1) {
                lastr();
            }
            Nodes.get(i).Run();
            if(i != Nodes.size()-1) {
                Log.d(TAG,"i:"+i+" size:"+Nodes.size());
                glint.glprogram.drawBlocks(Nodes.get(i).WorkingTexture);
                glint.glprogram.close();
            }
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
