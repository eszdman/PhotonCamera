package com.eszdman.photoncamera.ui;

import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.util.Log;
import android.view.View;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraFragment;
import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.gallery.GalleryActivity;
import com.eszdman.photoncamera.settings.PreferenceKeys;

public class CameraUIController implements CameraUIView.CameraUIEventsListener {
    private static final String TAG = "CameraUIController";
    private final CameraFragment mCameraFragment;

    public CameraUIController(CameraFragment cameraFragment) {
        this.mCameraFragment = cameraFragment;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture:
                if (PhotonCamera.getSettings().selectedMode != Settings.CameraMode.UNLIMITED) {
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
                            onUnlimitedButtonPressed();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        mCameraFragment.createCameraPreviewSession();
                    }
                }
                break;
            case R.id.settings:
                Intent settingsIntent = new Intent(mCameraFragment.getActivity(), SettingsActivity.class);
                mCameraFragment.startActivity(settingsIntent);
                break;

            case R.id.stacking:
                PreferenceKeys.setHdrX(!PreferenceKeys.isHdrXOn());
                if (PreferenceKeys.isHdrXOn())
                    CameraFragment.mTargetFormat = CameraFragment.rawFormat;
                else
                    CameraFragment.mTargetFormat = CameraFragment.yuvFormat;
                restartCamera();
                break;
            case R.id.ImageOut:
                Intent galleryIntent = new Intent(mCameraFragment.getActivity(), GalleryActivity.class);
                mCameraFragment.startActivity(galleryIntent);
                break;
            case R.id.eisPhoto:
                PreferenceKeys.setEisPhoto(!PreferenceKeys.isEisPhotoOn());
                break;
            case R.id.fpsPreview:
                PreferenceKeys.setFpsPreview(!PreferenceKeys.isFpsPreviewOn());
                break;
            case R.id.quadRes:
                PreferenceKeys.setQuadBayer(!PreferenceKeys.isQuadBayerOn());
                restartCamera();
                break;
            case R.id.flip_camera:
                view.animate().rotationBy(180).setDuration(450).start();
                mCameraFragment.mTextureView.animate().rotationBy(360).setDuration(450).start();
                PreferenceKeys.setCameraID(PhotonCamera.getCameraFragment().cycler(PreferenceKeys.getCameraID()));
                restartCamera();
                break;

        }
    }

    private void restartCamera() {
        this.mCameraFragment.restartCamera();
    }

    @Override
    public void onAuxButtonClick(String id) {
        Log.d(TAG, "onAuxButtonClick() called with: id = [" + id + "]");
        PreferenceKeys.setCameraID(String.valueOf(id));  //i = RadioButton's resource ID
        restartCamera();

    }

    @Override
    public void onUnlimitedButtonPressed() {
        mCameraFragment.unlimitedEnd();
    }

    @Override
    public void onCameraModeChanged(Settings.CameraMode cameraMode) {
        Log.d(TAG, "onCameraModeChanged() called with: cameraMode = [" + cameraMode + "]");
        switch (cameraMode) {
            case PHOTO:
            default:
                PhotonCamera.getSettings().selectedMode = Settings.CameraMode.PHOTO;
                break;
            case NIGHT:
                PhotonCamera.getSettings().selectedMode = Settings.CameraMode.NIGHT;
                break;
            case UNLIMITED:
                PhotonCamera.getSettings().selectedMode = Settings.CameraMode.UNLIMITED;
                break;
        }
        restartCamera();
    }
}
