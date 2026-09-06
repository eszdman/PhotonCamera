package com.particlesdevs.photoncamera.manual;
/*
 * Class responsible for setting manual mode parameters to the camera preview.
 *
 *         Copyright (C) 2021  Vibhor Srivastava
 *         This program is free software: you can redistribute it and/or modify
 *         it under the terms of the GNU General Public License as published by
 *         the Free Software Foundation, either version 3 of the License, or
 *         (at your option) any later version.
 *
 *         This program is distributed in the hope that it will be useful,
 *         but WITHOUT ANY WARRANTY; without even the implied warranty of
 *         MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *         GNU General Public License for more details.
 *
 *         You should have received a copy of the GNU General Public License
 *         along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.Log;

import androidx.annotation.NonNull;

import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.parameters.ColorTemperatureConverter;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;

import java.util.Observable;
import java.util.Observer;

/**
 * Observer class for {@link ManualParamModel}
 * This class is responsible for setting manual parameters to the camera preview
 * <p>
 * Created by Vibhor Srivastava on 07/Jan/2021
 */
public class ParamController implements Observer {
    public int ISO = -1;
    public int EV = 0;
    public long SHUTTER = -1;
    public float FOCUS = -1;
    public int WB = 0;
    public boolean isSpotWb = false;
    public String spotTintStr = "";
    public RggbChannelVector spotGains = null;
    public ColorSpaceTransform spotTransform = null;
    private static final String TAG = "ParamController";
    private final CaptureController captureController;
    private ManualParamModel manualParamModel;

    public ParamController(@NonNull CaptureController captureController) {
        this.captureController = captureController;
    }

    public void setShutter(long shutterNs, int currentISO) {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        if (builder == null) {
            Log.w(TAG, "setShutter(): mPreviewRequestBuilder is null");
            return;
        }
        if (shutterNs == ManualParamModel.EXPOSURE_AUTO) {
            if (currentISO == ManualParamModel.ISO_AUTO)// check if ISO is Auto
            {
                captureController.resetPreviewAEMode();
            }
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Math.min(shutterNs, ExposureIndex.sec / 5));
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, captureController.mPreviewIso);
        }
        captureController.rebuildPreviewBuilder();
    }

    public void setISO(int isoVal, double currentExposure) {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        if (builder == null) {
            Log.w(TAG, "setISO(): mPreviewRequestBuilder is null");
            return;
        }
        if (isoVal == ManualParamModel.ISO_AUTO) {
            if (currentExposure == ManualParamModel.EXPOSURE_AUTO) // check if Exposure is Auto
            {
                captureController.resetPreviewAEMode();
            }
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, isoVal);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, captureController.mPreviewExposureTime);
        }
        captureController.rebuildPreviewBuilder();
    }

    public void setFocus(float focusDist) {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        if (builder == null) {
            Log.w(TAG, "setFocus(): mPreviewRequestBuilder is null");
            return;
        }
        if (focusDist == ManualParamModel.FOCUS_AUTO) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, PreferenceKeys.getAfMode());
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDist);
        }
        captureController.rebuildPreviewBuilder();
    }

    public void setEV(int ev) {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        if (builder == null) {
            Log.w(TAG, "setEV(): mPreviewRequestBuilder is null");
            return;
        }
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev);
        captureController.rebuildPreviewBuilder();
    }

    public void setWB(int wbVal) {
        if (wbVal == (int) ManualParamModel.WB_AUTO || wbVal == 0) {
            this.isSpotWb = false;
            this.spotTintStr = "";
            this.spotGains = null;
            this.spotTransform = null;
            this.WB = 0;

            CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
            if (builder != null) {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST);
                Log.d("WB_REQUEST_DEBUG", "setWB: Switched to AUTO AWB");
                captureController.rebuildPreviewBuilder();
            }
            return;
        }

        // If Spot WB is active and the slider position matches the measured spot's knob position, ignore circular bar echo
        int expectedKnobEcho = (int) (Math.round(this.WB / 50.0) * 50);
        if (this.isSpotWb && this.spotGains != null && wbVal == expectedKnobEcho) {
            return;
        }

        // User manually dragged the slider to a different Kelvin value -> exit Spot WB mode
        this.isSpotWb = false;
        this.spotTintStr = "";
        this.spotGains = null;
        this.spotTransform = null;
        this.WB = wbVal;

        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        if (builder == null) {
            Log.w(TAG, "setWB(): mPreviewRequestBuilder is null");
            return;
        }

        RggbChannelVector gains = ColorTemperatureConverter.kelvinToRggb(wbVal,
                CaptureController.mCameraCharacteristics);
        ColorSpaceTransform transform = ColorTemperatureConverter.createColorTransform(wbVal,
                CaptureController.mCameraCharacteristics);

        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains);
        if (transform != null) {
            builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, transform);
        }

        Log.d("WB_REQUEST_DEBUG", "setWB: Manual Kelvin=" + wbVal
                + " | Gains: [R=" + gains.getRed()
                + ", Geven=" + gains.getGreenEven()
                + ", Godd=" + gains.getGreenOdd()
                + ", B=" + gains.getBlue() + "]"
                + " | Transform: " + transform);

        captureController.rebuildPreviewBuilder();
    }

    /**
     * Applies 2D Spot WB parameters (measured Kelvin, Tint, and custom
     * gains/transform).
     */
    public void setSpotWB(int wbVal, String tintStr, RggbChannelVector measuredGains,
            ColorSpaceTransform measuredTransform) {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        if (builder == null)
            return;

        this.WB = wbVal;
        this.isSpotWb = true;
        this.spotTintStr = tintStr;
        this.spotGains = measuredGains;
        this.spotTransform = measuredTransform;

        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, measuredGains);
        if (measuredTransform != null) {
            builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, measuredTransform);
        }

        Log.d(TAG, "setSpotWB applied: " + wbVal + "K | Tint=" + tintStr + " | Gains=" + measuredGains);
        captureController.rebuildPreviewBuilder();
    }

    public boolean isManualMode() {
        if (manualParamModel != null)
            return manualParamModel.isManualMode();
        return false;
    }

    @Override
    public void update(Observable observable, Object object) {
        if (observable != null && object != null) {
            ManualParamModel model = (ManualParamModel) observable;
            manualParamModel = model;
            if (object.equals(ManualParamModel.ID_ISO)) {
                ISO = (int) model.getCurrentISOValue();
                setISO((int) model.getCurrentISOValue(), model.getCurrentExposureValue());
            }
            if (object.equals(ManualParamModel.ID_EV)) {
                EV = (int) model.getCurrentEvValue();
                setEV((int) model.getCurrentEvValue());
            }
            if (object.equals(ManualParamModel.ID_SHUTTER)) {
                SHUTTER = (long) model.getCurrentExposureValue();
                setShutter((long) model.getCurrentExposureValue(), (int) model.getCurrentISOValue());
            }
            if (object.equals(ManualParamModel.ID_FOCUS)) {
                FOCUS = (float) model.getCurrentFocusValue();
                setFocus((float) model.getCurrentFocusValue());
            }
            if (object.equals(ManualParamModel.ID_WB)) {
                setWB((int) model.getCurrentWbValue());
            }
            if (object.equals(ManualParamModel.PANEL_INVISIBILITY)) {
                Log.d(TAG, "update: " + model.isManualMode());
                ISO = -1;
                EV = 0;
                SHUTTER = -1;
                FOCUS = -1;
                if (model.getCurrentWbValue() == ManualParamModel.WB_AUTO) {
                    WB = 0;
                    setWB(0);
                }
                captureController.unlockFocus();
            }
        }
    }

    public void setupPreview() {
        if (manualParamModel != null) {
            if (ISO != -1)
                setISO(ISO, manualParamModel.getCurrentExposureValue());
            if (EV != 0)
                setEV(EV);
            if (SHUTTER != -1)
                setShutter(SHUTTER, ISO);
            if (FOCUS != -1)
                setFocus(FOCUS);
            if(WB != 0) {
                if (isSpotWb && spotGains != null) {
                    setSpotWB(WB, spotTintStr, spotGains, spotTransform);
                } else {
                    setWB(WB);
                }
            }
        }
    }

    public double getCurrentExposureValue() {
        if (manualParamModel != null)
            return manualParamModel.getCurrentExposureValue();
        return ManualParamModel.EXPOSURE_AUTO;
    }

    public double getCurrentISOValue() {
        if (manualParamModel != null)
            return manualParamModel.getCurrentISOValue();
        return ManualParamModel.ISO_AUTO;
    }

    /**
     * Resets sensor-specific spot gains when switching cameras, preserving target Kelvin (MWB).
     */
    public void onCameraChanged() {
        this.isSpotWb = false;
        this.spotTintStr = "";
        this.spotGains = null;
        this.spotTransform = null;
    }
}
