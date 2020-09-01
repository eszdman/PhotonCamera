package com.eszdman.photoncamera.api;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;

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
            Object CameraMetadataNative = CameraMetadataNativeField.get(CameraFragment.mCameraCharacteristics);//Ur camera Characteristics
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(),"set",CameraCharacteristics.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> void set(CaptureResult.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = RestrictionBypass.getDeclaredField(CaptureResult.class,"mResults");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(CameraFragment.mCaptureResult);
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(),"set", CaptureResult.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static <T> void set(CaptureResult.Key<T> key, T value,CaptureResult res) {
        try {
            Field CameraMetadataNativeField = RestrictionBypass.getDeclaredField(CaptureResult.class,"mResults");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(res);
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(),"set", CaptureResult.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static <T> void set(CaptureRequest request,CaptureRequest.Key<T> key, T value) {
        try {
            Field CameraMetadataNativeField = RestrictionBypass.getDeclaredField(CaptureRequest.class,"mLogicalCameraSettings");
            CameraMetadataNativeField.setAccessible(true);
            Object CameraMetadataNative = CameraMetadataNativeField.get(request);
            assert CameraMetadataNative != null;
            Method set = RestrictionBypass.getDeclaredMethod(CameraMetadataNative.getClass(),"set", CaptureRequest.Key.class, Object.class);
            set.setAccessible(true);
            set.invoke(CameraMetadataNative, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void PatchBL(BlackLevelPattern pattern, int[] bl){
        try {
            //noinspection JavaReflectionMemberAccess
            Field mCfaOffsetsField = pattern.getClass().getDeclaredField("mCfaOffsets");
            mCfaOffsetsField.setAccessible(true);
            Object mCfaOffsets = mCfaOffsetsField.get(pattern);
            for(int i =0; i<4;i++){
                assert mCfaOffsets != null;
                Array.set(mCfaOffsets,i,bl[i]);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public static Field[] getAllMetadataFields(){
        return  CameraMetadata.class.getDeclaredFields();
    }
    /*public static void PrintMethods(Object in){
        Log.d(TAG,"StartPrinting:"+in.getClass());
        Method[] methods = in.getClass().getDeclaredMethods();
        int cnt = 0;
        for(Method m : methods) {Log.d(TAG,"["+cnt+"]"+m.toString());cnt++;}
    }
    public static void PrintFields(Object in){
        Log.d(TAG,"StartPrinting:"+in.getClass());
        Field[] fields = in.getClass().getDeclaredFields();
        int cnt = 0;
        for(Field f : fields) {Log.d(TAG,"["+cnt+"]"+f.toString());cnt++;}
    }*/
    public static void setVERBOSE(boolean in) {
        Object capres = CameraFragment.mCaptureResult;//Ur camera CaptureResult
        Field verbose;
        try {
            //noinspection JavaReflectionMemberAccess
            verbose =  CaptureResult.class.getDeclaredField("VERBOSE");
            verbose.setAccessible(true);
            verbose.set(capres, in);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }
}
