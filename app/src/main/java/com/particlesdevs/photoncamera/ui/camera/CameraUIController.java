package com.particlesdevs.photoncamera.ui.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;

import androidx.lifecycle.Observer;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.CountdownTimer;
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
    private CountDownTimer countdownTimer;
    private View shutterButton;

    public CameraUIController(CameraFragment cameraFragment) {
        this.cameraFragment = cameraFragment;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.shutter_button:
                shutterButton = view;
                switch (PhotonCamera.getSettings().selectedMode) {
                    case PHOTO:
                    case MOTION:
                    case NIGHT:
                        if (view.isHovered()) resetTimer();
                        else startTimer();
                        break;
                    case UNLIMITED:
                        if (!cameraFragment.captureController.onUnlimited) {
                            cameraFragment.captureController.callUnlimitedStart();
                            view.setActivated(false);
                        } else {
                            cameraFragment.captureController.callUnlimitedEnd();
                            view.setActivated(true);
                        }
                        break;
                    case VIDEO:
                        if (!cameraFragment.captureController.mIsRecordingVideo) {
                            cameraFragment.captureController.VideoStart();
                            view.setActivated(false);
                        } else {
                            cameraFragment.captureController.VideoEnd();
                            view.setActivated(true);
                        }
                        break;
                }
                break;
            case R.id.settings_button:
                cameraFragment.launchSettings();
                break;

            case R.id.hdrx_toggle_button:
                PreferenceKeys.setHdrX(!PreferenceKeys.isHdrXOn());
                if (PreferenceKeys.isHdrXOn())
                    CaptureController.setTargetFormat(CaptureController.RAW_FORMAT);
                else
                    CaptureController.setTargetFormat(CaptureController.YUV_FORMAT);
                cameraFragment.showSnackBar(cameraFragment.getString(R.string.hdrx) + ':' + onOff(PreferenceKeys.isHdrXOn()));
                this.restartCamera();
                break;

            case R.id.gallery_image_button:
                cameraFragment.launchGallery();
                break;

            case R.id.eis_toggle_button:
                PreferenceKeys.setEisPhoto(!PreferenceKeys.isEisPhotoOn());
                cameraFragment.showSnackBar(cameraFragment.getString(R.string.eis_toggle_text) + ':' + onOff(PreferenceKeys.isEisPhotoOn()));
                cameraFragment.updateSettingsBar();
                break;

            case R.id.fps_toggle_button:
                PreferenceKeys.setFpsPreview(!PreferenceKeys.isFpsPreviewOn());
                cameraFragment.showSnackBar(cameraFragment.getString(R.string.fps_60_toggle_text) + ':' + onOff(PreferenceKeys.isFpsPreviewOn()));
                cameraFragment.updateSettingsBar();
                break;

            case R.id.quad_res_toggle_button:
                PreferenceKeys.setQuadBayer(!PreferenceKeys.isQuadBayerOn());
                cameraFragment.showSnackBar(cameraFragment.getString(R.string.quad_bayer_toggle_text) + ':' + onOff(PreferenceKeys.isQuadBayerOn()));
                this.restartCamera();
                cameraFragment.updateSettingsBar();
                break;

            case R.id.flip_camera_button:
                view.animate().rotationBy(180).setDuration(450).start();
                cameraFragment.textureView.animate().rotationBy(360).setDuration(450).start();
                //PreferenceKeys.setCameraID(cycler(PreferenceKeys.getCameraID()));
                setID(cameraFragment.cycler(PreferenceKeys.getCameraID()));
                this.restartCamera();
                break;
            case R.id.grid_toggle_button:
                PreferenceKeys.setGridValue((PreferenceKeys.getGridValue() + 1) % view.getResources().getStringArray(R.array.vf_grid_entryvalues).length);
                view.setSelected(PreferenceKeys.getGridValue() != 0);
                cameraFragment.invalidateSurfaceView();
                cameraFragment.updateSettingsBar();
                break;

            case R.id.flash_button:
                PreferenceKeys.setAeMode((PreferenceKeys.getAeMode() + 1) % 4); //cycles in 0,1,2,3
                ((FlashButton) view).setFlashValueState(PreferenceKeys.getAeMode());
                cameraFragment.captureController.setPreviewAEModeRebuild(PreferenceKeys.getAeMode());
                cameraFragment.updateSettingsBar();
                break;

            case R.id.countdown_timer_button:
                PreferenceKeys.setCountdownTimerIndex((PreferenceKeys.getCountdownTimerIndex() + 1) % view.getResources().getIntArray(R.array.countdowntimer_entryvalues).length);
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
            this.countdownTimer = new CountdownTimer(
                    cameraFragment.findViewById(R.id.frameTimer),
                    getTimerValue(this.shutterButton.getContext()) * 1000L, 1000,
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
            default:
                break;
            case VIDEO:
                PreferenceKeys.setCameraModeOrdinal(CameraMode.VIDEO.ordinal());
                break;
        }
        this.restartCamera();
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
                        PreferenceKeys.setFpsPreview(value.equals(1));
                        break;
                    case TIMER:
                        PreferenceKeys.setCountdownTimerIndex((Integer) value);
                        cameraFragment.cameraFragmentBinding.layoutTopbar.countdownTimerButton.setTimerIconState((Integer) value);
                        break;
                    case EIS:
                        PreferenceKeys.setEisPhoto(value.equals(1));
                        break;
                    case RAW:
                        PreferenceKeys.setSaveRaw(value.equals(1));
                        break;
                    case BATTERY_SAVER:
                        PreferenceKeys.setBatterySaver(value.equals(1));
                        break;

                }
                cameraFragment.cameraFragmentBinding.layoutTopbar.invalidateAll();
            }
        }

    }
}
