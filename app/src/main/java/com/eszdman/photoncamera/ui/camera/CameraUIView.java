package com.eszdman.photoncamera.ui.camera;

import android.graphics.Bitmap;
import android.hardware.camera2.CaptureResult;
import android.view.View;

import com.eszdman.photoncamera.api.CameraMode;

import java.util.Set;

/**
 * Interface defining the functionality required to be implemented by the Camera User Interface
 * <p>
 * Created by Vibhor Srivastava on 25/09/2020
 */
public interface CameraUIView {
    /**
     * Activate/Deactivate shutter button
     *
     * @param status true = activate, false = deactivate
     */
    void activateShutterButton(boolean status);

    /**
     * Refresh all contained views here
     */
    void refresh();

    /**
     * Initialise Multi camera switch buttons here
     *
     * @param backCameraIdsList  set of cameras on the back
     * @param frontCameraIdsList set of cameras on the front
     */
    void initAuxButtons(Set<String> backCameraIdsList, Set<String> frontCameraIdsList);

    /**
     * Set Multi camera Buttons in an event of configuration change
     *
     * @param idsList set of camera ids
     * @param active  currently active id
     */
    void setAuxButtons(Set<String> idsList, String active);

    /**
     * Set an image to gallery icon
     *
     * @param bitmap bitmap image
     */
    void setGalleryButtonImage(Bitmap bitmap);

    /**
     * Processing ProgressBar methods
     * Control the Progress bar that displays image processing sequence
     */
    void resetProcessingProgressBar();

    void setProcessingProgressBarIndeterminate(boolean indeterminate);

    /**
     * Capture ProgressBar methods
     * Control the Progress bar that displays image capture sequence
     */
    void resetCaptureProgressBar();

    void incrementCaptureProgressBar(int step);

    void setCaptureProgressBarOpacity(float alpha);

    void setCaptureProgressMax(int max);

    void setFrameTimeCnt(int cnt, int maxcnt);

    void clearFrameTimeCnt();

    /**
     * Setter for CameraUIEventsListener
     *
     * @param cameraUIEventsListener instance of class which has implemented {@link CameraUIEventsListener}
     */
    void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener);

    /**
     * Interface which listens to input events from User
     */
    interface CameraUIEventsListener {
        void onClick(View v);

        void onAuxButtonClicked(String id);

        void onCameraModeChanged(CameraMode cameraMode);
    }
}
