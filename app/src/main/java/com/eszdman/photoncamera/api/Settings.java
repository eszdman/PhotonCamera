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
import static android.hardware.camera2.CaptureRequest.TONEMAP_PRESET_CURVE;

public class Settings {
    private String TAG = "Settings";
    public int noiseReduction = NOISE_REDUCTION_MODE_OFF;
    public int afMode = CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    public int frameCount = 25;
    public int lumenCount = 3;
    public int chromaCount = 12;
    public boolean enhancedProcess = false;
    public boolean grid = false;
    public boolean align = true;
    public boolean hdrx = true;
    public double saturation = 0.7;
    public double sharpness = 0.3;
    public double contrastMpy = 1.0;
    public int contrastConst = 0;
    public double compressor = 3.0;
    public double gain = 0.8;
    public String lastPicture = null;

    private int count = 0;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private SharedPreferences sharedPreferences;
    Settings(){
        load();
    }
    public void load() {
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        noiseReduction = get(noiseReduction);
        Log.d(TAG, "Loaded noise reduction:" + noiseReduction);
        afMode = get(afMode);
        Log.d(TAG, "Loaded af mode:" + afMode);
        frameCount = get(frameCount);
        Log.d(TAG, "Loaded frame count:" + frameCount);
        align = get(align);
        Log.d(TAG, "Loaded align:" + align);
        lumenCount = get(lumenCount);
        Log.d(TAG, "Loaded lumen count:" + lumenCount);
        chromaCount = get(chromaCount);
        Log.d(TAG, "Loaded chroma count:" + chromaCount);
        enhancedProcess = get(enhancedProcess);
        Log.d(TAG, "Loaded enhanced process:" + enhancedProcess);
        grid = get(grid);
        Log.d(TAG, "Loaded grid:" + grid);
        sharpness = get(sharpness);
        Log.d(TAG, "Loaded sharpness:" + sharpness);
        contrastMpy = get(contrastMpy);
        Log.d(TAG, "Loaded contrast mpy:" + contrastMpy);
        contrastConst = get(contrastConst);
        Log.d(TAG, "Loaded contrast const:" + contrastConst);
        saturation = get(saturation);
        Log.d(TAG, "Loaded saturation:" + saturation);
        compressor = get(compressor);
        Log.d(TAG, "Loaded compressor:" + compressor);
        gain = get(gain);
        Log.d(TAG, "Loaded gain:" + gain);
        lastPicture = get(lastPicture);
        Log.d(TAG, "Loaded last picture:" + lastPicture);
        count = 0;
    }

    public void save() {
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        sharedPreferencesEditor = sharedPreferences.edit();
        put(noiseReduction);
        Log.d(TAG, "Saved noise reduction:" + noiseReduction);
        put(afMode);
        Log.d(TAG, "Saved af mode:" + afMode);
        put(frameCount);
        Log.d(TAG, "Saved frame count:" + frameCount);
        put(align);
        Log.d(TAG, "Saved align:" + align);
        put(lumenCount);
        Log.d(TAG, "Saved lumen count:" + lumenCount);
        put(chromaCount);
        Log.d(TAG, "Saved chroma count:" + chromaCount);
        put(enhancedProcess);
        Log.d(TAG, "Saved enhanced process:" + enhancedProcess);
        put(grid);
        Log.d(TAG, "Saved grid:" + grid);
        put(sharpness);
        Log.d(TAG, "Saved sharpness:" + sharpness);
        put(contrastMpy);
        Log.d(TAG, "Saved contrast mpy:" + contrastMpy);
        put(contrastConst);
        Log.d(TAG, "Saved contrast const:" + contrastConst);
        put(saturation);
        Log.d(TAG, "Saved saturation:" + saturation);
        put(compressor);
        Log.d(TAG, "Saved compressor:" + compressor);
        put(gain);
        Log.d(TAG, "Saved gain:" + gain);
        put(lastPicture);
        Log.d(TAG, "Saved last picture:" + lastPicture);
        put(hdrx);
        sharedPreferencesEditor.apply();
        count = 0;
    }

    public void applyRes(CaptureRequest.Builder captureBuilder) {
        captureBuilder.set(JPEG_QUALITY, (byte) 100);
        captureBuilder.set(NOISE_REDUCTION_MODE, Interface.i.settings.noiseReduction);
        captureBuilder.set(HOT_PIXEL_MODE, HOT_PIXEL_MODE_HIGH_QUALITY);
        captureBuilder.set(COLOR_CORRECTION_MODE, COLOR_CORRECTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, CONTROL_AE_STATE_LOCKED);
        captureBuilder.set(STATISTICS_LENS_SHADING_MAP_MODE, STATISTICS_LENS_SHADING_MAP_MODE_ON);
        //captureBuilder.set(CONTROL_SCENE_MODE,CONTROL_SCENE_MODE_HDR);
        captureBuilder.set(EDGE_MODE, EDGE_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AF_MODE, Interface.i.settings.afMode);
    }

    public void applyPrev(CaptureRequest.Builder captureBuilder) {
        Camera2ApiAutoFix.Apply();
        //captureBuilder.set(CONTROL_ENABLE_ZSL,true);
        captureBuilder.set(EDGE_MODE, EDGE_MODE_HIGH_QUALITY);
        captureBuilder.set(COLOR_CORRECTION_MODE, COLOR_CORRECTION_MODE_HIGH_QUALITY);
        captureBuilder.set(NOISE_REDUCTION_MODE, NOISE_REDUCTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
        captureBuilder.set(CONTROL_AF_MODE, Interface.i.settings.afMode);
        //Log.d(TAG,"Points:"+captureBuilder.get(TONEMAP_PRESET_CURVE));
        captureBuilder.set(TONEMAP_PRESET_CURVE,0);
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
