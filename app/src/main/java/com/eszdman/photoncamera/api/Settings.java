package com.eszdman.photoncamera.api;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.TonemapCurve;
import android.util.Log;
import android.util.Size;

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
import static android.hardware.camera2.CameraMetadata.TONEMAP_MODE_GAMMA_VALUE;
import static android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_REGIONS;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_REGIONS;
import static android.hardware.camera2.CaptureRequest.CONTROL_ENABLE_ZSL;
import static android.hardware.camera2.CaptureRequest.EDGE_MODE;
import static android.hardware.camera2.CaptureRequest.HOT_PIXEL_MODE;
import static android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE;
import static android.hardware.camera2.CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE;
import static android.hardware.camera2.CaptureRequest.TONEMAP_CURVE;
import static android.hardware.camera2.CaptureRequest.TONEMAP_MODE;

public class Settings {
    private String TAG = "Settings";
    public int noiseReduction = NOISE_REDUCTION_MODE_OFF;
    public int afMode = CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    public int aeModeOn = CONTROL_AE_MODE_ON;
    public int aeModeLock = CONTROL_AE_STATE_LOCKED;
    public int aeCurrentPrev = CONTROL_AE_MODE_ON;
    public int frameCount = 25;
    public int lumenCount = 3;
    public int chromaCount = 12;
    public boolean enhancedProcess = false;
    public boolean grid = false;
    public boolean roundedge = true;
    public boolean align = true;
    public boolean hdrx = true;
    public double saturation = 1.0;
    public double sharpness = 0.3;
    public double contrastMpy = 1.0;
    public int contrastConst = 0;
    public double compressor = 1.6;
    public double gain = 1.6;
    public boolean rawSaver = false;
    public String lastPicture = null;
    public boolean ManualMode = false;
    //TODO Add button for QuadResolution, if supported
    public boolean QuadBayer = false;
    public int cfaPattern = -1;
    public boolean remosaic = false;
    public boolean eisPhoto = true;
    public boolean fpsPreview = true;
    public String mCameraID = "0";
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
        roundedge = get(roundedge);
        Log.d(TAG, "Loaded round edges:" + roundedge);
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
        hdrx = get(hdrx);
        cfaPattern = get(cfaPattern);
        Log.d(TAG, "Loaded CFA:" + cfaPattern);
        rawSaver = get(rawSaver);
        Log.d(TAG, "Loaded rawSaver:" + rawSaver);
        remosaic = get(remosaic);
        Log.d(TAG, "Loaded remosaic:" + remosaic);
        eisPhoto = get(eisPhoto);
        Log.d(TAG, "Loaded eisPhoto:" + eisPhoto);
        QuadBayer = get(QuadBayer);
        Log.d(TAG, "Loaded QuadBayer:" + QuadBayer);
        fpsPreview = get(fpsPreview);
        Log.d(TAG, "Loaded fpsPreview:" + fpsPreview);
        count = -1;
        mCameraID = get(mCameraID);
        Log.d(TAG, "Loaded mCameraID:" + mCameraID);
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
        put(roundedge);
        Log.d(TAG, "Saved round edges:" + roundedge);
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
        Log.d(TAG, "Saved CFA:" + cfaPattern);
        put(cfaPattern);
        Log.d(TAG, "Saved RawSaver:" + rawSaver);
        put(rawSaver);
        Log.d(TAG, "Saved remosaic:" + remosaic);
        put(remosaic);
        Log.d(TAG, "Saved eisPhoto:" + eisPhoto);
        put(eisPhoto);
        Log.d(TAG, "Saved QuadBayer:" + QuadBayer);
        put(QuadBayer);
        Log.d(TAG, "Saved fpsPreview:" + fpsPreview);
        put(fpsPreview);

        count = -1;
        Log.d(TAG, "Saved mCameraID:" + mCameraID);
        put(mCameraID);

        sharedPreferencesEditor.apply();
        count = 0;
    }

    public void saveID(){
        count = -1;
        sharedPreferences = MainActivity.act.getPreferences(MODE_PRIVATE);
        sharedPreferencesEditor = sharedPreferences.edit();
        Log.d(TAG, "Saved mCameraID:" + mCameraID);
        put(mCameraID);
        sharedPreferencesEditor.apply();
        count = 0;
    }
    public void applyRes(CaptureRequest.Builder captureBuilder) {
        captureBuilder.set(NOISE_REDUCTION_MODE, Interface.i.settings.noiseReduction);
        captureBuilder.set(HOT_PIXEL_MODE, HOT_PIXEL_MODE_HIGH_QUALITY);
        captureBuilder.set(COLOR_CORRECTION_MODE, COLOR_CORRECTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, aeModeLock);
        captureBuilder.set(CONTROL_AF_MODE, Interface.i.settings.afMode);
        captureBuilder.set(STATISTICS_LENS_SHADING_MAP_MODE, STATISTICS_LENS_SHADING_MAP_MODE_ON);
        //captureBuilder.set(CONTROL_SCENE_MODE,CONTROL_SCENE_MODE_HDR);
        captureBuilder.set(EDGE_MODE, EDGE_MODE_HIGH_QUALITY);
    }
    @SuppressLint("InlinedApi")
    public void applyPrev(CaptureRequest.Builder captureBuilder) {
        Camera2ApiAutoFix.Apply();
        captureBuilder.set(CONTROL_ENABLE_ZSL,false);
        captureBuilder.set(NOISE_REDUCTION_MODE, NOISE_REDUCTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AE_MODE, aeModeOn);
        Point size = new Point(Interface.i.camera.mImageReaderYuv.getWidth(),Interface.i.camera.mImageReaderYuv.getHeight());
        double sizex = size.x;
        double sizey = size.y;
        //captureBuilder.set(CONTROL_AE_TARGET_FPS_RANGE,new Range<>(24,60));
        MeteringRectangle rectm8[] = new MeteringRectangle[2];
        rectm8[0] = new MeteringRectangle(new Rect((int)(sizex/3.0),(int)(sizey/3.0),(int)(sizex*2.0/3.0),(int)(sizey*2.0/3.0)),10);
        rectm8[1] = new MeteringRectangle(new Point((int)(sizex/2.0),(int)(sizey/2.0)),new Size((int)(sizex/7),(int)(sizey/7)),30);
        captureBuilder.set(CONTROL_AF_REGIONS,rectm8);
        captureBuilder.set(CONTROL_AE_REGIONS,rectm8);
        captureBuilder.set(CONTROL_AF_MODE, Interface.i.settings.afMode);
        captureBuilder.set(TONEMAP_MODE,TONEMAP_MODE_GAMMA_VALUE);
        float rgb[] = new float[64];
        for(int i =0; i<64; i+=2){
            float x = ((float)i)/64.f;
            rgb[i] = x;
            float output = 2.8114f*x+-3.5701f*x*x+1.6807f*x*x*x;
            output = Math.max(output,0.f);
            output = Math.min(output,1.f);
            //Log.d(TAG,"Curve:"+output);
            rgb[i+1] = output;
        }
        TonemapCurve tonemapCurve = new TonemapCurve(rgb,rgb,rgb);
        captureBuilder.set(TONEMAP_CURVE,tonemapCurve);
        //final CaptureRequest.Key<Boolean> EISV2 = new CaptureRequest.Key<Boolean>("com.qti.node.eisv2", boolean.class);
        //captureBuilder.set(EISV2, true);
        //CameraReflectionApi.native_set("com.qti.node.eisv2","1");
        //Log.d(TAG,"Points:"+captureBuilder.get(TONEMAP_PRESET_CURVE));
    }

    void put(int in) {
        sharedPreferencesEditor.putInt(mCameraID+"Settings:" + count, in);
        count++;
    }

    void put(double in) {
        sharedPreferencesEditor.putFloat(mCameraID+"Settings:" + count, (float) in);
        count++;
    }

    void put(String in) {
        sharedPreferencesEditor.putString(mCameraID+"Settings:" + count, in);
        count++;
    }

    void put(boolean in) {
        sharedPreferencesEditor.putBoolean(mCameraID+"Settings:" + count, in);
        count++;
    }

    boolean get(boolean in) {
        boolean result = sharedPreferences.getBoolean(mCameraID+"Settings:" + count, in);
        count++;
        return result;
    }

    int get(int cur) {
        int result;
        result = sharedPreferences.getInt(mCameraID+"Settings:" + count, cur);
        count++;
        return result;
    }

    double get(double cur) {
        double result;
        result = (double) (sharedPreferences.getFloat(mCameraID+"Settings:" + count, (float) (cur)));
        count++;
        return result;
    }

    String get(String cur) {
        String result;
        result = (sharedPreferences.getString(mCameraID+"Settings:" + count, (cur)));
        count++;
        return result;
    }
}
