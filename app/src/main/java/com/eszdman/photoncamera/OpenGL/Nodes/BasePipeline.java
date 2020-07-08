package com.eszdman.photoncamera.OpenGL.Nodes;

import android.graphics.Bitmap;
import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;

public class BasePipeline implements AutoCloseable {
    ArrayList<Node> Nodes = new ArrayList<Node>();
    private final int[] bind = new int[1];
    void add(Node in){
        if(Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size()-1);
        Nodes.add(in);
    }
    private void lasti(){
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, bind, 0);
    }
    private void lastr(){
        glBindFramebuffer(GL_FRAMEBUFFER, bind[0]);
    }
    Bitmap runAll(){
        lasti();
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            if(i == Nodes.size()-1) {
                lastr();
            }
            Nodes.get(i).Run(this);
            if(i != Nodes.size()-1) {
                GLInterface.i.glprogram.drawBlocks(Nodes.get(i).WorkingTexture);
            }
        }
        GLInterface.i.glProc.drawBlocksToOutput();
        return GLInterface.i.glProc.mOut;
    }
    ByteBuffer runAllRaw(){
        lasti();
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            if(i == Nodes.size()-1) {
                lastr();
            }
            Nodes.get(i).Run(this);
            if(i != Nodes.size()-1) {
                GLInterface.i.glprogram.drawBlocks(Nodes.get(i).WorkingTexture);
            }
        }
        lastr();
        GLInterface.i.glProc.drawBlocksToOutput();
        return GLInterface.i.glProc.mOutBuffer;
    }

    @Override
    public void close() throws Exception {
        GLInterface.i.glProc.close();
    }
}
