/*
 *
 *  PhotonCamera
 *  CameraFragment.java
 *  Copyright (C) 2020 - 2021  Eszdman
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.particlesdevs.photoncamera.ui.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Pair;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraEventsListener;
import com.particlesdevs.photoncamera.api.CameraManager2;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.CameraReflectionApi;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.app.base.BaseActivity;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.capture.CaptureEventsListener;
import com.particlesdevs.photoncamera.control.CountdownTimer;
import com.particlesdevs.photoncamera.control.Swipe;
import com.particlesdevs.photoncamera.control.TouchFocus;
import com.particlesdevs.photoncamera.databinding.CameraFragmentBinding;
import com.particlesdevs.photoncamera.databinding.LayoutBottombuttonsBinding;
import com.particlesdevs.photoncamera.databinding.LayoutMainTopbarBinding;
import com.particlesdevs.photoncamera.gallery.ui.GalleryActivity;
import com.particlesdevs.photoncamera.pro.SupportedDevice;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingType;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.ui.camera.model.TopBarSettingsData;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.CameraFragmentViewModel;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.ManualModeViewModel;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.SettingsBarEntryProvider;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.TimerFrameCountViewModel;
import com.particlesdevs.photoncamera.ui.camera.views.FlashButton;
import com.particlesdevs.photoncamera.ui.camera.views.TimerButton;
import com.particlesdevs.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.AutoFitPreviewView;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.SurfaceViewOverViewfinder;
import com.particlesdevs.photoncamera.ui.settings.SettingsActivity;
import com.particlesdevs.photoncamera.util.log.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static androidx.constraintlayout.widget.ConstraintSet.GONE;
import static androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT;

public class CameraFragment extends Fragment implements BaseActivity.BackPressedListener {
    public static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final String FRAGMENT_DIALOG = "dialog";
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = CameraFragment.class.getSimpleName();
    private static final String ACTIVE_BACKCAM_ID = "ACTIVE_BACKCAM_ID"; //key for savedInstanceState
    private static final String ACTIVE_FRONTCAM_ID = "ACTIVE_FRONTCAM_ID"; //key for savedInstanceState
    private static final String NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL_ID";
    /**
     * sActiveBackCamId is either
     * = 0 or camera_id stored in SharedPreferences in case of fresh application Start; or
     * = camera id set from {@link CameraFragment#onViewStateRestored(Bundle)} if Activity re-created due to configuration change.
     * it will NEVER be = 1 *assuming* that 1 is the id of Front Camera on most devices
     */
    public static String sActiveBackCamId = "0";
    public static String sActiveFrontCamId = "1";
    public static CameraMode mSelectedMode;
    private final Field[] metadataFields = CameraReflectionApi.getAllMetadataFields();
    private final int NOTIFICATION_ID = 1;
    private final ExecutorService processExecutorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ProcessingThread");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    public SurfaceViewOverViewfinder surfaceView;
    public String[] mAllCameraIds;
    public Set<String> mFrontCameraIDs;
    public Set<String> mBackCameraIDs;
    public Map<String, Pair<Float, Float>> mFocalLengthAperturePairList;
    private Activity activity;
    private TimerFrameCountViewModel timerFrameCountViewModel;
    private CameraUIView mCameraUIView;
    private CameraUIController mCameraUIEventsListener;
    private CaptureController captureController;
    private CameraFragmentViewModel cameraFragmentViewModel;
    private CameraFragmentBinding cameraFragmentBinding;
    private TouchFocus mTouchFocus;
    private Swipe mSwipe;
    private MediaPlayer burstPlayer;
    private AutoFitPreviewView textureView;
    private NotificationManagerCompat notificationManager;
    private SettingsManager settingsManager;
    private SupportedDevice supportedDevice;
    private ManualModeViewModel manualModeViewModel;
    private SettingsBarEntryProvider settingsBarEntryProvider;

    public CameraFragment() {
        Log.v(TAG, "fragment created");
    }

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    public TouchFocus getTouchFocus() {
        return mTouchFocus;
    }

    public CaptureController getCaptureController() {
        return captureController;
    }

    public ManualModeViewModel getManualModeViewModel() {
        return manualModeViewModel;
    }

    public CameraFragmentViewModel getCameraFragmentViewModel() {
        return cameraFragmentViewModel;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        this.activity = getActivity();
        this.notificationManager = NotificationManagerCompat.from(activity);
        this.settingsManager = PhotonCamera.getInstance(activity).getSettingsManager();
        this.supportedDevice = PhotonCamera.getInstance(activity).getSupportedDevice();

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //create the ui binding
        this.cameraFragmentBinding = DataBindingUtil.inflate(inflater, R.layout.camera_fragment, container, false);

        initMembers();
        setModelsToLayout();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        logDisplayProperties(dm);
        float aspectRatio = (float) Math.max(dm.heightPixels, dm.widthPixels) / Math.min(dm.heightPixels, dm.widthPixels);

        return getAdjustedLayout(aspectRatio, cameraFragmentBinding.textureHolder);
    }

    private void initMembers() {
        //create the viewmodel which updates the model
        cameraFragmentViewModel = new ViewModelProvider(this).get(CameraFragmentViewModel.class);
        timerFrameCountViewModel = new ViewModelProvider(this).get(TimerFrameCountViewModel.class);
        manualModeViewModel = new ViewModelProvider(this).get(ManualModeViewModel.class);
        settingsBarEntryProvider = new ViewModelProvider(this).get(SettingsBarEntryProvider.class);
        surfaceView = cameraFragmentBinding.layoutViewfinder.surfaceView;
        textureView = cameraFragmentBinding.layoutViewfinder.texture;
    }

    private void setModelsToLayout() {
        //bind the model to the ui, it applies changes when the model values get changed
        cameraFragmentBinding.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.layoutTopbar.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.manualMode.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.layoutBottombar.bottomButtons.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        // associating timer model with layouts
        cameraFragmentBinding.layoutBottombar.bottomButtons.setTimermodel(timerFrameCountViewModel.getTimerFrameCountModel());
        cameraFragmentBinding.layoutViewfinder.setTimermodel(timerFrameCountViewModel.getTimerFrameCountModel());
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        this.mCameraUIView = new CameraUIViewImpl();
        this.mCameraUIEventsListener = new CameraUIController();
        this.mCameraUIView.setCameraUIEventsListener(mCameraUIEventsListener);
        this.captureController = new CaptureController(activity, processExecutorService, new CameraEventsListenerImpl());
        this.manualModeViewModel.setManualParamModel(captureController.getManualParamModel());
        PhotonCamera.setCaptureController(captureController);
        this.mSwipe = new Swipe(this);
        initSettingsBar();
    }

    private void initSettingsBar() {
        settingsBarEntryProvider.createEntries();
        settingsBarEntryProvider.addObserver(mCameraUIEventsListener);
        settingsBarEntryProvider.addEntries(cameraFragmentBinding.settingsBar);
    }

    private void updateSettingsBar(){
        settingsBarEntryProvider.updateAllEntries();
        settingsBarEntryProvider.addEntries(cameraFragmentBinding.settingsBar);
        this.mCameraUIView.refresh(CaptureController.isProcessing);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ACTIVE_BACKCAM_ID, sActiveBackCamId);
        outState.putString(ACTIVE_FRONTCAM_ID, sActiveFrontCamId);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (PhotonCamera.DEBUG)
            Log.d("FragmentMonitor", "[" + getClass().getSimpleName() + "] : onViewStateRestored(), savedInstanceState = [" + savedInstanceState + "]");
        if (savedInstanceState != null) {
            sActiveBackCamId = savedInstanceState.getString(ACTIVE_BACKCAM_ID);
            sActiveFrontCamId = savedInstanceState.getString(ACTIVE_FRONTCAM_ID);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showErrorDialog(R.string.request_permission);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSwipe.init();
        PhotonCamera.getGyro().register();
        PhotonCamera.getGravity().register();
        updateSettingsBar();
        this.mCameraUIView.refresh(CaptureController.isProcessing);
        burstPlayer = MediaPlayer.create(activity, R.raw.sound_burst);

        cameraFragmentViewModel.updateGalleryThumb();
        cameraFragmentViewModel.onResume();

        captureController.startBackgroundThread();
        captureController.resumeCamera();
        initTouchFocus();
        supportedDevice.loadCheck();
    }

    private void initTouchFocus() {
        if (cameraFragmentBinding != null && captureController != null) {
            View focusCircle = cameraFragmentBinding.layoutViewfinder.touchFocus;
            textureView.post(() -> {
                Point size = new Point(textureView.getWidth(), textureView.getHeight());
                mTouchFocus = TouchFocus.initialise(captureController, focusCircle, size);
            });
        }
    }

    @Override
    public void onPause() {
        PhotonCamera.getGravity().unregister();
        PhotonCamera.getGyro().unregister();
        PhotonCamera.getSettings().saveID();
        captureController.closeCamera();
//        stopBackgroundThread();
        cameraFragmentViewModel.onPause();
        mCameraUIEventsListener.onPause();
        burstPlayer.release();
        super.onPause();
    }

    @Override
    public boolean onBackPressed() {
        if(cameraFragmentViewModel.isSettingsBarVisible()) {
            cameraFragmentViewModel.setSettingsBarVisible(false);
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        Log.d(TAG, "onDestroy() called");
        try {
            captureController.stopBackgroundThread();
        } catch (Exception e) {
            e.printStackTrace();
        }
        getParentFragmentManager().beginTransaction().remove(CameraFragment.this).commitAllowingStateLoss();
        for (Future<?> taskResult : captureController.taskResults) {
            try {
                taskResult.get(); //wait for all tasks to complete
            } catch (ExecutionException | InterruptedException ignored) {
            }
        }
        settingsBarEntryProvider.removeObserver(mCameraUIEventsListener);
        cameraFragmentBinding = null;
        mCameraUIView.destroy();
        mCameraUIView = null;
        mCameraUIEventsListener = null;
        PhotonCamera.setCaptureController(captureController = null);
        processExecutorService.shutdown();
        Log.d(TAG, "onDestroy() finished");
    }

    @SuppressLint("DefaultLocale")
    private void updateScreenLog(CaptureResult result) {
        surfaceView.post(() -> {
            mTouchFocus.setState(result.get(CaptureResult.CONTROL_AF_STATE));
            if (PreferenceKeys.isAfDataOn()) {
                IsoExpoSelector.ExpoPair expoPair = IsoExpoSelector.GenerateExpoPair(-1, captureController);
                LinkedHashMap<String, String> stringMap = new LinkedHashMap<>();
                stringMap.put("AF_MODE", getResultFieldName("CONTROL_AF_MODE_", result.get(CaptureResult.CONTROL_AF_MODE)));
                stringMap.put("AF_TRIGGER", getResultFieldName("CONTROL_AF_TRIGGER_", result.get(CaptureResult.CONTROL_AF_TRIGGER)));
                stringMap.put("AF_STATE", getResultFieldName("CONTROL_AF_STATE_", result.get(CaptureResult.CONTROL_AF_STATE)));
                stringMap.put("AE_MODE", getResultFieldName("CONTROL_AE_MODE_", result.get(CaptureResult.CONTROL_AE_MODE)));
                stringMap.put("FLASH_MODE", getResultFieldName("FLASH_MODE_", result.get(CaptureResult.FLASH_MODE)));
                stringMap.put("FOCUS_DISTANCE", String.valueOf(result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
                stringMap.put("EXPOSURE_TIME", expoPair.ExposureString() + "s");
//            stringMap.put("EXPOSURE_TIME_CR", String.format(Locale.ROOT,"%.5f",result.get(CaptureResult.SENSOR_EXPOSURE_TIME).doubleValue()/1E9)+ "s");
                stringMap.put("ISO", String.valueOf(expoPair.iso));
//            stringMap.put("ISO_CR", String.valueOf(result.get(CaptureResult.SENSOR_SENSITIVITY)));
                stringMap.put("Shakeness", String.valueOf(PhotonCamera.getGyro().getShakiness()));
                stringMap.put("FrameNumber", String.valueOf(result.getFrameNumber()));
                float[] temp = new float[3];
                temp[0] = captureController.mPreviewTemp[0].floatValue();
                temp[1] = captureController.mPreviewTemp[1].floatValue();
                temp[2] = captureController.mPreviewTemp[2].floatValue();
                stringMap.put("White Point", String.format("%.3f %.3f %.3f", temp[0], temp[1], temp[2]));
                MeteringRectangle[] afRect = result.get(CaptureResult.CONTROL_AF_REGIONS);
                stringMap.put("AF_RECT", Arrays.deepToString(afRect));
                if (afRect != null && afRect.length > 0) {
                    RectF rect = getScreenRectFromMeteringRect(afRect[0]);
                    stringMap.put("AF_RECT(px)", rect.toString());
                    surfaceView.setAFRect(rect);
                } else {
                    surfaceView.setAFRect(null);
                }
                MeteringRectangle[] aeRect = result.get(CaptureResult.CONTROL_AE_REGIONS);
                stringMap.put("AE_RECT", Arrays.deepToString(aeRect));
                if (aeRect != null && aeRect.length > 0) {
                    RectF rect = getScreenRectFromMeteringRect(aeRect[0]);
                    stringMap.put("AE_RECT(px)", rect.toString());
                    surfaceView.setAERect(rect);
                } else {
                    surfaceView.setAERect(null);
                }
                surfaceView.setDebugText(Logger.createTextFrom(stringMap));
                surfaceView.refresh();
            } else {
                if (surfaceView.isCanvasDrawn) {
                    surfaceView.clear();
                }
            }
        });
    }

    private RectF getScreenRectFromMeteringRect(MeteringRectangle meteringRectangle) {
        if (captureController.mImageReaderPreview == null) return new RectF();
        float left = (((float) meteringRectangle.getY() / captureController.mImageReaderPreview.getHeight()) * (surfaceView.getWidth()));
        float top = (((float) meteringRectangle.getX() / captureController.mImageReaderPreview.getWidth()) * (surfaceView.getHeight()));
        float width = (((float) meteringRectangle.getHeight() / captureController.mImageReaderPreview.getHeight()) * (surfaceView.getWidth()));
        float height = (((float) meteringRectangle.getWidth() / captureController.mImageReaderPreview.getWidth()) * (surfaceView.getHeight()));
        return new RectF(
                left, //Left
                top,  //Top
                left + width,//Right
                top + height //Bottom
        );
    }

    private String getResultFieldName(String prefix, Integer value) {
        for (Field f : this.metadataFields)
            if (f.getName().startsWith(prefix)) {
                try {
                    if (f.getInt(f) == value)
                        return f.getName().replace(prefix, "").concat("(" + value + ")");
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        return "";
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    public void showToast(final String text) {
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }
    }

    public void showSnackBar(final String text) {
        final View v = getView();
        if (v != null) {
            v.post(() -> Snackbar.make(v, text, Snackbar.LENGTH_SHORT).show());
        }
    }

    /**
     * Returns the ConstraintLayout object after adjusting the LayoutParams of Views contained in it.
     * Adjusts the relative position of layout_topbar and camera_container (= viewfinder + rest of the buttons excluding layout_topbar)
     * depending on the aspect ratio of device.
     * This is done in order to re-organise the camera layout for long displays (having aspect ratio > 16:9)
     *
     * @param aspectRatio     the aspect ratio of device display given by (height in pixels / width in pixels)
     * @param activity_layout here, the layout of activity_main
     * @return Object of {@param activity_layout} after adjustments.
     */
    private ConstraintLayout getAdjustedLayout(float aspectRatio, ConstraintLayout activity_layout) {
        ConstraintLayout camera_container = activity_layout.findViewById(R.id.camera_container);
        ConstraintLayout.LayoutParams camera_containerLP = (ConstraintLayout.LayoutParams) camera_container.getLayoutParams();
        if (aspectRatio > 16f / 9f) {
            camera_containerLP.height = WRAP_CONTENT;
//            showToast(String.valueOf(aspectRatio));
            ConstraintLayout.LayoutParams layout_topbarLP = ((ConstraintLayout.LayoutParams) activity_layout.findViewById(R.id.layout_topbar).getLayoutParams());
            layout_topbarLP.bottomToTop = R.id.camera_container;    //sets the bottom constraint of layout_topbar to top of camera_container
            if (aspectRatio > 2) {                  //for ratios even greater than 18:9
                layout_topbarLP.topToTop = -1;      //resets/removes the top constraint of topbar
            } else if (aspectRatio == 2) {          //for ratio 18:9
                camera_containerLP.topToTop = -1;   //resets/removes the top constraint of camera_container
                camera_containerLP.topToBottom = R.id.layout_topbar;    //constraints the top of cameracontainer to bottom of layout_topbar
            }
            if (((ConstraintLayout.LayoutParams) activity_layout.findViewById(R.id.texture).getLayoutParams()).dimensionRatio.equals("H,3:4")) {  //if viewfinder ratio is 3:4
                ConstraintLayout.LayoutParams layout_viewfinderLP = (ConstraintLayout.LayoutParams) camera_container.findViewById(R.id.layout_viewfinder).getLayoutParams();
                layout_viewfinderLP.bottomToTop = R.id.layout_bottombar;    //set the bottom of layout_viewfinder to top of layout_bottombar
            }
        }
        return activity_layout;
    }

    /**
     * Logs the device display properties
     *
     * @param dm Object of {@link DisplayMetrics} obtained from Fragment
     */
    private void logDisplayProperties(DisplayMetrics dm) {
        String TAG = "DisplayProps";
        Log.i(TAG, "ScreenResolution = " + Math.max(dm.heightPixels, dm.widthPixels) + "x" + Math.min(dm.heightPixels, dm.widthPixels));
        Log.i(TAG, "AspectRatio = " + ((float) Math.max(dm.heightPixels, dm.widthPixels) / Math.min(dm.heightPixels, dm.widthPixels)));
        Log.i(TAG, "SmallestWidth = " + (int) (Math.min(dm.heightPixels, dm.widthPixels) / (dm.densityDpi / 160f)) + "dp");
    }

    public void initCameraIDLists(CameraManager cameraManager) {
        CameraManager2 manager2 = new CameraManager2(cameraManager, settingsManager);
        this.mAllCameraIds = manager2.getCameraIdList();
        this.mBackCameraIDs = manager2.getBackIDsSet();
        this.mFrontCameraIDs = manager2.getFrontIDsSet();
        this.mFocalLengthAperturePairList = manager2.mFocalLengthAperturePairList;
    }

    public String cycler(String savedCameraID) {
        if (mBackCameraIDs.contains(savedCameraID)) {
            sActiveBackCamId = savedCameraID;
            this.mCameraUIView.setAuxButtons(mFrontCameraIDs, mFocalLengthAperturePairList, sActiveFrontCamId);
            return sActiveFrontCamId;
        } else {
            sActiveFrontCamId = savedCameraID;
            this.mCameraUIView.setAuxButtons(mBackCameraIDs, mFocalLengthAperturePairList, sActiveBackCamId);
            return sActiveBackCamId;
        }
    }

    public void triggerMediaScanner(File imageToSave) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageToSave);
//        Bitmap bitmap = BitmapDecoder.from(Uri.fromFile(imageToSave)).scaleBy(0.1f).decode();
        mediaScanIntent.setData(contentUri);
        if (activity != null)
            activity.sendBroadcast(mediaScanIntent);
    }

    public void launchGallery() {
        Intent galleryIntent = new Intent(activity, GalleryActivity.class);
        startActivity(galleryIntent);
    }

    public void launchSettings() {
        Intent settingsIntent = new Intent(activity, SettingsActivity.class);
        startActivity(settingsIntent);
    }

    public <T extends View> T findViewById(@IdRes int id) {
        return activity.findViewById(id);
    }

    public void showErrorDialog(String errorMsg) {
        ErrorDialog.newInstance(errorMsg).show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }

    public void showErrorDialog(@StringRes int stringRes) {
        try {
            ErrorDialog.newInstance(getString(stringRes)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (Resources.NotFoundException e) {
            showErrorDialog(String.valueOf(stringRes));
        }
    }

    public void invalidateSurfaceView() {
        if (surfaceView != null) {
            surfaceView.invalidate();
        }
    }

    private void showNotification(String processName) {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(activity, NOTIFICATION_CHANNEL_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel
                    (NOTIFICATION_CHANNEL_ID, "NotificationChannel", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
        notificationBuilder
                .setSmallIcon(R.drawable.ic_round_photo_camera_24)
                .setContentTitle(activity.getString(R.string.app_name))
                .setContentText(activity.getString(R.string.processing_processname, processName))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void stopNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    //*****************************************************************************************************************
    //**************************************ErrorDialog****************************************************************
    //*****************************************************************************************************************

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            assert getArguments() != null;
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        if (activity != null) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    //*****************************************************************************************************************
    //**************************************CameraEventsListenerImpl***************************************************
    //*****************************************************************************************************************

    private class CameraEventsListenerImpl extends CameraEventsListener {
        /**
         * Implementation of {@link ProcessingEventsListener}
         */
        @Override
        public void onProcessingStarted(String processName) {
            logD("onProcessingStarted: " + processName + " Processing Started");
            mCameraUIView.setProcessingProgressBarIndeterminate(true);
            mCameraUIView.activateShutterButton(true);
            showNotification(processName);
        }

        @Override
        public void onProcessingChanged(Object obj) {
        }

        @Override
        public void onProcessingFinished(Object obj) {
            logD("onProcessingFinished: " + obj);
            mCameraUIView.setProcessingProgressBarIndeterminate(false);
            mCameraUIView.activateShutterButton(true);
            stopNotification();
        }

        @Override
        public void notifyImageSavedStatus(boolean saved, Path savedFilePath) {
            if (saved) {
                if (savedFilePath != null) {
                    triggerMediaScanner(savedFilePath.toFile());
                    logD("ImageSaved: " + savedFilePath.toString());
//                    showSnackBar("ImageSaved: " + savedFilePath.toString());
                }
                cameraFragmentViewModel.updateGalleryThumb();
            } else {
                logE("ImageSavingError");
                showSnackBar("ImageSavingError");
            }
        }

        @Override
        public void onProcessingError(Object obj) {
            if (obj instanceof String)
                showToast((String) obj);
            onProcessingFinished("Processing Finished Unexpectedly!!");
        }

        //*****************************************************************************************************************

        /**
         * Implementation of {@link CaptureEventsListener}
         */
        @Override
        public void onFrameCountSet(int frameCount) {
            mCameraUIView.setCaptureProgressMax(frameCount);
        }

        @Override
        public void onCaptureStillPictureStarted(Object o) {
            mCameraUIView.setCaptureProgressBarOpacity(1.0f);
            textureView.post(() -> textureView.setAlpha(0.5f));
        }

        @Override
        public void onFrameCaptureStarted(Object o) {
            burstPlayer.seekTo(0);
        }

        @Override
        public void onFrameCaptureProgressed(Object o) {
        }

        @Override
        public void onFrameCaptureCompleted(Object o) {
            mCameraUIView.incrementCaptureProgressBar(1);
            if (PreferenceKeys.isCameraSoundsOn()) {
                burstPlayer.start();
            }
            if (o instanceof TimerFrameCountViewModel.FrameCntTime) {
                timerFrameCountViewModel.setFrameTimeCnt((TimerFrameCountViewModel.FrameCntTime) o);
            }
        }

        @Override
        public void onCaptureSequenceCompleted(Object o) {
            timerFrameCountViewModel.clearFrameTimeCnt();
            mCameraUIView.resetCaptureProgressBar();
            textureView.post(() -> textureView.setAlpha(1f));
            mCameraUIView.activateShutterButton(true);
        }

        @Override
        public void onPreviewCaptureCompleted(CaptureResult captureResult) {
            updateScreenLog(captureResult);
        }

        /**
         * Implementation of abstract methods of {@link CameraEventsListener}
         */

        @Override
        public void onOpenCamera(CameraManager cameraManager) {
            initCameraIDLists(cameraManager);
        }

        @Override
        public void onCameraRestarted() {
            mCameraUIView.refresh(CaptureController.isProcessing);
            mTouchFocus.resetFocusCircle();
        }

        @Override
        public void onCharacteristicsUpdated(CameraCharacteristics characteristics) {
            mCameraUIView.initAuxButtons(mBackCameraIDs, mFocalLengthAperturePairList, mFrontCameraIDs);
            Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mCameraUIView.showFlashButton(flashAvailable != null && flashAvailable);
            manualModeViewModel.init(activity);
            cameraFragmentBinding.manualMode.setManualModeModel(manualModeViewModel.getManualModeModel());
            cameraFragmentBinding.manualMode.setKnobModel(manualModeViewModel.getKnobModel());
        }

        @Override
        public void onError(Object o) {
            if (o instanceof String) {
                showErrorDialog(o.toString());
            }
            if (o instanceof Integer) {
                showErrorDialog((Integer) o);
            }
        }

        @Override
        public void onFatalError(String errorMsg) {
            logE("onFatalError: " + errorMsg);
            activity.finish();
        }

        @Override
        public void onRequestTriggerMediaScanner(File file) {
            triggerMediaScanner(file);
        }
    }

    //*****************************************************************************************************************
    //**********************************************CameraUIViewImpl***************************************************
    //*****************************************************************************************************************

    /**
     * This Class is a dumb 'View' which contains view components visible in the main Camera User Interface
     * <p>
     * It gets instantiated in {@link CameraFragment#onViewCreated(View, Bundle)}
     */
    private final class CameraUIViewImpl implements CameraUIView {
        private static final String TAG = "CameraUIView";
        private final ProgressBar mCaptureProgressBar;
        private final ImageButton mShutterButton;
        private final ProgressBar mProcessingProgressBar;
        private final LinearLayout mAuxGroupContainer;
        private final HorizontalPicker mModePicker;
        private LayoutMainTopbarBinding topbar;
        private LayoutBottombuttonsBinding bottombuttons;
        private CameraUIEventsListener uiEventsListener;
        private HashMap<Integer, String> auxButtonsMap;
        private float baseF = 0.f;

        private CameraUIViewImpl() {
            this.topbar = cameraFragmentBinding.layoutTopbar;
            this.bottombuttons = cameraFragmentBinding.layoutBottombar.bottomButtons;
            this.mCaptureProgressBar = cameraFragmentBinding.layoutViewfinder.captureProgressBar;
            this.mProcessingProgressBar = bottombuttons.processingProgressBar;
            this.mShutterButton = bottombuttons.shutterButton;
            this.mModePicker = cameraFragmentBinding.layoutBottombar.modeSwitcher.modePickerView;
            this.mAuxGroupContainer = cameraFragmentBinding.auxButtonsContainer;
            this.initListeners();
            this.initModeSwitcher();
        }

        private void initListeners() {
            this.topbar.setTopBarClickListener(v -> this.uiEventsListener.onClick(v));
            this.bottombuttons.setBottomBarClickListener(v -> this.uiEventsListener.onClick(v));
        }

        private void initModeSwitcher() {
            this.mModePicker.setValues(CameraMode.names());
            this.mModePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
            this.mModePicker.setOnItemSelectedListener(index -> switchToMode(CameraMode.valueOf(index)));
            this.mModePicker.setSelectedItem(PreferenceKeys.getCameraModeOrdinal());
        }

        @Override
        public void activateShutterButton(boolean status) {
            this.mShutterButton.post(() -> {
                this.mShutterButton.setActivated(status);
                this.mShutterButton.setClickable(status);
            });
        }

        private void switchToMode(CameraMode cameraMode) {
            this.reConfigureModeViews(cameraMode);
            this.uiEventsListener.onCameraModeChanged(cameraMode);
        }

        private void reConfigureModeViews(CameraMode mode) {
            Log.d(TAG, "Current Mode:" + mode.name());
            switch (mode) {
                case VIDEO:
                    this.topbar.setEisVisible(true);
                case UNLIMITED:
                    this.topbar.setFpsVisible(true);
                    this.topbar.setTimerVisible(false);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.GONE);
                    this.mShutterButton.setBackgroundResource(R.drawable.unlimitedbutton);
                    break;
                case PHOTO:
                default:
                    this.topbar.setEisVisible(true);
                    this.topbar.setFpsVisible(true);
                    this.topbar.setTimerVisible(true);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.eis_entry_layout, View.VISIBLE);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.VISIBLE);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.hdrx_entry_layout, View.GONE);
                    this.mShutterButton.setBackgroundResource(R.drawable.roundbutton);
                    break;
                case NIGHT:
                    this.topbar.setEisVisible(false);
                    this.topbar.setFpsVisible(false);
                    this.topbar.setTimerVisible(true);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.eis_entry_layout, View.GONE);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.GONE);
                    cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.VISIBLE);
                    this.mShutterButton.setBackgroundResource(R.drawable.roundbutton);
                    break;
            }
        }

        @Override
        public void refresh(boolean processing) {
            cameraFragmentBinding.invalidateAll();
            this.reConfigureModeViews(CameraMode.valueOf(PreferenceKeys.getCameraModeOrdinal()));
            this.resetCaptureProgressBar();
            if (!processing) {
                this.activateShutterButton(true);
                this.setProcessingProgressBarIndeterminate(false);
            }
        }

        @Override
        public void initAuxButtons(Set<String> backCameraIdsList, Map<String, Pair<Float, Float>> Focals, Set<String> frontCameraIdsList) {
            String savedCameraID = PreferenceKeys.getCameraID();
            for (String id : backCameraIdsList) {
                if (this.baseF == 0.f) {
                    this.baseF = Focals.get(id).first;
                }
            }
            if (this.mAuxGroupContainer.getChildCount() == 0) {
                if (backCameraIdsList.contains(savedCameraID)) {
                    this.setAuxButtons(backCameraIdsList, Focals, savedCameraID);
                } else if (frontCameraIdsList.contains(savedCameraID)) {
                    this.setAuxButtons(frontCameraIdsList, Focals, savedCameraID);
                }
            }
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void setAuxButtons(Set<String> idsList, Map<String, Pair<Float, Float>> Focals, String active) {
            this.mAuxGroupContainer.removeAllViews();
            if (idsList.size() > 1) {
                Locale.setDefault(Locale.US);
                this.auxButtonsMap = new HashMap<>();
                for (String id : idsList) {
                    this.addToAuxGroupButtons(id, String.format("%.1fx",
                            ((Focals.get(id).first / this.baseF) - 0.049)).replace(".0", ""));
                }
                View.OnClickListener auxButtonListener = this::onAuxButtonClick;
                for (int i = 0; i < this.mAuxGroupContainer.getChildCount(); i++) {
                    Button b = (Button) this.mAuxGroupContainer.getChildAt(i);
                    b.setOnClickListener(auxButtonListener);
                    if (active.equals(auxButtonsMap.get(b.getId()))) {
                        b.setSelected(true);
                    }
                }
                this.mAuxGroupContainer.setVisibility(View.VISIBLE);
            } else {
                this.mAuxGroupContainer.setVisibility(View.GONE);
            }
        }

        private void onAuxButtonClick(View view) {
            for (int i = 0; i < this.mAuxGroupContainer.getChildCount(); i++) {
                this.mAuxGroupContainer.getChildAt(i).setSelected(false);
            }
            view.setSelected(true);
            this.uiEventsListener.onAuxButtonClicked(this.auxButtonsMap.get(view.getId()));
        }

        private void addToAuxGroupButtons(String cameraId, String name) {
            Button b = new Button(this.mAuxGroupContainer.getContext());
            int m = (int) this.mAuxGroupContainer.getContext().getResources().getDimension(R.dimen.aux_button_internal_margin);
            int s = (int) this.mAuxGroupContainer.getContext().getResources().getDimension(R.dimen.aux_button_size);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMargins(m, m, m, m);
            b.setLayoutParams(lp);
            b.setText(name);
            b.setTextAppearance(R.style.AuxButtonText);
            b.setBackgroundResource(R.drawable.aux_button_background);
            b.setStateListAnimator(null);
            b.setTransformationMethod(null);
            int buttonId = View.generateViewId();
            b.setId(buttonId);
            this.auxButtonsMap.put(buttonId, cameraId);
            this.mAuxGroupContainer.addView(b);
        }

        @Override
        public void setProcessingProgressBarIndeterminate(boolean indeterminate) {
            this.mProcessingProgressBar.post(() -> this.mProcessingProgressBar.setIndeterminate(indeterminate));
        }

        @Override
        public void incrementCaptureProgressBar(int step) {
            this.mCaptureProgressBar.post(() -> this.mCaptureProgressBar.incrementProgressBy(step));
        }

        @Override
        public void resetCaptureProgressBar() {
            this.mCaptureProgressBar.post(() -> this.mCaptureProgressBar.setProgress(0));
            this.setCaptureProgressBarOpacity(0);
        }

        @Override
        public void setCaptureProgressBarOpacity(float alpha) {
            this.mCaptureProgressBar.post(() -> this.mCaptureProgressBar.setAlpha(alpha));
        }

        @Override
        public void setCaptureProgressMax(int max) {
            this.mCaptureProgressBar.post(() -> this.mCaptureProgressBar.setMax(max));
        }

        @Override
        public void showFlashButton(boolean flashAvailable) {
            this.topbar.setFlashVisible(flashAvailable);
            cameraFragmentBinding.settingsBar.setChildVisibility(R.id.flash_entry_layout, flashAvailable ? View.VISIBLE : GONE);
        }

        @Override
        public void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener) {
            this.uiEventsListener = cameraUIEventsListener;
        }

        @Override
        public void destroy() {
            topbar = null;
            bottombuttons = null;
        }
    }

    //*****************************************************************************************************************
    //**********************************************CameraUIController*************************************************
    //*****************************************************************************************************************

    /**
     * Implementation of {@link CameraUIView.CameraUIEventsListener}
     * <p>
     * Responsible for converting user inputs into actions
     */
    private final class CameraUIController implements CameraUIView.CameraUIEventsListener, Observer<TopBarSettingsData<?, ?>> {
        private static final String TAG = "CameraUIController";
        private CountDownTimer countdownTimer;
        private View shutterButton;

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.shutter_button:
                    shutterButton = view;
                    switch (PhotonCamera.getSettings().selectedMode) {
                        case PHOTO:
                        case NIGHT:
                            if (view.isHovered()) resetTimer();
                            else startTimer();
                            break;
                        case UNLIMITED:
                            if (!captureController.onUnlimited) {
                                captureController.callUnlimitedStart();
                                view.setActivated(false);
                            } else {
                                captureController.callUnlimitedEnd();
                                view.setActivated(true);
                            }
                            break;
                        case VIDEO:
                            if (!captureController.mIsRecordingVideo) {
                                captureController.VideoStart();
                                view.setActivated(false);
                            } else {
                                captureController.VideoEnd();
                                view.setActivated(true);
                            }
                            break;
                    }
                    break;
                case R.id.settings_button:
                    launchSettings();
                    break;

                case R.id.hdrx_toggle_button:
                    PreferenceKeys.setHdrX(!PreferenceKeys.isHdrXOn());
                    if (PreferenceKeys.isHdrXOn())
                        CaptureController.setTargetFormat(CaptureController.RAW_FORMAT);
                    else
                        CaptureController.setTargetFormat(CaptureController.YUV_FORMAT);
                    showSnackBar(getString(R.string.hdrx) + ':' + onOff(PreferenceKeys.isHdrXOn()));
                    this.restartCamera();
                    break;

                case R.id.gallery_image_button:
                    launchGallery();
                    break;

                case R.id.eis_toggle_button:
                    PreferenceKeys.setEisPhoto(!PreferenceKeys.isEisPhotoOn());
                    showSnackBar(getString(R.string.eis_toggle_text) + ':' + onOff(PreferenceKeys.isEisPhotoOn()));
                    updateSettingsBar();
                    break;

                case R.id.fps_toggle_button:
                    PreferenceKeys.setFpsPreview(!PreferenceKeys.isFpsPreviewOn());
                    showSnackBar(getString(R.string.fps_60_toggle_text) + ':' + onOff(PreferenceKeys.isFpsPreviewOn()));
                    updateSettingsBar();
                    break;

                case R.id.quad_res_toggle_button:
                    PreferenceKeys.setQuadBayer(!PreferenceKeys.isQuadBayerOn());
                    showSnackBar(getString(R.string.quad_bayer_toggle_text) + ':' + onOff(PreferenceKeys.isQuadBayerOn()));
                    this.restartCamera();
                    updateSettingsBar();
                    break;

                case R.id.flip_camera_button:
                    view.animate().rotationBy(180).setDuration(450).start();
                    textureView.animate().rotationBy(360).setDuration(450).start();
                    PreferenceKeys.setCameraID(cycler(PreferenceKeys.getCameraID()));
                    this.restartCamera();
                    break;
                case R.id.grid_toggle_button:
                    PreferenceKeys.setGridValue((PreferenceKeys.getGridValue() + 1) % view.getResources().getStringArray(R.array.vf_grid_entryvalues).length);
                    view.setSelected(PreferenceKeys.getGridValue() != 0);
                    invalidateSurfaceView();
                    updateSettingsBar();
                    break;

                case R.id.flash_button:
                    PreferenceKeys.setAeMode((PreferenceKeys.getAeMode() + 1) % 4); //cycles in 0,1,2,3
                    ((FlashButton) view).setFlashValueState(PreferenceKeys.getAeMode());
                    captureController.setPreviewAEModeRebuild(PreferenceKeys.getAeMode());
                    updateSettingsBar();
                    break;

                case R.id.countdown_timer_button:
                    PreferenceKeys.setCountdownTimerIndex((PreferenceKeys.getCountdownTimerIndex() + 1) % view.getResources().getIntArray(R.array.countdowntimer_entryvalues).length);
                    ((TimerButton) view).setTimerIconState(PreferenceKeys.getCountdownTimerIndex());
                    updateSettingsBar();
                    break;
            }
        }

        private int getTimerValue(Context context) {
            int[] timerValues = context.getResources().getIntArray(R.array.countdowntimer_entryvalues);
            return timerValues[PreferenceKeys.getCountdownTimerIndex()];
        }

        private void startTimer() {
            if (this.shutterButton != null) {
                this.shutterButton.setHovered(true);
                this.countdownTimer = new CountdownTimer(
                        findViewById(R.id.frameTimer),
                        getTimerValue(this.shutterButton.getContext()) * 1000L, 1000,
                        this::onTimerFinished).start();
            }
        }

        private void resetTimer() {
            if (this.countdownTimer != null) this.countdownTimer.cancel();
            if (this.shutterButton != null) this.shutterButton.setHovered(false);
        }

        @Override
        public void onAuxButtonClicked(String id) {
            Log.d(TAG, "onAuxButtonClicked() called with: id = [" + id + "]");
            PreferenceKeys.setCameraID(String.valueOf(id));
            this.restartCamera();

        }

        @Override
        public void onCameraModeChanged(CameraMode cameraMode) {
            PreferenceKeys.setCameraModeOrdinal(cameraMode.ordinal());
            Log.d(TAG, "onCameraModeChanged() called with: cameraMode = [" + cameraMode + "]");
            switch (cameraMode) {
                case PHOTO:
                case NIGHT:
                case UNLIMITED:
                default:
                    break;
                case VIDEO:
                    PreferenceKeys.setCameraModeOrdinal(CameraMode.PHOTO.ordinal()); //since Video Mode is broken at the moment
                    break;
            }
            this.restartCamera();
        }

        @Override
        public void onPause() {
            this.resetTimer();
        }

        private void restartCamera() {
            this.resetTimer();
            captureController.restartCamera();
        }

        private String onOff(boolean value) {
            return value ? "On" : "Off";
        }

        private void onTimerFinished() {
            this.shutterButton.setHovered(false);
            this.shutterButton.setActivated(false);
            this.shutterButton.setClickable(false);
            captureController.takePicture();
        }

        @Override
        public void onChanged(TopBarSettingsData<?, ?> topBarSettingsData) {
            if (topBarSettingsData != null && topBarSettingsData.getType() != null && topBarSettingsData.getValue() != null) {
                if (topBarSettingsData.getType() instanceof SettingType) {
                    SettingType type = (SettingType) topBarSettingsData.getType();
                    Object value = topBarSettingsData.getValue();
                    switch (type) {
                        case FLASH:
                            PreferenceKeys.setAeMode((Integer) value); //cycles in 0,1,2,3
                            captureController.setPreviewAEModeRebuild(PreferenceKeys.getAeMode());
                            cameraFragmentBinding.layoutTopbar.flashButton.setFlashValueState((Integer) value);
                            break;
                        case HDRX:
                            PreferenceKeys.setHdrX(value.equals(1));
                            if (value.equals(1))
                                CaptureController.setTargetFormat(CaptureController.RAW_FORMAT);
                            else
                                CaptureController.setTargetFormat(CaptureController.YUV_FORMAT);
                            this.restartCamera();
                            break;
                        case QUAD:
                            PreferenceKeys.setQuadBayer(value.equals(1));
                            this.restartCamera();
                            break;
                        case GRID:
                            PreferenceKeys.setGridValue((Integer) value);
                            invalidateSurfaceView();
                            break;
                        case FPS_60:
                            PreferenceKeys.setFpsPreview(value.equals(1));
                            break;
                        case TIMER:
                            PreferenceKeys.setCountdownTimerIndex((Integer) value);
                            cameraFragmentBinding.layoutTopbar.countdownTimerButton.setTimerIconState((Integer) value);
                            break;
                        case EIS:
                            PreferenceKeys.setEisPhoto(value.equals(1));
                            break;
                        case RAW:
                            PreferenceKeys.setSaveRaw(value.equals(1));
                            break;
                        case BATTERY_SAVER:
                            PreferenceKeys.setBatterySaver(value.equals(1));
                            break;

                    }
                    cameraFragmentBinding.layoutTopbar.invalidateAll();
                }
            }

        }
    }
}