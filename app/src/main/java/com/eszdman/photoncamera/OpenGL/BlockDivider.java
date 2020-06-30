package com.eszdman.photoncamera.OpenGL;

public class BlockDivider {
    private final int mSize;
    private final int mBlock;
    private int mPassed;

    public BlockDivider(int size, int block) {
        mSize = size;
        mBlock = block;
    }

    // Out: pos, size
    public boolean nextBlock(int[] out) {
        final int remaining = mSize - mPassed;
        if (remaining > 0) {
            out[0] = mPassed;
            if (remaining >= mBlock) {
                out[1] = mBlock;
            } else {
                out[1] = remaining;
            }
            mPassed += out[1];
            return true;
        }
        return false;
    }
}
