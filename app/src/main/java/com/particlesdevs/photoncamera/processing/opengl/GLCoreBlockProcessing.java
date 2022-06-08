package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;

import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_NO_ERROR;
import static android.opengl.GLES20.GL_RENDERBUFFER;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindRenderbuffer;
import static android.opengl.GLES20.glFramebufferRenderbuffer;
import static android.opengl.GLES20.glGenFramebuffers;
import static android.opengl.GLES20.glGenRenderbuffers;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glRenderbufferStorage;
import static android.opengl.GLES30.GL_DRAW_FRAMEBUFFER;
import static android.opengl.GLES30.GL_RGBA8;
import static android.opengl.GLES30.glReadPixels;
import static android.opengl.GLES30.glViewport;

public class GLCoreBlockProcessing extends GLContext {
    private static String TAG = "GLCoreBlockProcessing";
    public GLImage mOut = null;
    public Point shift = new Point(0,0);
    private final int mOutWidth, mOutHeight;
    public ByteBuffer mBlockBuffer;
    public ByteBuffer mOutBuffer;
    private final GLFormat mglFormat;

    public GLDrawParams.Allocate allocation = GLDrawParams.Allocate.Heap;

    public static void checkEglError(String op) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            String msg = op + ": glError: " + GLUtils.getEGLErrorString(error) + " (" + Integer.toHexString(error) + ")";
            String TAG = "GLCoreBlockProcessing";
            Log.v(TAG, msg);
        }
    }
    public GLCoreBlockProcessing(Point size, GLImage out, GLFormat glFormat, GLDrawParams.Allocate alloc) {
        this(size, glFormat,alloc);
        mOut = out;
    }
    public GLCoreBlockProcessing(Point size, GLImage out, GLFormat glFormat) {
        this(size, glFormat, GLDrawParams.Allocate.Heap);
        mOut = out;
    }
    public GLCoreBlockProcessing(Point size, GLFormat glFormat) {
        this(size,glFormat, GLDrawParams.Allocate.Heap);
    }
    public GLCoreBlockProcessing(Point size, GLFormat glFormat, GLDrawParams.Allocate alloc) {
        super(size.x, GLDrawParams.TileSize);
        mglFormat = glFormat;
        mOutWidth = size.x;
        mOutHeight = size.y;
        mBlockBuffer = ByteBuffer.allocateDirect(mOutWidth * GLDrawParams.TileSize * mglFormat.mFormat.mSize * mglFormat.mChannels);
        glGenFramebuffers(1,bindFB,0);
        glGenRenderbuffers(1,bindRB,0);
        glBindRenderbuffer(GL_RENDERBUFFER,bindRB[0]);
        glRenderbufferStorage(GL_RENDERBUFFER, glFormat.getGLFormatInternal(), size.x, size.y);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,bindFB[0]);
        glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, bindRB[0]);
        final int capacity = mOutWidth * mOutHeight * mglFormat.mFormat.mSize * mglFormat.mChannels;
        if(alloc == GLDrawParams.Allocate.None) return;
        if(alloc == GLDrawParams.Allocate.Direct) mOutBuffer = ByteBuffer.allocateDirect(capacity);
        else {
            mOutBuffer = ByteBuffer.allocate(capacity);
        }
    }
    public GLCoreBlockProcessing(Point size, GLImage out, GLFormat glFormat,ByteBuffer output) {
        super(size.x, GLDrawParams.TileSize);
        output.position(0);
        mglFormat = glFormat;
        mOutWidth = size.x;
        mOutHeight = size.y;
        mBlockBuffer = ByteBuffer.allocate(mOutWidth * GLDrawParams.TileSize * mglFormat.mFormat.mSize * mglFormat.mChannels);
        glGenFramebuffers(1,bindFB,0);
        glGenRenderbuffers(1,bindRB,0);
        glBindRenderbuffer(GL_RENDERBUFFER,bindRB[0]);
        glRenderbufferStorage(GL_RENDERBUFFER, glFormat.getGLFormatInternal(), size.x, size.y);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,bindFB[0]);
        glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, bindRB[0]);
        mOutBuffer = output;
        mOut = out;
    }

    public void drawBlocksToOutput() {
        glBindFramebuffer(GL_FRAMEBUFFER, bindFB[0]);
        GLProg program = super.mProgram;
        GLBlockDivider divider = new GLBlockDivider(mOutHeight, GLDrawParams.TileSize);
        int[] row = new int[2];
        mOutBuffer.position(0);
        mBlockBuffer.position(0);
        while (divider.nextBlock(row)) {
            int y = row[0];
            int height = row[1];
            glViewport(0, 0, mOutWidth, height);
            checkEglError("glViewport");
            program.setVar("yOffset", y);
            program.draw();
            checkEglError("program");
            mBlockBuffer.position(0);
            glReadPixels(0, 0, mOutWidth, height, mglFormat.getGLFormatExternal(), mglFormat.getGLType(), mBlockBuffer);
            checkEglError("glReadPixels");
            if (height < GLDrawParams.TileSize) {
                // This can only happen 2 times at edges
                byte[] data = new byte[mOutWidth * height * mglFormat.mFormat.mSize * mglFormat.mChannels];
                mBlockBuffer.get(data);
                mOutBuffer.put(data);
            } else {
                mOutBuffer.put(mBlockBuffer);
            }
        }
        mOutBuffer.position(0);
        mBlockBuffer = null;
        if (mOut != null) mOut.byteBuffer = mOutBuffer;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }


    public ByteBuffer drawBlocksToOutput(Point size, GLFormat glFormat) {
        return drawBlocksToOutput(size,glFormat, GLDrawParams.Allocate.Heap);
    }

    public ByteBuffer drawBlocksToOutput(Point size, GLFormat glFormat,GLDrawParams.Allocate alloc) {
        ByteBuffer mOutBuffer;
        if(alloc == GLDrawParams.Allocate.Direct) mOutBuffer = ByteBuffer.allocateDirect(size.x * size.y * glFormat.mFormat.mSize * glFormat.mChannels);
        else
            mOutBuffer = ByteBuffer.allocate(size.x * size.y * glFormat.mFormat.mSize * glFormat.mChannels);
        return drawBlocksToOutput(size,glFormat,mOutBuffer);
    }

    public ByteBuffer drawBlocksToOutput(Point size, GLFormat glFormat,ByteBuffer mOutBuffer) {
        glBindFramebuffer(GL_FRAMEBUFFER, bindFB[0]);
        checkEglError("glBindFramebuffer");
        GLProg program = super.mProgram;
        GLBlockDivider divider = new GLBlockDivider(size.y, GLDrawParams.TileSize);
        int[] row = new int[2];
        ByteBuffer mBlockBuffert = mBlockBuffer;
        mOutBuffer.position(0);
        mBlockBuffert.position(0);
        while (divider.nextBlock(row)) {
            int y = row[0];
            int height = row[1];
            glViewport(0, 0, size.x, height);
            checkEglError("glViewport");
            program.setVar("yOffset", y);
            program.draw();
            checkEglError("program");
            mBlockBuffert.position(0);
            glReadPixels(0, 0, size.x, height, glFormat.getGLFormatExternal(), glFormat.getGLType(), mBlockBuffert);
            checkEglError("glReadPixels");
            if (height < GLDrawParams.TileSize) {
                // This can only happen 2 times at edges
                byte[] data = new byte[size.x * height * glFormat.mFormat.mSize * glFormat.mChannels];
                mBlockBuffert.get(data);
                mOutBuffer.put(data);
            } else {
                int lim = mBlockBuffert.limit();
                mOutBuffer.put((ByteBuffer) mBlockBuffert.limit(size.x * GLDrawParams.TileSize * glFormat.mFormat.mSize * glFormat.mChannels));
                mBlockBuffert.limit(lim);
            }
        }
        mOutBuffer.position(0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return mOutBuffer;
    }
}
