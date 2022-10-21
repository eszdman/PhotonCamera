package com.particlesdevs.photoncamera.api;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.eszdman.photonbypass.ReflectBypass;
import com.particlesdevs.photoncamera.capture.CaptureController;

import org.chickenhook.restrictionbypass.RestrictionBypass;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class CameraReflectionApi {
    //private static final String TAG = "CameraReflectionApi";
    public static CaptureResult.Key<?> createKeyResult(String name, Class<?> type){
        Class captureResultKey = ReflectBypass.findClass("android/hardware/camera2/CaptureResult$Key");
        try {
            Constructor constructor = captureResultKey.getConstructor(String.class, Class.class);
            return (CaptureResult.Key<?>) constructor.newInstance(name,type);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> void set(CameraCharacteristics.Key<T> key, T value) {
        try {
            //Class<?> metadataNativeClass = ReflectBypass.findClass("android/hardware/camera2/impl/CameraMetadataNative");
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
    public static void createCustomCaptureSession(CameraDevice cameraDevice,
                                                  InputConfiguration inputConfig,
                                                  List<OutputConfiguration> outputs,
                                                  int operatingMode,
                                                  CameraCaptureSession.StateCallback callback,
                                                  Handler handler) throws CameraAccessException, InvocationTargetException, IllegalAccessException {
        Method createCustomCaptureSession = null;
        try {
            createCustomCaptureSession = RestrictionBypass.getDeclaredMethod(cameraDevice.getClass(), "createCustomCaptureSession",
            //createCustomCaptureSession = cameraDevice.getClass().getDeclaredMethod("createCustomCaptureSession",
                    InputConfiguration.class,List.class,Integer.TYPE,CameraCaptureSession.StateCallback.class,Handler.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        createCustomCaptureSession.setAccessible(true);
        createCustomCaptureSession.invoke(cameraDevice,inputConfig,outputs,operatingMode,callback,handler);

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
