package com.eszdman.photoncamera.ui.camera;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.ViewModel;

import com.eszdman.photoncamera.app.PhotonCamera;
import com.manual.Rotation;

public class CameraFragmentViewModel extends ViewModel {

    private static final String TAG = CameraFragmentViewModel.class.getSimpleName();
    private Context context;
    private CameraFragmentModel cameraFragmentModel;
    private CustomOrientationEventListener mCustomOrientationEventListener;

    public void create(Context context)
    {
        this.context = context;
        cameraFragmentModel = new CameraFragmentModel();
        initOrientationEventListener();
    }

    public CameraFragmentModel getCameraFragmentModel() {
        return cameraFragmentModel;
    }

    public void onResume()
    {
        mCustomOrientationEventListener.enable();
    }

    public void onPause()
    {
        mCustomOrientationEventListener.disable();
    }

    private void initOrientationEventListener() {
        final int RotationDur = 350;
        final int Rotation90 = 2;
        final int Rotation180 = 3;
        final int Rotation270 = 4;
        mCustomOrientationEventListener = new CustomOrientationEventListener(context) {
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
                Log.d(TAG,"onSimpleOrientationChanged" +rot);
                cameraFragmentModel.setDuration(RotationDur);
                cameraFragmentModel.setOrientation(rot);

                //mCameraUIView.rotateViews(rot, RotationDur);
                PhotonCamera.getManualMode().rotate(rot, RotationDur);
            }
        };
    }
}
