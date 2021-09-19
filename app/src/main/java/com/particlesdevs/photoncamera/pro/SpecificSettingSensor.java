package com.particlesdevs.photoncamera.pro;

import com.particlesdevs.photoncamera.processing.render.NoiseModeler;

//Device current sensor specific
public class SpecificSettingSensor {
    public int id = 0;
    public boolean isRawColorCorrection = false;
    public float[] blackLevel;
    public float captureSharpeningS = 0.0f;
    public float captureSharpeningIntense = 0.0f;
    public float[][] CCT;
    public double[][] NoiseModelerArr;
    public boolean ModelerExists = false;
    public boolean CCTExists = false;
    public SpecificSettingSensor(){
        NoiseModelerArr = new double[4][4];
        CCT = new float[3][3];
    }
}
