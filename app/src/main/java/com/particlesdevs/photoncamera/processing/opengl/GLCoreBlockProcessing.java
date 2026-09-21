package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.particlesdevs.photoncamera.app.PhotonCamera;
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
     * DEBUG-only gauge (feeds VramStage logging): release builds skip the
     * bookkeeping entirely.
     */
    private static long sLiveRenderBytes = 0;

    /** Sums live sink-renderbuffer bytes (see sLiveRenderBytes). */
    public static synchronized long liveRenderBytes() {
        return sLiveRenderBytes;
    }

    private static synchronized void addRenderBytes(long b) {
        if (!PhotonCamera.DEBUG) {
            return;
        }
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

    /**
     * Readback pixel type for a GLFormat: FLOAT_16 outputs must request
     * GL_HALF_FLOAT because the block/out buffers are sized by the 2-byte
     * storage size - the GL_FLOAT convention used for FLOAT_16 texture
     * uploads (32-bit floats the driver converts) would read 4 bytes per
     * sample and overflow them.
     */
    private static int readbackType(GLFormat fmt) {
        return fmt.mFormat == GLFormat.DataType.FLOAT_16 ? GLES30.GL_HALF_FLOAT : fmt.getGLType();
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
            glReadPixels(0, 0, mOutWidth, height, mglFormat.getGLFormatExternal(), readbackType(mglFormat), mBlockBuffer);
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

    // ---- Async (PBO) variant of streamBand --------------------------------
    // A synchronous glReadPixels into client memory forces the driver to drain
    // the whole queue before it can copy, so each fused band serializes with
    // the next band's render. Reading into a pixel-pack buffer instead lets
    // the DMA run while the next band is drawn; the copy into the wrapped sink
    // happens when the slot comes around again (two slots, one band apart).

    private final int[] streamPbo = new int[2];
    private final long[] streamFence = new long[2];
    private final int[] streamPboY = new int[2];
    private final int[] streamPboRows = new int[2];
    private final int[] streamPboStride = new int[2];
    private final java.nio.ByteBuffer[] streamPboDst = new java.nio.ByteBuffer[2];
    private int streamSlot = 0;
    private int streamPendingMask = 0;

    /**
     * Draws one output band with the currently-bound program and starts an
     * asynchronous readback into a PBO (same geometry and bytes as
     * {@link #streamBand}; the caller must invoke {@link #finishStreamedBands}
     * before the destination buffer's memory is released).
     */
    public void streamBandAsync(int y, int rows, java.nio.ByteBuffer dst, int dstStrideBytes) {
        if (y < 0 || rows <= 0 || y + rows > mOutHeight) {
            throw new IllegalStateException("sink band [" + y + "," + (y + rows)
                    + ") outside height " + mOutHeight);
        }
        if (rows > renderHeight) {
            throw new IllegalStateException("sink band rows " + rows
                    + " exceed renderbuffer height " + renderHeight);
        }
        int slot = streamSlot;
        collectStreamSlot(slot);
        int rowBytes = mOutWidth * mglFormat.mFormat.mSize * mglFormat.mChannels;
        if (streamPbo[slot] == 0) {
            int[] pbo = new int[1];
            GLES30.glGenBuffers(1, pbo, 0);
            streamPbo[slot] = pbo[0];
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, streamPbo[slot]);
            GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER,
                    rowBytes * renderHeight, null, GLES30.GL_STREAM_READ);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, bindFB[0]);
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
        glViewport(0, 0, mOutWidth, rows);
        checkEglError("glViewport");
        super.mProgram.draw();
        checkEglError("program");
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, streamPbo[slot]);
        glReadPixels(0, 0, mOutWidth, rows, mglFormat.getGLFormatExternal(),
                mglFormat.getGLType(), 0);
        checkEglError("glReadPixels");
        streamFence[slot] = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        streamPboY[slot] = y;
        streamPboRows[slot] = rows;
        streamPboStride[slot] = dstStrideBytes;
        streamPboDst[slot] = dst;
        streamPendingMask |= (1 << slot);
        streamSlot ^= 1;
    }

    /** Waits for and copies out any band transfer still in flight. */
    public void finishStreamedBands() {
        collectStreamSlot(0);
        collectStreamSlot(1);
    }

    private void collectStreamSlot(int slot) {
        if ((streamPendingMask & (1 << slot)) == 0) return;
        streamPendingMask &= ~(1 << slot);
        long fence = streamFence[slot];
        if (fence != 0) {
            // Bounded retry, mirroring GLTexture.finishAsyncHalfFloatRead:
            // a driver that never signals must not hang the shot forever.
            for (int attempt = 0; attempt < 3; attempt++) {
                int status = GLES30.glClientWaitSync(fence,
                        GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
                if (status != GLES30.GL_TIMEOUT_EXPIRED) break;
            }
            GLES30.glDeleteSync(fence);
            streamFence[slot] = 0;
        }
        java.nio.ByteBuffer dst = streamPboDst[slot];
        streamPboDst[slot] = null;
        if (dst == null || streamPbo[slot] == 0) return;
        int rowBytes = mOutWidth * mglFormat.mFormat.mSize * mglFormat.mChannels;
        int rows = streamPboRows[slot];
        int y = streamPboY[slot];
        int stride = streamPboStride[slot];
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, streamPbo[slot]);
        java.nio.ByteBuffer mapped = (java.nio.ByteBuffer) GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_PACK_BUFFER, 0, rowBytes * rows,
                GLES30.GL_MAP_READ_BIT);
        if (mapped != null) {
            mapped.order(java.nio.ByteOrder.nativeOrder());
            for (int r = 0; r < rows; r++) {
                int srcOff = r * rowBytes;
                int dstOff = (y + r) * stride;
                if (dstOff + rowBytes > dst.capacity()) {
                    throw new IllegalStateException("sink copy out of range: band=" + y
                            + " rows=" + rows + " dstCap=" + dst.capacity());
                }
                mapped.limit(srcOff + rowBytes).position(srcOff);
                dst.limit(dstOff + rowBytes).position(dstOff);
                dst.put(mapped);
            }
        }
        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        checkEglError("streamBandAsync copy");
    }

    private void releaseStreamResources() {
        for (int slot = 0; slot < 2; slot++) {
            if (streamFence[slot] != 0) {
                try {
                    GLES30.glClientWaitSync(streamFence[slot],
                            GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
                    GLES30.glDeleteSync(streamFence[slot]);
                } catch (Exception ignored) {}
                streamFence[slot] = 0;
            }
            if (streamPbo[slot] != 0) {
                try {
                    int[] pbo = new int[]{streamPbo[slot]};
                    GLES30.glDeleteBuffers(1, pbo, 0);
                } catch (Exception ignored) {}
                streamPbo[slot] = 0;
            }
            streamPboDst[slot] = null;
        }
        streamPendingMask = 0;
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
            glReadPixels(0, 0, size.x, height, glFormat.getGLFormatExternal(), readbackType(glFormat), mBlockBuffert);
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
        // Any pending async band transfer belongs to this context; wait it out
        // and delete the fence/PBO while the context is still current.
        try {
            releaseStreamResources();
        } catch (Exception ignored) {}
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
