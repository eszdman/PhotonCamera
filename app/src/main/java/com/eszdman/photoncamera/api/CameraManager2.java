package com.eszdman.photoncamera.api;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.ImageView;

import org.chickenhook.restrictionbypass.RestrictionBypass;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import static android.content.Context.MODE_PRIVATE;

public class CameraManager2 {
    private static String TAG = "CameraManager2";
    public static CameraManager2 cameraManager2;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private SharedPreferences sharedPreferences;
    public CameraManager2(CameraManager manag) {
        cameraManager2 = this;
        sharedPreferences = Interface.i.mainActivity.getPreferences(MODE_PRIVATE);
        if(!sharedPreferences.getBoolean("GotAux",false)){
            getCameraIdList();
            save();
        } else {

        }
    }
    public String[] getCameraIdList() {
        String[] strArr= new String[1];
        return strArr;
    }

    void save() {
        sharedPreferencesEditor = sharedPreferences.edit();
        sharedPreferencesEditor.putBoolean("GotAux", true);
        sharedPreferencesEditor.apply();
    }
}
