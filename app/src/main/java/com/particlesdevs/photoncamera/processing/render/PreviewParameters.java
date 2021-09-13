package com.particlesdevs.photoncamera.processing.render;

import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;

import com.particlesdevs.photoncamera.app.PhotonCamera;

public class PreviewParameters {
    public NoiseModeler noiseModeler;
    private int analogIso;
    public byte cfaPattern;
    public void FillParameters(CaptureResult result, CameraCharacteristics characteristics) {
        Integer analogue = characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);
        if(analogue != null){
            analogIso = analogue;
        } else analogIso = 100;
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if (ptr != null) cfaPattern = (byte) (int) ptr;
        if (PhotonCamera.getSettings().cfaPattern >= 0) {
            cfaPattern = (byte) PhotonCamera.getSettings().cfaPattern;
        }
    }
    public void FillDynamicParameters(CaptureResult result) {
        noiseModeler = new NoiseModeler( result.get(CaptureResult.SENSOR_NOISE_PROFILE),analogIso,result.get(CaptureResult.SENSOR_SENSITIVITY),cfaPattern,null);

    }
}
