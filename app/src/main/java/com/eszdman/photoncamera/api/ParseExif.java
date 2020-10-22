package com.eszdman.photoncamera.api;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.app.PhotonCamera;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static android.hardware.camera2.CaptureResult.*;
import static androidx.exifinterface.media.ExifInterface.*;

public class ParseExif {
    static String getTime(long exposureTime) {
        String out;
        long sec = 1000000000;
        double time = (double) (exposureTime) / sec;
        out = String.valueOf((time));
        return out;
    }

    static public String resultget(CaptureResult res, Key<?> key) {
        Object out = res.get(key);
        if (out != null) return out.toString();
        else return "";
    }

    public static ExifInterface Parse(CaptureResult result, String path) {
        ExifInterface inter = null;
        try {
            inter = new ExifInterface(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int rotation = PhotonCamera.getParameters().cameraRotation;
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCameraFragment().mSensorOrientation);
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
        assert inter != null;
        //inter.setAttribute(TAG_ORIENTATION,Integer.toString(orientation));
        inter.setAttribute(TAG_SENSITIVITY_TYPE, String.valueOf(SENSITIVITY_TYPE_ISO_SPEED));
        Object iso = result.get(SENSOR_SENSITIVITY);
        int isonum = 100;
        if (iso != null) isonum = (int) ((int) (iso) * IsoExpoSelector.getMPY());
        Log.d(TAG, "sensivity:" + isonum);
        inter.setAttribute(TAG_PHOTOGRAPHIC_SENSITIVITY, String.valueOf(isonum));
        inter.setAttribute(TAG_F_NUMBER, resultget(result, LENS_APERTURE));
        inter.setAttribute(TAG_FOCAL_LENGTH, ((int) (100 * Double.parseDouble(resultget(result, LENS_FOCAL_LENGTH)))) + "/100");
        //inter.setAttribute(TAG_FOCAL_LENGTH_IN_35MM_FILM,result.get(LENS_FOCAL_LENGTH).toString());
        inter.setAttribute(TAG_COPYRIGHT, "PhotonCamera");
        inter.setAttribute(TAG_APERTURE_VALUE, String.valueOf(result.get(LENS_APERTURE)));
        inter.setAttribute(TAG_EXPOSURE_TIME, getTime(Long.parseLong(resultget(result, SENSOR_EXPOSURE_TIME))));
        inter.setAttribute(ExifInterface.TAG_DATETIME, sFormatter.format(new Date(System.currentTimeMillis())));
        inter.setAttribute(TAG_MODEL, Build.MODEL);
        inter.setAttribute(TAG_MAKE, Build.BRAND);
        inter.setAttribute(TAG_EXIF_VERSION, "0231");
        String version = "";
        try {
            PackageInfo pInfo = PhotonCamera.getCameraActivity().getPackageManager().getPackageInfo(PhotonCamera.getCameraActivity().getPackageName(), 0);
            version = pInfo.versionName;
            version +=pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        inter.setAttribute(TAG_IMAGE_DESCRIPTION, PhotonCamera.getParameters().toString() +
                "\n" + "Version:" + version);
        return inter;
    }

    private static final SimpleDateFormat sFormatter;

    static {
        sFormatter = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
        sFormatter.setTimeZone(TimeZone.getDefault());
    }
}
