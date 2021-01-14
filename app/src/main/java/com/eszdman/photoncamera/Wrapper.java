package com.eszdman.photoncamera;

import java.nio.ByteBuffer;
//PhotonCamera
//Copyright (C) 2020-2021  Eszdman
//https://github.com/eszdman/PhotonCamera
//Using this file when changing the main application package is prohibited
public class Wrapper {
    static {
        System.loadLibrary("HdrX");
        //System.loadLibrary("photon_accel");
    }

    /**
     * Function to create pointers for image buffers.
     *
     * @param rows   Image rows.
     * @param cols   Image cols.
     * @param frames Image count.
     */
    public static native void init(int rows, int cols, int frames);

    /**
     * Function to load images.
     *
     * @param bufferptr Image buffer.
     */
    public static native void loadFrame(ByteBuffer bufferptr);

    public static native ByteBuffer processFrame(float ghosting,float wfactor);
//public static native void Test();
//public static native ByteBuffer ProcessOpenCL(ByteBuffer in);
}
