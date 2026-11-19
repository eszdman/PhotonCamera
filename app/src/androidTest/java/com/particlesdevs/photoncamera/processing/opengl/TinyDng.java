package com.particlesdevs.photoncamera.processing.opengl;

import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Test-only tinydng owner. The pixel view is valid only until close(). */
final class TinyDng implements AutoCloseable {
    static { System.loadLibrary("burstreplay"); }
    private long handle;
    final JSONObject metadata;
    final ByteBuffer pixels;
    TinyDng(String path) throws Exception {
        handle = open(path);
        try {
            metadata = new JSONObject(metadata(handle));
            pixels = pixels(handle).order(ByteOrder.nativeOrder());
        } catch (Exception e) { close(); throw e; }
    }
    private static native long open(String path);
    private static native String metadata(long handle);
    private static native ByteBuffer pixels(long handle);
    private static native void destroy(long handle);
    public void close() { if (handle != 0) { destroy(handle); handle = 0; } }
}
