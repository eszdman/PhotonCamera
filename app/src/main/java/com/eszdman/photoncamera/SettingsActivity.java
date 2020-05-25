package com.eszdman.photoncamera;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import com.eszdman.photoncamera.api.Interface;

import static android.hardware.camera2.CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_STATE_LOCKED;
import static android.hardware.camera2.CameraMetadata.EDGE_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON;
import static android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.EDGE_MODE;
import static android.hardware.camera2.CaptureRequest.HOT_PIXEL_MODE;
import static android.hardware.camera2.CaptureRequest.JPEG_QUALITY;
import static android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE;
import static android.hardware.camera2.CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE;

public class SettingsActivity extends AppCompatActivity {
    TextView luma;
    TextView framestext;
    TextView chroma;
    Switch turnNR;
    Switch disablealign;
    Switch enhanced;
    Switch gridOnOff;

    SeekBar frameCount;
    SeekBar lumenCount;
    SeekBar chromaCount;

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

    private int count = 0;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private SharedPreferences sharedPreferences;

    double getDouble(int prog) {
        return ((double) (prog)) / 100;
    }

    <T extends View> T getView(@IdRes int id) {
        return findViewById(id);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Interface.i.settings.load();
        views();
        sharedPreferences = getPreferences(MODE_PRIVATE);
        chromaCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.chromaCount = chromaCount.getProgress();
                chroma.setText(res.getText(R.string.chroma_nr_count).toString() + Interface.i.settings.chromaCount);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        lumenCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.lumenCount = lumenCount.getProgress();
                luma.setText(res.getText(R.string.luma_nr_count).toString() + Interface.i.settings.lumenCount);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        frameCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.frameCount = frameCount.getProgress();
                framestext.setText(res.getText(R.string.frame_count).toString() + Interface.i.settings.frameCount);
                if (FrameNumberSelector.frameCount == 1) framestext.setText("Unprocessed Output");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        contrastconst.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.contrast_const = contrastconst.getProgress();
                contrasttext.setText(res.getText(R.string.contrmul) + String.valueOf(Interface.i.settings.contrast_mpy) + " " + res.getText(R.string.contrconst) + Interface.i.settings.contrast_const);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        contrastmul.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.contrast_mpy = getDouble(contrastmul.getProgress());
                contrasttext.setText(res.getText(R.string.contrmul) + String.valueOf(Interface.i.settings.contrast_mpy) + " " + res.getText(R.string.contrconst) + Interface.i.settings.contrast_const);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sharp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.sharpness = getDouble(sharp.getProgress());
                sharptext.setText(res.getText(R.string.sharpness) + String.valueOf(Interface.i.settings.sharpness));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        satur.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.saturation = getDouble(satur.getProgress());
                sattext.setText(res.getText(R.string.saturation).toString() + Interface.i.settings.saturation);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        compress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.compressor = getDouble(compress.getProgress());
                compgaintext.setText(res.getText(R.string.comp) + String.valueOf(Interface.i.settings.compressor) + " " + res.getText(R.string.gain) + String.valueOf(Interface.i.settings.gain));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        gains.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Interface.i.settings.gain = getDouble(gains.getProgress());
                compgaintext.setText(res.getText(R.string.comp) + String.valueOf(Interface.i.settings.compressor) + " " + res.getText(R.string.gain) + String.valueOf(Interface.i.settings.gain));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        set();
    }

    @Override
    protected void onPause() {
        sharedPreferencesEditor = sharedPreferences.edit();
        get();
        Interface.i.settings.save();
        save();
        super.onPause();
    }

    void set() {
        setv(disablealign, !Interface.i.settings.align);
        setv(frameCount, Interface.i.settings.frameCount);
        setv(chromaCount, Interface.i.settings.chromaCount);
        setv(lumenCount, Interface.i.settings.lumenCount);
        setv(enhanced, Interface.i.settings.enhancedProcess);
        setv(gridOnOff, Interface.i.settings.grid);
        setv(sharp, Interface.i.settings.sharpness);
        setv(contrastconst, Interface.i.settings.contrast_const);
        setv(contrastmul, Interface.i.settings.contrast_mpy);
        setv(compress, Interface.i.settings.compressor);
        setv(gains, Interface.i.settings.gain);
        setv(satur, Interface.i.settings.saturation);
    }

    void views() {
        gridOnOff = getView(R.id.setting_grid);
        turnNR = getView(R.id.setting_turnNR);
        disablealign = getView(R.id.setting_disablealign);
        framestext = getView(R.id.setting_framecounter);
        frameCount = getView(R.id.setting_framecnt);
        lumenCount = getView(R.id.setting_lumacnt);
        chromaCount = getView(R.id.setting_chromacnt);
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

    void get() {
        views();
        if (turnNR.isChecked()) Interface.i.settings.NoiseReduction = NOISE_REDUCTION_MODE_HIGH_QUALITY;
        else Interface.i.settings.NoiseReduction = NOISE_REDUCTION_MODE_OFF;
        Interface.i.settings.align = !disablealign.isChecked();
        Interface.i.settings.enhancedProcess = enhanced.isChecked();
        Interface.i.settings.grid = gridOnOff.isChecked();
        save();
    }

    Resources res = MainActivity.act.getResources();



    void save() {
        sharedPreferencesEditor = sharedPreferences.edit();
        putview(turnNR);
        sharedPreferencesEditor.apply();
        cnt2 = 0;
    }

    void load() {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        getview(turnNR);
        cnt2 = 0;
    }


    int cnt2 = 0;

    void putview(Switch in) {
        sharedPreferencesEditor.putBoolean("SettingsView:" + cnt2, in.isChecked());
        cnt2++;
    }

    void putview(TextView in) {
        sharedPreferencesEditor.putString("SettingsView:" + cnt2, in.getText().toString());
        cnt2++;
    }

    void putview(SeekBar in) {
        sharedPreferencesEditor.putInt("SettingsView:" + cnt2, in.getProgress());
        cnt2++;
    }

    void getview(Switch in) {
        in.setChecked(sharedPreferences.getBoolean("SettingsView:" + cnt2, in.isChecked()));
        cnt2++;
    }

    void getview(TextView in) {
        in.setText(sharedPreferences.getString("SettingsView:" + cnt2, in.getText().toString()));
        cnt2++;
    }

    void getview(SeekBar in) {
        in.setProgress(sharedPreferences.getInt("SettingsView:" + cnt2, in.getProgress()));
        cnt2++;
    }

    void setv(Switch in, boolean val) {
        in.setChecked(val);
    }

    void setv(TextView in, String val) {
        in.setText(val);
    }

    void setv(SeekBar in, int val) {
        in.setProgress(in.getMax() - 1);
        in.setProgress(val);
    }

    void setv(SeekBar in, double val) {
        val *= 100;
        setv(in, (int) val);
    }
}
