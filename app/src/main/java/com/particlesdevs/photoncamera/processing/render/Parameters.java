package com.particlesdevs.photoncamera.processing.render;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.LensShadingMap;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.Rational;

import androidx.annotation.NonNull;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Parameters {
    private static final String TAG = "Parameters";
    public byte cfaPattern;
    public Point rawSize;
    public boolean usedDynamic = false;
    public float[] blackLevel = new float[4];
    public float[] whitePoint = new float[3];
    public int whiteLevel = 1023;
    public int realWL = -1;
    public boolean hasGainMap;
    public Point mapSize;
    public Rect sensorPix;
    public float[] gainMap;
    public float[] proPhotoToSRGB = new float[9];
    public float[] sensorToProPhoto = new float[9];
    public float tonemapStrength = 1.4f;
    public float[] customTonemap;
    public Point[] hotPixels;
    public float focalLength;
    public int cameraRotation;
    public ColorCorrectionTransform CCT;

    public void FillConstParameters(CameraCharacteristics characteristics, Point size) {
        rawSize = size;
        for (int i = 0; i < 4; i++) blackLevel[i] = 64;
        tonemapStrength = (float) PhotonCamera.getSettings().compressor;
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if (ptr != null) cfaPattern = (byte) (int) ptr;
        if (PhotonCamera.getSettings().cfaPattern >= 0) {
            cfaPattern = (byte) PhotonCamera.getSettings().cfaPattern;
        }
        float[] flen = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (flen == null || flen.length <= 0) {
            flen = new float[1];
            flen[0] = 4.75f;
        }
        Log.d(TAG, "Focal Length:" + flen[0]);
        focalLength = flen[0];
        Object whiteLevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if (whiteLevel != null) this.whiteLevel = ((int) whiteLevel);
        hasGainMap = false;
        mapSize = new Point(1, 1);
        gainMap = new float[4];
        gainMap[0] = 1.f;
        gainMap[1] = 1.f;
        gainMap[2] = 1.f;
        gainMap[3] = 1.f;
        sensorPix = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (sensorPix == null) {
            sensorPix = new Rect(0, 0, rawSize.x, rawSize.y);
        }
        //hotPixels = PhotonCamera.getCameraFragment().mHotPixelMap;
    }

    public void FillDynamicParameters(CaptureResult result) {
        int[] blarr = new int[4];
        boolean isHuawei = Build.BRAND.equals("Huawei");
        BlackLevelPattern level = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if (result != null) {
            float[] dynbl = result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
            if (dynbl != null) {
                System.arraycopy(dynbl, 0, blackLevel, 0, 4);
                usedDynamic = true;
            }
        }
        if (!usedDynamic)
            if (level != null) {
                level.copyTo(blarr, 0);
                for (int i = 0; i < 4; i++) blackLevel[i] = blarr[i];
            }
        if (result != null) {
            LensShadingMap lensMap = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
            if (lensMap != null) {
                gainMap = new float[lensMap.getGainFactorCount()];
                mapSize = new Point(lensMap.getColumnCount(), lensMap.getRowCount());
                lensMap.copyGainFactors(gainMap, 0);
                hasGainMap = true;
                if ((gainMap[(gainMap.length / 8) - (gainMap.length / 8) % 4]) == 1.0 &&
                        (gainMap[(gainMap.length / 2) - (gainMap.length / 2) % 4]) == 1.0 &&
                        (gainMap[(gainMap.length / 2 + gainMap.length / 8) - (gainMap.length / 2 + gainMap.length / 8) % 4]) == 1.0 && isHuawei) {
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
            hotPixels = result.get(CaptureResult.STATISTICS_HOT_PIXEL_MAP);
            ReCalcColor(false);
        }
    }


    public float[] customNeutral;

    public void ReCalcColor(boolean customNeutr) {
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        CaptureResult result = CaptureController.mCaptureResult;
        Rational[] neutralR = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        if (!customNeutr)
            for (int i = 0; i < neutralR.length; i++) {
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
        File customCCT = new File(Environment.getExternalStorageDirectory() + "//DCIM//PhotonCamera//", "customCCT.txt");
        ColorSpaceTransform CST = PhotonCamera.getCaptureController().mColorSpaceTransform;//= result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        assert calibration2 != null;
        assert forwardt1 != null;
        assert forwardt2 != null;
        CCT = new ColorCorrectionTransform();
        boolean wrongCalibration =
                forwardt1.getElement(0, 0).floatValue() == forwardt2.getElement(0, 0).floatValue() &&
                        forwardt1.getElement(1, 1).floatValue() == forwardt2.getElement(1, 1).floatValue() &&
                        forwardt1.getElement(2, 2).floatValue() == forwardt2.getElement(2, 2).floatValue() &&
                        forwardt1.getElement(1, 2).floatValue() == forwardt2.getElement(1, 2).floatValue();
        Rational[] rat = new Rational[9];
        if (CST != null) {
            CST.copyElements(rat, 0);
            int cnt = 0;
            for (int i = 0; i < 9; i++) {
                if (rat[i].floatValue() != 0.0f) cnt++;
            }
            if (cnt <= 4) wrongCalibration = false;
        } else wrongCalibration = false;
        if (wrongCalibration && !customCCT.exists()) {
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
        if (CST != null && wrongCalibration && !customCCT.exists()) {
            Rational[] temp = new Rational[9];
            CST.copyElements(temp, 0);
            for (int i = 0; i < 9; i++) {
                proPhotoToSRGB[i] = temp[i].floatValue();
            }
        }

        Log.d(TAG, "customCCT exist:" + customCCT.exists());
        Scanner sc = null;
        CCT.matrix = proPhotoToSRGB;
        if (customCCT.exists()) {
            try {
                sc = new Scanner(customCCT);
            } catch (FileNotFoundException ignored) {
            }
            assert sc != null;
            CCT.FillCCT(sc);
            /*sc.useDelimiter(",");
            sc.useLocale(Locale.US);
            for (int i = 0; i < 9; i++) {
                String inp = sc.next();
                proPhotoToSRGB[i] = Float.parseFloat(inp);
                //Log.d(TAG, "Read1:" + proPhotoToSRGB[i]);
            }*/
        }
        customTonemap = new float[]{
                -2f + 2f * tonemapStrength,
                3f - 3f * tonemapStrength,
                tonemapStrength,
                0f
        };
    }

    private static void PrintMat(float[] mat) {
        StringBuilder outp = new StringBuilder();
        for (int i = 0; i < mat.length; i++) {
            outp.append(mat[i]).append(" ");
            if (i % 3 == 2) outp.append("\n");
        }
        Log.d(TAG, "matrix:\n" + outp);
    }

    protected Parameters Build() {
        Parameters params = new Parameters();
        params.cfaPattern = cfaPattern;
        params.usedDynamic = usedDynamic;
        params.blackLevel = blackLevel.clone();
        params.whitePoint = whitePoint.clone();
        params.whiteLevel = whiteLevel;
        params.realWL = realWL;
        params.hasGainMap = hasGainMap;
        params.mapSize = new Point(mapSize);
        params.sensorPix = new Rect(sensorPix);
        params.gainMap = gainMap.clone();
        params.proPhotoToSRGB = proPhotoToSRGB.clone();
        params.sensorToProPhoto = sensorToProPhoto.clone();
        params.tonemapStrength = tonemapStrength;
        params.customTonemap = customTonemap.clone();
        params.hotPixels = hotPixels.clone();
        params.focalLength = focalLength;
        params.cameraRotation = cameraRotation;
        params.CCT = CCT;
        return params;
    }

    @NonNull
    @Override
    public String toString() {
        return "parameters:\n" +
                "\n hasGainMap=" + hasGainMap +
                "\n FrameCount=" + FrameNumberSelector.frameCount +
                "\n CameraID=" + PhotonCamera.getSettings().mCameraID +
                "\n Sat=" + FltFormat(PreferenceKeys.getSaturationValue()) +
                "\n Shadows=" + FltFormat(PhotonCamera.getSettings().shadows) +
                "\n Sharp=" + FltFormat(PreferenceKeys.getSharpnessValue()) +
                "\n Denoise=" + FltFormat(PreferenceKeys.getFloat(PreferenceKeys.Key.KEY_NOISESTR_SEEKBAR)) +
                "\n DenoiseOn=" + PhotonCamera.getSettings().hdrxNR +
                "\n FocalL=" + FltFormat(focalLength) +
                "\n Version=" + PhotonCamera.getVersion();
    }

    @SuppressLint("DefaultLocale")
    private String FltFormat(Object in) {
        return String.format("%.2f", Float.parseFloat(in.toString()));
    }
}
