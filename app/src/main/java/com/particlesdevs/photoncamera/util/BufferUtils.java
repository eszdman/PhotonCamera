package com.particlesdevs.photoncamera.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BufferUtils {
    public static ByteBuffer getFrom(float[] floatArr){
        ByteBuffer buffer = ByteBuffer.allocateDirect(floatArr.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(floatArr);
        buffer.position(0);
        return buffer;
    }
}
