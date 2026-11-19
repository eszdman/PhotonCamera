package com.particlesdevs.photoncamera.util;

import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** CinematicRAWStudio container. All instance operations belong to the ordered writer thread. */
public final class CrawWriter implements RawContainerWriter {
    static { System.loadLibrary("craw"); }
    private long handle;
    private final int channels, sampleRate;

    private CrawWriter(long handle, int channels, int sampleRate) {
        this.handle = handle;
        this.channels = channels;
        this.sampleRate = sampleRate;
    }

    public static CrawWriter open(String path, String metadata, int channels, int sampleRate)
            throws IOException {
        int fd = SimpleStorageHelper.openFdForWrite(path);
        // nativeCreate duplicates the descriptor. Always close our copy, including on failure.
        try (ParcelFileDescriptor file = fd >= 0 ? ParcelFileDescriptor.adoptFd(fd)
                : ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY)) {
            return new CrawWriter(nativeCreate(file.getFd(), metadata), channels, sampleRate);
        }
    }

    public static int encode(ByteBuffer plane, int width, int height, int stride, boolean raw10,
            int cropTop, int cropHeight, boolean bin, int cfa, int codec, ByteBuffer output)
            throws IOException {
        // slice makes the JNI capacity match the actual camera plane limit.
        plane.rewind();
        return nativeEncode(plane.slice(), width, height, stride, raw10, cropTop, cropHeight,
                bin, cfa, codec, output);
    }

    private long requireOpen() {
        if (handle == 0) throw new IllegalStateException("CRAW writer closed");
        return handle;
    }

    @Override public int writeFrame(ByteBuffer encoded, int length, long timestampNs, String metadata)
            throws IOException {
        nativeWriteFrame(requireOpen(), encoded, length, timestampNs, metadata);
        return length;
    }

    @Override public void writeAudio(short[] samples, long timestampNs) throws IOException {
        nativeWriteAudio(requireOpen(), samples, timestampNs, channels, sampleRate);
    }

    public void writeImu(int sensor, long[] timestamps, float[] xyz) throws IOException {
        nativeWriteImu(requireOpen(), sensor, timestamps, xyz);
    }

    public void writeShading(long timestampNs, int id, int columns, int rows, float[] gains)
            throws IOException {
        nativeWriteShading(requireOpen(), timestampNs, id, columns, rows, gains);
    }

    @Override public long frameCount() { return handle == 0 ? 0 : nativeFrameCount(handle); }

    @Override public void close() throws IOException {
        if (handle == 0) return;
        long closing = handle;
        handle = 0;
        nativeClose(closing);
    }

    private static native long nativeCreate(int fd, String metadata) throws IOException;
    private static native int nativeEncode(ByteBuffer plane, int width, int height, int stride,
            boolean raw10, int cropTop, int cropHeight, boolean bin, int cfa, int codec,
            ByteBuffer output) throws IOException;
    private static native void nativeWriteFrame(long handle, ByteBuffer data, int length,
            long timestampNs, String metadata) throws IOException;
    private static native void nativeWriteAudio(long handle, short[] samples, long timestampNs,
            int channels, int rate) throws IOException;
    private static native void nativeWriteImu(long handle, int sensor, long[] timestamps,
            float[] xyz) throws IOException;
    private static native void nativeWriteShading(long handle, long ts, int id, int columns,
            int rows, float[] gains) throws IOException;
    private static native long nativeFrameCount(long handle);
    private static native void nativeClose(long handle) throws IOException;
}
