package com.eszdman.photoncamera.Extra;

import android.hardware.camera2.CameraCharacteristics;

import com.eszdman.photoncamera.Camera2Api;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CameraReflectionApi {
    public static <T> void set(CameraCharacteristics.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = Camera2Api.mCameraCharacteristics.getClass().getDeclaredField("mProperties");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(Camera2Api.mCameraCharacteristics);
            Method set = CameraMetadataNative.getClass().getDeclaredMethod("set", CameraCharacteristics.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative,key,value);
        } catch (Exception e ) {
            e.printStackTrace();
        }
    }
}
