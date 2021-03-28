package com.particlesdevs.photoncamera;

import java.nio.ByteBuffer;

//PhotonCamera
//Copyright (C) 2020-2021  Eszdman
//https://github.com/eszdman/PhotonCamera
//Using this file when changing the main application package is prohibited
public class Wrapper {
    static {
        System.loadLibrary("HdrX");
        System.loadLibrary("AlignVectors");
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
    public static native void initAlignments(int rows, int cols, int frames);

    /**
     * Function to load images.
     *
     * @param bufferptr Image buffer.
     */
    public static native void loadFrame(ByteBuffer bufferptr, float Exposure);
    public static native void loadFrameAlignments(ByteBuffer bufferptr, float Exposure);

    public static native void loadInterpolatedGainMap(ByteBuffer GainMap);

    public static native ByteBuffer processFrame(float DistMin, float DistMax, float ElFactor, float BLr,float BLg,float BLb, float WLFactor,
    float wpR,float wpG, float wpB,int CfaPattern);
    public static native ByteBuffer processFrameAlignments();
}
