package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import com.particlesdevs.photoncamera.util.Log;

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

import com.particlesdevs.photoncamera.util.Allocator;

public class GLCoreBlockProcessing extends GLContext implements AutoCloseable {
    private static String TAG = "GLCoreBlockProcessing";
    public GLImage mOut = null;
    public Point shift = new Point(0,0);
    private final int mOutWidth, mOutHeight;
    private final int mTileSize;
    public ByteBuffer mBlockBuffer;
    public ByteBuffer mOutBuffer;
    private final GLFormat mglFormat;
    /** Band height of the sink renderbuffer (see the constructor). */
    private final int renderHeight;
    /** This instance's registered renderbuffer bytes (deregistered in close). */
    private long renderBytes = 0;
    /**
     * Live sink-renderbuffer bytes across instances: sink FBOs are invisible
     * to the GLTexture gauge otherwise (~400 MB hidden at 50 MP before P3-E1).
     */
    private static long sLiveRenderBytes = 0;

    /** Sums live sink-renderbuffer bytes (see sLiveRenderBytes). */
    public static synchronized long liveRenderBytes() {
        return sLiveRenderBytes;
    }

    private static synchronized void addRenderBytes(long b) {
        sLiveRenderBytes += b;
    }

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
        allocation = alloc;
        mOut = out;
    }
    public GLCoreBlockProcessing(Point size, GLImage out, GLFormat glFormat) {
        this(size, glFormat, GLDrawParams.Allocate.Direct);
        mOut = out;
    }
    public GLCoreBlockProcessing(Point size, GLFormat glFormat) {
        this(size,glFormat, GLDrawParams.Allocate.Direct);
    }
    public GLCoreBlockProcessing(Point size, GLFormat glFormat, GLDrawParams.Allocate alloc) {
        super(size.x, GLDrawParams.TileSize);
        mTileSize = GLDrawParams.TileSize;
        allocation = alloc;
        mglFormat = glFormat;
        mOutWidth = size.x;
        mOutHeight = size.y;
        mBlockBuffer = ByteBuffer.allocateDirect(mOutWidth * mTileSize * mglFormat.mFormat.mSize * mglFormat.mChannels);
        glGenFramebuffers(1,bindFB,0);
        glGenRenderbuffers(1,bindRB,0);
        glBindRenderbuffer(GL_RENDERBUFFER,bindRB[0]);
        // Band-sized sink storage (P3-E1): every draw loop in this class
        // addresses at most one divider block (mTileSize rows), except the
        // fused tail streamer (512-row bands) — so the renderbuffer only ever
        // needs max(mTileSize, 512) rows, never the frame height.
        renderHeight = Math.max(mTileSize, 512);
        glRenderbufferStorage(GL_RENDERBUFFER, glFormat.getGLFormatInternal(), size.x, renderHeight);
        renderBytes = (long) size.x * renderHeight
                * glFormat.mFormat.mSize * glFormat.mChannels;
        addRenderBytes(renderBytes);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,bindFB[0]);
        glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, bindRB[0]);
        final int capacity = mOutWidth * mOutHeight * mglFormat.mFormat.mSize * mglFormat.mChannels;
        if(alloc == GLDrawParams.Allocate.None) return;
        if(alloc == GLDrawParams.Allocate.Direct) mOutBuffer = Allocator.allocate(capacity);
        else {
            mOutBuffer = ByteBuffer.allocate(capacity);
        }
    }
    public GLCoreBlockProcessing(Point size, GLImage out, GLFormat glFormat,ByteBuffer output) {
        super(size.x, GLDrawParams.TileSize);
        mTileSize = GLDrawParams.TileSize;
        output.position(0);
        mglFormat = glFormat;
        mOutWidth = size.x;
        mOutHeight = size.y;
        mBlockBuffer = ByteBuffer.allocate(mOutWidth * mTileSize * mglFormat.mFormat.mSize * mglFormat.mChannels);
        glGenFramebuffers(1,bindFB,0);
        glGenRenderbuffers(1,bindRB,0);
        glBindRenderbuffer(GL_RENDERBUFFER,bindRB[0]);
        // Band-sized (see the main constructor): this sink also streams only.
        renderHeight = Math.max(mTileSize, 512);
        glRenderbufferStorage(GL_RENDERBUFFER, glFormat.getGLFormatInternal(), size.x, renderHeight);
        renderBytes = (long) size.x * renderHeight
                * glFormat.mFormat.mSize * glFormat.mChannels;
        addRenderBytes(renderBytes);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,bindFB[0]);
        glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, bindRB[0]);
        mOutBuffer = output;
        mOut = out;
    }

    public void drawBlocksToOutput() {
        glBindFramebuffer(GL_FRAMEBUFFER, bindFB[0]);
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        GLProg program = super.mProgram;
        GLBlockDivider divider = new GLBlockDivider(mOutHeight, mTileSize);
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
            if (height < mTileSize) {
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

    /**
     * Streams the rendered tiles directly into {@code sink}'s pixel memory
     * (a software ARGB_8888 bitmap wrapped via {@link Allocator#wrapBitmap}),
     * skipping every intermediate full-frame buffer. The per-tile program
     * replay (viewport + yOffset) is identical to {@link #drawBlocksToOutput()}.
     */
    public void drawBlocksToOutput(Bitmap sink) {
        ByteBuffer wrapped = Allocator.wrapBitmap(sink);
        if (wrapped == null) {
            throw new IllegalStateException("Failed to lock bitmap pixels for direct output");
        }
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, bindFB[0]);
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
            GLProg program = super.mProgram;
            GLBlockDivider divider = new GLBlockDivider(mOutHeight, mTileSize);
            int[] row = new int[2];
            while (divider.nextBlock(row)) {
                int y = row[0];
                int height = row[1];
                glViewport(0, 0, mOutWidth, height);
                checkEglError("glViewport");
                program.setVar("yOffset", y);
                program.draw();
                checkEglError("program");
                wrapped.position(y * mOutWidth * 4);
                wrapped.limit((y + height) * mOutWidth * 4);
                glReadPixels(0, 0, mOutWidth, height, mglFormat.getGLFormatExternal(), mglFormat.getGLType(), wrapped);
                checkEglError("glReadPixels");
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        } finally {
            Allocator.unlockBitmap(sink);
        }
    }


    /**
     * T4b fused-sink draw: renders ONE output band with the currently-bound
     * program (the caller binds it and sets every sampler/uniform first,
     * INCLUDING yOffset) and reads it into {@code dst} at band offset,
     * mirroring one iteration of {@link #drawBlocksToOutput(Bitmap)} exactly
     * (framebuffer, alignment, viewport, draw, readPixels). Deliberately does
     * NOT set yOffset itself: fused bands sample tile-sized inputs whose
     * origin differs from the output origin, so the caller owns that uniform
     * (setting output rows here blacked every band past the first). The
     * shared sink loops are untouched. Throws (fail-fast to the caller's
     * legacy fallback) if the band leaves the frame.
     */
    public void streamBand(int y, int rows, java.nio.ByteBuffer dst, int dstStrideBytes) {
        if (y < 0 || rows <= 0 || y + rows > mOutHeight) {
            throw new IllegalStateException("sink band [" + y + "," + (y + rows)
                    + ") outside height " + mOutHeight);
        }
        if (rows > renderHeight) {
            throw new IllegalStateException("sink band rows " + rows
                    + " exceed renderbuffer height " + renderHeight);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, bindFB[0]);
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        glViewport(0, 0, mOutWidth, rows);
        checkEglError("glViewport");
        super.mProgram.draw();
        checkEglError("program");
        dst.position(y * dstStrideBytes);
        dst.limit((y + rows) * dstStrideBytes);
        glReadPixels(0, 0, mOutWidth, rows, mglFormat.getGLFormatExternal(),
                mglFormat.getGLType(), dst);
        checkEglError("glReadPixels");
    }


    public ByteBuffer drawBlocksToOutput(Point size, GLFormat glFormat) {
        return drawBlocksToOutput(size,glFormat, GLDrawParams.Allocate.Heap);
    }

    public ByteBuffer drawBlocksToOutput(Point size, GLFormat glFormat,GLDrawParams.Allocate alloc) {
        ByteBuffer mOutBuffer;
        allocation = alloc;
        if(alloc == GLDrawParams.Allocate.Direct) mOutBuffer = Allocator.allocate(size.x * size.y * glFormat.mFormat.mSize * glFormat.mChannels);
        else
            mOutBuffer = ByteBuffer.allocate(size.x * size.y * glFormat.mFormat.mSize * glFormat.mChannels);
        return drawBlocksToOutput(size,glFormat,mOutBuffer);
    }

    public ByteBuffer drawBlocksToOutput(Point size, GLFormat glFormat,ByteBuffer mOutBuffer) {
        glBindFramebuffer(GL_FRAMEBUFFER, bindFB[0]);
        checkEglError("glBindFramebuffer");
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        int bytes = glFormat.mFormat.mSize * glFormat.mChannels;
        int need = size.x * mTileSize * bytes;
        if (mBlockBuffer == null || mBlockBuffer.capacity() < need) {
            mBlockBuffer = ByteBuffer.allocateDirect(need);
        }
        GLProg program = super.mProgram;
        GLBlockDivider divider = new GLBlockDivider(size.y, mTileSize);
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
            if (height < mTileSize) {
                byte[] data = new byte[size.x * height * glFormat.mFormat.mSize * glFormat.mChannels];
                mBlockBuffert.get(data);
                mOutBuffer.put(data);
            } else {
                int lim = mBlockBuffert.limit();
                mOutBuffer.put((ByteBuffer) mBlockBuffert.limit(size.x * mTileSize * glFormat.mFormat.mSize * glFormat.mChannels));
                mBlockBuffert.limit(lim);
            }
        }
        mOutBuffer.position(0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return mOutBuffer;
    }

    @Override
    public void close() {
        // Ensure GPU work is complete before tearing down EGL state.
        try {
            GLES30.glFinish();
        } catch (Exception ignored) {}
        try {
            super.close();
        } catch (Exception ignored) {}
        if (mOut != null) {
            try {
                mOut.close();
            } catch (Exception ignored) {}
            mOut = null;
        }
        if (mBlockBuffer != null) {
            try {
                mBlockBuffer.clear();
            } catch (Exception ignored) {}
            mBlockBuffer = null;
        }
        if (mOutBuffer != null) {
            try {
                if (allocation == GLDrawParams.Allocate.Direct) {
                    // Intentionally temporarily leaked: must hold the malloc
                } else {
                    mOutBuffer.clear();
                }
            } catch (Exception ignored) {}
            mOutBuffer = null;
        }
        // FBO/RBO are owned by this context; delete while context was current.
        // They are recreated per-pipeline, so stale IDs must not survive eglTerminate.
        try {
            if (bindFB[0] != 0) GLES30.glDeleteFramebuffers(1, bindFB, 0);
        } catch (Exception ignored) {}
        try {
            if (bindRB[0] != 0) GLES30.glDeleteRenderbuffers(1, bindRB, 0);
        } catch (Exception ignored) {}
        if (renderBytes != 0) {
            addRenderBytes(-renderBytes);
            renderBytes = 0;
        }
    }
}
