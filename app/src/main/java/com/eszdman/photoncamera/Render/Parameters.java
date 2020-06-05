package com.eszdman.photoncamera.Render;

import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.util.Rational;

public class Parameters {
    public byte cfaPattern;
    public Point rawSize;
    public float[] blacklevel = new float[4];
    public float[] whitepoint = new float[3];
    public float[] ccm = new float[9];
    public short whitelevel = 1023;
    public String path;

    public Parameters(CaptureResult result, CameraCharacteristics characteristics, Point size){
        rawSize = size;
        for(int i =0; i<4; i++) blacklevel[i] = 64;
        int[] blarr = new int[4];
        BlackLevelPattern level = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if(level != null) for(int i =0; i<4;i++) blacklevel[i] = blarr[i];
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if(ptr !=null) cfaPattern = (byte)(int)ptr;
        Object wlevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if(wlevel != null) whitelevel = (short)((int)wlevel);
        ccm[0] = 1.776f;ccm[1] = -0.837f;ccm[2] = 0.071f;
        ccm[3] = -0.163f;ccm[4] = 1.396f;ccm[5] = -0.242f;
        ccm[6] = 0.0331f;ccm[7] = -0.526f;ccm[8] = 1.492f;
        float normalize = 0.f;
        for(float f : ccm) normalize+=f;
        normalize/=9;
        //for(int i =0; i<ccm.length;i++) ccm[i]/=normalize;
        Rational[] wpoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        if(wpoint != null)for(int i =0; i<3;i++) whitepoint[i] = wpoint[i].floatValue();

    }
}
