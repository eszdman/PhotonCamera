package com.eszdman.photoncamera.OpenGL;

import android.graphics.Bitmap;
import android.graphics.Point;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glReadPixels;
import static android.opengl.GLES20.glViewport;

public class GLCoreBlockProcessing extends GLContext {
    public Bitmap mOut = null;
    private final int mOutWidth, mOutHeight;
    public final ByteBuffer mBlockBuffer;
    public final ByteBuffer mOutBuffer;
    public GLCoreBlockProcessing(Point size,Bitmap out) {
        this(size);
        mOut = out;
    }
    public GLCoreBlockProcessing(Point size) {
        super(size.x, GLConst.TileSize);
        mOutWidth = size.x;
        mOutHeight = size.y;
        mBlockBuffer = ByteBuffer.allocate(mOutWidth * GLConst.TileSize*4);
        mOutBuffer = ByteBuffer.allocate(mOutWidth * mOutHeight*4);
    }
    public void drawBlocksToOutput(GLFormat glFormat) {
        GLProg program = super.mProgram;
        GLBlockDivider divider = new GLBlockDivider(mOutHeight, GLConst.TileSize);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            int y = row[0];
            int height = row[1];

            glViewport(0, 0, mOutWidth, height);
            program.servar("yOffset", y);
            program.draw();

            mBlockBuffer.position(0);
            glReadPixels(0, 0, mOutWidth, height, glFormat.getGLFormatExternal(), glFormat.getGLType(), mBlockBuffer);
            if (height < GLConst.TileSize) {
                // This can only happen once
                short[] data = new short[mOutWidth * height];
                mBlockBuffer.asShortBuffer().get(data);
                mOutBuffer.asShortBuffer().put(data);
            } else {
                mOutBuffer.put(mBlockBuffer);
            }
        }
        mOutBuffer.position(0);
        if(mOut !=null) mOut.copyPixelsFromBuffer(mOutBuffer);
    }
}
