package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.ui.camera.CustomOrientationEventListener;
import com.particlesdevs.photoncamera.ui.camera.model.CameraFragmentModel;

/**
 * Class get used to update the Models binded to the ui
 * it should not contain any ref to ui
 */
public class CameraFragmentViewModel extends AndroidViewModel {

    private static final String TAG = CameraFragmentViewModel.class.getSimpleName();
    //Model binded to the ui
    private final CameraFragmentModel cameraFragmentModel;
    //listen to device orientation changes
    private CustomOrientationEventListener mCustomOrientationEventListener;


    public CameraFragmentViewModel(@NonNull Application application) {
        super(application);
        cameraFragmentModel = new CameraFragmentModel();
        initOrientationEventListener();
    }

    public CameraFragmentModel getCameraFragmentModel() {
        return cameraFragmentModel;
    }

    public void onResume() {
        mCustomOrientationEventListener.enable();
        cameraFragmentModel.setSettingsBarVisibility(false);
    }

    public void onPause() {
        mCustomOrientationEventListener.disable();
    }

    private void initOrientationEventListener() {
        final int RotationDur = 350;
        final int Rotation90 = 2;
        final int Rotation180 = 3;
        final int Rotation270 = 4;
        mCustomOrientationEventListener = new CustomOrientationEventListener(getApplication()) {
            @Override
            public void onSimpleOrientationChanged(int orientation) {
                int rot = 0;
                switch (orientation) {
                    case Rotation90:
                        rot = -90;
                        //rotate as left on top
                        break;
                    case Rotation270:
                        //rotate as right on top
                        rot = 90;
                        break;
                    case Rotation180:
                        //rotate as upside down
                        rot = 180;
                        break;
                }
                Log.d(TAG, "onSimpleOrientationChanged" + rot);
                cameraFragmentModel.setDuration(RotationDur);
                cameraFragmentModel.setOrientation(rot);

                //mCameraUIView.rotateViews(rot, RotationDur);
                //PhotonCamera.getManualMode().rotate(rot, RotationDur);
            }
        };
    }

    public void updateGalleryThumb() {
        ImageFile lastImage = GalleryFileOperations.fetchLatestImage(getApplication().getContentResolver());
        if (lastImage != null) {
            Glide.with(getApplication())
                    .asBitmap()
                    .load(lastImage.getFileUri())
                    .override(200)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            cameraFragmentModel.setBitmap(resource);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {

                        }
                    });
        }
    }

    public void setScreenAspectRatio(float aspectRatio){
        cameraFragmentModel.setScreenAspectRatio(aspectRatio);
    }

    public boolean isSettingsBarVisible() {
        return cameraFragmentModel.isSettingsBarVisibility();
    }

    public void setSettingsBarVisible(boolean visible) {
        cameraFragmentModel.setSettingsBarVisibility(visible);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
