package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.GyroAlignment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.opengl.GLES31.*;

/** Bayer-plane GPU prewarp for the CPU matcher. Requires the caller's current GL context. */
public final class GpuGyroPrewarp implements AutoCloseable {
    private final GLProg program;
    private final GLTexture input;
    private final Point dispatchSize;
    private final int byteSize;
    private int buffer;
    private boolean mapped;

    /** Borrows the program and raw R16F input texture; owns only reusable GPU scratch. */
    public GpuGyroPrewarp(GLProg program, GLTexture input) {
        if (input.mSize.x < 2 || input.mSize.y < 2
                || (input.mSize.x & 1) != 0 || (input.mSize.y & 1) != 0) {
            throw new IllegalArgumentException("Gyro prewarp requires even raw dimensions");
        }
        this.program = program;
        this.input = input;
        dispatchSize = new Point(input.mSize.x / 2, input.mSize.y);
        byteSize = Math.multiplyExact(Math.multiplyExact(input.mSize.x, input.mSize.y), 2);
        int[] ids = new int[1];
        glGenBuffers(1, ids, 0);
        buffer = ids[0];
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, byteSize, null, GL_STREAM_READ);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Uploads the untouched fp16 source and applies the reference-to-source matrix on GPU.
     * The returned direct buffer is valid only until WarpedFrame.close(). Consume it
     * synchronously in Halide before unmapping or dispatching the next frame.
     */
    public WarpedFrame warp(ByteBuffer source, float[] homography) {
        if (buffer == 0 || mapped) throw new IllegalStateException("Prewarp closed or still mapped");
        if (source == null || !source.isDirect() || source.capacity() < byteSize
                || homography == null || homography.length != 9) {
            throw new IllegalArgumentException("Invalid gyro source or matrix");
        }
        for (float v : homography) {
            if (!Float.isFinite(v)) throw new IllegalArgumentException("Nonfinite gyro matrix");
        }
        ByteBuffer raw = source.duplicate();
        raw.clear();
        raw.limit(byteSize);
        input.loadRawHalf(raw);
        program.setLayout(8, 8, 1);
        program.useAssetProgram("alignment/gyroPrewarp", true);
        program.setTexture("rawTexture", input);
        program.setVar("gyroHomography", false, GyroAlignment.columnMajor(homography));
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, buffer);
        program.computeAuto(dispatchSize, 1);
        // Mapping synchronizes GPU completion; the barrier makes shader writes visible.
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        ByteBuffer result = (ByteBuffer) glMapBufferRange(GL_SHADER_STORAGE_BUFFER,
                0, byteSize, GL_MAP_READ_BIT);
        if (result == null) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
            throw new IllegalStateException("Could not map GPU gyro prewarp: GL error " + glGetError());
        }
        mapped = true;
        return new WarpedFrame(result.order(ByteOrder.nativeOrder()));
    }

    public final class WarpedFrame implements AutoCloseable {
        public final ByteBuffer pixels;
        private boolean closed;

        private WarpedFrame(ByteBuffer pixels) { this.pixels = pixels; }

        @Override public void close() {
            if (closed) return;
            closed = true;
            unmap();
        }
    }

    private void unmap() {
        if (!mapped) return;
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        boolean valid = glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        mapped = false;
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
        if (!valid) throw new IllegalStateException("GPU gyro prewarp buffer became invalid");
    }

    @Override public void close() {
        try {
            unmap();
        } finally {
            if (buffer != 0) {
                glDeleteBuffers(1, new int[]{buffer}, 0);
                buffer = 0;
            }
        }
    }
}
