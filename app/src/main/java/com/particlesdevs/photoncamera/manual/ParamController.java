package com.particlesdevs.photoncamera.manual;

import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;

import static com.particlesdevs.photoncamera.manual.model.ManualModel.*;

public class ParamController {
    public static void setShutter(long shutterNs) {
        CaptureController captureController = PhotonCamera.getCaptureController();
        try {
            CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
            if (shutterNs == SHUTTER_AUTO) {
                if (PhotonCamera.getManualMode().getCurrentISOValue() == ISO_AUTO)//check if ISO is Auto
                {
                    captureController.setPreviewAEMode();
                }
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                if (shutterNs > ExposureIndex.sec / 5)
                    shutterNs = ExposureIndex.sec / 5;
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNs);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, captureController.mPreviewIso);
            }
            captureController.rebuildPreviewBuilder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setISO(int isoVal) {
        CaptureController captureController = PhotonCamera.getCaptureController();
        try {
            CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
            if (isoVal == ISO_AUTO) {
                if (PhotonCamera.getManualMode().getCurrentExposureValue() == SHUTTER_AUTO) //check if Exposure is Auto
                {
                    captureController.setPreviewAEMode();
                }
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, isoVal);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, captureController.mPreviewExposureTime);
            }
            captureController.rebuildPreviewBuilder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setFocus(float focusDist) {
        CaptureController captureController = PhotonCamera.getCaptureController();
        try {
            CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
            if (focusDist == FOCUS_AUTO) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDist);
            }
            captureController.rebuildPreviewBuilder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setEV(int ev) {
        CaptureController captureController = PhotonCamera.getCaptureController();
        try {
            CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev);
            captureController.rebuildPreviewBuilder();
            //fireValueChangedEvent(knobItemInfo.text);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
