package com.eszdman.photoncamera;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.IdRes;
import static android.content.Context.MODE_PRIVATE;
import static android.hardware.camera2.CameraMetadata.*;
import static android.hardware.camera2.CaptureRequest.*;


public class Settings {
    public static Settings instance;
    int NoiseReduction = NOISE_REDUCTION_MODE_OFF;
    int AfMode = CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    public int framecount = 10;
    public int lumacount = 3;
    public int chromacount = 12;
    public boolean enhancedprocess = false;
    public boolean align = true;
    public boolean hdrx = true;
    public double saturation = 1.3;
    public double sharpness = 1.1;
    public double contrast_mpy = 1.3;
    public int contrast_const = 0;
    public double compressor = 1.0;
    public double gain = 1.0;

    TextView luma;
    TextView framestext;
    TextView chroma;
    Switch turnNR;
    Switch disablealign;
    Switch enhanced;

    SeekBar framecnt;
    SeekBar lumacnt;
    SeekBar chromacnt;

    //HDRX
    SeekBar sharp;
    SeekBar contrastmul;
    SeekBar contrastconst;
    SeekBar satur;
    SeekBar compress;
    SeekBar gains;
    TextView sattext;
    TextView sharptext;
    TextView contrasttext;
    TextView compgaintext;
    Settings(){
        instance = this;
        getsavedSettings();
    }
    double getDouble(int prog){
        return ((double)(prog))/100;
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
        enhanced = getView(R.id.setting_enhanced);
        //HDRX
        sharp = getView(R.id.setting_sharpcnt);
        contrastmul = getView(R.id.setting_contrmpy);
        contrastconst = getView(R.id.setting_contrconst);
        satur = getView(R.id.setting_saturation);
        compress = getView(R.id.setting_compressor);
        gains = getView(R.id.setting_gain);
        sharptext = getView(R.id.settings_sharpnesstext);
        contrasttext = getView(R.id.settings_contrasttext);
        compgaintext = getView(R.id.setting_compgainrext);
        sattext = getView(R.id.setting_sattext);
    }
    void getSettings(){
        views();
        if(turnNR.isChecked()) NoiseReduction = NOISE_REDUCTION_MODE_HIGH_QUALITY;
        else NoiseReduction = NOISE_REDUCTION_MODE_OFF;
        align = !disablealign.isChecked();
        enhancedprocess = enhanced.isChecked();

        saveSettings();
        saveViews();
    }
    Resources res = MainActivity.act.getResources();
    void openSettings(){
        MainActivity.act.setContentView(R.layout.settings);
        views();
        getViews();
        getsavedSettings();
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {




            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        };
        chromacnt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                chromacount = chromacnt.getProgress();
                chroma.setText(res.getText(R.string.chroma_nr_count).toString()+chromacount);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        lumacnt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lumacount = lumacnt.getProgress();
                luma.setText(res.getText(R.string.luma_nr_count).toString()+lumacount);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        framecnt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                framecount = framecnt.getProgress();
                framestext.setText(res.getText(R.string.frame_count).toString()+framecount);
                if(framecount == 1) framestext.setText("Unprocessed Output");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        contrastconst.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                contrast_const = contrastconst.getProgress();
                contrasttext.setText(res.getText(R.string.contrmul)+String.valueOf(contrast_mpy)+" "+res.getText(R.string.contrconst)+contrast_const);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        contrastmul.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                contrast_mpy = getDouble(contrastmul.getProgress());
                contrasttext.setText(res.getText(R.string.contrmul)+String.valueOf(contrast_mpy)+" "+res.getText(R.string.contrconst)+contrast_const);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sharp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sharpness = getDouble(sharp.getProgress());
                sharptext.setText(res.getText(R.string.sharpness)+String.valueOf(sharpness));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        satur.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                saturation = getDouble(satur.getProgress());
                sattext.setText(res.getText(R.string.saturation).toString()+saturation);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        compress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                compressor = getDouble(compress.getProgress());
                compgaintext.setText(res.getText(R.string.comp)+String.valueOf(compressor)+" "+res.getText(R.string.gain)+String.valueOf(gain));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        gains.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gain = getDouble(gains.getProgress());
                compgaintext.setText(res.getText(R.string.comp)+String.valueOf(compressor)+" "+res.getText(R.string.gain)+String.valueOf(gain));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        setViews();
    }
    void applyRes(CaptureRequest.Builder captureBuilder) {
        captureBuilder.set(JPEG_QUALITY, (byte) 100);
        captureBuilder.set(NOISE_REDUCTION_MODE, NoiseReduction);
        captureBuilder.set(HOT_PIXEL_MODE,HOT_PIXEL_MODE_HIGH_QUALITY);
        captureBuilder.set(COLOR_CORRECTION_MODE,COLOR_CORRECTION_MODE_HIGH_QUALITY);
        //captureBuilder.set(CONTROL_SCENE_MODE,CONTROL_SCENE_MODE_HDR);
        captureBuilder.set(EDGE_MODE,EDGE_MODE_HIGH_QUALITY);
        captureBuilder.set(CONTROL_AF_MODE,AfMode);
    }
    void applyPrev(CaptureRequest.Builder captureBuilder) {
        //captureBuilder.set(CONTROL_ENABLE_ZSL,true);
        captureBuilder.set(EDGE_MODE,EDGE_MODE_HIGH_QUALITY);
        captureBuilder.set(COLOR_CORRECTION_MODE,COLOR_CORRECTION_MODE_HIGH_QUALITY);
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
        putset(enhancedprocess);
        putset(sharpness);
        putset(contrast_mpy);
        putset(contrast_const);
        putset(saturation);
        putset(compressor);
        putset(gain);
        ed.commit();
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
        enhancedprocess = getset(enhancedprocess);
        sharpness = getset(sharpness);
        contrast_mpy = getset(contrast_mpy);
        contrast_const = getset(contrast_const);
        saturation = getset(saturation);
        compressor = getset(compressor);
        gain = getset(gain);
        cnt =0;
    }
    void setViews(){
        setv(disablealign,!align);
        setv(framecnt,framecount);
        setv(chromacnt,chromacount);
        setv(lumacnt,lumacount);
        setv(enhanced,enhancedprocess);
        setv(sharp,sharpness);
        setv(contrastconst,contrast_const);
        setv(contrastmul,contrast_mpy);
        setv(compress,compressor);
        setv(gains,gain);
        setv(satur,saturation);
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
    void putset(double in){
        Log.e("putset", "Saving:"+in+" Cur:"+(float)in);
        ed.putFloat("Settings:"+cnt,(float)in);
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
    double getset(double cur){
        double result;
        result= (double)(sPref.getFloat("Settings:"+cnt,(float)(cur)));
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
    void setv(Switch in,boolean val){
        in.setChecked(val);
    }
    void setv(TextView in,String val){
        in.setText(val);
    }
    void setv(SeekBar in,int val){
        in.setProgress(in.getMax()-1);
        in.setProgress(val);
    }
    void setv(SeekBar in,double val){
        val*=100;

        setv(in,(int)val);
    }
}
