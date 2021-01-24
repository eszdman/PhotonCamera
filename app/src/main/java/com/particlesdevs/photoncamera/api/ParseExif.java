package com.particlesdevs.photoncamera.api;

import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.util.Log;
import androidx.exifinterface.media.ExifInterface;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static android.hardware.camera2.CaptureResult.*;
import static androidx.exifinterface.media.ExifInterface.*;

public class ParseExif {
    public static final SimpleDateFormat sFormatter;
    private static final String TAG = "ParseExif";

    static {
        sFormatter = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
        sFormatter.setTimeZone(TimeZone.getDefault());
    }

    public static String getTime(long exposureTime) {
        String out;
        long sec = 1000000000;
        double time = (double) (exposureTime) / sec;
        out = String.valueOf((time));
        return out;
    }

    public static String resultget(CaptureResult res, Key<?> key) {
        Object out = res.get(key);
        if (out != null) return out.toString();
        else return "";
    }

    public static ExifData parse(CaptureResult result) {
        ExifData data = new ExifData();

        int rotation = PhotonCamera.getParameters().cameraRotation;
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCaptureController().mSensorOrientation);
        int orientation = ORIENTATION_NORMAL;
        switch (rotation) {
            case 90:
                orientation = ExifInterface.ORIENTATION_ROTATE_90;
                break;
            case 180:
                orientation = ExifInterface.ORIENTATION_ROTATE_180;
                break;
            case 270:
                orientation = ExifInterface.ORIENTATION_ROTATE_270;
                break;
        }
        Log.d(TAG, "rotation:" + rotation);
        Log.d(TAG, "orientation:" + orientation);

        Integer iso = result.get(SENSOR_SENSITIVITY);
        int isonum = 100;
        if (iso != null) isonum = (int) (iso * IsoExpoSelector.getMPY());
        Log.d(TAG, "sensivity:" + isonum);

        data.SENSITIVITY_TYPE = String.valueOf(ExifInterface.SENSITIVITY_TYPE_ISO_SPEED);
        data.PHOTOGRAPHIC_SENSITIVITY = String.valueOf(isonum);
        data.F_NUMBER = resultget(result, LENS_APERTURE);
        data.FOCAL_LENGTH = ((int) (100 * Double.parseDouble(resultget(result, LENS_FOCAL_LENGTH)))) + "/100";
        data.APERTURE_VALUE = String.valueOf(result.get(LENS_APERTURE));
        data.EXPOSURE_TIME = getTime(Long.parseLong(resultget(result, SENSOR_EXPOSURE_TIME)));
        data.DATETIME = sFormatter.format(new Date(System.currentTimeMillis()));
        data.COMPRESSION = "97";
        data.COLOR_SPACE = "sRGB";
        data.EXIF_VERSION = "0231";
        data.IMAGE_DESCRIPTION = PhotonCamera.getParameters().toString() +
                "\n " + "Version=" + PhotonCamera.getVersion();
        /*
        //saving for later use
        float sensorWidth = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE).getWidth();
        String mm35 = String.valueOf((short) (36 * (result.get(LENS_FOCAL_LENGTH) / sensorWidth)));
        inter.setAttribute(TAG_FOCAL_LENGTH_IN_35MM_FILM, mm35);
        Log.d(TAG, "Saving 35mm FocalLength = " + mm35);
        */
        return data;
    }

    public static ExifInterface setAllAttributes(File file, ExifData data) {
        ExifInterface inter = null;
        try {
            inter = new ExifInterface(file);
        } catch (IOException e) {
            e.printStackTrace();
            return inter;
        }
        inter.setAttribute(TAG_SENSITIVITY_TYPE, data.SENSITIVITY_TYPE);
        inter.setAttribute(TAG_PHOTOGRAPHIC_SENSITIVITY, data.PHOTOGRAPHIC_SENSITIVITY);
        inter.setAttribute(TAG_F_NUMBER, data.F_NUMBER);
        inter.setAttribute(TAG_FOCAL_LENGTH, data.FOCAL_LENGTH);
        inter.setAttribute(TAG_COPYRIGHT, data.COPYRIGHT);
        inter.setAttribute(TAG_APERTURE_VALUE, data.APERTURE_VALUE);
        inter.setAttribute(TAG_EXPOSURE_TIME, data.EXPOSURE_TIME);
        inter.setAttribute(ExifInterface.TAG_DATETIME, data.DATETIME);
        inter.setAttribute(TAG_MODEL, data.MODEL);
        inter.setAttribute(TAG_MAKE, data.MAKE);
        inter.setAttribute(TAG_COMPRESSION, data.COMPRESSION);
        inter.setAttribute(TAG_COLOR_SPACE, data.COLOR_SPACE);
        inter.setAttribute(TAG_EXIF_VERSION, data.EXIF_VERSION);
        inter.setAttribute(TAG_IMAGE_DESCRIPTION, data.IMAGE_DESCRIPTION);
        return inter;
    }

    public static int getOrientation() {
        int rotation = PhotonCamera.getGravity().getCameraRotation();
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCaptureController().mSensorOrientation);
        int orientation = ORIENTATION_NORMAL;
        switch (rotation) {
            case 90:
                orientation = ExifInterface.ORIENTATION_ROTATE_90;
                break;
            case 180:
                orientation = ExifInterface.ORIENTATION_ROTATE_180;
                break;
            case 270:
                orientation = ExifInterface.ORIENTATION_ROTATE_270;
                break;
        }
        return orientation;
    }

    public static class ExifData {
        public final String MODEL = Build.MODEL;
        public final String MAKE = Build.BRAND;
        public final String COPYRIGHT = "PhotonCamera";
        public String SENSITIVITY_TYPE;
        public String PHOTOGRAPHIC_SENSITIVITY;
        public String APERTURE_VALUE;
        public String COMPRESSION;
        public String COLOR_SPACE;
        public String EXIF_VERSION;
        public String IMAGE_DESCRIPTION;
        public String DATETIME;
        public String EXPOSURE_TIME;
        public String F_NUMBER;
        public String FOCAL_LENGTH;
    }
}
