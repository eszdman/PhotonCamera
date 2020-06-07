package com.eszdman.photoncamera.Control;

import android.os.Build;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

public class Manual {
    public double expvalue = 0;
    public int isovalue = 0;
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void Init(){
        SeekBar isoSlider = Interface.i.mainActivity.findViewById(R.id.isoSlider);
        TextView isoValue = Interface.i.mainActivity.findViewById(R.id.isoValue);
        isoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                isovalue = progress * 100;
                isoValue.setText(String.valueOf(isovalue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        SeekBar expSlider = Interface.i.mainActivity.findViewById(R.id.expSlider);
        TextView expValue = Interface.i.mainActivity.findViewById(R.id.expValue);
        expSlider.setMin(-6);
        expSlider.setMax(6);
        expSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                expvalue = Math.pow(2,expSlider.getProgress());
                expValue.setText(String.valueOf(Math.pow(2,expSlider.getProgress())));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
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
