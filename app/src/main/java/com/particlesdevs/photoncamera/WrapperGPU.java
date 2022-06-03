package com.particlesdevs.photoncamera;

import android.annotation.SuppressLint;
import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

//PhotonCamera
//Copyright (C) 2020-2021  Eszdman
//https://github.com/eszdman/PhotonCamera
//Using this file when changing the main application package is prohibited
public class WrapperGPU {
    static {
        System.loadLibrary("hdrxgpu");
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

    public static native void outputBuffer(ByteBuffer outputBuffer);
    public static native void processFrame(float NoiseS, float NoiseO,float Smooth, float ElFactor, float BLr,float BLg,float BLb, float WLFactor,
    float wpR,float wpG, float wpB,int CfaPattern);
    public static native ByteBuffer processFrameAlignments();
}
