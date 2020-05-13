package com.eszdman.photoncamera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import androidx.exifinterface.media.ExifInterface;
import java.io.IOException;
import static android.hardware.camera2.CaptureResult.*;
import static androidx.exifinterface.media.ExifInterface.*;

public class ParseExif {
    public static ExifInterface Parse(CaptureResult result, String path){
        ExifInterface inter = null;
        try {
            inter = new ExifInterface(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //inter.setAttribute(TAG_MODEL,Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.));
        int rotation = Camera2Api.context.getOrientation(MainActivity.act.getWindowManager().getDefaultDisplay().getRotation());
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
        inter.setAttribute(TAG_ORIENTATION,Integer.toString(orientation));
        inter.setAttribute(TAG_SENSITIVITY_TYPE, String.valueOf(SENSITIVITY_TYPE_ISO_SPEED));
        inter.setAttribute(TAG_ISO_SPEED,result.get(SENSOR_SENSITIVITY).toString());
        inter.setAttribute(TAG_APERTURE_VALUE,result.get(LENS_APERTURE).toString());
        inter.setAttribute(TAG_EXPOSURE_TIME,result.get(SENSOR_EXPOSURE_TIME).toString());
        //inter.setAltitude(TAG_);
        return inter;
    }
}
