package com.eszdman.photoncamera.OpenGL;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;

import static android.opengl.GLES20.GL_LUMINANCE;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.glReadPixels;
import static android.opengl.GLES20.glViewport;

public class GLCoreBlockProcessing extends GLContext {
    private static String TAG = "GLCoreBlockProcessing";
    public Bitmap mOut = null;
    private final int mOutWidth, mOutHeight;
    public ByteBuffer mBlockBuffer;
    public ByteBuffer mOutBuffer;
    private GLFormat mglFormat;
    public static void checkEglError(String op) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            String msg = op + ": glError: " + GLUtils.getEGLErrorString(error) + " (" + Integer.toHexString(error) + ")";
            Log.e(TAG, msg);
        }
    }
    public GLCoreBlockProcessing(Point size,Bitmap out,GLFormat glFormat) {
        this(size,glFormat);
        mOut = out;
    }
    public GLCoreBlockProcessing(Point size,GLFormat glFormat) {
        super(size.x, GLConst.TileSize);
        mglFormat = glFormat;
        mOutWidth = size.x;
        mOutHeight = size.y;
        mBlockBuffer = ByteBuffer.allocate(mOutWidth * GLConst.TileSize*mglFormat.mFormat.mSize*mglFormat.mChannels);
        mOutBuffer = ByteBuffer.allocate(mOutWidth * mOutHeight*mglFormat.mFormat.mSize*mglFormat.mChannels);
    }
    public void drawBlocksToOutput() {
        GLProg program = super.mProgram;
        GLBlockDivider divider = new GLBlockDivider(mOutHeight, GLConst.TileSize);
        int[] row = new int[2];
        //mOutBuffer.clear();
        mOutBuffer.position(0);
        //mBlockBuffer.clear();
        mBlockBuffer.position(0);
        while (divider.nextBlock(row)) {
            int y = row[0];
            int height = row[1];

            glViewport(0, 0, mOutWidth, height);
            checkEglError("glViewport");
            program.servar("yOffset", y);
            program.draw();
            checkEglError("program");
            mBlockBuffer.position(0);
            Log.d(TAG,"ReadParams:"+"width:"+mOutWidth+" height:"+height+" buffer:"+mBlockBuffer);
            glReadPixels(0, 0, mOutWidth, height, mglFormat.getGLFormatExternal(), mglFormat.getGLType(), mBlockBuffer);
            checkEglError("glReadPixels");
            if (height < GLConst.TileSize) {
                // This can only happen 2 times at edges
                byte[] data = new byte[mOutWidth * height*mglFormat.mFormat.mSize*mglFormat.mChannels];
                mBlockBuffer.get(data);
                mOutBuffer.put(data);
            } else {
                mOutBuffer.put(mBlockBuffer);
            }
        }
        mOutBuffer.position(0);
        if(mOut !=null) mOut.copyPixelsFromBuffer(mOutBuffer);
    }
}
