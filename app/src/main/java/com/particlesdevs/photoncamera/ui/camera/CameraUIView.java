package com.particlesdevs.photoncamera.ui.camera;

import android.view.View;

import com.particlesdevs.photoncamera.api.CameraMode;

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
     *
     * @param processing status of any ongoing process that might be relevant to view state
     */
    void refresh(boolean processing);

    /**
     * Processing ProgressBar methods
     * Control the Progress bar that displays image processing sequence
     */
    void setProcessingProgressBarIndeterminate(boolean indeterminate);

    /**
     * Capture ProgressBar methods
     * Control the Progress bar that displays image capture sequence
     */
    void resetCaptureProgressBar();

    void incrementCaptureProgressBar(int step);

    void setCaptureProgressBarOpacity(float alpha);

    void setCaptureProgressMax(int max);

    /**
     * Setter for CameraUIEventsListener
     *
     * @param cameraUIEventsListener instance of class which has implemented {@link CameraUIEventsListener}
     */
    void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener);

    void showFlashButton(boolean flashAvailable);

    void destroy();

    /**
     * Interface which listens to input events from User
     */
    interface CameraUIEventsListener {
        void onClick(View v);

        void onCameraModeChanged(CameraMode cameraMode);

        void onPause();
    }
}
