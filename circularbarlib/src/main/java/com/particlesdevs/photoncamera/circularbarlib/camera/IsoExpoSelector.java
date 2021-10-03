package com.particlesdevs.photoncamera.circularbarlib.camera;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Range;

/**
 * Created by Eszdman
 */
public class IsoExpoSelector {
    public static final long sec = 1000000000;


    private static double mpy1 = 1.0;

    public static double getMPY(CameraCharacteristics cameraCharacteristics) {
        return 100.0 / getISOLOW(cameraCharacteristics);
    }

    private static int mpyIso(int in, CameraCharacteristics cameraCharacteristics) {
        return (int) (in * getMPY(cameraCharacteristics));
    }

    private static int getISOHIGH(CameraCharacteristics cameraCharacteristics) {
        Object key = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 3200;
        else {
            return (int) ((Range) (key)).getUpper();
        }
    }

    public static int getISOHIGHExt(CameraCharacteristics cameraCharacteristics) {
        return mpyIso(getISOHIGH(cameraCharacteristics), cameraCharacteristics);
    }

    private static int getISOLOW(CameraCharacteristics cameraCharacteristics) {
        Object key = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (key == null) return 100;
        else {
            return (int) ((Range) (key)).getLower();
        }
    }

    public static int getISOAnalog(CameraCharacteristics cameraCharacteristics) {
        Object key = cameraCharacteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);
        if (key == null) return 100;
        else {
            return (int) (key);
        }
    }

    public static int getISOLOWExt(CameraCharacteristics cameraCharacteristics) {
        return mpyIso(getISOLOW(cameraCharacteristics), cameraCharacteristics);
    }

    public static long getEXPHIGH(CameraCharacteristics cameraCharacteristics) {
        Object key = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (key == null) return sec;
        else {
            return (long) ((Range) (key)).getUpper();
        }
    }

    public static long getEXPLOW(CameraCharacteristics cameraCharacteristics) {
        Object key = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (key == null) return sec / 1000;
        else {
            return (long) ((Range) (key)).getLower();
        }
    }


}
