package com.eszdman.photoncamera;

import java.nio.ByteBuffer;

public class Wrapper {
    public Wrapper(){
        System.loadLibrary("HdrX");
    }

public static native void init(int rows,int cols, int frames);
public static native void loadFrame(ByteBuffer bufferptr);
public static native ByteBuffer processFrame();
}
