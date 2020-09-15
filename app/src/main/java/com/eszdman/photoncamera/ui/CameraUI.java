package com.eszdman.photoncamera.ui;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.widget.*;


import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.CameraManager2;
import com.eszdman.photoncamera.api.Interface;
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
                RadioButton rb = new RadioButton(Interface.getMainActivity());
                rb.setText("");
                auxGroup.addView(rb);
            }
            Interface.getSettings().mCameraID = "0";
            auxGroup.check(1);
            auxGroup.setOnCheckedChangeListener((radioGroup, i) -> {
                if (i >= 2 && CameraManager2.cameraManager2.supportFrontCamera) i++;
                Interface.getSettings().mCameraID = Interface.getCameraFragment().mCameraIds[i - 1];
                Interface.getCameraFragment().restartCamera();
            });
        }
        Interface.getManualMode().init();
    }

    public void onCameraViewCreated() {
        Interface.getSettings().mCameraID = "0";
        lightcycle = Interface.getMainActivity().findViewById(R.id.lightCycle);
        lightcycle.setAlpha(0);
        lightcycle.setMax(Interface.getSettings().frameCount);
        loadingcycle = Interface.getMainActivity().findViewById(R.id.progressloading);
        loadingcycle.setMax(Interface.getSettings().frameCount);
        shot = Interface.getMainActivity().findViewById(R.id.picture);
        shot.setOnClickListener(Interface.getCameraFragment());
        shot.setActivated(true);
        galleryImageButton = Interface.getMainActivity().findViewById(R.id.ImageOut);
        galleryImageButton.setOnClickListener(Interface.getCameraFragment());
        galleryImageButton.setClickable(true);
        Interface.getTouchFocus().ReInit();
        fpsPreview = Interface.getMainActivity().findViewById(R.id.fpsPreview);
        fpsPreview.setChecked(PreferenceKeys.isFpsPreviewOn());
        quadResolution = Interface.getMainActivity().findViewById(R.id.quadRes);
        quadResolution.setChecked(PreferenceKeys.isQuadBayerOn());
        eisPhoto = Interface.getMainActivity().findViewById(R.id.eisPhoto);
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
            Interface.getCameraFragment().restartCamera();
        });
        flip = Interface.getMainActivity().findViewById(R.id.flip_camera);
        flip.setOnClickListener(v -> {
            flip.animate().rotationBy(180).setDuration(450).start();
            Interface.getCameraFragment().mTextureView.animate().rotationBy(360).setDuration(450).start();
            Interface.getSettings().mCameraID = Interface.getCameraFragment().cycler(Interface.getSettings().mCameraID);
            Interface.getSettings().saveID();
            Interface.getSettings().loadCache();
            Interface.getCameraFragment().restartCamera();
        });
        settings = Interface.getMainActivity().findViewById(R.id.settings);
        settings.setOnClickListener(Interface.getCameraFragment());
        hdrX = Interface.getMainActivity().findViewById(R.id.stacking);
        hdrX.setOnClickListener(Interface.getCameraFragment());
        Interface.getCameraFragment().loadGalleryButtonImage();
        modePicker = Interface.getMainActivity().findViewById(R.id.modePicker);
        String[] modes = Settings.CameraMode.names();
        modePicker.setValues(modes);
        modePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        modePicker.setOnItemSelectedListener(index -> switchToMode(Settings.CameraMode.valueOf(modes[index])));
        modePicker.setSelectedItem(1);
        auxGroup = Interface.getMainActivity().findViewById(R.id.auxButtons);
    }

    public void switchToMode(Settings.CameraMode cameraMode) {
        switch (cameraMode) {
            case PHOTO:
            default:
                Interface.getSettings().selectedMode = Settings.CameraMode.PHOTO;
                break;
            case NIGHT:
                Interface.getSettings().selectedMode = Settings.CameraMode.NIGHT;
                break;
            case UNLIMITED:
                Interface.getSettings().selectedMode = Settings.CameraMode.UNLIMITED;
                break;
        }
        configureMode(Interface.getSettings().selectedMode);
        Interface.getCameraFragment().restartCamera();
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
        Interface.getGravity().stop();
        Interface.getSensors().stop();
        Interface.getSettings().saveID();
    }
    public void onCameraResume(){
        Interface.getSwipe().RunDetection();
        Interface.getSensors().run();
        Log.d(TAG,"CameraResume");
        Interface.getTouchFocus().ReInit();
        ImageView grid_icon = MainActivity.act.findViewById(R.id.grid);
        ImageView edges = MainActivity.act.findViewById(R.id.edges);
        ToggleButton hdrX = MainActivity.act.findViewById(R.id.stacking);
        Interface.getGravity().run();
        if (Interface.getSettings().grid) grid_icon.setVisibility(View.VISIBLE);
        else grid_icon.setVisibility(View.GONE);
        if (Interface.getSettings().roundedge) edges.setVisibility(View.VISIBLE);
        else edges.setVisibility(View.GONE);
        hdrX.setChecked(PreferenceKeys.isHdrXOn());
        Interface.getCameraFragment().startBackgroundThread();
        burstUnlock();
        clearProcessingCycle();
    }
    public void onProcessingEnd(){
        clearProcessingCycle();
    }
    public void burstUnlock(){
        Interface.getCameraUI().shot.setActivated(true);
        Interface.getCameraUI().shot.setClickable(true);
    }
    public void clearProcessingCycle(){
        try {
            Interface.getCameraUI().loadingcycle.setProgress(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void incrementProcessingCycle(){
        try {
            int progress = (Interface.getCameraUI().loadingcycle.getProgress() + 1) % (Interface.getCameraUI().loadingcycle.getMax() + 1);
            progress = Math.max(1, progress);
            Interface.getCameraUI().loadingcycle.setProgress(progress);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
