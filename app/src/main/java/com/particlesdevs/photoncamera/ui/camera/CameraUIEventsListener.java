package com.particlesdevs.photoncamera.ui.camera;

import android.view.View;

import com.particlesdevs.photoncamera.api.CameraMode;

interface CameraUIEventsListener {
    void onClick(View v);

    void onCameraModeChanged(CameraMode cameraMode);

    void onPause();
}