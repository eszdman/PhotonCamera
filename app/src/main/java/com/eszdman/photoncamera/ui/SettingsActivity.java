package com.eszdman.photoncamera.ui;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;

import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY;
import static android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF;
@SuppressLint("UseSwitchCompatOrMaterialCode")
@Deprecated
public class SettingsActivity extends AppCompatActivity {

    //Here
    TextView luma;
    TextView framestext;
    TextView chroma;
    Switch turnNR;
    Switch disablealign;
    Switch enhanced;
    Switch gridOnOff;
    Switch roundoption;
    Switch rawSaving;
    Switch remosaic;
    Switch watermarkOnOff;
    Switch afdataOnOff;

    SeekBar frameCount;
    SeekBar lumenCount;
    SeekBar chromaCount;

    //HDRX
    Switch hdrxNR;
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
    Spinner cfaList;
    Spinner alignList;
    private int count = 0;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private SharedPreferences sharedPreferences;

    double getDouble(int prog) {
        return ((double) (prog)) / 100;
    }

    <T extends View> T getView(@IdRes int id) {
        return findViewById(id);
    }

    //SeekBar listeners
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PhotonCamera.setSettingsActivity(this);
        setContentView(R.layout.activity_settings);
        if(PhotonCamera.getSettings().hdrxNR){
            LinearLayout jpg = findViewById(R.id.settingsJPG);
            TextView jpgt = findViewById(R.id.textjpgset);
            jpgt.setVisibility(View.GONE);
            jpg.setVisibility(View.GONE);
            LinearLayout hdrx = findViewById(R.id.settingsHDRX);
            TextView hdrxt = findViewById(R.id.texthdrxset);
            hdrxt.setVisibility(View.VISIBLE);
            hdrx.setVisibility(View.VISIBLE);
        } else {
            LinearLayout jpg = findViewById(R.id.settingsJPG);
            jpg.setVisibility(View.VISIBLE);
            TextView jpgt = findViewById(R.id.textjpgset);
            jpgt.setVisibility(View.VISIBLE);
            LinearLayout hdrx = findViewById(R.id.settingsHDRX);
            TextView hdrxt = findViewById(R.id.texthdrxset);
            hdrxt.setVisibility(View.GONE);
            hdrx.setVisibility(View.GONE);
        }
        PhotonCamera.getSettings().loadCache();
        views();
        sharedPreferences = getPreferences(MODE_PRIVATE);
        chromaCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                PhotonCamera.getSettings().chromaCount = chromaCount.getProgress();
                chroma.setText((res.getText(R.string.chroma_nr_count).toString() + " " + PhotonCamera.getSettings().chromaCount));
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
                PhotonCamera.getSettings().lumenCount = lumenCount.getProgress();
                luma.setText((res.getText(R.string.luma_nr_count).toString() + " " + PhotonCamera.getSettings().lumenCount));
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
                PhotonCamera.getSettings().frameCount = frameCount.getProgress();
                framestext.setText((res.getText(R.string.frame_count).toString() + " " + PhotonCamera.getSettings().frameCount));
                if (PhotonCamera.getSettings().frameCount == 1) framestext.setText("Unprocessed Output");
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
                PhotonCamera.getSettings().contrastConst = contrastconst.getProgress();
                contrasttext.setText((res.getText(R.string.contrmul) + String.valueOf(PhotonCamera.getSettings().contrastMpy) + " " + res.getText(R.string.contrconst) + " " + PhotonCamera.getSettings().contrastConst));
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
                PhotonCamera.getSettings().contrastMpy = getDouble(contrastmul.getProgress());
                contrasttext.setText((res.getText(R.string.contrmul) + " " + PhotonCamera.getSettings().contrastMpy + " " + res.getText(R.string.contrconst) + " " + PhotonCamera.getSettings().contrastConst));
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
                PhotonCamera.getSettings().sharpness = getDouble(sharp.getProgress());
                sharptext.setText((res.getText(R.string.sharpness) + " " + PhotonCamera.getSettings().sharpness));
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
                PhotonCamera.getSettings().saturation = getDouble(satur.getProgress());
                sattext.setText((res.getText(R.string.saturation).toString() + " " + PhotonCamera.getSettings().saturation));
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
                PhotonCamera.getSettings().compressor = getDouble(compress.getProgress());
                compgaintext.setText((res.getText(R.string.comp) + String.valueOf(PhotonCamera.getSettings().compressor) + " " + res.getText(R.string.gain) + " " + PhotonCamera.getSettings().gain));
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
                PhotonCamera.getSettings().gain = getDouble(gains.getProgress());
                compgaintext.setText((res.getText(R.string.comp) + " " + PhotonCamera.getSettings().compressor + "  " + res.getText(R.string.gain) + " " + PhotonCamera.getSettings().gain));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        cfaList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
              @Override
              public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                  Log.d("Settings","Position:"+position);
                  switch (position){
                      case(0):
                          PhotonCamera.getSettings().cfaPattern = -1; break;
                      case(1):
                          PhotonCamera.getSettings().cfaPattern = 0; break;
                      case(2):
                          PhotonCamera.getSettings().cfaPattern = 3; break;
                      case(3):
                          PhotonCamera.getSettings().cfaPattern = 1; break;
                      case(4):
                          PhotonCamera.getSettings().cfaPattern = 2; break;
                      case(5):
                          PhotonCamera.getSettings().cfaPattern = -2; break;
                  }
              }
              @Override
              public void onNothingSelected(AdapterView<?> parent) {
                  PhotonCamera.getSettings().cfaPattern = -1;
              }
          }

        );
        alignList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                              @Override
                                              public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                                  Log.d("Settings","Position:"+position);
                                                  PhotonCamera.getSettings().alignAlgorithm = position;
                                              }
                                              @Override
                                              public void onNothingSelected(AdapterView<?> parent) {
                                                  PhotonCamera.getSettings().alignAlgorithm = 0;
                                              }
        }
        );
        set();
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onPause() {
        sharedPreferencesEditor = sharedPreferences.edit();
        get();
//        Interface.getSettings().save();
        save();
        super.onPause();
    }

    //Here
    public void set() {
        setv(disablealign, !PhotonCamera.getSettings().align);
        setv(frameCount, PhotonCamera.getSettings().frameCount);
        setv(chromaCount, PhotonCamera.getSettings().chromaCount);
        setv(lumenCount, PhotonCamera.getSettings().lumenCount);
        setv(enhanced, PhotonCamera.getSettings().enhancedProcess);
        setv(gridOnOff, PhotonCamera.getSettings().grid);
        setv(roundoption, PhotonCamera.getSettings().roundedge);
        setv(rawSaving, PhotonCamera.getSettings().rawSaver);
        setv(remosaic, PhotonCamera.getSettings().remosaic);
        setv(watermarkOnOff, PhotonCamera.getSettings().watermark);
        setv(afdataOnOff, PhotonCamera.getSettings().afdata);

        setv(hdrxNR, PhotonCamera.getSettings().hdrxNR);
        setv(sharp, PhotonCamera.getSettings().sharpness);
        setv(contrastconst, PhotonCamera.getSettings().contrastConst);
        setv(contrastmul, PhotonCamera.getSettings().contrastMpy);
        setv(compress, PhotonCamera.getSettings().compressor);
        setv(gains, PhotonCamera.getSettings().gain);
        setv(satur, PhotonCamera.getSettings().saturation);
        setv(cfaList, PhotonCamera.getSettings().cfaPattern);
        setv(alignList, PhotonCamera.getSettings().alignAlgorithm);
        load();
    }
    //And here
    public void views() {
        gridOnOff = getView(R.id.setting_grid);
        watermarkOnOff = getView(R.id.setting_watermark);
        afdataOnOff = getView(R.id.setting_afdata);
        roundoption = getView(R.id.setting_roundedge);
        rawSaving = getView(R.id.setting_raw);
        remosaic = getView(R.id.setting_remosaic);
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
        hdrxNR = getView(R.id.setting_hdrxNR);
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
        cfaList = getView(R.id.setting_cfa);
        alignList = getView(R.id.setting_align);
    }
    //Also here
    public void get() {
        views();
        if (turnNR.isChecked()) PhotonCamera.getSettings().noiseReduction = NOISE_REDUCTION_MODE_HIGH_QUALITY;
        else PhotonCamera.getSettings().noiseReduction = NOISE_REDUCTION_MODE_OFF;
        PhotonCamera.getSettings().hdrxNR = hdrxNR.isChecked();
        PhotonCamera.getSettings().align = !disablealign.isChecked();
        PhotonCamera.getSettings().enhancedProcess = enhanced.isChecked();
        PhotonCamera.getSettings().grid = gridOnOff.isChecked();
        PhotonCamera.getSettings().roundedge = roundoption.isChecked();
        PhotonCamera.getSettings().rawSaver = rawSaving.isChecked();
        PhotonCamera.getSettings().remosaic = remosaic.isChecked();
        PhotonCamera.getSettings().watermark = watermarkOnOff.isChecked();
        PhotonCamera.getSettings().afdata = afdataOnOff.isChecked();
        save();
    }

    final Resources res = MainActivity.act.getResources();



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
        sharedPreferencesEditor.putBoolean(PhotonCamera.getSettings().mCameraID+"SettingsView:" + cnt2, in.isChecked());
        cnt2++;
    }

    void putview(TextView in) {
        sharedPreferencesEditor.putString(PhotonCamera.getSettings().mCameraID+"SettingsView:" + cnt2, in.getText().toString());
        cnt2++;
    }

    void putview(SeekBar in) {
        sharedPreferencesEditor.putInt(PhotonCamera.getSettings().mCameraID+"SettingsView:" + cnt2, in.getProgress());
        cnt2++;
    }

    void getview(Switch in) {
        in.setChecked(sharedPreferences.getBoolean(PhotonCamera.getSettings().mCameraID+"SettingsView:" + cnt2, in.isChecked()));
        cnt2++;
    }

    void getview(TextView in) {
        in.setText(sharedPreferences.getString(PhotonCamera.getSettings().mCameraID+"SettingsView:" + cnt2, in.getText().toString()));
        cnt2++;
    }

    void getview(SeekBar in) {
        in.setProgress(sharedPreferences.getInt(PhotonCamera.getSettings().mCameraID+"SettingsView:" + cnt2, in.getProgress()));
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

    void setv(Spinner in, int val)
    {
        in.setSelection(val);
    }
    void setv(SeekBar in, double val) {
        val *= 100;
        setv(in, (int) val);
    }

    public void telega(View view) {
        Intent browserint = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/photon_camera_channel"));
        startActivity(browserint);
    }

    public void export_settings(View view) {
        PhotonCamera.getSettings().ExportSettings();
    }

    public void import_settings(View view) {
        PhotonCamera.getSettings().ImportSettings();
    }
}
