package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;

public class GLComputeLayout {
    Point xy;
    int z;
    int binding;
    public GLComputeLayout(int binding){
        this.binding = binding;
    }
    public GLComputeLayout(int x, int y, int z){
        xy = new Point(x,y);
        this.z = z;

    }
}
