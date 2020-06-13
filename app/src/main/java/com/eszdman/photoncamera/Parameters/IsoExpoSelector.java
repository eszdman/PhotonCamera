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
        long exposuretime = CameraFragment.context.mPreviewExposuretime;
        int iso = CameraFragment.context.mPreviewIso;
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        Log.d(TAG, "InputParams: expo time:" + exposuretime + " iso:" + iso);
        if (iso >= 12700) {
            exposuretime *= 2.0;
            iso /= 2;
        }
        if (exposuretime < ExposureIndex.sec / 40 && iso > 300) {
            exposuretime *= 2.0;
            iso /= 2;
        }
        if (exposuretime < ExposureIndex.sec / 8 && iso > 3000) {
            exposuretime *= 2.0;
            iso /= 2;
        }
        if (exposuretime < ExposureIndex.sec / 8 && iso > 1500) {
            exposuretime *= 2.0;
            iso /= 2;
        }
        //if (iso > 4000) exposuretime *= 1.35;
        if (CameraFragment.mTargetFormat == CameraFragment.rawFormat)
            if (iso >= 100 * 1.35) iso *= 0.65;
            else {
                exposuretime *= 0.65;
            }
        if(Interface.i.settings.ManualMode){
            exposuretime = (long)(ExposureIndex.sec*Interface.i.manual.expvalue);
            iso = Interface.i.manual.isovalue;
        }
        iso = Math.max(getISOLOW(), iso);
        iso = Math.min(getISOHIGH(), iso);
        Log.d(TAG, "IsoSelected:" + iso + " ExpoSelected:" + exposuretime);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposuretime);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
    }

    static int getISOHIGH() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 3200;
        else {
            return (int) ((Range) (key)).getUpper();
        }
    }

    static int getISOLOW() {
        Object key = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 100;
        else {
            return (int) ((Range) (key)).getLower();
        }
    }
}
