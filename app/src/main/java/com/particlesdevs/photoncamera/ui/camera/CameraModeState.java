package com.particlesdevs.photoncamera.ui.camera;

import com.particlesdevs.photoncamera.api.CameraMode;

/**
 * Execute the reConfigureModeViews method based on the CameraMode state
 */
public interface CameraModeState {
    void reConfigureModeViews(CameraMode mode);
}