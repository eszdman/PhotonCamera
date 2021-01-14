package com.particlesdevs.photoncamera.api;

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;

import java.lang.reflect.Field;

import static android.hardware.camera2.CameraCharacteristics.*;
import static android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE;
import static android.hardware.camera2.CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON;
import static android.hardware.camera2.CaptureResult.*;

@SuppressWarnings("ALL")
public class Camera2ApiAutoFix {
    private static final String TAG = "Camera2ApiAutoFix";
    private CameraCharacteristics characteristics;
    private CaptureResult result;

    Camera2ApiAutoFix(CameraCharacteristics characteristic) {
        characteristics = characteristic;
    }

    Camera2ApiAutoFix(CaptureResult res) {
        result = res;
    }

    public static void Init() {
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(CaptureController.mCameraCharacteristics);
        fix.ExposureTime();
    }

    public static void Apply() {
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(characteristics);
        fix.MaxRegionsAF();
    }
    //private static double oldWL = -1.0;
    public static void ApplyRes(CaptureResult captureResult) {
        Camera2ApiAutoFix fix = new Camera2ApiAutoFix(captureResult);
        //fix.gains();
        fix.BL();
        fix.whitePoint();
        fix.CCM();
        /*Camera2ApiAutoFix.WhiteLevel(CameraFragment.mCaptureResult, (int)oldWL);
        Camera2ApiAutoFix.BlackLevel(CameraFragment.mCaptureResult, PhotonCamera.getParameters().blackLevel, 1.f);
        oldWL = -1.0;*/
    }
    public static void ApplyBurst() {
        /*if(oldWL == -1.0) {
            PhotonCamera.getParameters().FillParameters(null,CameraFragment.mCameraCharacteristics,null);
            Camera2ApiAutoFix.WhiteLevel(null, 65535);
            Camera2ApiAutoFix.BlackLevel(null, PhotonCamera.getParameters().blackLevel, (float) (65535) / PhotonCamera.getParameters().whiteLevel);
            oldWL = PhotonCamera.getParameters().whiteLevel;
        }*/
    }

    private void whitePoint() {
        CameraReflectionApi.set(SENSOR_NEUTRAL_COLOR_POINT, PhotonCamera.getCaptureController().mPreviewTemp);
    }

    private void CCM() {
        CameraReflectionApi.set(COLOR_CORRECTION_TRANSFORM, PhotonCamera.getCaptureController().mColorSpaceTransform);
    }

    public void curve() {
        CameraReflectionApi.set(TONEMAP_MAX_CURVE_POINTS, 128);
    }

    boolean checkdouble(double in) {
        return (((int) in * 100) % 100 == 0);
    }

    private void ExposureTime() {
        Range exprange = characteristics.get(SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (exprange == null) return;
        if ((long) exprange.getUpper() < ExposureIndex.sec / 7) {
            Log.d(TAG, "Applied Fix ExposureTime no CIT");
            Range nrange = new Range(exprange.getLower(), ExposureIndex.sec / 3);
            CameraReflectionApi.set(SENSOR_INFO_EXPOSURE_TIME_RANGE, nrange);
        } else if ((long) exprange.getUpper() > ExposureIndex.sec * 5 / 6 && (long) exprange.getUpper() < ExposureIndex.sec * 2) {
            Log.d(TAG, "Applied Fix ExposureTime2 CIT SHIFT");
            Range nrange = new Range(exprange.getLower(), (long) (ExposureIndex.sec * 5.2));
            CameraReflectionApi.set(SENSOR_INFO_EXPOSURE_TIME_RANGE, nrange);
        }
    }
    public static void patchWL(CameraCharacteristics characteristics,
                               CaptureResult captureResult,
                               int patchWL) {
        if (patchWL != 0) {
            WhiteLevel(captureResult, patchWL);
            BlackLevel(characteristics, captureResult, PhotonCamera.getParameters().blackLevel,
                    (float) (patchWL) / PhotonCamera.getParameters().whiteLevel);
        }
    }

    public static void resetWL(CameraCharacteristics characteristics,
                               CaptureResult captureResult,
                               int patchWL) {
        if (patchWL != 0) {
            WhiteLevel(captureResult, PhotonCamera.getParameters().whiteLevel);
            BlackLevel(characteristics, captureResult, PhotonCamera.getParameters().blackLevel,
                    1.f);
        }
    }

    public static void WhiteLevel(CaptureResult res, int whitelevel) {
        if (res != null)
            CameraReflectionApi.set(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL, whitelevel);
        CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL, whitelevel);
    }

    public static void BlackLevel(CameraCharacteristics characteristics, CaptureResult res, int[] blacklevel) {
        BlackLevelPattern blackLevel = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        int[] levelArr = new int[4];
        if (blackLevel != null) {
            blackLevel.copyTo(levelArr, 0);
            for (int i = 0; i < 4; i++) {
                levelArr[i] = (int) (blacklevel[i]);
            }
            CameraReflectionApi.PatchBL(blackLevel, levelArr);
            CameraReflectionApi.set(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN, blackLevel);
        }

        float[] dynBL = res.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        if (dynBL != null) {
            for (int i = 0; i < dynBL.length; i++) {
                dynBL[i] = blacklevel[i];
            }
            CameraReflectionApi.set(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL, dynBL, res);
        }
    }

    public static void BlackLevel(CameraCharacteristics characteristics, CaptureResult res, float[] blacklevel, float mpy) {
        BlackLevelPattern blackLevel = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        int[] levelArr = new int[4];
        if (blackLevel != null) {
            blackLevel.copyTo(levelArr, 0);
            for (int i = 0; i < 4; i++) {
                levelArr[i] = (int) (blacklevel[i]*mpy);
            }
            CameraReflectionApi.PatchBL(blackLevel, levelArr);
            CameraReflectionApi.set(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN, blackLevel);
        }
        if(res != null) {
            float[] dynBL = res.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
            if (dynBL != null) {
                for (int i = 0; i < dynBL.length; i++) {
                    dynBL[i] = blacklevel[i] * mpy;
                }
                CameraReflectionApi.set(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL, dynBL, res);
            }
        }
    }
    private void MaxRegionsAF() {
        //CameraReflectionApi.set(CONTROL_MAX_REGIONS_AF,5);
    }

    public void gains() {
        CameraReflectionApi.setVERBOSE(true);
        Rational[] WB = result.get(SENSOR_NEUTRAL_COLOR_POINT);
        if (WB == null) return;
        RggbChannelVector rggbChannelVector = result.get(COLOR_CORRECTION_GAINS);
        if (rggbChannelVector == null) {
            CameraReflectionApi.set(COLOR_CORRECTION_GAINS, new RggbChannelVector(WB[0].floatValue() * 1.3f, WB[1].floatValue() / 1.78f, WB[1].floatValue() / 1.78f, WB[2].floatValue() * 2f));
        }
        Log.d(TAG, "Initial channelVector:" + rggbChannelVector.toString());
        if (checkdouble(rggbChannelVector.getRed()) && checkdouble(rggbChannelVector.getGreenEven()) && checkdouble(rggbChannelVector.getGreenOdd()) && checkdouble(rggbChannelVector.getBlue()))
            try {
                Field field = rggbChannelVector.getClass().getDeclaredField("mRed");
                field.setAccessible(true);
                field.set(rggbChannelVector, 1f / WB[0].floatValue());
                field = rggbChannelVector.getClass().getDeclaredField("mGreenEven");
                field.setAccessible(true);
                field.set(rggbChannelVector, 1f / WB[1].floatValue());
                field = rggbChannelVector.getClass().getDeclaredField("mGreenOdd");
                field.setAccessible(true);
                field.set(rggbChannelVector, 1f / WB[1].floatValue());
                field = rggbChannelVector.getClass().getDeclaredField("mBlue");
                field.setAccessible(true);
                field.set(rggbChannelVector, 1f / WB[2].floatValue());
                CameraReflectionApi.set(COLOR_CORRECTION_GAINS, null);
                CameraReflectionApi.set(COLOR_CORRECTION_GAINS, rggbChannelVector);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        Log.d(TAG, "Overrided channelVector:" + rggbChannelVector.toString());
    }

    @SuppressLint("NewApi")
    public void BL() {
        float[] level = result.get(SENSOR_DYNAMIC_BLACK_LEVEL);
        BlackLevelPattern ptr = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        if (ptr == null) return;
        if (level == null) {
            level = new float[4];
            for (int i = 0; i < 4; i++) {
                level[i] = ptr.getOffsetForIndex(i % 2, i / 2);
            }
            CameraReflectionApi.set(SENSOR_DYNAMIC_BLACK_LEVEL, level);
        }
    }

    public static void applyEnergySaving() {
        if(PhotonCamera.getSettings().energySaving){
            GLDrawParams.TileSize = 8;
        } else {
            GLDrawParams.TileSize = 256;
        }
    }

    public static void applyPrev(CaptureRequest.Builder captureBuilder) {
        Camera2ApiAutoFix.Apply();
//        captureBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
        //captureBuilder.set(COLOR_CORRECTION_MODE,COLOR_CORRECTION_MODE_HIGH_QUALITY);
        int[] stabilizationModes = CaptureController.mCameraCharacteristics.get(LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        if (stabilizationModes != null && stabilizationModes.length > 1) {
            captureBuilder.set(LENS_OPTICAL_STABILIZATION_MODE, LENS_OPTICAL_STABILIZATION_MODE_ON);//Fix ois bugs for preview and burst
        }
        //captureBuilder.set(CONTROL_AE_EXPOSURE_COMPENSATION,-1);
        Range<Integer> range = CaptureController.mCameraCharacteristics.get(CONTROL_AE_COMPENSATION_RANGE);

        //if (selectedMode == CameraMode.NIGHT && range != null)
        //    captureBuilder.set(CONTROL_AE_EXPOSURE_COMPENSATION, (int) range.getUpper());

        /*Point size = new Point(Interface.getCameraFragment().mImageReaderPreview.getWidth(),Interface.getCameraFragment().mImageReaderPreview.getHeight());
        double sizex = size.x;
        double sizey = size.y;*/
        //captureBuilder.set(CONTROL_AE_TARGET_FPS_RANGE,new Range<>(24,60));
        /*MeteringRectangle[] rectm8 = new MeteringRectangle[2];
        rectm8[0] = new MeteringRectangle(new Point((int)(sizex/2.0),(int)(sizey/2.0)),new Size((int)(sizex*2.0/4.0),(int)(sizey*2.0/4.0)),10);
        rectm8[1] = new MeteringRectangle(new Point((int)(sizex/2.0),(int)(sizey/2.0)),new Size((int)(sizex/7),(int)(sizey/7)),30);
        MeteringRectangle[] rectaf = new MeteringRectangle[1];
        rectaf[0] =  new MeteringRectangle(new Point((int)(sizex/2.0),(int)(sizey/2.0)),new Size((int)(sizex/4),(int)(sizey/4)),10);
        //captureBuilder.set(CONTROL_AF_REGIONS,rectaf);
        captureBuilder.set(CONTROL_AE_REGIONS,rectm8);
        //captureBuilder.set(CONTROL_AF_MODE, Interface.getSettings().afMode);*/
//        Object focus = captureBuilder.get(CONTROL_AF_MODE);
//        Log.d(TAG, "InDeviceFocus:" + (int) (focus));
//        if (focus != null)
//            afMode = (int) focus;
//        TouchFocus.onConfigured = false;
//        initialAF = captureBuilder.get(CONTROL_AF_REGIONS);
//        initialAE = captureBuilder.get(CONTROL_AE_REGIONS);
        //Interface.getTouchFocus().setFocus(size.x/2,size.y/2);
//        TouchFocus.onConfigured = true;
        //captureBuilder.set(TONEMAP_MODE, TONEMAP_MODE_GAMMA_VALUE);
        /*float[] rgb = new float[64];
        for (int i = 0; i < 64; i += 2) {
            float x = ((float) i) / 64.f;
            rgb[i] = x;
            float output = 2.8114f * x + -3.5701f * x * x + 1.6807f * x * x * x;
            output = Math.max(output, 0.f);
            output = Math.min(output, 1.f);
            //Log.d(TAG,"Curve:"+output);
            rgb[i + 1] = output;
        }
        TonemapCurve tonemapCurve = new TonemapCurve(rgb, rgb, rgb);
        captureBuilder.set(TONEMAP_CURVE, tonemapCurve);*/

        /*float[] apertures = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        if(apertures != null && apertures.length > 1){
            captureBuilder.set(LENS_APERTURE,apertures[1]);
        }*/
    }
}
