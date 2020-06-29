package com.eszdman.photoncamera.Parameters;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;

import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

public class IsoExpoSelector {
    private static String TAG = "IsoExpoSelector";

    public static void setExpo(CaptureRequest.Builder builder, int step) {
        ExpoPair pair = new ExpoPair(CameraFragment.context.mPreviewExposuretime,getEXPLOW(),getEXPHIGH(),
                CameraFragment.context.mPreviewIso,getISOLOW(),getISOHIGH());
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        pair.normalizeiso100();
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
        if (pair.exposure < ExposureIndex.sec / 13 && pair.iso > 1500) {
            pair.ReduceIso();
        }
        if (CameraFragment.mTargetFormat == CameraFragment.rawFormat){
        if (pair.iso >= 100 * 1.35) pair.iso *= 0.65;
            else {
                pair.exposure *= 0.65;
            }
        }
        if(Interface.i.settings.ManualMode){
            pair.exposure = (long)(ExposureIndex.sec*Interface.i.manual.expvalue);
            pair.iso = (int)(Interface.i.manual.isovalue/getMPY());
        }
        if(step == 1){
            if(pair.iso <= 150){
                pair.ReduceExpo(2);
            }
            if(pair.iso <= 310){
                pair.ReduceExpo(1.5);
            }
            if(pair.exposure > ExposureIndex.sec/4 && pair.iso <= 2000){
                pair.ReduceExpo(2.0);
            }
            if(pair.exposure > ExposureIndex.sec/4 && pair.iso <= 2000){
                pair.ReduceExpo(2.0);
            }
            if(pair.exposure > ExposureIndex.sec/4 && pair.iso <= 3000){
                pair.ReduceExpo(1.5);
            }
        }
        pair.denormalizeSystem();
        Log.d(TAG, "IsoSelected:" + pair.iso + " ExpoSelected:" + pair.exposure + " step:"+step);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, pair.exposure);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, pair.iso);
    }


    public static double getMPY(){
        return 50.0/getISOLOW();
    }
    private static int mpyIso(int in){
        return (int)(in*getMPY());
    }
    private static int getISOHIGH() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 3200;
        else {
            return (int) ((Range) (key)).getUpper();
        }
    }
    public static int getISOHIGHExt() {
        return mpyIso(getISOHIGH());
    }
    private static int getISOLOW() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 100;
        else {
            return (int) ((Range) (key)).getLower();
        }
    }
    public static int getISOLOWExt() {
        return mpyIso(getISOLOW());
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
        long exposurehigh,exposurelow;
        int iso;
        int isolow,isohigh;
        public ExpoPair(long expo,long expl,long exph,int is,int islow,int ishigh){
            exposure = expo;
            iso = is;
            exposurehigh = exph;
            exposurelow = expl;
            isolow = islow;
            isohigh = ishigh;
        }
        public void normalizeiso100(){
            double mpy = 100.0/isolow;
            iso*=mpy;
        }
        public void denormalizeSystem(){
            double div = 100.0/isolow;
            iso/=div;
        }
        public void normalize(){
            if(iso > isohigh) iso = isohigh;
            if(iso < isolow) iso = isolow;
            if(exposure > exposurehigh) exposure = exposurehigh;
            if(exposure < exposurelow) exposure = exposurelow;
        }
        public void ReduceIso(){
            iso/=2;
            exposure*=2;
            normalize();
        }
        public void ReduceExpo(){
            ReduceExpo(2);
        }
        public void ReduceExpo(double k){
            Log.d(TAG,"ExpoReducing iso:"+iso+" expo:"+ exposure);
            iso*=k;
            exposure/=k;
            normalize();
            Log.d(TAG,"ExpoReducing done iso:"+iso+" expo:"+ exposure);
        }

    }
}
