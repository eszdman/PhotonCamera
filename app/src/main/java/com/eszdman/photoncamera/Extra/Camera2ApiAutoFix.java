package com.eszdman.photoncamera.Extra;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Rational;
import com.eszdman.photoncamera.Camera2Api;

import static android.hardware.camera2.CaptureResult.*;

public class Camera2ApiAutoFix {
    private CameraCharacteristics characteristics;
    private CaptureResult result;
    Camera2ApiAutoFix(CameraCharacteristics characteristic) {
        characteristics = characteristic;
    }
    Camera2ApiAutoFix(CaptureResult res) {
        result = res;
    }
    public static void Apply(){
        CameraCharacteristics  characteristics= Camera2Api.mCameraCharacteristics;
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(characteristics);
    }
    public static void ApplyRes(){
        CaptureResult characteristics= Camera2Api.mCaptureResult;
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(characteristics);
        fix.gains();
        fix.dynBL();
    }
    public void gains(){
        Rational[] WB = result.get(SENSOR_NEUTRAL_COLOR_POINT);
        if(WB == null) return;
        RggbChannelVector rggbChannelVector = result.get(COLOR_CORRECTION_GAINS);
        if(rggbChannelVector == null){
            CameraReflectionApi.set(COLOR_CORRECTION_GAINS,new RggbChannelVector(WB[0].floatValue(),1,1,WB[2].floatValue()));
            return;
        }
        if(rggbChannelVector.getRed() == 0 || rggbChannelVector.getGreenEven() == 0 || rggbChannelVector.getGreenOdd() == 0 || rggbChannelVector.getBlue() == 0){
            CameraReflectionApi.set(COLOR_CORRECTION_GAINS,new RggbChannelVector(WB[0].floatValue(),1,1,WB[2].floatValue()));
        }
    }
    public void dynBL(){
       float[] level = result.get(SENSOR_DYNAMIC_BLACK_LEVEL);
        BlackLevelPattern ptr = Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        int[] lvl = new int[4];
        if(ptr == null) return;
        ptr.copyTo(lvl,0);
       if(level == null){
           for(int i =0; i<4; i++){
               level[i] = lvl[i];
           }
       }
       for(int i=0; i<4;i++){
           if(level[i] == 0) {
               level[i] = lvl[i];
           }
       }
    }
}
