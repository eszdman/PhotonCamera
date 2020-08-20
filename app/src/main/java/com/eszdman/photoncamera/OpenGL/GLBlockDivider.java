package com.eszdman.photoncamera.OpenGL;

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
