package com.particlesdevs.photoncamera.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Single writer thread; frame encoding runs independently on worker threads. */
public interface RawContainerWriter extends Closeable {
    int writeFrame(ByteBuffer encoded, int length, long timestampNs, String metadata) throws IOException;
    void writeAudio(short[] samples, long timestampNs) throws IOException;
    long frameCount();
}
