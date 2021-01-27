package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.particlesdevs.photoncamera.ui.camera.CustomOrientationEventListener;
import com.particlesdevs.photoncamera.ui.camera.model.CameraFragmentModel;
import com.particlesdevs.photoncamera.util.FileManager;

import java.io.File;
import java.util.List;

import rapid.decoder.BitmapDecoder;
import rapid.decoder.Quality;

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
    private HandlerThread thumbnailThread;

    public CameraFragmentViewModel(@NonNull Application application) {
        super(application);
        cameraFragmentModel = new CameraFragmentModel();
        initOrientationEventListener();
        thumbnailThread = new HandlerThread("ThumbnailUpdater", Process.THREAD_PRIORITY_BACKGROUND);
        thumbnailThread.start();
    }

    public CameraFragmentModel getCameraFragmentModel() {
        return cameraFragmentModel;
    }

    public void onResume() {
        mCustomOrientationEventListener.enable();
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
        List<File> allFiles = FileManager.getAllImageFiles();
        if (allFiles.isEmpty())
            return;
        File lastImage = allFiles.get(0);
        if (lastImage != null) {
            if (thumbnailThread != null && thumbnailThread.isAlive()) {
                new Handler(thumbnailThread.getLooper()).post(() -> {
                    Bitmap bitmap = BitmapDecoder.from(Uri.fromFile(lastImage))
                            .quality(Quality.LOWEST_OPAQUE)
                            .scaleBy(0.1f)
                            .decode();
                    cameraFragmentModel.setBitmap(bitmap);
                });
            }
        }
    }

    @Override
    protected void onCleared() {
        thumbnailThread.quitSafely();
        thumbnailThread = null;
        super.onCleared();
    }
}
