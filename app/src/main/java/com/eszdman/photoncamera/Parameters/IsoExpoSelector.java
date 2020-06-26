package com.eszdman.photoncamera.Parameters;

import android.hardware.SensorEventListener;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;

import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.ui.CameraFragment;

public class IsoExpoSelector {
    private static String TAG = "IsoExpoSelector";

    public static void setExpo(CaptureRequest.Builder builder, int step) {
        ExpoPair pair = new ExpoPair();
        pair.iso = CameraFragment.context.mPreviewIso;
        pair.exposure = CameraFragment.context.mPreviewExposuretime;
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        Log.d(TAG, "InputParams: expo time:" + pair.exposure + " iso:" + pair.iso);
        if (pair.iso >= 12700) {
            pair.ReduceIso();
        }
        if (pair.exposure < ExposureIndex.sec / 40 && pair.iso > 300) {
            pair.ReduceIso();
        }
        if (pair.exposure < ExposureIndex.sec / 8 && pair.iso > 3000) {
            pair.ReduceIso();
        }
        if (pair.exposure < ExposureIndex.sec / 8 && pair.iso > 1500) {
            pair.ReduceIso();
        }
        if (CameraFragment.mTargetFormat == CameraFragment.rawFormat){
        if(step == 0){
            if(pair.iso < 500){
                pair.ReduceExpo();
            }
            if(pair.iso < 500*2.1){
                pair.ReduceExpo();
            }
        }
        if (pair.iso >= 100 * 1.35) pair.iso *= 0.65;
            else {
                pair.exposure *= 0.65;
            }
        }
        if(Interface.i.settings.ManualMode){
            pair.exposure = (long)(ExposureIndex.sec*Interface.i.manual.expvalue);
            pair.iso = Interface.i.manual.isovalue;
        }
        pair.iso = Math.max(getISOLOW(), pair.iso);
        pair.iso = Math.min(getISOHIGH(), pair.iso);
        Log.d(TAG, "IsoSelected:" + pair.iso + " ExpoSelected:" + pair.exposure + " step:"+step);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, pair.exposure);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, pair.iso);
    }

    public static int getISOHIGH() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 3200;
        else {
            return (int) ((Range) (key)).getUpper();
        }
    }

    public static int getISOLOW() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 100;
        else {
            return (int) ((Range) (key)).getLower();
        }
    }
    public static long getEXPHIGH() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (key == null) return ExposureIndex.sec;
        else {
            return (long) ((Range) (key)).getUpper();
        }
    }
    public static long getEXPLOW() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (key == null) return ExposureIndex.sec/1000;
        else {
            return (long) ((Range) (key)).getLower();
        }
    }
    static class ExpoPair {
        long exposure;
        int iso;
        public void ReduceIso(){
            iso/=2;
            exposure*=2;
            if(iso < getISOLOW()) iso = getISOLOW();
        }
        public void ReduceExpo(){
            iso*=2;
            exposure/=2;
            if(exposure <getEXPLOW()) exposure = getEXPLOW();
        }
    }
}
