package com.particlesdevs.photoncamera.control;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class GyroBurst {
    public float shakiness;
    public ArrayList<Float>[] movements;
    public ArrayList<Long> timestamps;
    public GyroBurst(){
        movements = new ArrayList[3];
        movements[0] = new ArrayList<>();
        movements[1] = new ArrayList<>();
        movements[2] = new ArrayList<>();
        timestamps = new ArrayList<>();
    }

    @NonNull
    @Override
    protected GyroBurst clone() {
        GyroBurst out = new GyroBurst();
        out.movements = movements.clone();
        out.timestamps = (ArrayList<Long>) timestamps.clone();
        out.shakiness = shakiness;
        return out;
    }
}
