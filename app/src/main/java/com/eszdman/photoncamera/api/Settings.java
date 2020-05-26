package com.eszdman.photoncamera.api;

import android.content.SharedPreferences;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;

import com.eszdman.photoncamera.ui.MainActivity;

import static android.content.Context.MODE_PRIVATE;
import static android.hardware.camera2.CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_STATE_LOCKED;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
import static android.hardware.camera2.CameraMetadata.EDGE_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON;
import static android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.EDGE_MODE;
import static android.hardware.camera2.CaptureRequest.HOT_PIXEL_MODE;
import static android.hardware.camera2.CaptureRequest.JPEG_QUALITY;
import static android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE;
import static android.hardware.camera2.CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE;

public class Settings {
    private String TAG = "Settings";
    public int NoiseReduction = NOISE_REDUCTION_MODE_OFF;
    public int AfMode = CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    public int frameCount = 25;
    public int lumenCount = 3;
    public int chromaCount = 12;
    public boolean enhancedProcess = false;
    public boolean grid = false;
    public boolean align = true;
    public boolean hdrx = true;
    public double saturation = 0.7;
    public double sharpness = 0.3;
    public double contrast_mpy = 1.5;
    public int contrast_const = 0;
    public double compressor = 2.13;
    public double gain = 0.7;
    public String lastPicture = null;

    private int count = 0;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private SharedPreferences sharedPreferences;

    public void load() {
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        NoiseReduction = get(NoiseReduction);
        AfMode = get(AfMode);
        Log.d(TAG,"Initial framecount:"+frameCount);
        frameCount = get(frameCount);
        Log.d(TAG,"Loaded framecount:"+frameCount);
        align = get(align);
        lumenCount = get(lumenCount);
        chromaCount = get(chromaCount);
        enhancedProcess = get(enhancedProcess);
        grid = get(grid);
        sharpness = get(sharpness);
        contrast_mpy = get(contrast_mpy);
        contrast_const = get(contrast_const);
        saturation = get(saturation);
        compressor = get(compressor);
        gain = get(gain);
        lastPicture = get(lastPicture);
        count = 0;
    }

    public void save() {
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        sharedPreferencesEditor = sharedPreferences.edit();
        put(NoiseReduction);
        put(AfMode);
        put(frameCount);
        put(align);
        put(lumenCount);
        put(chromaCount);
        put(enhancedProcess);
        put(grid);
        put(sharpness);
        put(contrast_mpy);
        put(contrast_const);
        put(saturation);
        put(compressor);
        put(gain);
        put(lastPicture);
        Log.d(TAG,"Saved framecount:"+frameCount);
        sharedPreferencesEditor.apply();
        count = 0;
    }

    public void applyRes(CaptureRequest.Builder captureBuilder) {
        captureBuilder.set(JPEG_QUALITY, (byte) 100);
        captureBuilder.set(NOISE_REDUCTION_MODE, Interface.i.settings.NoiseReduction);
        captureBuilder.set(HOT_PIXEL_MODE, HOT_PIXEL_MODE_HIGH_QUALITY);
        captureBuilder.set(COLOR_CORRECTION_MODE, COLOR_CORRECTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, CONTROL_AE_STATE_LOCKED);
        captureBuilder.set(STATISTICS_LENS_SHADING_MAP_MODE, STATISTICS_LENS_SHADING_MAP_MODE_ON);
        //captureBuilder.set(CONTROL_SCENE_MODE,CONTROL_SCENE_MODE_HDR);
        captureBuilder.set(EDGE_MODE, EDGE_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AF_MODE, Interface.i.settings.AfMode);
    }

    public void applyPrev(CaptureRequest.Builder captureBuilder) {
        //captureBuilder.set(CONTROL_ENABLE_ZSL,true);
        captureBuilder.set(EDGE_MODE, EDGE_MODE_HIGH_QUALITY);
        captureBuilder.set(COLOR_CORRECTION_MODE, COLOR_CORRECTION_MODE_HIGH_QUALITY);
        captureBuilder.set(NOISE_REDUCTION_MODE, NOISE_REDUCTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
        captureBuilder.set(CONTROL_AF_MODE, Interface.i.settings.AfMode);
    }


    void put(int in) {
        sharedPreferencesEditor.putInt("Settings:" + count, in);
        count++;
    }

    void put(double in) {
        sharedPreferencesEditor.putFloat("Settings:" + count, (float) in);
        count++;
    }

    void put(String in) {
        sharedPreferencesEditor.putString("Settings:" + count, in);
        count++;
    }

    void put(boolean in) {
        sharedPreferencesEditor.putBoolean("Settings:" + count, in);
        count++;
    }

    boolean get(boolean in) {
        boolean result = sharedPreferences.getBoolean("Settings:" + count, in);
        count++;
        return result;
    }

    int get(int cur) {
        int result;
        result = sharedPreferences.getInt("Settings:" + count, cur);
        count++;
        return result;
    }

    double get(double cur) {
        double result;
        result = (double) (sharedPreferences.getFloat("Settings:" + count, (float) (cur)));
        count++;
        return result;
    }

    String get(String cur) {
        String result;
        result = (sharedPreferences.getString("Settings:" + count, (cur)));
        count++;
        return result;
    }
}
