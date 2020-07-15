package com.eszdman.photoncamera.Control;

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.widget.SeekBar;
import android.widget.TextView;
import com.eszdman.photoncamera.Parameters.ExposureIndex;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraReflectionApi;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

public class Manual {
    public double expvalue = 1.0/20;
    public int isovalue = 1600;
    public boolean exposure = false;
    @SuppressLint("NewApi")
    public void Init() {
        SeekBar isoSlider = Interface.i.mainActivity.findViewById(R.id.isoSlider);
        TextView isoValue = Interface.i.mainActivity.findViewById(R.id.isoValue);
        int miniso = IsoExpoSelector.getISOLOWExt();
        isoSlider.setMin(1);
        isoSlider.setMax(IsoExpoSelector.getISOHIGHExt()/miniso);
        isoSlider.setProgress(isoSlider.getMax()/2);
        isoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                isovalue = progress * miniso;
                isoValue.setText(String.valueOf(isovalue));
                try{
                    //Interface.i.camera.mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,isovalue);
                    CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
                    CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.SENSOR_SENSITIVITY,(int)(isovalue/IsoExpoSelector.getMPY()));
                    CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.SENSOR_EXPOSURE_TIME,Interface.i.camera.mPreviewExposuretime);
                    exposure = true;
                    Interface.i.camera.rebuildPreview();

                } catch (Exception ignored){}
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
        long minexp = IsoExpoSelector.getEXPLOW();
        long maxexp = IsoExpoSelector.getEXPHIGH();
        expSlider.setMin((int)(Math.log((double)(minexp)/ ExposureIndex.sec)/Math.log(2))-1);
        expSlider.setMax((int)(Math.log((double)(maxexp)/ ExposureIndex.sec)/Math.log(2))+1);
        expSlider.setProgress(-4);
        expSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint({"DefaultLocale", "SetTextI18n"})
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                expvalue = Math.pow(2,expSlider.getProgress());
                if(expSlider.getProgress() == expSlider.getMax()) expvalue = ((double)maxexp)/ExposureIndex.sec;
                if(expSlider.getProgress() == expSlider.getMin()) expvalue = ((double)minexp)/ExposureIndex.sec;
                if(expvalue < 1.0) {
                    expValue.setText("1/"+(int)(1.0 / expvalue));
                    try{
                        //Interface.i.camera.mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,ExposureIndex.sec2time(expvalue));
                        CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
                        CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.SENSOR_EXPOSURE_TIME,ExposureIndex.sec2time(expvalue));
                        CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.SENSOR_SENSITIVITY,Interface.i.camera.mPreviewIso);
                        exposure = true;
                        Interface.i.camera.rebuildPreview();
                    } catch (Exception ignored){}
                } else expValue.setText(String.valueOf((int)expvalue));
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
        float min = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        focusSlider.setMin(0);
        focusSlider.setMax(1000);
        float max = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        float k = (max-min)/1000.f;
        focusSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float progressf = (float)(progress*k + min);
                CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);
                CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.LENS_FOCUS_DISTANCE,progressf);
                Interface.i.camera.rebuildPreview();
                if((float)progress == 1000f) {
                    focusValue.setText("INF");
                }
                else if((float)progress <= 100f) {
                    focusValue.setText((float)progress + "cm");
                }
                else {
                    focusValue.setText((float)progress / 100 + "m");
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
