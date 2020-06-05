package com.eszdman.photoncamera.Render;

import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;

public class Parameters {
    public byte cfaPattern;
    public Point rawSize;
    public byte[] blacklevel = new byte[4];
    public float[] whitepoint = new float[3];
    public short whitelevel = 1023;
    public String path;

    public Parameters(CaptureResult result, CameraCharacteristics characteristics, Point size){
        rawSize = size;
        for(int i =0; i<4; i++) blacklevel[i] = 64;
        int[] blarr = new int[4];
        BlackLevelPattern level = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if(level != null) for(int i =0; i<4;i++) blacklevel[i] = (byte)blarr[i];
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if(ptr !=null) cfaPattern = (byte)(int)ptr;
        Object wlevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if(wlevel != null) whitelevel = (short)((int)wlevel);
    }
}
