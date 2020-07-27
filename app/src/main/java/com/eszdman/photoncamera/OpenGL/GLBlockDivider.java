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

public class GLBlockDivider {
    private final int mSize;
    private final int mBlock;
    private int mPassed;

    public GLBlockDivider(int size, int block) {
        mSize = size;
        mBlock = block;
    }
    // Out: pos, size
    public boolean nextBlock(int[] out) {
        final int remaining = mSize - mPassed;
        if (remaining > 0) {
            out[0] = mPassed;
            out[1] = Math.min(remaining, mBlock);
            mPassed += out[1];
            return true;
        }
        return false;
    }

}
