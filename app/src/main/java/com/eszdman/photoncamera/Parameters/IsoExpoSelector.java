package com.eszdman.photoncamera.Parameters;

import android.graphics.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.util.Log;
import android.util.Range;

import com.eszdman.photoncamera.Camera2Api;
import com.eszdman.photoncamera.Settings;

public class IsoExpoSelector {
    private static String TAG = "IsoExpoSelector";
    public static void setExpo(CaptureRequest.Builder builder, int step) {
        long exposuretime = Camera2Api.context.mPreviewExposuretime;
        int iso = Camera2Api.context.mPreviewIso;
        builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
        Log.d("IsoExpoSelector","InputParams: expotime:"+exposuretime+" iso:"+iso);
        if(iso >= 12700){
            exposuretime*=2.0;
            iso/=2;
            //Settings.instance.framecount = 8;
        }
       if(exposuretime < ExposureIndex.sec/8 && iso > 1500) {
           exposuretime*=2.0;
           iso/=2;
       }
       if(iso > 4000) exposuretime*=1.35;
       //iso += iso*step/(Camera2Api.mburstcount*2);
        if(Camera2Api.mTargetFormat == Camera2Api.rawFormat)
      if(iso >= 100*1.35) iso*=0.80;
       else{
           exposuretime*=0.80;
       }
       //if(step%3==1) iso*=1.1;
       //if(step%3 ==2) iso*=0.35;
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
