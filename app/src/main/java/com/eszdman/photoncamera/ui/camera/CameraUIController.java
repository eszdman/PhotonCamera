package com.eszdman.photoncamera.ui.camera;

import android.util.Log;
import android.view.View;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.settings.PreferenceKeys;

/**
 * Implementation of {@link CameraUIView.CameraUIEventsListener}
 * <p>
 * Responsible for converting user inputs into actions
 */
public final class CameraUIController implements CameraUIView.CameraUIEventsListener {
    private static final String TAG = "CameraUIController";
    private final CameraFragment mCameraFragment;

    public CameraUIController(CameraFragment cameraFragment) {
        this.mCameraFragment = cameraFragment;
    }

    private void restartCamera() {
        this.mCameraFragment.getCaptureController().restartCamera();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.shutter_button:
                CaptureController captureController = mCameraFragment.getCaptureController();
                switch (PhotonCamera.getSettings().selectedMode) {
                    case PHOTO:
                    case NIGHT:
                        view.setActivated(false);
                        view.setClickable(false);
                        captureController.takePicture();
                        break;
                    case UNLIMITED:
                        if (!captureController.onUnlimited) {
                            captureController.callUnlimitedStart();
                            view.setActivated(false);
                        } else {
                            captureController.callUnlimitedEnd();
                            view.setActivated(true);
                        }
                        break;
                    case VIDEO:
                        if (!captureController.mIsRecordingVideo) {
                            captureController.VideoStart();
                            view.setActivated(false);
                        } else {
                            captureController.VideoEnd();
                            view.setActivated(true);
                        }
                        break;
                }
                break;
            case R.id.settings_button:
                mCameraFragment.launchSettings();
                break;

            case R.id.hdrx_toggle_button:
                PreferenceKeys.setHdrX(!PreferenceKeys.isHdrXOn());
                if (PreferenceKeys.isHdrXOn())
                    CaptureController.setTargetFormat(CaptureController.RAW_FORMAT);
                else
                    CaptureController.setTargetFormat(CaptureController.YUV_FORMAT);
                mCameraFragment.showSnackBar(mCameraFragment.getString(R.string.hdrx) + ':' + onOff(PreferenceKeys.isHdrXOn()));
                restartCamera();
                break;

            case R.id.gallery_image_button:
                mCameraFragment.launchGallery();
                break;

            case R.id.eis_toggle_button:
                PreferenceKeys.setEisPhoto(!PreferenceKeys.isEisPhotoOn());
                mCameraFragment.showSnackBar(mCameraFragment.getString(R.string.eis_toggle_text) + ':' + onOff(PreferenceKeys.isEisPhotoOn()));
                break;

            case R.id.fps_toggle_button:
                PreferenceKeys.setFpsPreview(!PreferenceKeys.isFpsPreviewOn());
                mCameraFragment.showSnackBar(mCameraFragment.getString(R.string.fps_60_toggle_text) + ':' + onOff(PreferenceKeys.isFpsPreviewOn()));
                break;

            case R.id.quad_res_toggle_button:
                PreferenceKeys.setQuadBayer(!PreferenceKeys.isQuadBayerOn());
                mCameraFragment.showSnackBar(mCameraFragment.getString(R.string.quad_bayer_toggle_text) + ':' + onOff(PreferenceKeys.isQuadBayerOn()));
                restartCamera();
                break;

            case R.id.flip_camera_button:
                view.animate().rotationBy(180).setDuration(450).start();
                mCameraFragment.findViewById(R.id.texture).animate().rotationBy(360).setDuration(450).start();
                PreferenceKeys.setCameraID(mCameraFragment.cycler(PreferenceKeys.getCameraID()));
                restartCamera();
                break;
            case R.id.grid_toggle_button:
                PreferenceKeys.setShowGridOn(!PreferenceKeys.isShowGridOn());
                mCameraFragment.invalidate();
                break;

        }
    }

    @Override
    public void onAuxButtonClicked(String id) {
        Log.d(TAG, "onAuxButtonClicked() called with: id = [" + id + "]");
        PreferenceKeys.setCameraID(String.valueOf(id));  //i = RadioButton's resource ID
        restartCamera();

    }

    @Override
    public void onCameraModeChanged(CameraMode cameraMode) {
        Log.d(TAG, "onCameraModeChanged() called with: cameraMode = [" + cameraMode + "]");
        switch (cameraMode) {
            case PHOTO:
            default:
                PhotonCamera.getSettings().selectedMode = CameraMode.PHOTO;
                break;
            case NIGHT:
                PhotonCamera.getSettings().selectedMode = CameraMode.NIGHT;
                break;
            case UNLIMITED:
                PhotonCamera.getSettings().selectedMode = CameraMode.UNLIMITED;
                break;
            /*case VIDEO:
                PhotonCamera.getSettings().selectedMode = CameraMode.VIDEO;
                break;*/
        }
        restartCamera();
    }

    private String onOff(boolean value) {
        return value ? "On" : "Off";
    }
}
