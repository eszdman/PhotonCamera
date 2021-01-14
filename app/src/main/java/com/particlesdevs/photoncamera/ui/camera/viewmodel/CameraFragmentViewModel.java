package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.particlesdevs.photoncamera.ui.camera.CustomOrientationEventListener;
import com.particlesdevs.photoncamera.ui.camera.model.CameraFragmentModel;
import com.particlesdevs.photoncamera.util.FileManager;
import rapid.decoder.BitmapDecoder;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public void updateGalleryThumb() {
        List<File> allFiles = FileManager.getAllImageFiles();
        if (allFiles.isEmpty())
            return;
        File lastImage = allFiles.get(0);
        Handler handler = new Handler(Looper.getMainLooper(), msg -> {
            cameraFragmentModel.setBitmap((Bitmap) msg.obj);
            return true;
        });
        if (lastImage != null) {
            executorService.execute(() -> {
                Bitmap bitmap = BitmapDecoder.from(Uri.fromFile(lastImage))
                        .scaleBy(0.1f)
                        .decode();
                Message m = new Message();
                m.obj = bitmap;
                handler.sendMessage(m);
            });
        }
    }
}
