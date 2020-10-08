package com.eszdman.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glGetIntegerv;
import static com.eszdman.photoncamera.processing.opengl.GLCoreBlockProcessing.checkEglError;

public class GLBasePipeline implements AutoCloseable {
    public final ArrayList<Node> Nodes = new ArrayList<Node>();
    public GLInterface glint = null;
    private long timeStart;
    private static final String TAG = "BasePipeline";
    private final int[] bind = new int[1];

    public void startT() {
        timeStart = System.currentTimeMillis();
    }

    public void endT(String Name) {
        Log.d("Pipeline", "Node:" + Name + " elapsed:" + (System.currentTimeMillis() - timeStart) + " ms");
    }

    public void add(Node in) {
        if (Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size() - 1);
        in.basePipeline = this;
        Nodes.add(in);
        Log.d(TAG, "Added:" + in.Name + " Nodes size:" + Nodes.size());
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
            if (i == Nodes.size() - 1) {
                lastR();
            }
            startT();
            Nodes.get(i).Run();
            endT(Nodes.get(i).Name);
            if (i != Nodes.size() - 1) {
                glint.glProgram.drawBlocks(Nodes.get(i).GetProgTex());
                glint.glProgram.close();
            }
        }
        glint.glProcessing.drawBlocksToOutput();
        glint.glProgram.close();
        Nodes.clear();
        return glint.glProcessing.mOut;
    }

    public ByteBuffer runAllRaw() {
        lastI();
        for (int i = 0; i < Nodes.size(); i++) {
            Nodes.get(i).Compile();
            if (i == Nodes.size() - 1) {
                lastR();
            }
            startT();
            Nodes.get(i).Run();
            if (i != Nodes.size() - 1) {
                Log.d(TAG, "i:" + i + " size:" + Nodes.size());
                glint.glProgram.drawBlocks(Nodes.get(i).GetProgTex());
                glint.glProgram.close();
            }
            endT(Nodes.get(i).Name);
        }
        glint.glProgram.drawBlocks(Nodes.get(Nodes.size() - 1).GetProgTex());
        glint.glProcessing.drawBlocksToOutput();
        glint.glProgram.close();
        Nodes.clear();
        return glint.glProcessing.mOutBuffer;
    }

    @Override
    public void close() {
        if (glint.glProcessing != null) glint.glProcessing.close();
        if (glint.glContext != null) glint.glContext.close();
    }
}
