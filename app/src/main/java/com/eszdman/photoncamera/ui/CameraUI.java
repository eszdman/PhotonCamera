package com.eszdman.photoncamera.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.view.View;
import android.widget.*;


import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.CameraController;
import com.eszdman.photoncamera.api.CameraFragment;
import com.eszdman.photoncamera.api.CameraManager2;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Settings;

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
                if (isFrontCam(CameraController.GET().mCameraIds[i])) i++;
                if (i >= CameraController.GET().mCameraIds.length)
                    i = i - CameraController.GET().mCameraIds.length;
                Interface.getSettings().mCameraID = CameraController.GET().mCameraIds[i];
                CameraController.GET().restartCamera();
            });
        }
        Interface.getManualMode().init();
    }

    private boolean isFrontCam(String id)
    {
        CameraManager manager = (CameraManager) Interface.getMainActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(id);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (characteristics == null) {
            Log.e(TAG, "Failed to get Characteristics for camera id:" + id);
            return false;
        }
        return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
    }

    public void onCameraViewCreated() {
        Interface.getSettings().mCameraID = "0";
        lightcycle = Interface.getMainActivity().findViewById(R.id.lightCycle);
        lightcycle.setAlpha(0);
        lightcycle.setMax(Interface.getSettings().frameCount);
        loadingcycle = Interface.getMainActivity().findViewById(R.id.progressloading);
        loadingcycle.setMax(Interface.getSettings().frameCount);
        shot = Interface.getMainActivity().findViewById(R.id.picture);
        shot.setOnClickListener(CameraFragment.GET());
        shot.setActivated(true);
        galleryImageButton = Interface.getMainActivity().findViewById(R.id.ImageOut);
        galleryImageButton.setOnClickListener(CameraFragment.GET());
        galleryImageButton.setClickable(true);
        Interface.getTouchFocus().ReInit();
        fpsPreview = Interface.getMainActivity().findViewById(R.id.fpsPreview);
        fpsPreview.setChecked(Interface.getSettings().fpsPreview);
        quadResolution = Interface.getMainActivity().findViewById(R.id.quadRes);
        quadResolution.setChecked(Interface.getSettings().QuadBayer);
        eisPhoto = Interface.getMainActivity().findViewById(R.id.eisPhoto);
        eisPhoto.setChecked(Interface.getSettings().eisPhoto);
        eisPhoto.setOnClickListener(v -> {
            Interface.getSettings().eisPhoto = !Interface.getSettings().eisPhoto;
            Interface.getSettings().save();
        });
        fpsPreview.setOnClickListener(v -> {
            Interface.getSettings().fpsPreview = !Interface.getSettings().fpsPreview;
            Interface.getSettings().save();
        });
        quadResolution.setOnClickListener(v -> {
            Interface.getSettings().QuadBayer = !Interface.getSettings().QuadBayer;
            Interface.getSettings().save();
            CameraController.GET().restartCamera();
        });
        flip = Interface.getMainActivity().findViewById(R.id.flip_camera);
        flip.setOnClickListener(v -> {
            flip.animate().rotationBy(180).setDuration(450).start();
            CameraFragment.GET().mTextureView.animate().rotationBy(360).setDuration(450).start();
            Interface.getSettings().mCameraID = cycler(Interface.getSettings().mCameraID);
            Interface.getSettings().saveID();
            Interface.getSettings().load();
            CameraController.GET().restartCamera();
        });
        settings = Interface.getMainActivity().findViewById(R.id.settings);
        settings.setOnClickListener(CameraFragment.GET());
        hdrX = Interface.getMainActivity().findViewById(R.id.stacking);
        hdrX.setOnClickListener(CameraFragment.GET());
        CameraFragment.GET().loadGalleryButtonImage();
        modePicker = Interface.getMainActivity().findViewById(R.id.modePicker);
        String[] modes = Settings.CameraMode.names();
        modePicker.setValues(modes);
        modePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        modePicker.setOnItemSelectedListener(index -> switchToMode(Settings.CameraMode.valueOf(modes[index])));
        modePicker.setSelectedItem(1);
        auxGroup = Interface.getMainActivity().findViewById(R.id.auxButtons);
    }

    private String cycler(String id) {
        boolean front1 = isFrontCam(id);
        String ret = "0";
        if (front1)
        {
            for (String s : CameraController.GET().mCameraIds)
                if (!isFrontCam(s)) {
                    ret = s;
                    Interface.getCameraUI().auxGroup.setVisibility(View.VISIBLE);
                    break;
                }
        }
        else
        {
            for (String s : CameraController.GET().mCameraIds)
                if (isFrontCam(s)) {
                    ret = s;
                    Interface.getCameraUI().auxGroup.setVisibility(View.INVISIBLE);
                    break;
                }
        }
        return ret;

        /*boolean front2 = isFrontCam(id+1);
        String[] ids;
        if(CameraManager2.cameraManager2.supportFrontCamera) {
            if(Interface.getCameraUI().auxGroup.getChildCount() != 0) {
                int i = Interface.getCameraUI().auxGroup.getCheckedRadioButtonId();
                if (i >= 2) i++;
                ids = new String[]{CameraController.GET().mCameraIds[i - 1], "1"};
            } else ids = new String[]{"0","1"};
        }
        else {
            return "0";
        }
        int n = 0;
        for (int i = 0; i < ids.length; i++) {
            if (id.equals(ids[i])) n = i;
        }
        n++;
        n %= ids.length;
        if(n == 1) Interface.getCameraUI().auxGroup.setVisibility(View.INVISIBLE);
        else {
            Interface.getCameraUI().auxGroup.setVisibility(View.VISIBLE);
        }
        return ids[n];*/
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
        CameraController.GET().restartCamera();
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
        hdrX.setChecked(Interface.getSettings().hdrx);
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
