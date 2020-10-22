package com.eszdman.photoncamera.processing.render;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.LensShadingMap;
import android.os.Environment;
import android.util.Log;
import android.util.Rational;

import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.parameters.FrameNumberSelector;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Scanner;

public class Parameters {
    private static final String TAG = "parameters";
    public byte cfaPattern;
    public Point rawSize;
    public final float[] blackLevel = new float[4];
    public float[] whitePoint = new float[3];
    public int whiteLevel = 1023;
    public int realWL = -1;
    public boolean hasGainMap;
    public Point mapSize;
    public float[] gainMap;
    public float[] proPhotoToSRGB = new float[9];
    public final float[] sensorToProPhoto = new float[9];
    public float tonemapStrength = 1.4f;
    public float[] customTonemap;
    public Point[] hotPixels;
    public float focalLength;

    public void FillParameters(CaptureResult result, CameraCharacteristics characteristics, Point size) {
        rawSize = size;
        for (int i = 0; i < 4; i++) blackLevel[i] = 64;
        tonemapStrength = (float) PhotonCamera.getSettings().compressor;
        int[] blarr = new int[4];
        BlackLevelPattern level = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if (level != null) {
            level.copyTo(blarr, 0);
            for (int i = 0; i < 4; i++) blackLevel[i] = blarr[i];
        }
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if (ptr != null) cfaPattern = (byte) (int) ptr;
        if (PhotonCamera.getSettings().cfaPattern != -1) {
            cfaPattern = (byte) PhotonCamera.getSettings().cfaPattern;
        }
        float[] flen = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        Log.d(TAG,"Focal Length:"+flen[0]);
        if(flen == null || flen.length <= 0) {
            flen = new float[1];
            flen[0] = 4.75f;
        }
        focalLength = flen[0];
        Object whiteLevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if (whiteLevel != null) this.whiteLevel = ((int) whiteLevel);
        hasGainMap = false;
        mapSize = new Point(1, 1);
        gainMap = new float[1];
        gainMap[0] = 1.f;
        LensShadingMap lensMap = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
        if (lensMap != null) {
            gainMap = new float[lensMap.getGainFactorCount()];
            mapSize = new Point(lensMap.getColumnCount(), lensMap.getRowCount());
            lensMap.copyGainFactors(gainMap, 0);
            hasGainMap = true;
            if ((gainMap[(gainMap.length / 8) - (gainMap.length / 8) % 4]) == 1.0 &&
                    (gainMap[(gainMap.length / 2) - (gainMap.length / 2) % 4]) == 1.0 &&
                    (gainMap[(gainMap.length / 2 + gainMap.length / 8) - (gainMap.length / 2 + gainMap.length / 8) % 4]) == 1.0) {
                Log.d(TAG, "DETECTED FAKE GAINMAP, REPLACING WITH STATIC GAINMAP");
                gainMap = new float[Const.gainMap.length];
                for (int i = 0; i < Const.gainMap.length; i += 4) {
                    float in = (float) Const.gainMap[i] + (float) Const.gainMap[i + 1] + (float) Const.gainMap[i + 2] + (float) Const.gainMap[i + 3];
                    in /= 4.f;
                    gainMap[i] = in;
                    gainMap[i + 1] = in;
                    gainMap[i + 2] = in;
                    gainMap[i + 3] = in;
                }
                mapSize = Const.mapSize;
            }
        }
        //hotPixels = PhotonCamera.getCameraFragment().mHotPixelMap;
        hotPixels = result.get(CaptureResult.STATISTICS_HOT_PIXEL_MAP);
        ReCalcColor(false);
    }
    public float[] customNeutral;
    public void ReCalcColor(boolean customNeutr){
        CameraCharacteristics characteristics = CameraFragment.mCameraCharacteristics;
        CaptureResult result = CameraFragment.mCaptureResult;
        Rational[] neutralR = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        if(!customNeutr)
        for(int i =0; i<neutralR.length;i++){
            whitePoint[i] = neutralR[i].floatValue();
        }
        else {
            whitePoint = customNeutral;
        }
        ColorSpaceTransform calibration1 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1);
        ColorSpaceTransform calibration2 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2);
        ColorSpaceTransform colorMat1 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1);
        ColorSpaceTransform colorMat2 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2);
        ColorSpaceTransform forwardt1 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1);
        ColorSpaceTransform forwardt2 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2);
        float[] calibrationTransform1 = new float[9];
        float[] normalizedForwardTransform1 = new float[9];
        float[] normalizedColorMatrix1 = new float[9];
        float[] normalizedColorMatrix2 = new float[9];
        float[] calibrationTransform2 = new float[9];
        float[] normalizedForwardTransform2 = new float[9];

        Converter.convertColorspaceTransform(calibration1, calibrationTransform1);
        Converter.convertColorspaceTransform(calibration2, calibrationTransform2);
        Converter.convertColorspaceTransform(forwardt1, normalizedForwardTransform1);
        Converter.convertColorspaceTransform(forwardt2, normalizedForwardTransform2);
        Converter.convertColorspaceTransform(colorMat1, normalizedColorMatrix1);
        Converter.convertColorspaceTransform(colorMat2, normalizedColorMatrix2);

        Converter.normalizeFM(normalizedForwardTransform1);
        Converter.normalizeFM(normalizedForwardTransform2);

        Converter.normalizeFM(normalizedColorMatrix1);
        Converter.normalizeFM(normalizedColorMatrix2);
        float[] sensorToXYZ = new float[9];
        int ref1 = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
        int ref2;
        Object ref2obj = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);
        if (ref2obj != null) {
            ref2 = (byte) ref2obj;
        } else {
            ref2 = ref1;
        }
        double interpolationFactor = Converter.findDngInterpolationFactor(ref1,
                ref2, calibrationTransform1, calibrationTransform2,
                normalizedColorMatrix1, normalizedColorMatrix2, whitePoint);
        Converter.calculateCameraToXYZD50Transform(normalizedForwardTransform1, normalizedForwardTransform2,
                calibrationTransform1, calibrationTransform2, whitePoint,
                interpolationFactor, /*out*/sensorToXYZ);
        Converter.multiply(Converter.sXYZtoProPhoto, sensorToXYZ, /*out*/sensorToProPhoto);
        File customCCM = new File(Environment.getExternalStorageDirectory() + "//DCIM//PhotonCamera//", "customCCM.txt");
        if (!customCCM.exists()) {
            sensorToProPhoto[0] = 1.0f / whitePoint[0];
            sensorToProPhoto[1] = 0.0f;
            sensorToProPhoto[2] = 0.0f;

            sensorToProPhoto[3] = 0.0f;
            sensorToProPhoto[4] = 1.0f / whitePoint[1];
            sensorToProPhoto[5] = 0.0f;

            sensorToProPhoto[6] = 0.0f;
            sensorToProPhoto[7] = 0.0f;
            sensorToProPhoto[8] = 1.0f / whitePoint[2];
        }
        Converter.multiply(Converter.HDRXCCM, Converter.sProPhotoToXYZ, /*out*/proPhotoToSRGB);
        ColorSpaceTransform CCT = PhotonCamera.getCameraFragment().mColorSpaceTransform;//= result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        if (CCT != null) {
            Rational[] temp = new Rational[9];
            CCT.copyElements(temp, 0);
            for (int i = 0; i < 9; i++) {
                proPhotoToSRGB[i] = temp[i].floatValue();
            }
        }

        Log.d(TAG, "customCCM exist:" + customCCM.exists());
        Scanner sc = null;
        if (customCCM.exists()) {
            try {
                sc = new Scanner(customCCM);
            } catch (FileNotFoundException ignored) {
            }
            sc.useDelimiter(",");
            sc.useLocale(Locale.US);
            for (int i = 0; i < 9; i++) {
                String inp = sc.next();
                proPhotoToSRGB[i] = Float.parseFloat(inp);
                //Log.d(TAG, "Read1:" + proPhotoToSRGB[i]);
            }
        }
        customTonemap = new float[]{
                -2f + 2f * tonemapStrength,
                3f - 3f * tonemapStrength,
                tonemapStrength,
                0f
        };
    }

    @androidx.annotation.NonNull
    @Override
    public String toString() {
        return "parameters:\n" +
                ",\n hasGainMap=" + hasGainMap +
                ",\n framecount=" + FrameNumberSelector.frameCount +
                ",\n CameraID=" + PhotonCamera.getSettings().mCameraID +
                ",\n Satur=" + FltFormat(PreferenceKeys.getSaturationValue()) +
                ",\n Gain=" + FltFormat(PhotonCamera.getSettings().gain) +
                ",\n Compressor=" + FltFormat(PhotonCamera.getSettings().compressor) +
                ",\n Sharpness=" + FltFormat(PreferenceKeys.getSharpnessValue())+
                ",\n FocalL=" + FltFormat(focalLength);
    }

    @SuppressLint("DefaultLocale")
    private String FltFormat(Object in) {
        return String.format("%.2f", in);
    }
}
