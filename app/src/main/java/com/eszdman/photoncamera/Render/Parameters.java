package com.eszdman.photoncamera.Render;

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

import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.settings.PreferenceKeys;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Scanner;

public class Parameters {
    private static final String TAG = "Parameters";
    public byte cfaPattern;
    public Point rawSize;
    public final float[] blacklevel = new float[4];
    public final float[] whitepoint = new float[3];
    public int whitelevel = 1023;
    public int realWL = -1;
    public boolean hasGainMap;
    public Point mapsize;
    public float[] gainmap;
    public String path;
    public float[] proPhotoToSRGB = new float[9];
    public final float[] sensorToProPhoto = new float[9];
    public float tonemapStrength = 1.4f;
    public float[] customTonemap;

    public void FillParameters(CaptureResult result, CameraCharacteristics characteristics, Point size) {
        rawSize = size;
        for (int i = 0; i < 4; i++) blacklevel[i] = 64;
        tonemapStrength = (float) PhotonCamera.getSettings().compressor;
        int[] blarr = new int[4];
        BlackLevelPattern level = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if (level != null) {
            level.copyTo(blarr, 0);
            for (int i = 0; i < 4; i++) blacklevel[i] = blarr[i];
        }
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if (ptr != null) cfaPattern = (byte) (int) ptr;
        if(PhotonCamera.getSettings().cfaPattern != -1){
            cfaPattern = (byte) PhotonCamera.getSettings().cfaPattern;
        }
        Object wlevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if (wlevel != null) whitelevel = ((int)wlevel);
        hasGainMap = false;
        mapsize = new Point(1, 1);
        gainmap = new float[1];
        gainmap[0] = 1.f;
        LensShadingMap lensmap = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
        if (lensmap != null) {
            gainmap = new float[lensmap.getGainFactorCount()];
            mapsize = new Point(lensmap.getColumnCount(), lensmap.getRowCount());
            lensmap.copyGainFactors(gainmap, 0);
            hasGainMap = true;
            if ((gainmap[(gainmap.length / 8) - (gainmap.length / 8) % 4]) == 1.0 &&
                    (gainmap[(gainmap.length / 2) - (gainmap.length / 2) % 4]) == 1.0 &&
                    (gainmap[(gainmap.length / 2 + gainmap.length / 8) - (gainmap.length / 2 + gainmap.length / 8) % 4]) == 1.0) {
                Log.d(TAG, "DETECTED FAKE GAINMAP, REPLACING WITH STATIC GAINMAP");
                gainmap = new float[Const.gainmap.length];
                for (int i = 0; i < Const.gainmap.length; i += 4) {
                    float in = (float) Const.gainmap[i] + (float) Const.gainmap[i + 1] + (float) Const.gainmap[i + 2] + (float) Const.gainmap[i + 3];
                    in /= 4.f;
                    gainmap[i] = in;
                    gainmap[i + 1] = in;
                    gainmap[i + 2] = in;
                    gainmap[i + 3] = in;
                }
                mapsize = Const.mapsize;
            }
        }
        Rational[] neutral = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        ColorSpaceTransform calibr1 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1);
        ColorSpaceTransform calibr2 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2);
        ColorSpaceTransform colmat1 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1);
        ColorSpaceTransform colmat2 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2);
        ColorSpaceTransform forwardt1 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1);
        ColorSpaceTransform forwardt2 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2);
        float[] calibrationTransform1 = new float[9];
        float[] normalizedForwardTransform1 = new float[9];
        float[] normalizedColorMatrix1 = new float[9];
        float[] normalizedColorMatrix2 = new float[9];
        float[] calibrationTransform2 = new float[9];
        float[] normalizedForwardTransform2 = new float[9];

        Converter.convertColorspaceTransform(calibr1, calibrationTransform1);
        Converter.convertColorspaceTransform(calibr2, calibrationTransform2);
        Converter.convertColorspaceTransform(forwardt1, normalizedForwardTransform1);
        Converter.convertColorspaceTransform(forwardt2, normalizedForwardTransform2);
        Converter.convertColorspaceTransform(colmat1, normalizedColorMatrix1);
        Converter.convertColorspaceTransform(colmat2, normalizedColorMatrix2);

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
                normalizedColorMatrix1, normalizedColorMatrix2, neutral);
        Converter.calculateCameraToXYZD50Transform(normalizedForwardTransform1, normalizedForwardTransform2,
                calibrationTransform1, calibrationTransform2, neutral,
                interpolationFactor, /*out*/sensorToXYZ);
        Converter.multiply(Converter.sXYZtoProPhoto, sensorToXYZ, /*out*/sensorToProPhoto);
        File customCCM  = new File(Environment.getExternalStorageDirectory()+"//DCIM//PhotonCamera//","customCCM.txt");
        if(!customCCM.exists()) {
            sensorToProPhoto[0] = 1.0f / neutral[0].floatValue();
            sensorToProPhoto[1] = 0.0f;
            sensorToProPhoto[2] = 0.0f;

            sensorToProPhoto[3] = 0.0f;
            sensorToProPhoto[4] = 1.0f / neutral[1].floatValue();
            sensorToProPhoto[5] = 0.0f;

            sensorToProPhoto[6] = 0.0f;
            sensorToProPhoto[7] = 0.0f;
            sensorToProPhoto[8] = 1.0f / neutral[2].floatValue();
        }
        Converter.multiply(Converter.HDRXCCM, Converter.sProPhotoToXYZ, /*out*/proPhotoToSRGB);
        ColorSpaceTransform CCT = PhotonCamera.getCameraFragment().mColorSpaceTransform;//= result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        if(CCT != null) {
            Rational[] temp = new Rational[9];
            CCT.copyElements(temp, 0);
            for (int i = 0; i < 9; i++) {
                proPhotoToSRGB[i] = temp[i].floatValue();
                Log.d(TAG,"\nTransform:"+proPhotoToSRGB[i]);
            }
        }

        Log.d(TAG,"customCCM exist:"+customCCM.exists());
        Scanner sc = null;
        if(customCCM.exists()) {
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
        Rational[] wpoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        customTonemap = new float[]{
                -2f + 2f * tonemapStrength,
                3f - 3f * tonemapStrength,
                tonemapStrength,
                0f
        };
        if (wpoint != null) for (int i = 0; i < 3; i++) whitepoint[i] = wpoint[i].floatValue();

    }

    @Override
    public String toString() {
        return "Parameters:" +
                ", hasGainMap=" + hasGainMap +
                ", framecount=" + FrameNumberSelector.frameCount +
                ", CameraID=" + PhotonCamera.getSettings().mCameraID +
                ", Satur=" + FltFormat(PreferenceKeys.getSaturationValue()) +
                ", Gain=" + FltFormat(PhotonCamera.getSettings().gain) +
                ", Sharpness=" + FltFormat(PreferenceKeys.getSharpnessValue());
    }

    @SuppressLint("DefaultLocale")
    private String FltFormat(Object in) {
        return String.format("%.2f", in);
    }
}
