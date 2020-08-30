package com.eszdman.photoncamera.api;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ToggleButton;


import com.eszdman.photoncamera.Control.Manual;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.ui.MainActivity;

import de.hdodenhof.circleimageview.CircleImageView;

public class CameraUI {
    private static final String TAG = "CameraUI";
    public ImageButton shot;
    public ProgressBar lightcycle;
    public ProgressBar loadingcycle;
    public CircleImageView galleryImageButton;
    public RadioGroup auxGroup;
    RadioGroup switcher;
    ToggleButton fpsPreview;
    ToggleButton quadResolution;
    ToggleButton eisPhoto;
    ImageButton flip;
    Button settings;
    ToggleButton hdrX;
    @SuppressLint("ResourceType")
    public void onCameraInitialization(){
        Camera2ApiAutoFix.Init();
        Interface.i.manual.Init();
        String[] cameras = CameraManager2.cameraManager2.getCameraIdList();
        if(auxGroup.getChildCount() == 0 && cameras.length > 2) {
            for (int i = 1; i < cameras.length; i++) {
                RadioButton rb = new RadioButton(Interface.i.mainActivity);
                rb.setText("");
                auxGroup.addView(rb);
            }
            auxGroup.check(1);
            auxGroup.setOnCheckedChangeListener((radioGroup, i) -> {
                if(i >= 2 && CameraManager2.cameraManager2.supportFrontCamera) i++;
                Interface.i.settings.mCameraID = Interface.i.camera.mCameraIds[i-1];
                Interface.i.camera.restartCamera();
            });
        }
    }
    public void onCameraViewCreated(){
        Interface.i.settings.mCameraID = "0";
        lightcycle = Interface.i.mainActivity.findViewById(R.id.lightCycle);
        lightcycle.setAlpha(0);
        lightcycle.setMax(Interface.i.settings.frameCount);
        loadingcycle = Interface.i.mainActivity.findViewById(R.id.progressloading);
        loadingcycle.setMax(Interface.i.settings.frameCount);
        shot = Interface.i.mainActivity.findViewById(R.id.picture);
        shot.setOnClickListener(Interface.i.camera);
        shot.setActivated(true);
        galleryImageButton = Interface.i.mainActivity.findViewById(R.id.ImageOut);
        galleryImageButton.setOnClickListener(Interface.i.camera);
        galleryImageButton.setClickable(true);
        Interface.i.touchFocus.ReInit();
        fpsPreview = Interface.i.mainActivity.findViewById(R.id.fpsPreview);
        fpsPreview.setChecked(Interface.i.settings.fpsPreview);
        quadResolution = Interface.i.mainActivity.findViewById(R.id.quadRes);
        quadResolution.setChecked(Interface.i.settings.QuadBayer);
        eisPhoto = Interface.i.mainActivity.findViewById(R.id.eisPhoto);
        eisPhoto.setChecked(Interface.i.settings.eisPhoto);
        eisPhoto.setOnClickListener(v -> {
            Interface.i.settings.eisPhoto = !Interface.i.settings.eisPhoto;
            Interface.i.settings.save();
        });
        fpsPreview.setOnClickListener(v -> {
            Interface.i.settings.fpsPreview = !Interface.i.settings.fpsPreview;
            Interface.i.settings.save();
        });
        quadResolution.setOnClickListener(v -> {
            Interface.i.settings.QuadBayer = !Interface.i.settings.QuadBayer;
            Interface.i.settings.save();
            Interface.i.camera.restartCamera();
        });
        flip = Interface.i.mainActivity.findViewById(R.id.flip_camera);
        flip.setOnClickListener(v -> {
            flip.animate().rotationBy(180).setDuration(450).start();
            Interface.i.camera.mTextureView.animate().rotationBy(360).setDuration(450).start();
            Interface.i.settings.mCameraID = Interface.i.camera.cycler(Interface.i.settings.mCameraID);
            Interface.i.settings.saveID();
            Interface.i.settings.load();
            Interface.i.camera.restartCamera();
        });
        settings = Interface.i.mainActivity.findViewById(R.id.settings);
        settings.setOnClickListener(Interface.i.camera);
        hdrX = Interface.i.mainActivity.findViewById(R.id.stacking);
        hdrX.setOnClickListener(Interface.i.camera);
        Interface.i.camera.loadGalleryButtonImage();
        switcher = Interface.i.mainActivity.findViewById(R.id.radioswitcher);
        switcher.check(R.id.cameraMode);
        switcher.setOnCheckedChangeListener((radioGroup, i) -> {
            switch(i) {
                case (R.id.cameraMode):
                    Interface.i.settings.selectedMode = Settings.CameraMode.DEFAULT;
                    break;
                case (R.id.nightMode):
                    Interface.i.settings.selectedMode = Settings.CameraMode.NIGHT;
                    break;
                case (R.id.unlimitedMode):
                    Interface.i.settings.selectedMode = Settings.CameraMode.UNLIMITED;
                    break;
            }
            configureMode(Interface.i.settings.selectedMode);
            Interface.i.camera.restartCamera();
        });
        auxGroup = Interface.i.mainActivity.findViewById(R.id.auxButtons);
        Interface.i.manual = new Manual();
    }
    public void configureMode(Settings.CameraMode input){
        switch(input){
            case UNLIMITED:
                eisPhoto.setVisibility(View.INVISIBLE);
                fpsPreview.setVisibility(View.VISIBLE);
                hdrX.setVisibility(View.VISIBLE);
                break;
            case DEFAULT:
                eisPhoto.setVisibility(View.VISIBLE);
                fpsPreview.setVisibility(View.VISIBLE);
                hdrX.setVisibility(View.VISIBLE);
                break;
            case NIGHT:
                eisPhoto.setVisibility(View.INVISIBLE);
                fpsPreview.setVisibility(View.INVISIBLE);
                break;
        }
    }
    public void onCameraPause(){
        Interface.i.gravity.stop();
        Interface.i.sensors.stop();
        Interface.i.settings.saveID();
    }
    public void onCameraResume(){
        Interface.i.swipedetection.RunDetection();
        Interface.i.sensors.run();
        Log.d(TAG,"CameraResume");
        Interface.i.touchFocus.ReInit();
        ImageView grid_icon = MainActivity.act.findViewById(R.id.grid);
        ImageView edges = MainActivity.act.findViewById(R.id.edges);
        ToggleButton hdrX = MainActivity.act.findViewById(R.id.stacking);
        Interface.i.gravity.run();
        if (Interface.i.settings.grid) grid_icon.setVisibility(View.VISIBLE);
        else grid_icon.setVisibility(View.GONE);
        if (Interface.i.settings.roundedge) edges.setVisibility(View.VISIBLE);
        else edges.setVisibility(View.GONE);
        hdrX.setChecked(Interface.i.settings.hdrx);
        Interface.i.camera.startBackgroundThread();
        burstUnlock();
        clearProcessingCycle();
    }
    public void onProcessingEnd(){
        clearProcessingCycle();
    }
    public void burstUnlock(){
        Interface.i.cameraui.shot.setActivated(true);
        Interface.i.cameraui.shot.setClickable(true);
    }
    public void clearProcessingCycle(){
        try {
            Interface.i.cameraui.loadingcycle.setProgress(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void incrementProcessingCycle(){
        try {
            int progress = (Interface.i.cameraui.loadingcycle.getProgress() + 1) % (Interface.i.cameraui.loadingcycle.getMax() + 1);
            progress = Math.max(1, progress);
            Interface.i.cameraui.loadingcycle.setProgress(progress);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
