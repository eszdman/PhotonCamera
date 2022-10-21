package com.particlesdevs.photoncamera.control;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class GyroBurst {
    public float shakiness;
    public int samples;
    public float[][] movementss;
    public long[] timestampss;
    public float[] integrated;
    private final int maxSamples;
    public GyroBurst(int maxSamples){
        this.maxSamples = maxSamples;
        movementss = new float[3][maxSamples];
        timestampss = new long[maxSamples];
        integrated = new float[3];
        samples = 0;
    }

    @NonNull
    @Override
    public GyroBurst clone() {
        GyroBurst out = new GyroBurst(maxSamples);
        out.movementss = movementss.clone();
        out.timestampss = timestampss.clone();
        out.integrated = integrated.clone();
        out.shakiness = shakiness;
        out.samples = samples;
        return out;
    }
}
