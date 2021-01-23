package com.particlesdevs.photoncamera;

public class AiPhoto {
    static {
        System.loadLibrary("aiphoto");
    }

    public native static void initAi(Object act);
}
