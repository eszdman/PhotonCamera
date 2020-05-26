package com.eszdman.photoncamera.api;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;

import com.eszdman.photoncamera.ui.CameraFragment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CameraReflectionApi {
    private static String TAG = "CameraReflectionApi";

    public static <T> void set(CameraCharacteristics.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = CameraCharacteristics.class.getDeclaredField("mProperties");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(CameraFragment.mCameraCharacteristics);//Ur camera Characteristics
            Method set = CameraMetadataNative.getClass().getDeclaredMethod("set", CameraCharacteristics.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> void set(CaptureResult.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = CaptureResult.class.getDeclaredField("mResults");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(CameraFragment.mCaptureResult);//Ur camera CaptureResult
            Method set = CameraMetadataNative.getClass().getDeclaredMethod("set", CaptureResult.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setVERBOSE(boolean in) {
        Object capres = CameraFragment.mCaptureResult;//Ur camera CaptureResult
        Field verbose = null;
        try {
            verbose =  CaptureResult.class.getDeclaredField("VERBOSE");
            verbose.setAccessible(true);
            verbose.set(capres, in);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }
}
