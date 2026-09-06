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
import android.graphics.Bitmap;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.HorizonIndicatorView;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.ViewfinderHudView;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.manual.ParamController;
import android.util.Size;
import android.util.SizeF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
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
import com.particlesdevs.photoncamera.circularbarlib.api.ManualInstanceProvider;
import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;
import com.particlesdevs.photoncamera.control.Swipe;
import com.particlesdevs.photoncamera.control.TouchFocus;
import com.particlesdevs.photoncamera.databinding.CameraFragmentBinding;
import com.particlesdevs.photoncamera.gallery.ui.GalleryActivity;
import com.particlesdevs.photoncamera.pro.SupportedDevice;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.parameters.ColorTemperatureConverter;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.ui.camera.binding.CustomBinding;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.*;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.GLPreview;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.SurfaceViewOverViewfinder;
import com.particlesdevs.photoncamera.ui.settings.SettingsActivity;
import com.particlesdevs.photoncamera.util.log.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    /*
    private final ExecutorService processExecutorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ProcessingThread");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });*/
    private final ExecutorService processExecutorService = Executors.newFixedThreadPool(2);
    public SurfaceViewOverViewfinder surfaceView;
    public Map<String, CameraLensData> mCameraLensDataMap;
    public Activity activity;
    private TimerFrameCountViewModel timerFrameCountViewModel;
    private CameraUIView mCameraUIView;
    private CameraUIController mCameraUIEventsListener;
    public CaptureController captureController;
    private CameraFragmentViewModel cameraFragmentViewModel;
    public AuxButtonsViewModel auxButtonsViewModel;
    public CameraFragmentBinding cameraFragmentBinding;
    private TouchFocus mTouchFocus;
    public Swipe mSwipe;
    // Created on an AsyncTask thread in onResume and consumed from the camera
    // callback threads; volatile + local-copy access keeps them consistent.
    private volatile MediaPlayer burstPlayer;
    private volatile MediaPlayer endPlayer;
    public GLPreview textureView;
    private NotificationManagerCompat notificationManager;
    private SettingsManager settingsManager;
    private SupportedDevice supportedDevice;
    private SettingsBarEntryProvider settingsBarEntryProvider;
    private ManualModeConsole manualModeConsole;
    public float displayAspectRatio;
    private HorizonIndicatorView mHorizonIndicatorView;
    private ViewfinderHudView mViewfinderHudView;

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

    public ManualModeConsole getManualModeConsole() {
        return manualModeConsole;
    }

    public CameraFragmentViewModel getCameraFragmentViewModel() {
        return cameraFragmentViewModel;
    }
   @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        assert activity != null;
        notificationManager = NotificationManagerCompat.from(activity);
        settingsManager = Objects.requireNonNull(PhotonCamera.getInstance(activity)).getSettingsManager();
        supportedDevice = Objects.requireNonNull(PhotonCamera.getInstance(activity)).getSupportedDevice();
    }
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //create the ui binding
        this.cameraFragmentBinding = DataBindingUtil.inflate(inflater, R.layout.camera_fragment, container, false);
        Log.d(TAG, "onCreateView: ");
        initMembers();
        setModelsToLayout();
        return cameraFragmentBinding.getRoot();
    }
    private void initMembers() {
        //create the viewmodel which updates the model
        cameraFragmentViewModel = new ViewModelProvider(this).get(CameraFragmentViewModel.class);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        logDisplayProperties(dm);
        displayAspectRatio = (float) Math.max(dm.heightPixels, dm.widthPixels) / Math.min(dm.heightPixels, dm.widthPixels);
        cameraFragmentViewModel.setScreenAspectRatio(displayAspectRatio);

        timerFrameCountViewModel = new ViewModelProvider(this).get(TimerFrameCountViewModel.class);
        manualModeConsole = ManualInstanceProvider.getNewManualModeConsole();
        settingsBarEntryProvider = new ViewModelProvider(this).get(SettingsBarEntryProvider.class);
        auxButtonsViewModel = new ViewModelProvider(this).get(AuxButtonsViewModel.class);
        surfaceView = cameraFragmentBinding.layoutViewfinder.surfaceView;
        textureView = cameraFragmentBinding.layoutViewfinder.texture;
        mViewfinderHudView = cameraFragmentBinding.layoutViewfinder.viewfinderHudView;
    }

    private void setModelsToLayout() {
        //bind the model to the ui, it applies changes when the model values get changed
        cameraFragmentBinding.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.layoutTopbar.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.layoutBottombar.bottomButtons.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        // associating timer model with layouts
        cameraFragmentBinding.layoutBottombar.bottomButtons.setTimermodel(timerFrameCountViewModel.getTimerFrameCountModel());
        cameraFragmentBinding.layoutViewfinder.setTimermodel(timerFrameCountViewModel.getTimerFrameCountModel());
        // associating AuxButtonsModel with layout
        cameraFragmentBinding.setAuxmodel(auxButtonsViewModel.getAuxButtonsModel());
    }

    /**
     * Applies the same default layout configuration that the data-binding pipeline
     * applies at runtime via {@link #setModelsToLayout()} and the adapters in
     * {@link CustomBinding}:
     * <ul>
     * <li>{@code uimodel.dummyAspectRatio} -&gt; {@code dummy_reference_view} aspect ratio</li>
     * <li>{@code uimodel.settingsBarVisibility == false} -&gt; settings bar hidden</li>
     * <li>{@code uimodel.screenAspectRatio} -&gt; topbar notch margin and camera container anchor</li>
     * </ul>
     * The layout editor preview never runs fragments, viewmodels or data binding,
     * so {@link com.particlesdevs.photoncamera.ui.camera.CameraLayout} invokes this
     * during inflation to render the same UI. No logic is duplicated; it reuses the
     * exact binding adapters used at runtime.
     *
     * @param rootLayout the inflated camera_fragment root view
     */
    static void preparePreviewLayout(View rootLayout) {
        // The bottom-bar anchor uses a portrait 3:4 ratio in photo mode (CameraUIViewImpl).
        CustomBinding.setAspectRatio(rootLayout.findViewById(R.id.dummy_reference_view), "3:4");
        // The viewfinder is a 3:4 portrait block on the phone; the layout editor can't
        // measure it from the camera, so give it the same ratio for the preview.
        View viewfinder = rootLayout.findViewById(R.id.layout_viewfinder);
        if (viewfinder != null && viewfinder.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) viewfinder.getLayoutParams();
            params.height = 0;
            params.dimensionRatio = "3:4";
            viewfinder.setLayoutParams(params);
        }
        // settingsBarVisibility defaults to false -> the settings bar is hidden
        View settingsBar = rootLayout.findViewById(R.id.settings_bar);
        if (settingsBar != null) {
            settingsBar.setVisibility(View.INVISIBLE);
        }
        // screenAspectRatio (the device display ratio) drives the topbar notch
        // margin and the camera container's top anchor via the same binding
        // adapters used at runtime. The layout editor exposes the preview device
        // metrics, so compute it the same way as CameraFragment#onViewCreated.
        DisplayMetrics dm = rootLayout.getResources().getDisplayMetrics();
        float displayAspectRatio = (float) Math.max(dm.heightPixels, dm.widthPixels)
                / Math.min(dm.heightPixels, dm.widthPixels);
        CustomBinding.adjustTopBar(rootLayout.findViewById(R.id.layout_topbar), displayAspectRatio);
        CustomBinding.adjustCameraContainer(rootLayout.findViewById(R.id.camera_container), displayAspectRatio);
    }
    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        this.mCameraUIView = new CameraUIViewImpl(this);
        this.mCameraUIEventsListener = new CameraUIController(this);
        this.mCameraUIView.setCameraUIEventsListener(mCameraUIEventsListener);
        this.captureController = new CaptureController(activity, processExecutorService, new CameraEventsListenerImpl());
        this.captureController.setManualModeConsole(manualModeConsole);
        this.manualModeConsole.addParamObserver(captureController.getParamController());
        this.textureView.setManualModeConsole(manualModeConsole);
        PhotonCamera.setCaptureController(captureController);
        captureController.isDualSession = supportedDevice.specific.specificSetting.isDualSessionSupported;
        mHorizonIndicatorView = cameraFragmentBinding.layoutViewfinder.horizonIndicatorView;
        this.mSwipe = new Swipe(this);
        var gyro = PhotonCamera.getGyro();
        if ((mHorizonIndicatorView != null) && (gyro != null)) {
            mHorizonIndicatorView.updateDisplayRotation(getCameraFragmentViewModel().getCameraFragmentModel().getOrientation());
            mHorizonIndicatorView.setGyro(gyro);
        }
        if (mHorizonIndicatorView != null) {
            mHorizonIndicatorView.setVisible(PreferenceKeys.isHorizonOn());
        }
        initSettingsBar();
    }

    private void initSettingsBar() {
        settingsBarEntryProvider.createEntries();
        settingsBarEntryProvider.addObserver(mCameraUIEventsListener);
        settingsBarEntryProvider.addEntries(cameraFragmentBinding.settingsBar);
    }

    public void updateSettingsBar(){
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
        updateSettingsBar();
        mSwipe.init();
        this.mCameraUIView.refresh(CaptureController.isProcessing);
        AsyncTask.execute(() -> {
            PhotonCamera.getGyro().register();
            PhotonCamera.getGravity().register();
            // onPause may already have released the players if the app was
            // backgrounded before this task ran; only create what is missing.
            if (burstPlayer == null) {
                burstPlayer = MediaPlayer.create(activity, R.raw.sound_burst2);
            }
            if (endPlayer == null) {
                endPlayer = MediaPlayer.create(activity, R.raw.sound_end);
            }
            cameraFragmentViewModel.updateGalleryThumb(null);
        });
        cameraFragmentViewModel.onResume();
        auxButtonsViewModel.setAuxButtonListener(mCameraUIEventsListener);
        if (mHorizonIndicatorView != null) {
            mHorizonIndicatorView.setVisible(PreferenceKeys.isHorizonOn());
        }
        captureController.startBackgroundThread();
        textureView.onResume();
        captureController.resumeCamera();
        initTouchFocus();
        manualModeConsole.onResume();
    }

    private void initTouchFocus() {
        if (cameraFragmentBinding != null && captureController != null) {
            View focusCircle = cameraFragmentBinding.layoutViewfinder.touchFocus;
            View spotWbIndicator = cameraFragmentBinding.layoutViewfinder.spotWbIndicator;
            textureView.post(() -> {
                mTouchFocus = new TouchFocus(captureController, focusCircle, spotWbIndicator, textureView);
                captureController.mTouchFocus = mTouchFocus;
            });
        }
    }

    @Override
    public void onPause() {
        PhotonCamera.getGravity().unregister();
        PhotonCamera.getGyro().unregister();
        PhotonCamera.getSettings().saveID();
        textureView.onPause();
        surfaceView.clear();
        if (mViewfinderHudView != null) mViewfinderHudView.clear();
        captureController.closeCamera();
//        stopBackgroundThread();
        cameraFragmentViewModel.onPause();
        mCameraUIEventsListener.onPause();
        auxButtonsViewModel.setAuxButtonListener(null);
        // The players are created asynchronously in onResume, so they may still
        // be null when the app is backgrounded again quickly.
        MediaPlayer burst = burstPlayer;
        if (burst != null) {
            burst.release();
            burstPlayer = null;
        }
        MediaPlayer end = endPlayer;
        if (end != null) {
            end.release();
            endPlayer = null;
        }
        mSwipe.SwipeDown();
        manualModeConsole.onPause();
        super.onPause();
    }

    @Override
    public boolean onBackPressed() {
        boolean handleBack = false;
        if (cameraFragmentViewModel.isSettingsBarVisible()) {
            cameraFragmentViewModel.setSettingsBarVisible(false);
            handleBack = true;
        }
        if (manualModeConsole.isPanelVisible()) {
            mSwipe.SwipeDown();
            handleBack = true;
        }
        return handleBack;
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
        manualModeConsole.onDestroy();

        // Only clear global controller reference if it still points to this dying fragment instance
        if (PhotonCamera.getCaptureController() == this.captureController) {
            PhotonCamera.setCaptureController(null);
        }
        this.captureController = null;

        processExecutorService.shutdown();
        Log.d(TAG, "onDestroy() finished");
    }

    @SuppressLint("DefaultLocale")
    private void updateScreenLog(CaptureResult result) {
        surfaceView.post(() -> {
            int orientation = getCameraFragmentViewModel().getCameraFragmentModel().getOrientation();
            if (mHorizonIndicatorView != null) {
                mHorizonIndicatorView.updateDisplayRotation(orientation);
            }
            if (mViewfinderHudView != null) {
                mViewfinderHudView.setOrientation(orientation);
            }
            if (mTouchFocus != null) {
                mTouchFocus.setOrientation(orientation);
                mTouchFocus.setState(result.get(CaptureResult.CONTROL_AF_STATE));
            }
            int afDataMode = PreferenceKeys.getAfDataValue();
            if (mViewfinderHudView != null) {
                mViewfinderHudView.setHudMode(afDataMode);
            }
            if (afDataMode == 1 || afDataMode == 2) {
                // Mode 1: HUD, Mode 2: HUD + Histogram
                updateViewfinderHud(result, afDataMode);
            } else if (afDataMode == 3) {
                // Mode 3: Full Raw Debug Mode
                boolean isZsl = (captureController != null && captureController.isZslMode());
                String exposureStr;
                String isoStr;
                if (isZsl) {
                    Long expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    long expTime = (expNs != null) ? expNs : (captureController != null ? captureController.mPreviewExposureTime : 10000000L);
                    int iso = (isoVal != null) ? isoVal : (captureController != null ? captureController.mPreviewIso : 100);
                    exposureStr = ExposureIndex.sec2string(ExposureIndex.time2sec(expTime)) + "s";
                    isoStr = String.valueOf(iso);
                } else {
                    IsoExpoSelector.ExpoPair expoPair = IsoExpoSelector.GenerateExpoPair(-1, captureController);
                    exposureStr = expoPair.ExposureString() + "s";
                    if (expoPair.isShutterTripodBypassed) {
                        exposureStr += " · UNLMT";
                    } else if (expoPair.isShutterLimited) {
                        exposureStr += " · LMT";
                    } else if (expoPair.isShutterManualOverLimit) {
                        exposureStr += " · >LMT";
                    }

                    isoStr = String.valueOf(expoPair.iso);
                    if (expoPair.isIsoLimited) {
                        isoStr += " · LMT";
                    } else if (expoPair.isIsoManualOverLimit) {
                        isoStr += " · >LMT";
                    }
                }

                LinkedHashMap<String, String> stringMap = new LinkedHashMap<>();
                stringMap.put("AF_MODE", getResultFieldName("CONTROL_AF_MODE_", result.get(CaptureResult.CONTROL_AF_MODE)));
                stringMap.put("AF_TRIGGER", getResultFieldName("CONTROL_AF_TRIGGER_", result.get(CaptureResult.CONTROL_AF_TRIGGER)));
                stringMap.put("AF_STATE", getResultFieldName("CONTROL_AF_STATE_", result.get(CaptureResult.CONTROL_AF_STATE)));
                stringMap.put("AE_MODE", getResultFieldName("CONTROL_AE_MODE_", result.get(CaptureResult.CONTROL_AE_MODE)));
                stringMap.put("FLASH_MODE", getResultFieldName("FLASH_MODE_", result.get(CaptureResult.FLASH_MODE)));
                stringMap.put("FOCUS_DISTANCE", String.valueOf(result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
                stringMap.put("EXPOSURE_TIME", exposureStr);
                stringMap.put("ISO", isoStr);
                stringMap.put("Shakiness", String.valueOf(PhotonCamera.getGyro().getShakiness()));
                stringMap.put("TripodShakiness", String.valueOf(PhotonCamera.getGyro().tripodShakiness));
                stringMap.put("Tripod", String.valueOf(PhotonCamera.getGyro().getTripod()));
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
                if (mViewfinderHudView != null) {
                    mViewfinderHudView.clear();
                }
            }
        });
    }

    private long lastHudUpdateTime = 0;
    private static final long HUD_UPDATE_INTERVAL_MS = 150; // Smooth 6.6 Hz update rate to eliminate jitter

    private void updateViewfinderHud(CaptureResult result, int afDataMode) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHudUpdateTime < HUD_UPDATE_INTERVAL_MS) {
            return; // Damping: skip intermediate frame fluctuations to keep HUD rock-steady
        }
        lastHudUpdateTime = now;

        String exposureStr;
        String isoStr;

        // In ZSL mode (Motion without HDR), frames are sourced from live preview stream (hardware AE).
        // In non-ZSL modes (Photo, Night, HDR), frames are shot using IsoExpoSelector's manual calculation.
        boolean isZsl = (captureController != null && captureController.isZslMode());

        if (isZsl) {
            Long expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            long expTime = (expNs != null) ? expNs : (captureController != null ? captureController.mPreviewExposureTime : 10000000L);
            int iso = (isoVal != null) ? isoVal : (captureController != null ? captureController.mPreviewIso : 100);

            exposureStr = ExposureIndex.sec2string(ExposureIndex.time2sec(expTime)) + "s";
            isoStr = "ISO " + iso;
        } else {
            IsoExpoSelector.ExpoPair expoPair = IsoExpoSelector.GenerateExpoPair(-1, captureController);
            exposureStr = expoPair.ExposureString() + "s";
            if (expoPair.isShutterTripodBypassed) {
                exposureStr += " · UNLMT";
            } else if (expoPair.isShutterLimited) {
                exposureStr += " · LMT";
            } else if (expoPair.isShutterManualOverLimit) {
                exposureStr += " · >LMT";
            }

            isoStr = "ISO " + expoPair.iso;
            if (expoPair.isIsoLimited) {
                isoStr += " · LMT";
            } else if (expoPair.isIsoManualOverLimit) {
                isoStr += " · >LMT";
            }
        }

        // 35mm equivalent focal length & aperture calculation (Lens specs)
        int eqFocalLength = 24;
        float aperture = 1.8f;
        CameraCharacteristics chars = CaptureController.mCameraCharacteristics;
        if (chars != null) {
            float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float fl = (focalLengths != null && focalLengths.length > 0) ? focalLengths[0] : 4.75f;
            float zoom = 1.0f;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Float zr = result.get(CaptureResult.CONTROL_ZOOM_RATIO);
                if (zr != null) zoom = zr;
            } else {
                Rect crop = result.get(CaptureResult.SCALER_CROP_REGION);
                Rect activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (crop != null && activeArray != null && crop.width() > 0) {
                    zoom = (float) activeArray.width() / crop.width();
                }
            }
            if (sensorSize != null && sensorSize.getWidth() > 0) {
                eqFocalLength = Math.round((36.0f / sensorSize.getWidth()) * fl * zoom);
            }

            Float apVal = result.get(CaptureResult.LENS_APERTURE);
            if (apVal != null && apVal > 0.0f) {
                aperture = apVal;
            } else {
                float[] apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
                if (apertures != null && apertures.length > 0) {
                    aperture = apertures[0];
                }
            }
        }
        String lensStr = eqFocalLength + "mm · f/" + String.format(Locale.ROOT, "%.1f", aperture);

        // Focus mode, dynamic AF state & manual distance
        String focusStr;
        Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
        Float focusDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
        boolean isManual = (manualModeConsole != null && manualModeConsole.isManualFocusModeActive())
                || (afMode != null && afMode == CaptureRequest.CONTROL_AF_MODE_OFF);

        if (isManual) {
            if (focusDist == null || focusDist == 0.0f) {
                focusStr = "MF · ∞";
            } else {
                float meters = 1.0f / focusDist;
                focusStr = String.format(Locale.ROOT, "MF · %.1fm", meters);
            }
        } else {
            String modePrefix;
            if (afMode != null && afMode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                modePrefix = "AF-S";
            } else if (afMode != null && afMode == CaptureRequest.CONTROL_AF_MODE_MACRO) {
                modePrefix = "AF-Macro";
            } else {
                modePrefix = "AF-C";
            }

            if (afState != null) {
                switch (afState) {
                    case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                    case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                        focusStr = modePrefix + " · SCAN";
                        break;
                    case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                    case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                        focusStr = modePrefix + " · LOCK";
                        break;
                    case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                        focusStr = modePrefix + " · FAIL";
                        break;
                    case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                        focusStr = modePrefix + " · LOST";
                        break;
                    default:
                        focusStr = modePrefix;
                        break;
                }
            } else {
                focusStr = modePrefix;
            }
        }

        // White Balance / CCT in Kelvin
        String wbStr = calculateWhitebalanceString(result);

        // Tripod indicator
        boolean isTripod = PhotonCamera.getGyro() != null && PhotonCamera.getGyro().getTripod();

        // OIS hardware & runtime active status
        boolean oisSupported = false;
        boolean oisActive = true;
        if (chars != null) {
            int[] stabModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
            oisSupported = (stabModes != null && stabModes.length > 1);
            if (oisSupported && captureController != null) {
                int oisMode = captureController.oisMode;
                if (oisMode == 2) {
                    oisActive = false;
                } else if (oisMode == 1) {
                    CameraMode curMode = PhotonCamera.getSettings().selectedMode;
                    boolean isContinuous = (curMode == CameraMode.UNLIMITED);
                    oisActive = !(isTripod || isContinuous);
                } else {
                    oisActive = true;
                }
            }
        }

        if (mViewfinderHudView != null) {
            mViewfinderHudView.setHudData(exposureStr, isoStr, lensStr, focusStr, wbStr, isTripod, oisSupported, oisActive);
        }

        // Trigger live histogram sampling if mode 2 (HUD + Histogram) is active
        if (afDataMode == 2) {
            requestLiveHistogram();
        }
    }

    private String calculateWhitebalanceString(CaptureResult result) {
        ParamController paramController = (captureController != null)
                ? captureController.getParamController() : null;
        int wbVal = (paramController != null) ? paramController.WB : 0;

        // 1. Spot White Balance (SWB) with live Tint indicator (e.g. "SWB · 4400K · G+1.2")
        if (paramController != null && paramController.isSpotWb && wbVal >= 2000) {
            String tint = paramController.spotTintStr;
            return "SWB · " + wbVal + "K" + (tint.isEmpty() ? "" : " · " + tint);
        }

        // 2. Manual Kelvin (MWB) dialed via knob
        if (wbVal >= 2000) {
            return "MWB · " + wbVal + "K";
        }

        // 3. Dynamic Auto White Balance (AWB) from live sensor neutral point
        android.util.Rational[] neutralPoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        if (neutralPoint == null || neutralPoint.length < 3) {
            if (captureController != null) {
                neutralPoint = captureController.mPreviewTemp;
            }
        }

        if (neutralPoint != null && neutralPoint.length >= 3) {
            int liveKelvin = ColorTemperatureConverter.neutralPointToKelvin(neutralPoint);
            return "AWB · " + liveKelvin + "K";
        }

        return "AWB";
    }

    private Bitmap mHistBitmap = null;
    private long lastHistTime = 0;
    private static final long HIST_INTERVAL_MS = 120; // 8.3 Hz sampling rate for zero CPU load
    private final int[] mHistPixels = new int[128 * 96];
    private final int[][] mHistData = new int[3][64];

    private void requestLiveHistogram() {
        if (textureView == null || surfaceView == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHistTime < HIST_INTERVAL_MS) {
            return;
        }
        lastHistTime = now;

        if (mHistBitmap == null) {
            mHistBitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.view.PixelCopy.request(textureView, mHistBitmap, copyResult -> {
                    if (copyResult == android.view.PixelCopy.SUCCESS) {
                        processExecutorService.execute(this::processHistogramData);
                    }
                }, surfaceView.getHandler() != null ? surfaceView.getHandler() : new android.os.Handler(android.os.Looper.getMainLooper()));
            } catch (Exception ignored) {
            }
        }
    }

    private void processHistogramData() {
        if (mHistBitmap == null || mHistBitmap.isRecycled()) return;
        int w = mHistBitmap.getWidth();
        int h = mHistBitmap.getHeight();
        int size = 64;
        mHistBitmap.getPixels(mHistPixels, 0, w, 0, 0, w, h);

        // Clear previous histogram bins
        for (int i = 0; i < 3; i++) {
            Arrays.fill(mHistData[i], 0);
        }

        int total = w * h;
        for (int i = 0; i < total; i += 2) { // 2x subsampling for maximum performance
            int c = mHistPixels[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            // Mathematically neutralize artificial magenta focus peaking boost (delta added equally to R and B)
            int delta = Math.max(0, Math.min(r - g, b - g));
            int cleanR = r - delta;
            int cleanB = b - delta;

            mHistData[0][cleanR * size / 256]++;
            mHistData[1][g * size / 256]++;
            mHistData[2][cleanB * size / 256]++;
        }

        // Square-root compression as in original Histogram.java
        int maxY = 1;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < size; j++) {
                mHistData[i][j] = (int) Math.sqrt(mHistData[i][j]);
                if (mHistData[i][j] > maxY) {
                    maxY = mHistData[i][j];
                }
            }
        }

        final int calculatedMaxY = maxY;
        if (mViewfinderHudView != null) {
            mViewfinderHudView.post(() -> {
                mViewfinderHudView.setHistogramData(mHistData, calculatedMaxY, size);
            });
        }
    }

    private RectF getScreenRectFromMeteringRect(MeteringRectangle meteringRectangle) {
        if (captureController.mImageReaderPreview == null) return new RectF();
        Size size = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        if (size == null) {
            size = new Size(captureController.mImageReaderPreview.getWidth(), captureController.mImageReaderPreview.getHeight());
        }
        float left = (((float) meteringRectangle.getY() / size.getHeight()) * (textureView.getWidth()));
        float top = (((float) meteringRectangle.getX() / size.getWidth()) * (textureView.getHeight()));
        float width = (((float) meteringRectangle.getHeight() / size.getHeight()) * (textureView.getWidth()));
        float height = (((float) meteringRectangle.getWidth() / size.getWidth()) * (textureView.getHeight()));
        //left = textureView.getWidth() - left;
        return new RectF(
                //meteringRectangle.getY()-left, //Left
                textureView.getWidth()-left-width,//Right
                top,  //Top
                //meteringRectangle.getY() - (left + width),//Right
                textureView.getWidth()-left,
                top + height //Bottom
        );
    }

    private String getResultFieldName(String prefix, Integer value) {
        if(value == null) return "";
        for (Field f : this.metadataFields)
            if (f.getName().startsWith(prefix)) {
                try {
                    if (f.getInt(f) == value)
                        return f.getName().replace(prefix, "").concat("(" + value + ")");
                } catch (Exception e) {
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
     * Adjusts the relative position of layout_top-bar and camera_container (= viewfinder + rest of the buttons excluding layout_topbar)
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
            DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
            float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
            float dpWidth = displayMetrics.widthPixels / displayMetrics.density;

            float dpmargin = (dpHeight - (dpWidth / 9f * 16f));
            ConstraintLayout.LayoutParams layout_topbarLP = ((ConstraintLayout.LayoutParams) activity_layout.findViewById(R.id.layout_topbar).getLayoutParams());

            layout_topbarLP.topMargin = (int) dpmargin;
            camera_containerLP.bottomMargin = (int) dpmargin;
            camera_containerLP.topToTop = -1;
            camera_containerLP.topToBottom = R.id.layout_topbar;
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
        this.mCameraLensDataMap = manager2.getCameraLensDataMap();
        // Re-anchor sActiveBackCamId / sActiveFrontCamId to real cameras.
        // The static defaults ("0" / "1") may not exist on every device (e.g. devices
        // whose camera IDs start at 1).  After a full process restart there is no
        // savedInstanceState to restore them, so we must derive them from the map here.
        if (!mCameraLensDataMap.containsKey(sActiveBackCamId)) {
            for (Map.Entry<String, CameraLensData> entry : mCameraLensDataMap.entrySet()) {
                if (entry.getValue().getFacing() == CameraCharacteristics.LENS_FACING_BACK) {
                    sActiveBackCamId = entry.getKey();
                    break;
                }
            }
        }
        if (!mCameraLensDataMap.containsKey(sActiveFrontCamId)) {
            for (Map.Entry<String, CameraLensData> entry : mCameraLensDataMap.entrySet()) {
                if (entry.getValue().getFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
                    sActiveFrontCamId = entry.getKey();
                    break;
                }
            }
        }
    }

    public String cycler(String savedCameraID) {
        if (Objects.requireNonNull(mCameraLensDataMap.get(savedCameraID)).getFacing() == CameraCharacteristics.LENS_FACING_BACK) {
            sActiveBackCamId = savedCameraID;
            return sActiveFrontCamId;
        } else {
            sActiveFrontCamId = savedCameraID;
            return sActiveBackCamId;
        }
    }

    public void triggerMediaScanner(Uri imageUri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//        Bitmap bitmap = BitmapDecoder.from(Uri.fromFile(imageToSave)).scaleBy(0.1f).decode();
        mediaScanIntent.setData(imageUri);
        if (activity != null)
            activity.sendBroadcast(mediaScanIntent);
    }

    public void launchGallery() {
        Intent galleryIntent = new Intent(activity, GalleryActivity.class);
        // Create gallery bundle
        galleryIntent.putExtra("CameraFragment", true);
        
        startActivity(galleryIntent, null);
    }

    public void launchSettings() {
        Intent settingsIntent = new Intent(activity, SettingsActivity.class);
        // Pass current camera mode to settings
        settingsIntent.putExtra("camera_mode", PreferenceKeys.getCameraModeOrdinal());
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
        NotificationChannel channel = new NotificationChannel
                (NOTIFICATION_CHANNEL_ID, "NotificationChannel", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
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
            if (PhotonCamera.getSettings().selectedMode == CameraMode.RAWVIDEO
                    && obj instanceof com.particlesdevs.photoncamera.processing.processor.RawVideoProcessor.RawVideoStats) {
                com.particlesdevs.photoncamera.processing.processor.RawVideoProcessor.RawVideoStats stats =
                        (com.particlesdevs.photoncamera.processing.processor.RawVideoProcessor.RawVideoStats) obj;
                timerFrameCountViewModel.setFrameTimeCnt(
                        new TimerFrameCountViewModel.FrameCntTime(stats.pendingWrites, 0, 0));
                mCameraUIView.updateVideoRecordingInfo(stats.elapsedMs, stats.estimatedBytes, stats.availableBytes);
            }
        }

        @Override
        public void onProcessingFinished(Object obj) {
            logD("onProcessingFinished: " + obj);
            mCameraUIView.setProcessingProgressBarIndeterminate(false);
            mCameraUIView.activateShutterButton(true);
            mCameraUIView.lockUIForBurst(false);
            stopNotification();

        }

        @Override
        public void notifyImageSavedStatus(boolean saved, Path savedFilePath) {
            if (saved) {
                Uri imageUri = null;
                if (savedFilePath != null) {
                    triggerMediaScanner(imageUri = Uri.fromFile(savedFilePath.toFile()));
                    logD("ImageSaved: " + savedFilePath);
//                    showSnackBar("ImageSaved: " + savedFilePath.toString());
                }
                cameraFragmentViewModel.updateGalleryThumb(imageUri);
            } else {
                logE("ImageSavingError");
                showSnackBar("ImageSavingError");
            }
        }

        @Override
        public void onProcessingError(Object obj) {
            if (obj instanceof String)
                showToast((String) obj);
            mCameraUIView.lockUIForBurst(false);
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
            if (PhotonCamera.getSettings().selectedMode != CameraMode.RAWVIDEO) {
                mCameraUIView.setCaptureProgressBarOpacity(1.0f);
                mCameraUIView.lockUIForBurst(true);
            }
            //textureView.post(() -> textureView.setAlpha(0.8f));
        }

        private long prevPlayTime = 0;
        @Override
        public void onFrameCaptureStarted(Object o) {
            long seekDelay = 50;
            if(prevPlayTime + seekDelay < System.currentTimeMillis()){
                prevPlayTime = System.currentTimeMillis();
                MediaPlayer player = burstPlayer;
                if (player != null) {
                    player.seekTo(0);
                }
            }
        }

        @Override
        public void onBurstPrepared(Object o) {
        }
        @Override
        public void onFrameCaptureProgressed(Object o) {
        }

        @Override
        public void onFrameCaptureCompleted(Object o) {
            if (PhotonCamera.getSettings().selectedMode != CameraMode.RAWVIDEO) {
                mCameraUIView.incrementCaptureProgressBar(1);
                if (PreferenceKeys.isCameraSoundsOn()) {
                    MediaPlayer player = burstPlayer;
                    if (player != null) {
                        player.start();
                    }
                }
                if (o instanceof TimerFrameCountViewModel.FrameCntTime) {
                    timerFrameCountViewModel.setFrameTimeCnt((TimerFrameCountViewModel.FrameCntTime) o);
                }
            }
        }

        @Override
        public void onCaptureSequenceCompleted(Object o) {
            if (PreferenceKeys.isCameraSoundsOn()) {
                MediaPlayer player = endPlayer;
                if (player != null) {
                    player.start();
                }
            }
            timerFrameCountViewModel.clearFrameTimeCnt();
            mCameraUIView.resetCaptureProgressBar();
            mCameraUIView.lockUIForBurst(false);
            mCameraUIView.setVideoRecordingInfoVisible(false);
            textureView.post(() -> textureView.setAlpha(1f));
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
            auxButtonsViewModel.initCameraLists(mCameraLensDataMap);
        }

        @Override
        public void onCameraRestarted() {
            surfaceView.clear();
            if (mViewfinderHudView != null) mViewfinderHudView.clear();
            mCameraUIView.refresh(CaptureController.isProcessing);
            mTouchFocus.resetFocusCircle();
        }

        @Override
        public void onCharacteristicsUpdated(CameraCharacteristics characteristics) {
            surfaceView.clear();
            if (mViewfinderHudView != null) mViewfinderHudView.clear();
            auxButtonsViewModel.setActiveId(PreferenceKeys.getCameraID());
            Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mCameraUIView.showFlashButton(flashAvailable != null && flashAvailable);
            manualModeConsole.setPreserveManualWb(PreferenceKeys.isPreserveManualWbOn());
            manualModeConsole.init(activity, characteristics);
            captureController.setManualModeConsole(manualModeConsole);
            manualModeConsole.onResume();
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
        public void onRequestTriggerMediaScanner(Uri fileUri) {
            triggerMediaScanner(fileUri);
        }
    }


}