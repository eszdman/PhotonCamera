package com.eszdman.photoncamera.ui.camera;

import android.hardware.camera2.CameraAccessException;
import android.util.Log;
import android.view.View;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.app.PhotonCamera;
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
        this.mCameraFragment.restartCamera();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.shutter_button:
                if (PhotonCamera.getSettings().selectedMode != CameraMode.UNLIMITED) {
                    view.setActivated(false);
                    view.setClickable(false);
                    mCameraFragment.takePicture();
                } else {
                    if (!mCameraFragment.onUnlimited) {
                        mCameraFragment.onUnlimited = true;
                        view.setActivated(false);
                        view.setClickable(true);
                        mCameraFragment.takePicture();
                    } else {
                        view.setActivated(true);
                        view.setClickable(true);
                        mCameraFragment.onUnlimited = false;
                        try {
                            mCameraFragment.mCaptureSession.abortCaptures();
                            mCameraFragment.unlimitedEnd();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        mCameraFragment.createCameraPreviewSession();
                    }
                }
                break;

            case R.id.settings_button:
                mCameraFragment.launchSettings();
                break;

            case R.id.hdrx_toggle_button:
                PreferenceKeys.setHdrX(!PreferenceKeys.isHdrXOn());
                if (PreferenceKeys.isHdrXOn())
                    CameraFragment.mTargetFormat = CameraFragment.rawFormat;
                else
                    CameraFragment.mTargetFormat = CameraFragment.yuvFormat;
                restartCamera();
                break;

            case R.id.gallery_image_button:
                mCameraFragment.launchGallery();
                break;

            case R.id.eis_toggle_button:
                PreferenceKeys.setEisPhoto(!PreferenceKeys.isEisPhotoOn());
                break;

            case R.id.fps_toggle_button:
                PreferenceKeys.setFpsPreview(!PreferenceKeys.isFpsPreviewOn());
                break;

            case R.id.quad_res_toggle_button:
                PreferenceKeys.setQuadBayer(!PreferenceKeys.isQuadBayerOn());
                restartCamera();
                break;

            case R.id.flip_camera_button:
                view.animate().rotationBy(180).setDuration(450).start();
                mCameraFragment.mTextureView.animate().rotationBy(360).setDuration(450).start();
                PreferenceKeys.setCameraID(mCameraFragment.cycler(PreferenceKeys.getCameraID()));
                restartCamera();
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
        }
        restartCamera();
    }
}
