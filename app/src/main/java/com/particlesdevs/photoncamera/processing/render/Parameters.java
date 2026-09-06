package com.particlesdevs.photoncamera.processing.render;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.LensShadingMap;
import android.os.Build;
import android.os.Environment;

import com.particlesdevs.photoncamera.settings.annotations.SensorConfig;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Rational;
import android.util.SizeF;

import androidx.annotation.NonNull;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.util.Allocator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;


public class Parameters {
    private static final String TAG = "Parameters";
    private int analogIso;
    public int iso;
    public double exposureTime = 1.0/30.0; // Default to 1/30s if not available
    public byte cfaPattern;
    public Point rawSize;
    public boolean usedDynamic = false;
    public float[] blackLevel = new float[4];
    public float[] whitePoint = new float[3];
    public int whiteLevel = 1023;
    public static int mergeWhiteLevel = 65535;
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
    public float aperture;
    public int cameraRotation;
    public NoiseModeler noiseModeler;
    public ColorCorrectionTransform CCT;
    public SizeF sensorSize;
    public double angleX;
    public double angleY;
    public double perXAngle;
    public double perYAngle;
    public double XPerMm;
    public double YPerMm;
    public double[] cameraIntrinsic = new double[9];
    public double[] cameraIntrinsicRev = new double[9];
    public float[][] tonemapCurves = new float[3][];
    public float gammaCurve = 2.0f;
    public SpecificSettingSensor sensorSpecifics;

    public int tile = 16;
    public int tilesX = 0;
    public Point alignmentSize = new Point(0, 0);
    public float[] HSVMap = null;
    public float[] LookMap = null;
    public int[] HSVMapSize = new int[2];
    public int[] LookMapSize = new int[3];

    public int calibrationIlluminant1 = -1;
    public int calibrationIlluminant2 = -1;

    public float[] calibrationTransform1 = new float[9];
    public float[] ForwardTransform1 = new float[9];
    public float[] ColorMatrix1 = new float[9];
    public float[] ColorMatrix2 = new float[9];
    public float[] calibrationTransform2 = new float[9];
    public float[] ForwardTransform2 = new float[9];

    public boolean mirror = false;
    public String cameraID = PhotonCamera.getSettings().mCameraID;
    public int physicalID = 0;
    public int logicalID = 0;

    @Tunable(title = "Use Dynamic Black Level", category = "Parameters", defaultValue = 0, min = 0, max = 1, step = 1,
            description = "Use dynamic black level from the camera2api capture result if available (may cause instability on some devices)"
    )
    boolean useDynamicBlackLevel;

    @Tunable(title = "Use Dynamic White Level", category = "Parameters", defaultValue = 1, min = 0, max = 1, step = 1,
            description = "Use dynamic white level from the camera2api capture result if available (may cause instability on some devices)"
    )
    boolean useDynamicWhiteLevel;

    @SensorConfig(title = "White Level Override",
            defaultValue = -1, min = -1, max = 65535, step = 1,
            description = "Override white level for all channels -1 is disabled")
    int whiteLevelOverride;

    @Tunable(title = "Disable front mirror", category = "Parameters", defaultValue = 0, min = 0, max = 1, step = 1,
            description = "Disable front camera mirroring")
    boolean disableMirror;

    @SensorConfig(title = "Black Level Override",
            description = "Override black level for this sensor (-1 = auto)",
            min = -1.0f, max = 8192.0f, step = 1.0f, defaultValue = -1.0f
    )
    float blackLevelOverride;

    public void FillConstParameters(CameraCharacteristics characteristics, Point size) {
        com.particlesdevs.photoncamera.settings.TunableInjector.inject(this);
        cameraID = PhotonCamera.getSettings().mCameraID;
        // Split x-y, x - logical, y - physical
        if(cameraID.contains("-")){
            String[] ids = cameraID.split("-");
            logicalID = Integer.parseInt(ids[0]);
            physicalID = Integer.parseInt(ids[1]);
            //isDualSession = true;
        } else {
            physicalID = Integer.parseInt(cameraID);
            logicalID = Integer.parseInt(cameraID);
        }
        com.particlesdevs.photoncamera.settings.SensorConfigInjector.applyToSensor(String.valueOf(physicalID), this);

        rawSize = size;
        alignmentSize = new Point((size.x / (tile)) + 1, (size.y / (tile)) + 1);
        tilesX = (rawSize.x / 800) + 1;
        Integer analogue = characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);
        if (analogue != null) {
            analogIso = analogue;
        } else analogIso = 100;
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
        sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        XPerMm = rawSize.x / sensorSize.getWidth();
        YPerMm = rawSize.y / sensorSize.getHeight();


        double[] cameraIntrinsic = this.cameraIntrinsic;
        cameraIntrinsic[0] = flen[0];
        cameraIntrinsic[1] = 0.0;
        cameraIntrinsic[2] = rawSize.x / 2.0;
        cameraIntrinsic[3] = 0.0;
        cameraIntrinsic[4] = flen[0];
        cameraIntrinsic[5] = rawSize.y / 2.0;
        cameraIntrinsic[6] = 0.0;
        cameraIntrinsic[7] = 0.0;
        cameraIntrinsic[8] = 1.0;

        cameraIntrinsicRev[0] = 1.0;
        cameraIntrinsicRev[1] = 0.0;
        cameraIntrinsicRev[2] = -rawSize.x / 2.0;
        cameraIntrinsicRev[3] = 0.0;
        cameraIntrinsicRev[4] = 1.0;
        cameraIntrinsicRev[5] = -rawSize.y / 2.0;
        cameraIntrinsicRev[6] = 0.0;
        cameraIntrinsicRev[7] = 0.0;
        cameraIntrinsicRev[8] = flen[0];

        Log.d(TAG, "IntrinsicMatrix:\n"
                + cameraIntrinsic[0] + "," + cameraIntrinsic[1] + "," + cameraIntrinsic[2] + ",\n"
                + cameraIntrinsic[3] + "," + cameraIntrinsic[4] + "," + cameraIntrinsic[5] + ",\n"
                + cameraIntrinsic[6] + "," + cameraIntrinsic[7] + "," + cameraIntrinsic[8] + ",\n");
        angleX = (2 * Math.atan(sensorSize.getWidth() / ((double) flen[0] * 2)));
        angleY = (2 * Math.atan(sensorSize.getWidth() / ((double) flen[0] * 2)));
        perXAngle = rawSize.x / angleX;
        perYAngle = rawSize.y / angleY;
        Log.d(TAG, "Focal Length:" + flen[0]);
        focalLength = flen[0];

        float[] aperture = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        if (aperture == null || aperture.length <= 0) {
            aperture = new float[1];
            aperture[0] = 1.8f;
        }
        Log.d(TAG, "Aperture:" + aperture[0]);
        this.aperture = aperture[0];

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
        var facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            mirror = true;
        }
        if(disableMirror){
            mirror = false;
        }
        //hotPixels = PhotonCamera.getCameraFragment().mHotPixelMap;
    }

    public void FillDynamicParameters(CaptureResult result, CaptureRequest request, int ISO) {
        sensorSpecifics = PhotonCamera.getSpecificSensor().selectedSensorSpecifics;
        Integer sensivity = result.get(CaptureResult.SENSOR_SENSITIVITY);
        if (sensivity == null) {
            sensivity = request.get(CaptureRequest.SENSOR_SENSITIVITY);
            if (sensivity == null) {
                sensivity = ISO;
            }
        }
        iso = sensivity;
        noiseModeler = new NoiseModeler(result.get(CaptureResult.SENSOR_NOISE_PROFILE), analogIso, sensivity, cfaPattern, sensorSpecifics);
        Long exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if (exposure == null) {
            exposure = request.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            if (exposure == null) {
                exposure = 1000000000L / 30; // Default to 1/30s if not available
            }
        }
        exposureTime = ExposureIndex.time2sec(exposure);

        int[] blarr = new int[4];
        BlackLevelPattern level = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if (result != null) {
            boolean isHuawei = Build.BRAND.equals("Huawei");

            if(useDynamicBlackLevel) {
                float[] dynbl = result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
                if (dynbl != null) {
                    System.arraycopy(dynbl, 0, blackLevel, 0, 4);
                    usedDynamic = true;
                }
            }
            Object white = result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
            if (white != null && useDynamicWhiteLevel) {
                whiteLevel = (int) white;
            }

            if(whiteLevelOverride >= 0) {
                whiteLevel = (int) whiteLevelOverride;
            }
            try {
                gainMap = new float[]{1.f, 1.f, 1.f, 1.f};
                mapSize = new Point(1, 1);
                LensShadingMap lensMap = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
                if (lensMap != null) {
                    gainMap = new float[lensMap.getGainFactorCount()];
                    mapSize = new Point(lensMap.getColumnCount(), lensMap.getRowCount());
                    lensMap.copyGainFactors(gainMap, 0);
                    hasGainMap = true;
                    if ((gainMap[(gainMap.length / 8) - (gainMap.length / 8) % 4]) == 1.0 &&
                            (gainMap[(gainMap.length / 2) - (gainMap.length / 2) % 4]) == 1.0 &&
                            (gainMap[(gainMap.length / 2 + gainMap.length / 8) - (gainMap.length / 2 + gainMap.length / 8) % 4]) == 1.0) {
                        hasGainMap = false;
                        if (isHuawei) {
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
                }
            } catch (Exception e){
                Log.d(TAG, "Error retrieving lens shading map, disabling gain map: " + Log.getStackTraceString(e));
            }
            hotPixels = result.get(CaptureResult.STATISTICS_HOT_PIXEL_MAP);

            // Populate custom White Balance neutral point directly from the frame's CaptureRequest metadata
            Integer awbMode = (request != null) ? request.get(CaptureRequest.CONTROL_AWB_MODE) : null;
            RggbChannelVector gains = (request != null) ? request.get(CaptureRequest.COLOR_CORRECTION_GAINS) : null;

            if (awbMode != null && awbMode == CaptureRequest.CONTROL_AWB_MODE_OFF && gains != null) {
                float gAvg = (gains.getGreenEven() + gains.getGreenOdd()) / 2.0f;
                if (Float.isNaN(gAvg) || gAvg <= 1e-4f) {
                    gAvg = 1.0f;
                }
                float rGain = Math.max(gains.getRed(), 1e-4f);
                float bGain = Math.max(gains.getBlue(), 1e-4f);
                if (Float.isNaN(rGain)) rGain = 1.0f;
                if (Float.isNaN(bGain)) bGain = 1.0f;

                customNeutral = new float[]{ gAvg / rGain, 1.0f, gAvg / bGain };
                Log.d("WB_RAW_DEBUG", "FillDynamicParameters Manual: awbMode=" + awbMode
                        + " | Request Gains=" + gains
                        + " | Derived customNeutral=" + Arrays.toString(customNeutral));
                ReCalcColor(true, result);
            } else {
                ReCalcColor(false, result);
            }
        }
        if (!usedDynamic)
            if (level != null) {
                level.copyTo(blarr, 0);
                for (int i = 0; i < 4; i++) blackLevel[i] = blarr[i];
            }
        if(blackLevelOverride >= 0) {
            for (int i = 0; i < 4; i++) blackLevel[i] = blackLevelOverride;
        }
        Float aperture = result.get(CaptureResult.LENS_APERTURE);
        if (aperture == null) {
            aperture = request.get(CaptureRequest.LENS_APERTURE);
            if (aperture == null) {
                aperture = 1.8f; // Default to f/1.8 if not available
            }
        }
        this.aperture = aperture;

        Float focalLength = result.get(CaptureResult.LENS_FOCAL_LENGTH);
        if (focalLength == null) {
            focalLength = request.get(CaptureRequest.LENS_FOCAL_LENGTH);
            if (focalLength == null) {
                focalLength = 4.75f; // Default to 4.75mm if not available
            }
        }
        this.focalLength = focalLength;

        if (Allocator.binning) {
            for (int i = 0; i < blackLevel.length; i++) {
                blackLevel[i] = Math.min(blackLevel[i] * 4f, 65535f);
            }
            whiteLevel = Math.min(whiteLevel * 4, 65535);
        }
    }


    public float[] customNeutral;

    public void ReCalcColor(boolean customNeutr, CaptureResult result) {
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        Rational[] neutralR = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        if (!customNeutr) {
            for (int i = 0; i < neutralR.length; i++) {
                whitePoint[i] = neutralR[i].floatValue();
            }
            Log.d("WB_RAW_DEBUG", "ReCalcColor AUTO: HAL SENSOR_NEUTRAL_COLOR_POINT=" + Arrays.toString(neutralR)
                    + " -> whitePoint=" + Arrays.toString(whitePoint));
        } else {
            whitePoint = customNeutral;
            Log.d("WB_RAW_DEBUG", "ReCalcColor MANUAL: Using customNeutral -> whitePoint=" + Arrays.toString(whitePoint));
        }
        int ref1 = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
        int ref2;
        Object ref2obj = characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);
        if (ref2obj != null) {
            ref2 = (byte) ref2obj;
        } else {
            ref2 = ref1;
        }
        calibrationIlluminant1 = ref1;
        calibrationIlluminant2 = ref2;

        ColorSpaceTransform calibration1 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1);
        ColorSpaceTransform calibration2 = characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2);
        ColorSpaceTransform colorMat1 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1);
        ColorSpaceTransform colorMat2 = characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2);
        ColorSpaceTransform forwardt1 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1);
        ColorSpaceTransform forwardt2 = characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2);

        if (sensorSpecifics.CCTExists) {
            if (sensorSpecifics.calibrationTransform1 != null) {
                calibration1 = sensorSpecifics.calibrationTransform1;
                Log.d(TAG, "Using custom calibration transform 1:"+ calibration1);
            }
            if (sensorSpecifics.calibrationTransform2 != null) {
                calibration2 = sensorSpecifics.calibrationTransform2;
            }

            if (sensorSpecifics.colorTransform1 != null) {
                colorMat1 = sensorSpecifics.colorTransform1;
                Log.d(TAG, "Using custom color transform 1:"+ colorMat1.toString());
            }
            if (sensorSpecifics.colorTransform2 != null) {
                colorMat2 = sensorSpecifics.colorTransform2;
            }

            if (sensorSpecifics.forwardMatrix1 != null) {
                forwardt1 = sensorSpecifics.forwardMatrix1;
            }
            if (sensorSpecifics.forwardMatrix2 != null) {
                forwardt2 = sensorSpecifics.forwardMatrix2;
            }
            if (sensorSpecifics.referenceIlluminant1 != -1) {
                ref1 = sensorSpecifics.referenceIlluminant1;
            }
            if (sensorSpecifics.referenceIlluminant2 != -1) {
                ref2 = sensorSpecifics.referenceIlluminant2;
            }

        }
        // Check if forward matrices have each component non-zero, otherwise replace with identity
        boolean invertible = true;
        for (int i = 0; i < 3; i++) {
            float sum = 0.0f;
            for (int j = 0; j < 3; j++) {
                if (forwardt1 != null)
                    sum += Math.abs(forwardt1.getElement(i, j).floatValue());
            }
            if(sum == 0.0f) invertible = false;
        }
        if(!invertible) {
            Log.d(TAG, "Forward matrix 1 is not invertible, using identity");
            forwardt1 = new ColorSpaceTransform(new Rational[]{
                    new Rational(1,1), new Rational(0,1), new Rational(0,1),
                    new Rational(0,1), new Rational(1,1), new Rational(0,1),
                    new Rational(0,1), new Rational(0,1), new Rational(1,1)
            });
        }
        invertible = true;
        for (int i = 0; i < 3; i++) {
            float sum = 0.0f;
            for (int j = 0; j < 3; j++) {
                if (forwardt2 != null)
                    sum += Math.abs(forwardt2.getElement(i, j).floatValue());
            }
            if(sum == 0.0f) invertible = false;
        }
        if(!invertible) {
            Log.d(TAG, "Forward matrix 1 is not invertible, using identity");
            forwardt2 = new ColorSpaceTransform(new Rational[]{
                    new Rational(1,1), new Rational(0,1), new Rational(0,1),
                    new Rational(0,1), new Rational(1,1), new Rational(0,1),
                    new Rational(0,1), new Rational(0,1), new Rational(1,1)
            });
        }

        Converter.convertColorspaceTransform(calibration1, calibrationTransform1);
        Converter.convertColorspaceTransform(calibration2, calibrationTransform2);
        Converter.convertColorspaceTransform(forwardt1, ForwardTransform1);
        Converter.convertColorspaceTransform(forwardt2, ForwardTransform2);
        Converter.convertColorspaceTransform(colorMat1, ColorMatrix1);
        Converter.convertColorspaceTransform(colorMat2, ColorMatrix2);

        float[] normalizedForwardTransform1 = ForwardTransform1.clone();
        float[] normalizedColorMatrix1 = ColorMatrix1.clone();
        float[] normalizedColorMatrix2 = ColorMatrix2.clone();
        float[] normalizedForwardTransform2 = ForwardTransform2.clone();

        Converter.normalizeFM(normalizedForwardTransform1);
        Converter.normalizeFM(normalizedForwardTransform2);

        Converter.normalizeFM(normalizedColorMatrix1);
        Converter.normalizeFM(normalizedColorMatrix2);
        float[] sensorToXYZ = new float[9];
        Log.d("Parameters", "calibrationTransform1: " + Arrays.toString(calibrationTransform1) + " calibrationTransform2: " + Arrays.toString(calibrationTransform2) + " normalizedColorMatrix1: " + Arrays.toString(normalizedColorMatrix1) + " normalizedColorMatrix2: " + Arrays.toString(normalizedColorMatrix2));
        double interpolationFactor = Converter.findDngInterpolationFactor(ref1,
                ref2, calibrationTransform1, calibrationTransform2,
                normalizedColorMatrix1, normalizedColorMatrix2, whitePoint);
        Log.d("Parameters", "Interpolation factor: " + interpolationFactor);
        Log.d("Parameters", "normalizedForwardTransform1:" + Arrays.toString(normalizedForwardTransform1) +
                " normalizedForwardTransform2:" + Arrays.toString(normalizedForwardTransform2));
        Converter.calculateCameraToXYZD50Transform(normalizedForwardTransform1, normalizedForwardTransform2,
                calibrationTransform1, calibrationTransform2, whitePoint,
                interpolationFactor, /*out*/sensorToXYZ);
        Log.d("Parameters", "sensorToXYZ: " + Arrays.toString(sensorToXYZ));
        if (sensorSpecifics.profileHueSatMapDims != null && sensorSpecifics.profileHueSatMapData1 != null && sensorSpecifics.profileHueSatMapData2 != null) {
            HSVMapSize[0] = sensorSpecifics.profileHueSatMapDims[0];
            HSVMapSize[1] = sensorSpecifics.profileHueSatMapDims[1];
            HSVMap = new float[HSVMapSize[0] * HSVMapSize[1] * 3];
            for (int i = 0; i < HSVMap.length; i+=3) {
                HSVMap[i] = sensorSpecifics.profileHueSatMapData1[i] * (1.f - (float)interpolationFactor) + sensorSpecifics.profileHueSatMapData2[i] * (float)interpolationFactor;
                HSVMap[i+1] = sensorSpecifics.profileHueSatMapData1[i+1] * (1.f - (float)interpolationFactor) + sensorSpecifics.profileHueSatMapData2[i+1] * (float)interpolationFactor;
                HSVMap[i+2] = sensorSpecifics.profileHueSatMapData1[i+2] * (1.f - (float)interpolationFactor) + sensorSpecifics.profileHueSatMapData2[i+2] * (float)interpolationFactor;
                HSVMap[i] /= 360.f;
            }
        }
        if (sensorSpecifics.profileLookTableDims != null && sensorSpecifics.profileLookTableData != null) {
            LookMapSize[0] = sensorSpecifics.profileLookTableDims[0];
            LookMapSize[1] = sensorSpecifics.profileLookTableDims[1];
            LookMapSize[2] = sensorSpecifics.profileLookTableDims[2];
            LookMap = new float[LookMapSize[0] * LookMapSize[1] * LookMapSize[2] * 3];
            for (int i = 0; i < LookMap.length; i+=3) {
                LookMap[i] = sensorSpecifics.profileLookTableData[i] / 360.f;
                LookMap[i+1] = sensorSpecifics.profileLookTableData[i+1];
                LookMap[i+2] = sensorSpecifics.profileLookTableData[i+2];
            }
        }
        Converter.multiply(Converter.sXYZtoProPhoto, sensorToXYZ, /*out*/sensorToProPhoto);
        File customCCT = new File(Environment.getExternalStorageDirectory() + "//DCIM//PhotonCamera//", "customCCT.txt");
        //ColorSpaceTransform CST = PhotonCamera.getCaptureController().mColorSpaceTransform;//= result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        ColorSpaceTransform CST = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
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
        if (PhotonCamera.getSettings().colorMethod == 1){
            wrongCalibration = false;
        }
        if (PhotonCamera.getSettings().colorMethod == 2){
            wrongCalibration = true;
        }
        if (CST != null) {
            CST.copyElements(rat, 0);
            int cnt = 0;
            for (int i = 0; i < 9; i++) {
                if (rat[i].floatValue() != 0.0f) cnt++;
            }
            if (cnt <= 4) wrongCalibration = false;
        } else wrongCalibration = false;
        if (sensorSpecifics.CCTExists) wrongCalibration = false;
        if (PhotonCamera.getSpecific().specificSetting.isRawColorCorrection)
            wrongCalibration = false;
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
            Log.d(TAG, "Using captured color correction transform");
        } else {
            Log.d(TAG, "Using calculated color correction transform");
        }
        Log.d(TAG, Arrays.toString(sensorToProPhoto) + PhotonCamera.getSettings().colorMethod);
        Converter.multiply(Converter.sXYZtoSRGB, Converter.sProPhotoToXYZ, /*out*/proPhotoToSRGB);
        if (CST != null && wrongCalibration && !customCCT.exists()) {
            Rational[] temp = new Rational[9];
            CST.copyElements(temp, 0);
            for (int i = 0; i < 9; i++) {
                proPhotoToSRGB[i] = temp[i].floatValue();
            }
            //Normalize CST result
            normalize(proPhotoToSRGB);
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

    private void normalize(float[] in) {
        float avr = in[0] + in[1] + in[2];
        in[0] /= avr;
        in[1] /= avr;
        in[2] /= avr;
        avr = in[3] + in[4] + in[5];
        in[3] /= avr;
        in[4] /= avr;
        in[5] /= avr;
        avr = in[6] + in[7] + in[8];
        in[6] /= avr;
        in[7] /= avr;
        in[8] /= avr;
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
                "\n CameraID=" + cameraID +
                "\n DenoiseOn=" + PhotonCamera.getSettings().hdrxNR +
                "\n Sharp=" + FltFormat(PreferenceKeys.getSharpnessValue()) +
                "\n Sat=" + FltFormat(PreferenceKeys.getSaturationValue()) +
                "\n Contrast=" + FltFormat(PreferenceKeys.getContrastValue()) +
                "\n ExpoCorrect=" + FltFormat(PhotonCamera.getSettings().exposureCompensation) +
                "\n Denoise=" + FltFormat(PreferenceKeys.getFloat(PreferenceKeys.Key.KEY_NOISESTR_SEEKBAR)) +
                "\n Noise Merging=" + FltFormat(PhotonCamera.getSettings().mergeStrength) +
                "\n Shadows=" + FltFormat(PhotonCamera.getSettings().shadows) +
                "\n Compressor=" + FltFormat(PhotonCamera.getSettings().compressor) +
                "\n Align=" + PhotonCamera.getSettings().alignAlgorithm +
                "\n Color=" + PhotonCamera.getSettings().colorMethod +
                "\n PreviewFormat=" + PhotonCamera.getSettings().previewFormat +
                "\n FocalL=" + FltFormat(focalLength) +
                "\n Version=" + PhotonCamera.getVersion();
    }

    @SuppressLint("DefaultLocale")
    private String FltFormat(Object in) {
        return String.format("%.2f", Float.parseFloat(in.toString()));
    }
}
