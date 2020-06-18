package com.eszdman.photoncamera.Control;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Rational;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.eszdman.photoncamera.Parameters.ExposureIndex;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

public class Manual {
    public double expvalue = 0;
    public int isovalue = 0;
    @SuppressLint("NewApi")
    public void Init() {
        SeekBar isoSlider = Interface.i.mainActivity.findViewById(R.id.isoSlider);
        TextView isoValue = Interface.i.mainActivity.findViewById(R.id.isoValue);
        int miniso = IsoExpoSelector.getISOLOW();
        isoSlider.setMin(1);
        isoSlider.setMax(IsoExpoSelector.getISOHIGH()/miniso);
        isoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                isovalue = progress * miniso;
                isoValue.setText(String.valueOf(isovalue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        isoSlider.setProgress(isoSlider.getMax()/2);
        SeekBar expSlider = Interface.i.mainActivity.findViewById(R.id.expSlider);
        TextView expValue = Interface.i.mainActivity.findViewById(R.id.expValue);
        long minexp = IsoExpoSelector.getEXPLOW();
        long maxexp = IsoExpoSelector.getEXPHIGH();
        expSlider.setMin((int)(Math.log((double)(minexp)/ ExposureIndex.sec)/Math.log(2))-1);
        expSlider.setMax((int)(Math.log((double)(maxexp)/ ExposureIndex.sec)/Math.log(2))+1);
        expSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint({"DefaultLocale", "SetTextI18n"})
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                expvalue = Math.pow(2,expSlider.getProgress());
                if(expSlider.getProgress() == expSlider.getMax()) expvalue = ((double)maxexp)/ExposureIndex.sec;
                if(expSlider.getProgress() == expSlider.getMin()) expvalue = ((double)minexp)/ExposureIndex.sec;
                if(expvalue < 1.0) {
                    expValue.setText("1/"+(int)(1.0 / expvalue));
                } else expValue.setText(String.valueOf((int)expvalue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        expSlider.setProgress(expSlider.getMax()/2);
        SeekBar focusSlider = Interface.i.mainActivity.findViewById(R.id.focusSlider);
        TextView focusValue = Interface.i.mainActivity.findViewById(R.id.focusValue);
        focusSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Float progressFloat = new Float(progress);
                if(progressFloat == 1000f) {
                    focusValue.setText("INF");
                }
                else if(progressFloat <= 100f) {
                    focusValue.setText(progressFloat + "cm");
                }
                else {
                    focusValue.setText(progressFloat / 100 + "m");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }
}
