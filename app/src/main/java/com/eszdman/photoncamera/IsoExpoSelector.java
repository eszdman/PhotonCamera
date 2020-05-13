package com.eszdman.photoncamera;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.util.Log;

public class IsoExpoSelector {
    private static final int sec = 1627500;
    public static void setExpo(CaptureRequest.Builder builder) {
        Object expotimeobj = Camera2Api.mPreviewResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        long exposuretime = 0;
        Object isoobj = Camera2Api.mPreviewResult.get(CaptureResult.SENSOR_SENSITIVITY);
        int iso = 0;
        if(expotimeobj != null) exposuretime = (long)expotimeobj;
        if(isoobj != null) iso = (int)isoobj;
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
