package com.eszdman.photoncamera;

import android.content.SharedPreferences;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

import static android.content.Context.MODE_PRIVATE;
import static android.hardware.camera2.CameraMetadata.*;
import static android.hardware.camera2.CaptureRequest.*;


public class Settings {
    public static Settings instance;
    int NoiseReduction = NOISE_REDUCTION_MODE_OFF;
    int AfMode = CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    Switch turnNR;
    Settings(){
        instance = this;
    }
    void views(){
        turnNR = MainActivity.act.findViewById(R.id.setting_turnNR);
    }
    void getSettings(){
        views();
        if(turnNR.isChecked()) NoiseReduction = NOISE_REDUCTION_MODE_HIGH_QUALITY;
        else NoiseReduction = NOISE_REDUCTION_MODE_OFF;



        saveSettings();
        saveViews();
    }
    void openSettings(){
        MainActivity.act.setContentView(R.layout.settings);
        views();
        getViews();
        getsavedSettings();
    }
    void applyRes(CaptureRequest.Builder captureBuilder) {
        captureBuilder.set(JPEG_QUALITY, (byte) 100);
        captureBuilder.set(NOISE_REDUCTION_MODE, NoiseReduction);
        captureBuilder.set(CONTROL_AF_MODE,AfMode);
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    void applyPrev(CaptureRequest.Builder captureBuilder) {
        captureBuilder.set(CONTROL_ENABLE_ZSL,true);
        captureBuilder.set(NOISE_REDUCTION_MODE, NOISE_REDUCTION_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AF_MODE,AfMode);
    }


    void saveSettings(){
        sPref = MainActivity.act.getPreferences(MODE_PRIVATE);
        ed = sPref.edit();
        putset(NoiseReduction);
        putset(AfMode);



        ed.apply();
        cnt = 0;
    }
    void getsavedSettings(){
        sPref = MainActivity.act.getPreferences(MODE_PRIVATE);
        NoiseReduction = getset(NoiseReduction);
        AfMode = getset(AfMode);



        cnt =0;
    }

    void saveViews(){
        ed = sPref.edit();
        putview(turnNR);


        ed.commit();
        cnt2 =0;
    }
    void getViews(){
        sPref = MainActivity.act.getPreferences(MODE_PRIVATE);
        getview(turnNR);



        cnt2 =0;
    }



    int cnt =0;
    SharedPreferences.Editor ed;
    SharedPreferences sPref;
    void putset(int in){
        ed.putInt("Settings:"+cnt,in);
        cnt++;
    }
    void putset(String in){
        ed.putString("Settings:"+cnt,in);
        cnt++;
    }
    int getset(int cur){
        int result;
        result= sPref.getInt("Settings:"+cnt,-1);
        cnt++;
        if(result == -1) return cur;
        return result;
    }
    int cnt2 =0;
    void putview(Switch in){
        ed.putBoolean("SettingsView:"+cnt2,in.isChecked());
        cnt2++;
    }
    void getview(Switch in){
        in.setChecked(sPref.getBoolean("SettingsView:"+cnt2,in.isChecked()));
        cnt2++;
    }
}
