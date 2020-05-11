package com.eszdman.photoncamera;

import android.content.SharedPreferences;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.support.annotation.IdRes;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import static android.content.Context.MODE_PRIVATE;
import static android.hardware.camera2.CameraMetadata.*;
import static android.hardware.camera2.CaptureRequest.*;


public class Settings {
    public static Settings instance;
    int NoiseReduction = NOISE_REDUCTION_MODE_OFF;
    int AfMode = CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    public int framecount = 3;
    public int lumacount = 3;
    public int chromacount = 3;
    public boolean align = true;
    Switch turnNR;
    Switch disablealign;
    TextView framestext;
    SeekBar framecnt;
    TextView luma;
    SeekBar lumacnt;
    TextView chroma;
    SeekBar chromacnt;
    Settings(){
        instance = this;
        getsavedSettings();
    }
    <T extends View> T getView(@IdRes int id){
        return MainActivity.act.findViewById(id);
    }
    void views(){
        turnNR = getView(R.id.setting_turnNR);
        disablealign = getView(R.id.setting_disablealign);
        framestext = getView(R.id.setting_framecounter);
        framecnt = getView(R.id.setting_framecnt);
        lumacnt = getView(R.id.setting_lumacnt);
        chromacnt = getView(R.id.setting_chromacnt);
        chroma = getView(R.id.setting_chroma);
        luma = getView(R.id.setting_luma);
    }
    void getSettings(){
        views();
        if(turnNR.isChecked()) NoiseReduction = NOISE_REDUCTION_MODE_HIGH_QUALITY;
        else NoiseReduction = NOISE_REDUCTION_MODE_OFF;
        align = !disablealign.isChecked();


        saveSettings();
        saveViews();
    }
    void openSettings(){
        MainActivity.act.setContentView(R.layout.settings);
        views();
        getViews();
        getsavedSettings();
        chromacnt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                chroma.setText("Chroma NR count:"+chromacnt.getProgress());
                chromacount = chromacnt.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        lumacnt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                luma.setText("Luma NR count:"+lumacnt.getProgress());
                lumacount = lumacnt.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        framecnt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                framestext.setText("Frame count:"+framecnt.getProgress());
                framecount = framecnt.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
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
        putset(framecount);
        putset(align);
        putset(lumacount);
        putset(chromacount);
        ed.apply();
        cnt = 0;
    }
    void getsavedSettings(){
        sPref = MainActivity.act.getPreferences(MODE_PRIVATE);
        NoiseReduction = getset(NoiseReduction);
        AfMode = getset(AfMode);
        framecount = getset(framecount);
        align = getset(align);
        lumacount = getset(lumacount);
        chromacount = getset(chromacount);
        cnt =0;
    }

    void saveViews(){
        ed = sPref.edit();
        putview(turnNR);
        putview(disablealign);
        putview(framestext);
        putview(framecnt);
        putview(chromacnt);
        putview(chroma);
        putview(lumacnt);
        putview(luma);
        ed.commit();
        cnt2 =0;
    }
    void getViews(){
        sPref = MainActivity.act.getPreferences(MODE_PRIVATE);
        getview(turnNR);
        getview(disablealign);
        getview(framestext);
        getview(framecnt);
        getview(chromacnt);
        getview(chroma);
        getview(lumacnt);
        getview(luma);

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
    void putset(boolean in){
        ed.putBoolean("Settings:"+cnt,in);
        cnt++;
    }
    boolean getset(boolean in){
        boolean result = sPref.getBoolean("Settings:"+cnt,in);
        cnt++;
        return result;
    }
    int getset(int cur){
        int result;
        result= sPref.getInt("Settings:"+cnt,cur);
        cnt++;
        return result;
    }
    int cnt2 =0;
    void putview(Switch in){
        ed.putBoolean("SettingsView:"+cnt2,in.isChecked());
        cnt2++;
    }
    void putview(TextView in){
        ed.putString("SettingsView:"+cnt2,in.getText().toString());
        cnt2++;
    }
    void putview(SeekBar in){
        ed.putInt("SettingsView:"+cnt2,in.getProgress());
        cnt2++;
    }
    void getview(Switch in){
        in.setChecked(sPref.getBoolean("SettingsView:"+cnt2,in.isChecked()));
        cnt2++;
    }
    void getview(TextView in){
        in.setText(sPref.getString("SettingsView:"+cnt2,in.getText().toString()));
        cnt2++;
    }
    void getview(SeekBar in){
        in.setProgress(sPref.getInt("SettingsView:"+cnt2,in.getProgress()));
        cnt2++;
    }
}
