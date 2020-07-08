package com.eszdman.photoncamera.Render;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.LensShadingMap;
import android.util.Log;
import android.util.Rational;

import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import com.eszdman.photoncamera.api.Interface;

public class Parameters {
    private String TAG = "Parameters";
    public byte cfaPattern;
    public Point rawSize;
    public float[] blacklevel = new float[4];
    public float[] whitepoint = new float[3];
    public int whitelevel = 1023;
    public boolean hasGainMap;
    public Point mapsize;
    public float[] gainmap;
    public String path;
    public float[] proPhotoToSRGB = new float[9];
    public float[] sensorToProPhoto = new float[9];
    public float tonemapStrength = 1.4f;
    public float[] customTonemap;

    public void FillParameters(CaptureResult result, CameraCharacteristics characteristics, Point size) {
        rawSize = size;
        for (int i = 0; i < 4; i++) blacklevel[i] = 64;
        tonemapStrength = (float) Interface.i.settings.compressor;
        int[] blarr = new int[4];
        BlackLevelPattern level = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if (level != null) {
            level.copyTo(blarr, 0);
            for (int i = 0; i < 4; i++) blacklevel[i] = blarr[i];
        }
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if (ptr != null) cfaPattern = (byte) (int) ptr;
        if(Interface.i.settings.cfaPatern != 0){
            cfaPattern = (byte)Interface.i.settings.cfaPatern;
        }
        Object wlevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if (wlevel != null) whitelevel = (short) ((int) wlevel);
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
        Converter.convertColorspaceTransform(forwardt1, normalizedForwardTransform1);
        Converter.convertColorspaceTransform(calibr2, calibrationTransform2);
        Converter.convertColorspaceTransform(forwardt2, normalizedForwardTransform2);
        Converter.convertColorspaceTransform(colmat1, normalizedColorMatrix1);
        Converter.convertColorspaceTransform(colmat2, normalizedColorMatrix2);

        Converter.normalizeFM(normalizedForwardTransform2);
        Converter.normalizeFM(normalizedForwardTransform1);
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


        Converter.multiply(Converter.HDRXCCM, Converter.sProPhotoToXYZ, /*out*/proPhotoToSRGB);
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
        String gainmapstr = ", GainMapSamples=NoGainMap";
        if (hasGainMap)
            gainmapstr = ", GainMapSamples=(" + FltFormat(gainmap[(gainmap.length / 8) - (gainmap.length / 8) % 4]) + " "
                    + FltFormat(gainmap[(gainmap.length / 2) - (gainmap.length / 2) % 4]) + " " + FltFormat(gainmap[(gainmap.length / 2 + gainmap.length / 8) - (gainmap.length / 2 + gainmap.length / 8) % 4]) + ")";
        return "Parameters:" +
                " rawSize=" + rawSize +
                ", hasGainMap=" + hasGainMap +
                ", tonemapStrength=" + FltFormat(tonemapStrength) +
                ", framecount=" + FrameNumberSelector.frameCount +
                ", CameraID=" + Interface.i.settings.mCameraID +
                ", Satur=" + FltFormat(Interface.i.settings.saturation) +
                ", Gain=" + FltFormat(Interface.i.settings.gain) +
                ", Sharpness=" + FltFormat(Interface.i.settings.sharpness) +
                gainmapstr;
    }

    @SuppressLint("DefaultLocale")
    private String FltFormat(Object in) {
        return String.format("%.2f", in);
    }
}
