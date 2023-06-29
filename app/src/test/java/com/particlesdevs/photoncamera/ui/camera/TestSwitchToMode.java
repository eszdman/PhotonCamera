package com.particlesdevs.photoncamera.ui.camera;

import com.particlesdevs.photoncamera.api.CameraMode;

/**
 * This class was created for testing. for {@link CameraUIViewImpl in switchToMode(CameraMode)}
 * Test class Link {@link TestSwitchToModeTest}
 */
class TestSwitchToMode {

    CameraModeState currentState;

    TestSwitchToMode(){
        System.out.println("\nNow setting mode");
        currentState=new PhotoMotionModeState();
    }

    void switchToMode(CameraMode cameraMode) {
        switch (cameraMode) {
            case VIDEO:
                currentState = new VideoModeState();
                break;
            case UNLIMITED:
                currentState = new UnlimitedModeState();
                break;
            case PHOTO:
            case MOTION:
                currentState = new PhotoMotionModeState();
                break;
            case NIGHT:
                currentState = new NightModeState();
                break;
        }

        currentState.reConfigureModeViews(cameraMode);
    }
    void toggleConstraints(CameraMode mode) {
        switch (mode) {
            case VIDEO:
                System.out.println("Video mode switched");
                break;
            case UNLIMITED:
                System.out.println("Unlimited mode switched");
                break;
            case PHOTO:
                System.out.println("Photo mode switched");
                break;
            case MOTION:
                System.out.println("Motion mode switched");
                break;
            case NIGHT:
                System.out.println("Night mode switched");
                break;
        }

    }

    public class VideoModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            toggleConstraints(mode);
        }
    }

    //
    public class UnlimitedModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            toggleConstraints(mode);
        }
    }

    //
    public class PhotoMotionModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            toggleConstraints(mode);
        }
    }

    public class NightModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            toggleConstraints(mode);
        }
    }
}
