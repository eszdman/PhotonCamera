package com.eszdman.photoncamera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.util.Log;
import android.util.Range;

public class IsoExpoSelector {
    private static String TAG = "IsoExpoSelector";
    private static final long sec = 1000000000;
    public static void setExpo(CaptureRequest.Builder builder, int step) {
        long exposuretime = Camera2Api.context.mPreviewExposuretime;
        int iso = Camera2Api.context.mPreviewIso;
        builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
        Log.d("IsoExpoSelector","InputParams: expotime:"+exposuretime+" iso:"+iso);
        if(exposuretime < sec/60 && iso > 1500) {
            exposuretime*=2;
            iso/=2;
        }
        if(exposuretime < sec/30 && iso > 1500) {
            exposuretime*=2;
            iso/=2;
        }
       if(exposuretime < sec/8 && iso > 1500) {
           exposuretime*=2;
           iso/=2;
       }
       //iso += iso*step/(Camera2Api.mburstcount*2);
       iso*=2;
       iso = Math.max(getISOLOW(),iso);
       iso = Math.min(getISOHIGH(),iso);
       Log.d(TAG,"IsoSelected:"+iso+" ExpoSelected:"+exposuretime);
       builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,exposuretime);
       builder.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
    }
    static int getISOHIGH(){
        Object key = Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if(key == null) return 3200;
        else {
            return (int)((Range)(key)).getUpper();
        }
    }
    static int getISOLOW(){
        Object key = Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if(key == null) return 100;
        else {
            return (int)((Range)(key)).getLower();
        }
    }
}
