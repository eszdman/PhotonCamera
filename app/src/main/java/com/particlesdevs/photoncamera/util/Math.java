package com.particlesdevs.photoncamera.util;

public class Math {
    public static double mix(double in, double in2, double t){
        return in*(1.-t)+in2*(t);
    }
    public static float mix(float in, float in2, float t){
        return in*(1.f-t)+in2*(t);
    }
}
