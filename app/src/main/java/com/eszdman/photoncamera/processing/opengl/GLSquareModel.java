package com.eszdman.photoncamera.processing.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glVertexAttribPointer;

class GLSquareModel {
    private static final int CORDS_PER_VERTEX = 3;
    private static final float[] CORDS = {//Default square cords
            -1, 1, 0,
            -1, -1, 0,
            1, 1, 0,
            1, -1, 0
    };
    private static final int STRIDE = CORDS_PER_VERTEX * 4; // 4 bytes per vertex
    private final FloatBuffer mVertexBuffer;

    GLSquareModel() {
        // (# of coordinate values * 4 bytes per float)
        ByteBuffer bb = ByteBuffer.allocateDirect(CORDS.length * 4);

        bb.order(ByteOrder.nativeOrder());
        mVertexBuffer = bb.asFloatBuffer();
        mVertexBuffer.put(CORDS);
        mVertexBuffer.position(0);
    }

    void draw(int posHandle) {
        glEnableVertexAttribArray(posHandle);
        glVertexAttribPointer(
                posHandle, CORDS_PER_VERTEX,
                GL_FLOAT, false,
                STRIDE, mVertexBuffer);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, CORDS.length / 3);
        glDisableVertexAttribArray(posHandle);
    }
}