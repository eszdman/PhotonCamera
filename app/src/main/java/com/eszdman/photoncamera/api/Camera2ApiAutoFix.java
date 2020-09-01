package com.eszdman.photoncamera.api;

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Log;
import android.util.Range;
import android.util.Rational;

import com.eszdman.photoncamera.Parameters.ExposureIndex;

import java.lang.reflect.Field;

import static android.hardware.camera2.CaptureResult.*;
import static android.hardware.camera2.CameraCharacteristics.*;
@SuppressWarnings("ALL")
public class Camera2ApiAutoFix {
    private static final String TAG = "Camera2ApiAutoFix";
    private CameraCharacteristics characteristics;
    private CaptureResult result;
    Camera2ApiAutoFix(CameraCharacteristics characteristic) {
        characteristics = characteristic;
    }
    Camera2ApiAutoFix(CaptureResult res) {
        result = res;
    }
    public static void Init(){
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(CameraFragment.mCameraCharacteristics);
        fix.ExposureTime();
    }
    public static void Apply(){
        CameraCharacteristics  characteristics= CameraFragment.mCameraCharacteristics;
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(characteristics);
        fix.MaxRegionsAF();
    }
    public static void ApplyRes(){
        CaptureResult characteristics= CameraFragment.mCaptureResult;
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(characteristics);
        //fix.gains();
        fix.BL();
        fix.whitePoint();
        fix.CCM();
    }
    private void whitePoint(){
        CameraReflectionApi.set(SENSOR_NEUTRAL_COLOR_POINT,Interface.i.camera.mPreviewTemp);
    }
    private void CCM(){
        CameraReflectionApi.set(COLOR_CORRECTION_TRANSFORM,Interface.i.camera.mColorSpaceTransform);
    }
    public void curve(){
        CameraReflectionApi.set(TONEMAP_MAX_CURVE_POINTS,128);
    }
    boolean checkdouble(double in){
        return (((int)in*100) %100 == 0);
    }
    private void ExposureTime(){
        Range exprange = characteristics.get(SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if(exprange == null) return;
        if((long)exprange.getUpper() < ExposureIndex.sec/7){
            Log.d(TAG,"Applied Fix ExposureTime no CIT");
            Range nrange = new Range(exprange.getLower(),ExposureIndex.sec/3);
            CameraReflectionApi.set(SENSOR_INFO_EXPOSURE_TIME_RANGE,nrange);
        } else if((long)exprange.getUpper() > ExposureIndex.sec*5/6 && (long)exprange.getUpper() < ExposureIndex.sec*2){
            Log.d(TAG,"Applied Fix ExposureTime2 CIT SHIFT");
            Range nrange = new Range(exprange.getLower(),(long)(ExposureIndex.sec*5.2));
            CameraReflectionApi.set(SENSOR_INFO_EXPOSURE_TIME_RANGE,nrange);
        }
    }
    private void MaxRegionsAF(){
        //CameraReflectionApi.set(CONTROL_MAX_REGIONS_AF,5);
    }
    public void gains(){
        CameraReflectionApi.setVERBOSE(true);
        Rational[] WB = result.get(SENSOR_NEUTRAL_COLOR_POINT);
        if(WB == null) return;
        RggbChannelVector rggbChannelVector = result.get(COLOR_CORRECTION_GAINS);
        if(rggbChannelVector == null){
            CameraReflectionApi.set(COLOR_CORRECTION_GAINS,new RggbChannelVector(WB[0].floatValue()*1.3f,WB[1].floatValue()/1.78f,WB[1].floatValue()/1.78f,WB[2].floatValue()*2f));
        }
        Log.d(TAG,"Initial channelVector:"+rggbChannelVector.toString());
        if(checkdouble(rggbChannelVector.getRed()) && checkdouble(rggbChannelVector.getGreenEven()) && checkdouble(rggbChannelVector.getGreenOdd()) && checkdouble(rggbChannelVector.getBlue()))
        try {
            Field field = rggbChannelVector.getClass().getDeclaredField("mRed");
            field.setAccessible(true);
            field.set(rggbChannelVector, 1f/WB[0].floatValue());
            field = rggbChannelVector.getClass().getDeclaredField("mGreenEven");
            field.setAccessible(true);
            field.set(rggbChannelVector, 1f/WB[1].floatValue());
            field = rggbChannelVector.getClass().getDeclaredField("mGreenOdd");
            field.setAccessible(true);
            field.set(rggbChannelVector, 1f/WB[1].floatValue());
            field = rggbChannelVector.getClass().getDeclaredField("mBlue");
            field.setAccessible(true);
            field.set(rggbChannelVector, 1f/WB[2].floatValue());
            CameraReflectionApi.set(COLOR_CORRECTION_GAINS,null);
            CameraReflectionApi.set(COLOR_CORRECTION_GAINS,rggbChannelVector);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        Log.d(TAG,"Overrided channelVector:"+rggbChannelVector.toString());
    }
    @SuppressLint("NewApi")
    public void BL(){
       float[] level = result.get(SENSOR_DYNAMIC_BLACK_LEVEL);
        BlackLevelPattern ptr = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if(ptr == null) return;
       if(level == null){
           level = new float[4];
           for(int i =0; i<4; i++){
               level[i] = ptr.getOffsetForIndex(i%2,i/2);
           }
           CameraReflectionApi.set(SENSOR_DYNAMIC_BLACK_LEVEL,level);
       }
    }
}
