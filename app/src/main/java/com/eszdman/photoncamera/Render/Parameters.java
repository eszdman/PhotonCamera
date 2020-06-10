package com.eszdman.photoncamera.Render;

import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.LensShadingMap;
import android.util.Rational;

import com.eszdman.photoncamera.api.Interface;

public class Parameters {
    public byte cfaPattern;
    public Point rawSize;
    public float[] blacklevel = new float[4];
    public float[] whitepoint = new float[3];
    public float[] ccm = new float[9];
    public short whitelevel = 1023;
    boolean hasGainMap;
    Point mapsize;
    float[] gainmap;
    public String path;
    float[] proPhotoToSRGB = new float[9];
    float[] sensorToProPhoto = new float[9];
    float tonemapStrength = 1.4f;
    float[] customTonemap;
    public Parameters(CaptureResult result, CameraCharacteristics characteristics, Point size){
        rawSize = size;
        for(int i =0; i<4; i++) blacklevel[i] = 64;
        tonemapStrength = (float)Interface.i.settings.compressor;
        int[] blarr = new int[4];
        BlackLevelPattern level = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if(level != null) {
            level.copyTo(blarr,0);
            for(int i =0; i<4;i++) blacklevel[i] = blarr[i];
        }
        Object ptr = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if(ptr !=null) cfaPattern = (byte)(int)ptr;
        Object wlevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if(wlevel != null) whitelevel = (short)((int)wlevel);
        ccm[0] = 1.776f;ccm[1] = -0.837f;ccm[2] = 0.071f;
        ccm[3] = -0.163f;ccm[4] = 1.396f;ccm[5] = -0.242f;
        ccm[6] = 0.0331f;ccm[7] = -0.526f;ccm[8] = 1.492f;
        float normalize = 0.f;
        for(float f : ccm) normalize+=f;
        normalize/=9;
        hasGainMap =false;
        mapsize = new Point(1,1);
        LensShadingMap lensmap = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
        if(lensmap != null) {
            gainmap = new float[lensmap.getGainFactorCount()];
            mapsize = new Point(lensmap.getColumnCount(),lensmap.getRowCount());
            lensmap.copyGainFactors(gainmap,0);
            hasGainMap = true;
        }
        Rational[] neutral = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        ColorSpaceTransform calibr1 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1);
        ColorSpaceTransform calibr2 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2);
        ColorSpaceTransform colmat1 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1);
        ColorSpaceTransform colmat2 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2);
        ColorSpaceTransform forwardt1= characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1);
        ColorSpaceTransform forwardt2= characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2);
        float[] calibrationTransform1 = new float[9];
        float[] normalizedForwardTransform1 = new float[9];
        float[] normalizedColorMatrix1 = new float[9];
        float[] normalizedColorMatrix2 = new float[9];
        float[] calibrationTransform2 = new float[9];
        float[] normalizedForwardTransform2 = new float[9];

        Converter.convertColorspaceTransform(calibr1,calibrationTransform1);
        Converter.convertColorspaceTransform(forwardt1,normalizedForwardTransform1);
        Converter.convertColorspaceTransform(calibr2,calibrationTransform2);
        Converter.convertColorspaceTransform(forwardt2,normalizedForwardTransform2);
        Converter.convertColorspaceTransform(colmat1,normalizedColorMatrix1);
        Converter.convertColorspaceTransform(colmat2,normalizedColorMatrix2);

        Converter.normalizeFM(normalizedForwardTransform2);
        Converter.normalizeFM(normalizedForwardTransform1);
        Converter.normalizeFM(normalizedColorMatrix1);
        Converter.normalizeFM(normalizedColorMatrix2);
        float[] sensorToXYZ = new float[9];
        int ref1 = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
        int ref2;
        if (characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2) != null) {
            ref2 = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);
        }
        else {
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
        //for(int i =0; i<ccm.length;i++) ccm[i]/=normalize;
        Rational[] wpoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        customTonemap = new float[] {
                -2f + 2f * tonemapStrength,
                3f - 3f * tonemapStrength,
                tonemapStrength,
                0f
        };
        if(wpoint != null)for(int i =0; i<3;i++) whitepoint[i] = wpoint[i].floatValue();

    }
}
