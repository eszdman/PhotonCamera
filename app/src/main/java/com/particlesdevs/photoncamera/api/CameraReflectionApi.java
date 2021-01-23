package com.particlesdevs.photoncamera.api;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;

import com.particlesdevs.photoncamera.capture.CaptureController;

import org.chickenhook.restrictionbypass.RestrictionBypass;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CameraReflectionApi {
    //private static final String TAG = "CameraReflectionApi";

    public static <T> void set(CameraCharacteristics.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = RestrictionBypass.getDeclaredField(CameraCharacteristics.class, "mProperties");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(CaptureController.mCameraCharacteristics);
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(), "set", CameraCharacteristics.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> void set(CaptureResult.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = RestrictionBypass.getDeclaredField(CaptureResult.class, "mResults");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(CaptureController.mCaptureResult);
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(), "set", CaptureResult.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> void set(CaptureResult.Key<T> key, T value, CaptureResult res) {
        try {
            Field CameraMetadataNativeField = RestrictionBypass.getDeclaredField(CaptureResult.class, "mResults");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(res);
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(), "set", CaptureResult.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> void set(CaptureRequest request, CaptureRequest.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = RestrictionBypass.getDeclaredField(CaptureRequest.class, "mLogicalCameraSettings");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(request);
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(), "set", CaptureRequest.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void PatchBL(BlackLevelPattern pattern, int[] bl) {
        try {
            //noinspection JavaReflectionMemberAccess
            Field mCfaOffsetsField = pattern.getClass().getDeclaredField("mCfaOffsets");
            mCfaOffsetsField.setAccessible(true);
            Object mCfaOffsets = mCfaOffsetsField.get(pattern);
            for (int i = 0; i < 4; i++) {
                assert mCfaOffsets != null;
                Array.set(mCfaOffsets, i, bl[i]);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public static Field[] getAllMetadataFields() {
        return CameraMetadata.class.getDeclaredFields();
    }
}
