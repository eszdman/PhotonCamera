package com.eszdman.photoncamera.ui;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.widget.*;


import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.CameraManager2;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.api.Settings;

import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.wefika.horizontalpicker.HorizontalPicker;
import de.hdodenhof.circleimageview.CircleImageView;

public class CameraUI {
    private static final String TAG = "CameraUI";
    public ImageButton shot;
    public ProgressBar lightcycle;
    public ProgressBar loadingcycle;
    public CircleImageView galleryImageButton;
    public RadioGroup auxGroup;
    HorizontalPicker modePicker;
    ToggleButton fpsPreview;
    ToggleButton quadResolution;
    ToggleButton eisPhoto;
    ImageButton flip;
    Button settings;
    ToggleButton hdrX;

    @SuppressLint("ResourceType")
    public void onCameraInitialization() {
        Camera2ApiAutoFix.Init();
        String[] cameras = CameraManager2.cameraManager2.getCameraIdList();
        if (auxGroup.getChildCount() == 0 && cameras.length > 2) {
            for (int i = 1; i < cameras.length; i++) {
                RadioButton rb = new RadioButton(PhotonCamera.getMainActivity());
                rb.setText("");
                auxGroup.addView(rb);
            }
            PhotonCamera.getSettings().mCameraID = "0";
            auxGroup.check(1);
            auxGroup.setOnCheckedChangeListener((radioGroup, i) -> {
                if (i >= 2 && CameraManager2.cameraManager2.supportFrontCamera) i++;
                PhotonCamera.getSettings().mCameraID = PhotonCamera.getCameraFragment().mCameraIds[i - 1];
                PhotonCamera.getCameraFragment().restartCamera();
            });
        }
        PhotonCamera.getManualMode().init();
    }

    public void onCameraViewCreated() {
        PhotonCamera.getSettings().mCameraID = "0";
        lightcycle = PhotonCamera.getMainActivity().findViewById(R.id.lightCycle);
        lightcycle.setAlpha(0);
        lightcycle.setMax(PhotonCamera.getSettings().frameCount);
        loadingcycle = PhotonCamera.getMainActivity().findViewById(R.id.progressloading);
        loadingcycle.setMax(PhotonCamera.getSettings().frameCount);
        shot = PhotonCamera.getMainActivity().findViewById(R.id.picture);
        shot.setOnClickListener(PhotonCamera.getCameraFragment());
        shot.setActivated(true);
        galleryImageButton = PhotonCamera.getMainActivity().findViewById(R.id.ImageOut);
        galleryImageButton.setOnClickListener(PhotonCamera.getCameraFragment());
        galleryImageButton.setClickable(true);
        PhotonCamera.getTouchFocus().ReInit();
        fpsPreview = PhotonCamera.getMainActivity().findViewById(R.id.fpsPreview);
        fpsPreview.setChecked(PreferenceKeys.isFpsPreviewOn());
        quadResolution = PhotonCamera.getMainActivity().findViewById(R.id.quadRes);
        quadResolution.setChecked(PreferenceKeys.isQuadBayerOn());
        eisPhoto = PhotonCamera.getMainActivity().findViewById(R.id.eisPhoto);
        eisPhoto.setChecked(PreferenceKeys.isEisPhotoOn());
        eisPhoto.setOnClickListener(v -> {
//            Interface.getSettings().eisPhoto = !Interface.getSettings().eisPhoto;
//            Interface.getSettings().save();
            PreferenceKeys.setEisPhoto(!PreferenceKeys.isEisPhotoOn());
        });
        fpsPreview.setOnClickListener(v -> {
//            Interface.getSettings().fpsPreview = !Interface.getSettings().fpsPreview;
//            Interface.getSettings().save();
            PreferenceKeys.setFpsPreview(!PreferenceKeys.isFpsPreviewOn());


        });
        quadResolution.setOnClickListener(v -> {
//            Interface.getSettings().QuadBayer = !Interface.getSettings().QuadBayer;
//            Interface.getSettings().save();
            PreferenceKeys.setQuadBayer(!PreferenceKeys.isQuadBayerOn());
            PhotonCamera.getCameraFragment().restartCamera();
        });
        flip = PhotonCamera.getMainActivity().findViewById(R.id.flip_camera);
        flip.setOnClickListener(v -> {
            flip.animate().rotationBy(180).setDuration(450).start();
            PhotonCamera.getCameraFragment().mTextureView.animate().rotationBy(360).setDuration(450).start();
            PreferenceKeys.setCameraID(PhotonCamera.getCameraFragment().cycler(PhotonCamera.getSettings().mCameraID));
            PhotonCamera.getCameraFragment().restartCamera();
        });
        settings = PhotonCamera.getMainActivity().findViewById(R.id.settings);
        settings.setOnClickListener(PhotonCamera.getCameraFragment());
        hdrX = PhotonCamera.getMainActivity().findViewById(R.id.stacking);
        hdrX.setOnClickListener(PhotonCamera.getCameraFragment());
        PhotonCamera.getCameraFragment().loadGalleryButtonImage();
        modePicker = PhotonCamera.getMainActivity().findViewById(R.id.modePicker);
        String[] modes = Settings.CameraMode.names();
        modePicker.setValues(modes);
        modePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        modePicker.setOnItemSelectedListener(index -> switchToMode(Settings.CameraMode.valueOf(modes[index])));
        modePicker.setSelectedItem(1);
        auxGroup = PhotonCamera.getMainActivity().findViewById(R.id.auxButtons);
    }

    public void switchToMode(Settings.CameraMode cameraMode) {
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
        configureMode(PhotonCamera.getSettings().selectedMode);
        PhotonCamera.getCameraFragment().restartCamera();
    }

    public void configureMode(Settings.CameraMode input) {
        switch (input) {
            case UNLIMITED:
                eisPhoto.setVisibility(View.INVISIBLE);
                fpsPreview.setVisibility(View.VISIBLE);
                hdrX.setVisibility(View.INVISIBLE);
                shot.setBackgroundResource(R.drawable.unlimitedbutton);
                break;
            case PHOTO:
            default:
                eisPhoto.setVisibility(View.VISIBLE);
                fpsPreview.setVisibility(View.VISIBLE);
                hdrX.setVisibility(View.VISIBLE);
                shot.setBackgroundResource(R.drawable.roundbutton);
                break;
            case NIGHT:
                eisPhoto.setVisibility(View.INVISIBLE);
                fpsPreview.setVisibility(View.INVISIBLE);
                break;
        }
    }
    public void onCameraPause(){
        PhotonCamera.getGravity().unregister();
        PhotonCamera.getSensors().unregister();
        PhotonCamera.getSettings().saveID();
    }
    public void onCameraResume(){
        Log.d(TAG,"CameraResume");
        PhotonCamera.getSwipe().init();
        PhotonCamera.getSensors().register();
        PhotonCamera.getGravity().register();
        PhotonCamera.getTouchFocus().ReInit();

        ImageView grid_icon = MainActivity.act.findViewById(R.id.grid);
        ImageView edges = MainActivity.act.findViewById(R.id.edges);
        ToggleButton hdrX = MainActivity.act.findViewById(R.id.stacking);
        if (PhotonCamera.getSettings().grid)
            grid_icon.setVisibility(View.VISIBLE);
        else
            grid_icon.setVisibility(View.GONE);
        if (PhotonCamera.getSettings().roundedge)
            edges.setVisibility(View.VISIBLE);
        else
            edges.setVisibility(View.GONE);
        hdrX.setChecked(PreferenceKeys.isHdrXOn());
        burstUnlock();
        clearProcessingCycle();
    }
    public void onProcessingEnd(){
        clearProcessingCycle();
    }
    public void burstUnlock(){
        PhotonCamera.getCameraUI().shot.setActivated(true);
        PhotonCamera.getCameraUI().shot.setClickable(true);
    }
    public void clearProcessingCycle(){
        try {
            PhotonCamera.getCameraUI().loadingcycle.setProgress(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void incrementProcessingCycle(){
        try {
            int progress = (PhotonCamera.getCameraUI().loadingcycle.getProgress() + 1) % (PhotonCamera.getCameraUI().loadingcycle.getMax() + 1);
            progress = Math.max(1, progress);
            PhotonCamera.getCameraUI().loadingcycle.setProgress(progress);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
