package com.eszdman.photoncamera.api;

import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.ui.CameraFragment;
import com.eszdman.photoncamera.ui.MainActivity;

import java.io.IOException;
import static android.hardware.camera2.CaptureResult.*;
import static androidx.exifinterface.media.ExifInterface.*;

public class ParseExif {
    static String getTime(long exposuretime){
        String out;
        CaptureResult res;
        long sec = 1000000000;
        double time = (double)(exposuretime)/sec;
        out = String.valueOf((time));
        return out;
    }
    static public String resultget(CaptureResult res,Key<Object> key){
        Object out = res.get(key);
        if(out !=null) return out.toString();
        else return "";
    }

    public static ExifInterface Parse(CaptureResult result, String path){
        ExifInterface inter = null;
        try {
            inter = new ExifInterface(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int rotation = Interface.i.gravity.getCameraRotation();
        String TAG = "ParseExif";
        Log.d(TAG,"Gravity rotation:"+Interface.i.gravity.getRotation());
        Log.d(TAG,"Sensor rotation:"+Interface.i.camera.mSensorOrientation);
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
        Log.d(TAG,"rotation:"+rotation);
        Log.d(TAG,"orientation:"+orientation);
        assert inter != null;
        //inter.setAttribute(TAG_ORIENTATION,Integer.toString(orientation));
        inter.setAttribute(TAG_SENSITIVITY_TYPE, String.valueOf(SENSITIVITY_TYPE_ISO_SPEED));
        Object iso = result.get(SENSOR_SENSITIVITY);
        int isonum = 100;
        if(iso != null) isonum = (int)((int)(iso)*IsoExpoSelector.getMPY());
        Log.d(TAG, "sensivity:"+isonum);
        inter.setAttribute(TAG_PHOTOGRAPHIC_SENSITIVITY, String.valueOf(isonum));
        inter.setAttribute(TAG_F_NUMBER,result.get(LENS_APERTURE).toString());
        inter.setAttribute(TAG_FOCAL_LENGTH,result.get(LENS_FOCAL_LENGTH).toString());
        inter.setAttribute(TAG_FOCAL_LENGTH_IN_35MM_FILM,result.get(LENS_FOCAL_LENGTH).toString());
        inter.setAttribute(TAG_COPYRIGHT,"PhotonCamera");
        inter.setAttribute(TAG_APERTURE_VALUE,result.get(LENS_APERTURE).toString());
        inter.setAttribute(TAG_EXPOSURE_TIME,getTime(result.get(SENSOR_EXPOSURE_TIME)));
        inter.setAttribute(TAG_MODEL, Build.MODEL);
        inter.setAttribute(TAG_MAKE, Build.BRAND);
        inter.setAttribute(TAG_EXIF_VERSION,"0231");
        inter.setAttribute(TAG_IMAGE_DESCRIPTION,Interface.i.parameters.toString()+
                "\n"+"Version:"+"0.55");
        return inter;
    }
}
