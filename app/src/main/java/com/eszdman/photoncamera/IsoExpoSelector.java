package com.eszdman.photoncamera;

import android.hardware.camera2.CaptureRequest;
import android.util.Log;

public class IsoExpoSelector {
    private static final int sec = 1627500;
    public static void setExpo(CaptureRequest.Builder builder) {
        long exposuretime = Camera2Api.context.mPreviewExposuretime;
        int iso = Camera2Api.context.mPreviewIso;
        //builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
        Log.d("IsoExpoSelector","InputParams: expotime:"+exposuretime+" iso:"+iso);
        /*if(exposuretime < sec/60 && iso > 1500) {
            exposuretime*=2;
            iso/=2;
        }
        if(exposuretime < sec/30 && iso > 1500) {
            exposuretime*=2;
            iso/=2;
        }
       if(exposuretime < sec/15 && iso > 1500) {
           exposuretime*=2;
           iso/=2;
       }*/
       builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,exposuretime);
       builder.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
    }
}
