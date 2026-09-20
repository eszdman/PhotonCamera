package com.particlesdevs.photoncamera.ui.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.CountDownTimer;

import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.util.Log;
import android.view.View;

import androidx.lifecycle.Observer;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.control.CountdownTimer;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingType;
import com.particlesdevs.photoncamera.ui.camera.model.TopBarSettingsData;
import com.particlesdevs.photoncamera.ui.camera.views.AuxButtonsLayout;
import com.particlesdevs.photoncamera.ui.camera.views.FlashButton;
import com.particlesdevs.photoncamera.ui.camera.views.TimerButton;

/**
 * Implementation of {@link CameraUIEventsListener}
 * <p>
 * Responsible for converting user inputs into actions
 */
final class CameraUIController implements CameraUIEventsListener,
        Observer<TopBarSettingsData<?, ?>>, AuxButtonsLayout.AuxButtonListener {
    private static final String TAG = "CameraUIController";
    private final CameraFragment cameraFragment;
    private final Vibration haptics;
    private CountDownTimer countdownTimer;
    private View shutterButton;

    public CameraUIController(CameraFragment cameraFragment) {
        this.cameraFragment = cameraFragment;
        this.haptics = PhotonCamera.getVibration();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.shutter_button:
                shutterButton = view;
                if (haptics != null) haptics.shutterPress();
                switch (PhotonCamera.getSettings().selectedMode) {
                    case PHOTO:
                    case MOTION:
                    case NIGHT:
                        if (view.isHovered()) resetTimer();
                        else startTimer();
                        break;
                    case UNLIMITED:
                    case RAWVIDEO:
                        if (!cameraFragment.captureController.onUnlimited) {
                            cameraFragment.captureController.callUnlimitedStart();
                            view.setActivated(false);
                            setShutterRecording(true);
                        } else {
                            cameraFragment.captureController.callUnlimitedEnd();
                            view.setActivated(true);
                            setShutterRecording(false);
                        }
                        break;
                    case VIDEO:
                        if (!cameraFragment.captureController.mIsRecordingVideo) {
                            cameraFragment.captureController.VideoStart();
                            view.setActivated(false);
                            setShutterRecording(true);
                        } else {
                            cameraFragment.captureController.VideoEnd();
                            view.setActivated(true);
                            setShutterRecording(false);
                        }
                        break;
                }
                break;
            case R.id.settings_button:
                if (haptics != null) haptics.confirm();
                cameraFragment.launchSettings();
                break;

            case R.id.hdrx_toggle_button:
                PreferenceKeys.setHdrX(!PreferenceKeys.isHdrXOn());
                if (haptics != null) haptics.toggle(PreferenceKeys.isHdrXOn());
                if (PreferenceKeys.isHdrXOn())
                    CaptureController.setTargetFormat(CaptureController.RAW_FORMAT);
                else
                    CaptureController.setTargetFormat(CaptureController.YUV_FORMAT);
                cameraFragment.showSnackBar(cameraFragment.getString(R.string.hdrx) + ':' + onOff(PreferenceKeys.isHdrXOn()));
                this.restartCamera();
                break;

            case R.id.gallery_image_button:
                if (haptics != null) haptics.confirm();
                cameraFragment.launchGallery();
                break;

            case R.id.eis_toggle_button:
                PreferenceKeys.setEisPhoto(!PreferenceKeys.isEisPhotoOn());
                if (haptics != null) haptics.toggle(PreferenceKeys.isEisPhotoOn());
                cameraFragment.showSnackBar(cameraFragment.getString(R.string.eis_toggle_text) + ':' + onOff(PreferenceKeys.isEisPhotoOn()));
                if (PhotonCamera.getSettings().selectedMode == CameraMode.VIDEO) {
                    cameraFragment.captureController.applyVideoStabilization();
                }
                cameraFragment.updateSettingsBar();
                break;

            case R.id.fps_toggle_button:
                // The top-bar button only exists in video mode and edits the
                // video/RAW-video rate.
                PreferenceKeys.setVideoFpsMode((PreferenceKeys.getVideoFpsMode() + 1) % 4);
                if (haptics != null) haptics.modeChange();
                cameraFragment.captureController.applyFpsRange();
                cameraFragment.cameraFragmentBinding.layoutTopbar.fpsToggleButton
                        .setFpsModeState(PreferenceKeys.getVideoFpsMode());
                cameraFragment.updateSettingsBar();
                break;

            case R.id.quad_res_toggle_button:
                PreferenceKeys.setQuadBayer(!PreferenceKeys.isQuadBayerOn());
                if (haptics != null) haptics.toggle(PreferenceKeys.isQuadBayerOn());
                cameraFragment.showSnackBar(cameraFragment.getString(R.string.quad_bayer_toggle_text) + ':' + onOff(PreferenceKeys.isQuadBayerOn()));
                this.restartCamera();
                cameraFragment.updateSettingsBar();
                break;

            case R.id.flip_camera_button:
                if (haptics != null) haptics.modeChange();
                view.animate().rotationBy(180).setDuration(Motion.durationLong1(view.getContext()))
                        .setInterpolator(Motion.emphasized(view.getContext())).start();
                //cameraFragment.textureView.animate().rotationBy(360).setDuration(450).start();
                //PreferenceKeys.setCameraID(cycler(PreferenceKeys.getCameraID()));
                setID(cameraFragment.cycler(PreferenceKeys.getCameraID()));
                this.restartCamera();
                break;
            case R.id.grid_toggle_button:
                PreferenceKeys.setGridValue((PreferenceKeys.getGridValue() + 1) % view.getResources().getStringArray(R.array.vf_grid_entryvalues).length);
                if (haptics != null) haptics.toggle(PreferenceKeys.getGridValue() != 0);
                view.setSelected(PreferenceKeys.getGridValue() != 0);
                cameraFragment.invalidateSurfaceView();
                cameraFragment.updateSettingsBar();
                break;

            case R.id.flash_button:
                PreferenceKeys.setAeMode((PreferenceKeys.getAeMode() + 1) % 2); //cycles in 0 (torch), 1 (off)
                if (haptics != null) haptics.toggle(PreferenceKeys.getAeMode() != 0);
                ((FlashButton) view).setFlashValueState(PreferenceKeys.getAeMode());
                cameraFragment.captureController.setPreviewAEModeRebuild(PreferenceKeys.getAeMode());
                cameraFragment.updateSettingsBar();
                break;

            case R.id.countdown_timer_button:
                PreferenceKeys.setCountdownTimerIndex((PreferenceKeys.getCountdownTimerIndex() + 1) % view.getResources().getIntArray(R.array.countdowntimer_entryvalues).length);
                if (haptics != null) haptics.modeChange();
                ((TimerButton) view).setTimerIconState(PreferenceKeys.getCountdownTimerIndex());
                cameraFragment.updateSettingsBar();
                break;
        }
    }

    private int getTimerValue(Context context) {
        int[] timerValues = context.getResources().getIntArray(R.array.countdowntimer_entryvalues);
        return timerValues[PreferenceKeys.getCountdownTimerIndex()];
    }

    private void startTimer() {
        if (this.shutterButton != null) {
            this.shutterButton.setHovered(true);
            int timerValue = getTimerValue(this.shutterButton.getContext());
            this.countdownTimer = new CountdownTimer(
                    cameraFragment.findViewById(R.id.frameTimer),
                    timerValue * 1000L, 1000, timerValue > 0,
                    this::onTimerFinished).start();
        }
    }

    private void resetTimer() {
        if (this.countdownTimer != null) this.countdownTimer.cancel();
        if (this.shutterButton != null) this.shutterButton.setHovered(false);
    }

    @Override
    public void onAuxButtonClicked(String id) {
        Log.d(TAG, "onAuxButtonClicked() called with: id = [" + id + "]");
        if (haptics != null) haptics.confirm();
        if (id != null
                && com.particlesdevs.photoncamera.api.LogicalCameraResolver.isMemberId(id)
                && cameraFragment.captureController != null
                && cameraFragment.captureController.isVideoLogicalActive()) {
            // Logical member tap: seamless zoom on the open device, no reopen.
            cameraFragment.captureController.zoomToLogicalMember(id);
            return;
        }
        setID(id);
        this.restartCamera();

    }

    private void setID(String input) {
        PreferenceKeys.setCameraID(String.valueOf(input));
    }

    @Override
    public void onCameraModeChanged(CameraMode cameraMode) {
        PreferenceKeys.setCameraModeOrdinal(cameraMode.ordinal());
        Log.d(TAG, "onCameraModeChanged() called with: cameraMode = [" + cameraMode + "]");
        switch (cameraMode) {
            case PHOTO:
            case MOTION:
            case NIGHT:
            case UNLIMITED:
            case RAWVIDEO:
            default:
                break;
            case VIDEO:
                PreferenceKeys.setCameraModeOrdinal(CameraMode.VIDEO.ordinal());
                break;
        }
        this.restartCamera();
        // The frame-rate entry belongs to the mode group (video/RAW video vs
        // photo/motion), so refresh its selected state and visibility right
        // after the switch.
        cameraFragment.updateSettingsBar();
    }

    @Override
    public void onPause() {
        this.resetTimer();
    }

    private void restartCamera() {
        this.resetTimer();
        cameraFragment.captureController.restartCamera();
    }

    private String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private void setShutterRecording(boolean recording) {
        if (cameraFragment.getCameraUIView() != null) {
            cameraFragment.getCameraUIView().setShutterRecording(recording);
        }
    }

    private void onTimerFinished() {
        this.shutterButton.setHovered(false);
        this.shutterButton.setActivated(false);
        this.shutterButton.setClickable(false);
        cameraFragment.captureController.takePicture();
    }

    @Override
    public void onChanged(TopBarSettingsData<?, ?> topBarSettingsData) {
        if (topBarSettingsData != null && topBarSettingsData.getType() != null && topBarSettingsData.getValue() != null) {
            if (topBarSettingsData.getType() instanceof SettingType) {
                SettingType type = (SettingType) topBarSettingsData.getType();
                Object value = topBarSettingsData.getValue();
                switch (type) {
                    case FLASH:
                        PreferenceKeys.setAeMode((Integer) value); //cycles in 0,1,2,3
                        cameraFragment.captureController.setPreviewAEModeRebuild(PreferenceKeys.getAeMode());
                        cameraFragment.cameraFragmentBinding.layoutTopbar.flashButton.setFlashValueState((Integer) value);
                        break;
                    case HDRX:
                        PreferenceKeys.setHdrX(value.equals(1));
                        if (value.equals(1))
                            CaptureController.setTargetFormat(CaptureController.RAW_FORMAT);
                        else
                            CaptureController.setTargetFormat(CaptureController.YUV_FORMAT);
                        this.restartCamera();
                        break;
                    case QUAD:
                        PreferenceKeys.setQuadBayer(value.equals(1));
                        this.restartCamera();
                        break;
                    case GRID:
                        PreferenceKeys.setGridValue((Integer) value);
                        cameraFragment.invalidateSurfaceView();
                        break;
                    case FPS_60:
                        // The entry is shared by the pulldowns of every mode,
                        // so store into the setting of the mode's group (video
                        // + RAW video vs photo + motion).
                        CameraMode fpsMode = PhotonCamera.getSettings().selectedMode;
                        PreferenceKeys.setFpsModeForMode(fpsMode, (Integer) value);
                        cameraFragment.captureController.applyFpsRange();
                        cameraFragment.cameraFragmentBinding.layoutTopbar.fpsToggleButton
                                .setFpsModeState(PreferenceKeys.getFpsModeForMode(fpsMode));
                        break;
                    case TIMER:
                        PreferenceKeys.setCountdownTimerIndex((Integer) value);
                        cameraFragment.cameraFragmentBinding.layoutTopbar.countdownTimerButton.setTimerIconState((Integer) value);
                        break;
                    case EIS:
                        PreferenceKeys.setEisPhoto(value.equals(1));
                        if (PhotonCamera.getSettings().selectedMode == CameraMode.VIDEO) {
                            cameraFragment.captureController.applyVideoStabilization();
                        }
                        break;
                    case RAW:
                        PreferenceKeys.setSaveRaw((Integer) value);
                        break;
                    case BATTERY_SAVER:
                        PreferenceKeys.setBatterySaver(value.equals(1));
                        break;
                    case BRACKETING:
                        PreferenceKeys.setBracketingMode((Integer) value);
                        // Update HDR class to use the new bracketing mode
                        IsoExpoSelector.HDR = (Integer) value > 0;
                        break;
                    case AE_METERING_STD:
                        PreferenceKeys.setAeMeteringStd((Integer) value);
                        cameraFragment.captureController.applyAeMetering();
                        break;

                }
                cameraFragment.cameraFragmentBinding.layoutTopbar.invalidateAll();
            }
        }

    }
}
