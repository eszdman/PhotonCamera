package com.eszdman.photoncamera.OpenGL;

import android.graphics.Bitmap;
import java.nio.IntBuffer;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glReadPixels;
import static android.opengl.GLES20.glViewport;

public class GLCoreBlockProcessing extends GLContext {
    public final Bitmap mOut;
    private final int mOutWidth, mOutHeight;
    private final IntBuffer mBlockBuffer;
    private final IntBuffer mOutBuffer;
    public GLCoreBlockProcessing(Bitmap out) {
        super(out.getWidth(), GLConst.TileSize);
        mOut = out;
        mOutWidth = out.getWidth();
        mOutHeight = out.getHeight();
        mBlockBuffer = IntBuffer.allocate(mOutWidth * GLConst.TileSize);
        mOutBuffer = IntBuffer.allocate(mOutWidth * mOutHeight);
    }
    public void drawBlocksToOutput() {
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
            glReadPixels(0, 0, mOutWidth, height, GL_RGBA, GL_UNSIGNED_BYTE, mBlockBuffer);
            if (height < GLConst.TileSize) {
                // This can only happen once
                int[] data = new int[mOutWidth * height];
                mBlockBuffer.get(data);
                mOutBuffer.put(data);
            } else {
                mOutBuffer.put(mBlockBuffer);
            }
        }

        mOutBuffer.position(0);
        mOut.copyPixelsFromBuffer(mOutBuffer);
    }
}
