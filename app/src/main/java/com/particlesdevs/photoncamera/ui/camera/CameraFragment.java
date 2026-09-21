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
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
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
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.PreviewScopeAnalyzer;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.ViewfinderHudView;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.manual.ManualAutoValues;
import com.particlesdevs.photoncamera.manual.ParamController;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.view.LayoutInflater;
import android.view.View;
import android.animation.LayoutTransition;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.Observable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.particlesdevs.photoncamera.BR;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraEventsListener;
import com.particlesdevs.photoncamera.api.CameraManager2;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.CameraReflectionApi;
import com.particlesdevs.photoncamera.api.LogicalCameraResolver;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.capture.CaptureEventsListener;
import com.particlesdevs.photoncamera.circularbarlib.api.ManualInstanceProvider;
import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;
import com.particlesdevs.photoncamera.circularbarlib.console.ManualModeConsoleImpl;
import com.particlesdevs.photoncamera.circularbarlib.model.ManualModeModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.Binding;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.ManualPaletteBackground;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.control.LocationProvider;
import com.particlesdevs.photoncamera.control.Swipe;
import com.particlesdevs.photoncamera.control.TouchFocus;
import com.particlesdevs.photoncamera.control.Vibration;
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
import com.particlesdevs.photoncamera.ui.camera.model.CameraFragmentModel;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.*;
import com.particlesdevs.photoncamera.ui.camera.views.LensZoomBarController;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.ViewfinderEdgeBlurController;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.MainRenderer;
import com.particlesdevs.photoncamera.ui.camera.views.settingsbar.SettingsBarLayout;
import com.particlesdevs.photoncamera.util.BlurSupport;
import com.particlesdevs.photoncamera.util.FocalEquivalence;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.GLPreview;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.SurfaceViewOverViewfinder;
import com.particlesdevs.photoncamera.ui.settings.SettingsActivity;
import com.particlesdevs.photoncamera.util.SecureCameraHelper;
import com.particlesdevs.photoncamera.util.SystemBarsHelper;
import com.particlesdevs.photoncamera.util.log.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Observer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CameraFragment extends Fragment {
    public static final int REQUEST_CAMERA_PERMISSION = 1;
    /** Request code for the lockscreen gallery device-credential confirmation. */
    private static final int REQUEST_UNLOCK_GALLERY = 9001;
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
    /** Logical members driving the pill/zoom in video logical mode (empty when inactive). */
    private List<com.particlesdevs.photoncamera.api.LogicalCameraResolver.Member> mVideoLogicalMembers =
            new ArrayList<>();
    private volatile float mActiveLens35mm;
    public Activity activity;
    private TimerFrameCountViewModel timerFrameCountViewModel;
    private CameraUIView mCameraUIView;
    private CameraUIController mCameraUIEventsListener;

    public CameraUIView getCameraUIView() {
        return mCameraUIView;
    }    public CaptureController captureController;
    private CameraFragmentViewModel cameraFragmentViewModel;
    public AuxButtonsViewModel auxButtonsViewModel;
    public CameraFragmentBinding cameraFragmentBinding;
    private TouchFocus mTouchFocus;
    public Swipe mSwipe;
    private LensZoomBarController lensZoomBarController;
    private ViewfinderEdgeBlurController edgeBlurController;
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
    /**
     * True while running over the keyguard. Gallery shows no pre-lock content and
     * settings entry points are disabled until {@link #onSecureSessionChanged(boolean)}
     * clears it after unlock.
     */
    private boolean secureSession = false;

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
        if (activity instanceof CameraActivity) {
            secureSession = ((CameraActivity) activity).isSecureSession();
        }
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
        manualPanelObserver = (observable, arg) -> {
            if (arg == ManualModeModel.ManualModelFields.PANEL_VISIBILITY) {
                updateBackIntercept();
            }
        };
        ((ManualModeConsoleImpl) manualModeConsole).getManualModeModel().addObserver(manualPanelObserver);
        settingsBarEntryProvider = new ViewModelProvider(this).get(SettingsBarEntryProvider.class);
        auxButtonsViewModel = new ViewModelProvider(this).get(AuxButtonsViewModel.class);
        surfaceView = cameraFragmentBinding.layoutViewfinder.surfaceView;
        textureView = cameraFragmentBinding.texture;
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
        // System back closes any in-fragment surface; otherwise the dispatcher falls
        // through to the default behaviour (predictive-back compatible).
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), cameraBackCallback);
        updateBackIntercept();
        // Keep the shutter/mode bottom bar above the transparent navigation bar.
        // The viewfinder itself stays full-bleed behind it.
        View bottomBar = view.findViewById(R.id.layout_bottombar);
        if (bottomBar != null) {
            SystemBarsHelper.padBottomForNavBar(bottomBar);
        }
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
        this.lensZoomBarController = new LensZoomBarController(
                cameraFragmentBinding.cameraContainer,
                cameraFragmentBinding.lensZoomBar,
                cameraFragmentBinding.auxButtonsContainer,
                cameraFragmentBinding.zoomSliderContainer,
                cameraFragmentBinding.zoomSlider,
                cameraFragmentBinding.zoomLockPill,
                cameraFragmentBinding.zoomLockButton,
                captureController,
                cameraFragmentViewModel);
        lensZoomBarController.init();
        // Preview surface geometry: frame-sized by default, full-bleed with
        // mirrored edge-blur bands when the option is on.
        this.edgeBlurController = new ViewfinderEdgeBlurController(
                cameraFragmentBinding.textureHolder,
                cameraFragmentBinding.texture,
                cameraFragmentBinding.layoutViewfinder.viewfinderFrame);
        cameraFragmentBinding.textureHolder.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> edgeBlurController.update());
        cameraFragmentBinding.layoutViewfinder.viewfinderFrame.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> edgeBlurController.update());
        edgeBlurController.setEnabled(PreferenceKeys.isBlurViewfinderEdgesOn());
        textureView.setRoundCorners(PreferenceKeys.isRoundEdgeOn());
        mSwipe.setZoomGestureListener(lensZoomBarController);
        cameraFragmentViewModel.getCameraFragmentModel().addOnPropertyChangedCallback(
                new Observable.OnPropertyChangedCallback() {
                    @Override
                    public void onPropertyChanged(Observable sender, int propertyId) {
                        if (lensZoomBarController == null) return;
                        CameraFragmentModel model = (CameraFragmentModel) sender;
                        if (propertyId == BR._all || propertyId == BR.zoomRatio) {
                            lensZoomBarController.onZoomChanged(model.getZoomRatio());
                        }
                        if (propertyId == BR._all || propertyId == BR.settingsBarVisibility) {
                            lensZoomBarController.setSettingsHidden(model.isSettingsBarVisibility());
                            applyManualDomeHeight();
                            updateBackIntercept();
                        }
                    }
                });
        var gyro = PhotonCamera.getGyro();
        if ((mHorizonIndicatorView != null) && (gyro != null)) {
            mHorizonIndicatorView.updateDisplayRotation(getCameraFragmentViewModel().getCameraFragmentModel().getOrientation());
            mHorizonIndicatorView.setGyro(gyro);
        }
        if (mHorizonIndicatorView != null) {
            mHorizonIndicatorView.setVisible(PreferenceKeys.isHorizonOn());
        }
        manualPanelRoot = view.findViewById(R.id.manual_mode);
        manualPanelBar = view.findViewById(R.id.buttons_container);
        manualKnobContainer = view.findViewById(R.id.knobViewContainer);
        manualKnobView = view.findViewById(R.id.knobView);
        // The burst ring and the frame timer hide through their alpha, which
        // capture callbacks manage. The viewfinder root's layout transition
        // would fade a reappearing child (returning from a video/raw-video
        // mode, where the mode state sets it GONE) up to alpha 1 and leave
        // the idle ring stuck visible; photo-to-photo switches change
        // nothing, so it then "heals". Disable only the appearing leg — the
        // disappearing fade the container was given stays intact.
        if (cameraFragmentBinding.layoutViewfinder.getRoot() instanceof ViewGroup) {
            LayoutTransition viewfinderTransitions =
                    ((ViewGroup) cameraFragmentBinding.layoutViewfinder.getRoot()).getLayoutTransition();
            if (viewfinderTransitions != null) {
                viewfinderTransitions.disableTransitionType(LayoutTransition.APPEARING);
            }
        }
        camPanelCornerPx = getResources().getDimension(R.dimen.cam_panel_corner_radius);
        camPanelBlurPx = getResources().getDimension(R.dimen.cam_panel_blur_radius);
        if (manualPanelBar != null) {
            // One drawable owns the palette silhouette — the bubble with the
            // wheel's dome grown out of it — so the wheel inflates from the
            // bubble as a single shape. Geometry is mirrored in
            // shaders/preview/panel_blur_fs.glsl for the frosted blur.
            manualPanelBar.setBackground(new ManualPaletteBackground(
                    ContextCompat.getColor(requireContext(), R.color.cam_panel_scrim),
                    camPanelCornerPx,
                    getResources().getDimension(com.particlesdevs.photoncamera.circularbarlib.R.dimen.manual_knob_height)));
            // The bar's bounds include the dome zone; keep the reveal/predictive
            // back scale pivoted on the visible bubble.
            manualPanelBar.post(() -> Binding.pinOptionBarPivot(manualPanelBar));
        }
        // The dome tracks the preview area so the disc reads as ~1/3 of the
        // viewfinder on every device and mode (the dummy view's height is the
        // visible preview above the bottom bar).
        cameraFragmentBinding.dummyReferenceView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        applyManualDomeHeight());
        cameraFragmentBinding.settingsBar.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        applyManualDomeHeight());
        view.post(this::applyManualDomeHeight);
        textureView.postOnAnimation(panelBlurTracker);
        view.getViewTreeObserver().addOnPreDrawListener(lensOffsetCorrection);
        initSettingsBar();
        applySecureSessionUI();
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
        lensZoomBarController.applyPosition(PreferenceKeys.getLensBarPosition(), true);
        if (cameraFragmentBinding != null) {
            cameraFragmentBinding.auxButtonsContainer.refresh();
        }
        edgeBlurController.setEnabled(PreferenceKeys.isBlurViewfinderEdgesOn());
        textureView.setRoundCorners(PreferenceKeys.isRoundEdgeOn());
        mSwipe.init();
        this.mCameraUIView.refresh(CaptureController.isProcessing);
        final boolean lockedAtResume = secureSession;
        if (lockedAtResume) {
            // Never expose pre-lock library content on the gallery button while locked.
            cameraFragmentViewModel.clearGalleryThumb();
        }
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
            if (!lockedAtResume) {
                cameraFragmentViewModel.updateGalleryThumb(null);
            }
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
        updateLocationProvider();
    }

    /**
     * Runs the location provider only while the camera is open and "Save
     * location" is enabled with the permission granted. Called from onResume so
     * a permission granted/revoked in Settings applies when coming back.
     */
    private void updateLocationProvider() {
        LocationProvider provider = PhotonCamera.getLocationProvider();
        if (provider == null) {
            return;
        }
        Context context = getContext();
        if (context != null && PreferenceKeys.isSaveLocationOn()
                && LocationProvider.hasPermission(context)) {
            provider.start();
        } else {
            provider.stop();
        }
    }

    private void initTouchFocus() {
        if (cameraFragmentBinding != null && captureController != null) {
            View focusCircle = cameraFragmentBinding.layoutViewfinder.touchFocus;
            View spotWbIndicator = cameraFragmentBinding.layoutViewfinder.spotWbIndicator;
            View viewfinderFrame = cameraFragmentBinding.layoutViewfinder.viewfinderFrame;
            textureView.post(() -> {
                mTouchFocus = new TouchFocus(captureController, focusCircle, spotWbIndicator, viewfinderFrame);
                captureController.mTouchFocus = mTouchFocus;
            });
        }
    }

    @Override
    public void onPause() {
        LocationProvider locationProvider = PhotonCamera.getLocationProvider();
        if (locationProvider != null) {
            locationProvider.stop();
        }
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
        if (lensZoomBarController != null) lensZoomBarController.onPause();
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

    /** Corner radius of the rounded panels (settings bar, manual bar), cached. */
    private float camPanelCornerPx;
    /** Blur kernel radius shared by every panel, cached. */
    private float camPanelBlurPx;
    /** Manual-console hierarchy that carries a blurred backdrop while visible. */
    private View manualPanelRoot;
    private View manualPanelBar;
    private View manualKnobContainer;
    private KnobView manualKnobView;
    /** Applied manual wheel dome height in px, -1 until the first layout. */
    private float manualDomeHeightPx = -1f;
    /** Current translation applied to the lens cluster to offset layout jumps. */
    private float lensClusterOffset = Float.NaN;

    /**
     * Re-asserts the lens cluster offset after layout, so the drawn frame can
     * never show the offset from the pre-layout pass (which briefly dips the
     * cluster when the selector's space is removed on close).
     */
    private final ViewTreeObserver.OnPreDrawListener lensOffsetCorrection =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (syncLensClusterOffset()) {
                        updatePanelBlurSpecs();
                    }
                    return true;
                }
            };
    /** Scratch/applied spec lists so the renderer only receives real changes. */
    private final List<MainRenderer.PanelBlurSpec> scratchBlurSpecs = new ArrayList<>(8);
    private List<MainRenderer.PanelBlurSpec> appliedBlurSpecs;
    private boolean settingsBarScrimActive;

    /**
     * Keeps the preview's live blur regions aligned with every visible panel
     * (settings bar, lens/zoom pills, manual bar, knob wheel). Panels are
     * scaled/translated/rotated by their show-hide animations, the orientation
     * listener and predictive-back progress, so geometry is recomputed per frame.
     */
    private final Runnable panelBlurTracker = new Runnable() {
        @Override
        public void run() {
            if (getView() == null || textureView == null) {
                return;
            }
            updatePanelBlurSpecs();
            textureView.postOnAnimation(panelBlurTracker);
        }
    };

    private void updatePanelBlurSpecs() {
        if (camPanelCornerPx == 0f) {
            camPanelCornerPx = getResources().getDimension(R.dimen.cam_panel_corner_radius);
            camPanelBlurPx = getResources().getDimension(R.dimen.cam_panel_blur_radius);
        }
        scratchBlurSpecs.clear();
        syncLensClusterOffset();
        // Without a live preview there is nothing to blur: the opaque panels stay,
        // and the next ticks keep polling so blur returns if the preview restarts.
        if (textureView.isAvailable()) {
            addRoundedBlurSpec(cameraFragmentBinding.settingsBar, camPanelCornerPx,
                    cameraFragmentBinding.settingsBar.getAlpha());
            addPillBlurSpec(cameraFragmentBinding.lensZoomBar);
            addPillBlurSpec(cameraFragmentBinding.zoomSliderContainer);
            addPillBlurSpec(cameraFragmentBinding.zoomIndicator);
            addPillBlurSpec(cameraFragmentBinding.zoomLockPill);
            // The manual hierarchy carries its own alpha/transform on the root
            // (show/hide slides it down and fades it out, leaving children's
            // visibility untouched), so the region gates on the root too. One
            // region covers the merged bubble+dome the palette background
            // draws, dome included whenever it is grown.
            if (manualPanelRoot != null && manualPanelRoot.getVisibility() == View.VISIBLE) {
                float manualAlpha = manualPanelRoot.getAlpha();
                addPaletteBlurSpec(manualPanelBar, manualAlpha);
            }
        }
        boolean settingsScrim = textureView.isAvailable()
                && cameraFragmentBinding.settingsBar.getVisibility() == View.VISIBLE;
        if (settingsBarScrimActive != settingsScrim) {
            cameraFragmentBinding.settingsBar.setBlurActive(settingsScrim);
            settingsBarScrimActive = settingsScrim;
        }
        if (sameBlurSpecs(scratchBlurSpecs, appliedBlurSpecs)) {
            return;
        }
        if (scratchBlurSpecs.isEmpty()) {
            appliedBlurSpecs = null;
            textureView.setPanelBlur(null);
        } else {
            appliedBlurSpecs = new ArrayList<>(scratchBlurSpecs);
            textureView.setPanelBlur(appliedBlurSpecs);
        }
    }

    /**
     * Lifts the lens cluster clear of the manual panel. The cluster is anchored
     * to the bottom bar (its layout never moves with the panel), so the whole
     * motion is this explicit translation: it follows the panel's own reveal
     * alpha — gliding on the same M3E curve as the panel — and the wheel's
     * alpha once the panel is fully in. Keeping the layout still means the
     * container's layout transition never animates the cluster, so there is no
     * second movement to fight. The blur specs read the translation, so the
     * pills' backdrops follow along.
     */
    private boolean syncLensClusterOffset() {
        float offset = 0f;
        if (manualPanelRoot != null && manualPanelRoot.getVisibility() == View.VISIBLE) {
            float panelHeight = manualPanelRoot.getHeight();
            if (panelHeight > 0f) {
                float knobArea = manualKnobContainer != null ? manualKnobContainer.getHeight() : 0f;
                float knobAlpha = manualKnobView != null && manualKnobView.getVisibility() == View.VISIBLE
                        ? clampAlpha(manualKnobView.getAlpha()) : 0f;
                float barArea = Math.max(0f, panelHeight - knobArea);
                offset = -(barArea + knobArea * knobAlpha) * clampAlpha(manualPanelRoot.getAlpha());
            }
        }
        if (!Float.isNaN(lensClusterOffset) && Math.abs(offset - lensClusterOffset) < 0.25f) {
            return false;
        }
        lensClusterOffset = offset;
        cameraFragmentBinding.lensZoomBar.setTranslationY(offset);
        cameraFragmentBinding.zoomLockPill.setTranslationY(offset);
        cameraFragmentBinding.zoomSliderContainer.setTranslationY(offset);
        cameraFragmentBinding.zoomIndicator.setTranslationY(offset);
        return true;
    }

    private static float clampAlpha(float alpha) {
        return Math.min(1f, Math.max(0f, alpha));
    }

    /**
     * Sizes the manual palette's wheel dome. The reference is always the 4:3
     * preview — the dummy view's width at the "3:4" ratio — so the disc is
     * identical in 4:3, 16:9 video and RAW video, where the mode's dummy
     * aspect differs. The result is clamped to the space above the bottom bar
     * so the palette (dome + option bar) can never be measured short. The
     * palette's reserved dome zone, the wheel's container and the bubble
     * background all take the same height; the blur region and the
     * lens-cluster lift read them back.
     */
    private void applyManualDomeHeight() {
        if (manualPanelBar == null || manualKnobContainer == null) {
            return;
        }
        int previewWidth = cameraFragmentBinding.dummyReferenceView.getWidth();
        if (previewWidth <= 0) {
            return;
        }
        // 4:3 preview height: the dummy's "3:4" ratio gives height = 4/3 width.
        float reference = previewWidth * 4f / 3f;
        // The quick settings bar overlays the preview from the bottom; while it
        // is open the disc must fit the viewfinder that stays visible above it.
        if (cameraFragmentBinding.getUimodel() != null
                && cameraFragmentBinding.getUimodel().isSettingsBarVisibility()) {
            int visibleTop = cameraFragmentBinding.settingsBar.getTop();
            if (visibleTop > 0 && visibleTop < reference) {
                reference = visibleTop;
            }
        }
        float minDome = getResources().getDimension(R.dimen.manual_dome_min_height);
        float domePx = Math.max(reference / 5.75f, minDome);
        // The palette is anchored at the bottom bar's top and grows upward; cap
        // the dome so dome + option bar always fits the container above it.
        int bottomBarTop = cameraFragmentBinding.layoutBottombar.getRoot().getTop();
        if (bottomBarTop > 0) {
            int optionBarHeight = manualPanelBar.getHeight() - manualKnobContainer.getHeight();
            float maxDome = bottomBarTop - Math.max(optionBarHeight, 0);
            if (maxDome > minDome) {
                domePx = Math.min(domePx, maxDome);
            }
        }
        if (Math.abs(domePx - manualDomeHeightPx) < 1f) {
            return;
        }
        manualDomeHeightPx = domePx;
        ViewGroup.LayoutParams params = manualKnobContainer.getLayoutParams();
        if (params != null) {
            params.height = (int) domePx;
            manualKnobContainer.setLayoutParams(params);
        }
        manualPanelBar.setPadding(manualPanelBar.getPaddingLeft(), (int) domePx,
                manualPanelBar.getPaddingRight(), manualPanelBar.getPaddingBottom());
        if (manualPanelBar.getBackground() instanceof ManualPaletteBackground) {
            ((ManualPaletteBackground) manualPanelBar.getBackground()).setDomeHeightPx(domePx);
        }
        manualPanelBar.post(() -> Binding.pinOptionBarPivot(manualPanelBar));
    }

    /**
     * Snaps the manual palette's reveal to its shown end state. A mode switch
     * while the palette is open can leave the option-bar scale or the dome
     * inflation mid-flight, which reads as a squashed bar/wheel and drops the
     * blur behind it.
     */
    void reassertManualPanelState() {
        if (manualPanelRoot == null || manualPanelBar == null) {
            return;
        }
        manualPanelRoot.post(() -> {
            if (manualPanelRoot.getVisibility() != View.VISIBLE) {
                return;
            }
            manualPanelRoot.setAlpha(1f);
            manualPanelRoot.setTranslationY(0f);
            manualPanelBar.setScaleX(1f);
            manualPanelBar.setScaleY(1f);
            Binding.pinOptionBarPivot(manualPanelBar);
            boolean wheelVisible = manualKnobView != null
                    && manualKnobView.getVisibility() == View.VISIBLE;
            if (manualPanelBar.getBackground() instanceof ManualPaletteBackground) {
                ((ManualPaletteBackground) manualPanelBar.getBackground())
                        .setDomeProgress(wheelVisible ? 1f : 0f);
            }
            if (wheelVisible) {
                manualKnobView.setAlpha(1f);
                manualKnobView.setDomeProgress(1f);
            }
            applyManualDomeHeight();
            updatePanelBlurSpecs();
        });
    }

    /** Adds a rounded-rect blur region for {@code view}, or nothing when hidden/empty. */
    private void addRoundedBlurSpec(View view, float cornerRadiusPx, float alpha) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        addBlurSpec(view, alpha, cornerRadiusPx, false);
    }

    /** Pill-shaped region (corner radius = half its smaller extent). */
    private void addPillBlurSpec(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        addBlurSpec(view, view.getAlpha(),
                Math.min(view.getWidth() * view.getScaleX(),
                        view.getHeight() * view.getScaleY()) / 2f, true);
    }

    /**
     * One region for the manual palette's merged bubble+dome silhouette. The
     * pill top line is the reserved dome zone (it never moves), and the dome
     * height follows the palette background's inflation so the frosted region
     * grows with the shape; zero collapses it to the plain bubble — never the
     * empty dome zone above it.
     */
    private void addPaletteBlurSpec(View bar, float parentAlpha) {
        if (bar == null || bar.getVisibility() != View.VISIBLE
                || !(bar.getBackground() instanceof ManualPaletteBackground)) {
            return;
        }
        ManualPaletteBackground palette = (ManualPaletteBackground) bar.getBackground();
        addBlurSpec(bar, parentAlpha, palette.getCornerRadiusPx(), false,
                palette.getDomeHeightPx(), palette.getEffectiveDomeHeightPx(),
                palette.getEffectiveShoulderRadiusPx());
    }

    /**
     * Computes the panel's on-screen geometry and stores one blur region.
     * {@code cornerRadiusPx} is unscaled unless {@code radiusIsScaled} is set.
     */
    private void addBlurSpec(View view, float alpha, float cornerRadiusPx,
                             boolean radiusIsScaled) {
        addBlurSpec(view, alpha, cornerRadiusPx, radiusIsScaled, 0f, 0f, 0f);
    }

    /**
     * As above, optionally shaping the region like the palette background's
     * bubble whose top line sits {@code pillTopPx} below the panel's top, with
     * a dome of {@code domeHeightPx} blended in through shoulder arcs of
     * {@code shoulderRadiusPx} (all unscaled; zero pillTop disables the mode).
     */
    private void addBlurSpec(View view, float alpha, float cornerRadiusPx,
                             boolean radiusIsScaled, float pillTopPx,
                             float domeHeightPx, float shoulderRadiusPx) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0 || alpha <= 0.02f) {
            return;
        }
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        if (parent == null) {
            return;
        }
        int[] parentLocation = new int[2];
        parent.getLocationOnScreen(parentLocation);
        int[] previewLocation = new int[2];
        textureView.getLocationOnScreen(previewLocation);
        float scaleX = Math.max(0.0001f, view.getScaleX());
        float scaleY = Math.max(0.0001f, view.getScaleY());
        // The visible centre is the local centre transformed about the view's
        // pivot (scale then rotation) plus its translation, so off-centre
        // pivots (the lock pill scales about the lens pill) stay aligned.
        float pivotX = view.getPivotX();
        float pivotY = view.getPivotY();
        float dx = (view.getWidth() / 2f - pivotX) * scaleX;
        float dy = (view.getHeight() / 2f - pivotY) * scaleY;
        double rotationRad = Math.toRadians(view.getRotation());
        float cos = (float) Math.cos(rotationRad);
        float sin = (float) Math.sin(rotationRad);
        float centerX = parentLocation[0] + view.getLeft() + pivotX + dx * cos - dy * sin
                + view.getTranslationX() - previewLocation[0];
        float centerY = parentLocation[1] + view.getTop() + pivotY + dx * sin + dy * cos
                + view.getTranslationY() - previewLocation[1];
        float halfW = view.getWidth() * scaleX / 2f;
        float halfH = view.getHeight() * scaleY / 2f;
        scratchBlurSpecs.add(new MainRenderer.PanelBlurSpec(true,
                centerX, centerY, halfW, halfH, view.getRotation(),
                radiusIsScaled ? cornerRadiusPx : cornerRadiusPx * scaleX,
                camPanelBlurPx, alpha,
                pillTopPx * scaleY, domeHeightPx * scaleY,
                shoulderRadiusPx * Math.min(scaleX, scaleY)));
    }

    private static boolean sameBlurSpecs(List<MainRenderer.PanelBlurSpec> a,
                                         List<MainRenderer.PanelBlurSpec> b) {
        if (b == null) {
            return a.isEmpty();
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            MainRenderer.PanelBlurSpec s = a.get(i);
            MainRenderer.PanelBlurSpec t = b.get(i);
            if (changed(s.centerX, t.centerX) || changed(s.centerY, t.centerY)
                    || changed(s.halfW, t.halfW) || changed(s.halfH, t.halfH)
                    || changed(s.angle, t.angle) || changed(s.cornerRadius, t.cornerRadius)
                    || changedAlpha(s.alpha, t.alpha)
                    || changed(s.pillTop, t.pillTop)
                    || changed(s.domeHeight, t.domeHeight)
                    || changed(s.shoulderRadius, t.shoulderRadius)) {
                return false;
            }
        }
        return true;
    }

    private static boolean changed(float a, float b) {
        return Math.abs(a - b) > 0.25f;
    }

    /** Alpha needs a finer threshold so the backdrop fades smoothly with the panel. */
    private static boolean changedAlpha(float a, float b) {
        return Math.abs(a - b) > 0.02f;
    }

    /**
     * System-back handling. The callback is enabled only while an in-fragment
     * surface (settings bar or manual panel) is open; otherwise the dispatcher
     * falls through to the default activity behaviour.
     */
    private static final int BACK_TARGET_NONE = 0;
    private static final int BACK_TARGET_SETTINGS_BAR = 1;
    private static final int BACK_TARGET_MANUAL_PANEL = 2;

    private int backProgressTarget = BACK_TARGET_NONE;

    private final OnBackPressedCallback cameraBackCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
            // API 34+: remember which surface the gesture started on.
            backProgressTarget = BACK_TARGET_NONE;
            if (cameraFragmentViewModel != null && cameraFragmentViewModel.isSettingsBarVisible()) {
                backProgressTarget = BACK_TARGET_SETTINGS_BAR;
            } else if (manualModeConsole != null && manualModeConsole.isPanelVisible()) {
                backProgressTarget = BACK_TARGET_MANUAL_PANEL;
            }
        }

        @Override
        public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
            // API 34+: drive the dismiss transform without committing any state.
            float progress = Math.min(1f, Math.max(0f, backEvent.getProgress()));
            View target = backTargetView();
            if (target == null) {
                return;
            }
            if (backProgressTarget == BACK_TARGET_SETTINGS_BAR && target instanceof SettingsBarLayout) {
                ((SettingsBarLayout) target).setBackProgress(progress);
            } else if (backProgressTarget == BACK_TARGET_MANUAL_PANEL) {
                // Map the gesture onto the same reveal/hide transform as the
                // panel animation: fade + slide + option-bar collapse.
                float slide = target.getResources().getDimension(R.dimen.manual_panel_slide);
                target.setTranslationY(slide * progress);
                target.setAlpha(1f - progress);
                if (manualPanelBar != null) {
                    manualPanelBar.setScaleX(1f - progress);
                    manualPanelBar.setScaleY(1f - progress);
                }
            }
        }

        @Override
        public void handleOnBackCancelled() {
            // API 34+: restore the fully-open transform.
            View target = backTargetView();
            if (target instanceof SettingsBarLayout && backProgressTarget == BACK_TARGET_SETTINGS_BAR) {
                ((SettingsBarLayout) target).cancelBackProgress();
            } else if (target != null && backProgressTarget == BACK_TARGET_MANUAL_PANEL) {
                target.animate().setDuration(Motion.durationMedium2(target.getContext()))
                        .setInterpolator(Motion.emphasized(target.getContext()))
                        .alpha(1f).translationY(0f).start();
                if (manualPanelBar != null) {
                    manualPanelBar.animate().scaleX(1f).scaleY(1f)
                            .setDuration(Motion.durationMedium2(manualPanelBar.getContext()))
                            .setInterpolator(Motion.emphasized(manualPanelBar.getContext()))
                            .start();
                }
            }
            backProgressTarget = BACK_TARGET_NONE;
        }

        @Override
        public void handleOnBackPressed() {
            backProgressTarget = BACK_TARGET_NONE;
            if (cameraFragmentViewModel != null && cameraFragmentViewModel.isSettingsBarVisible()) {
                cameraFragmentViewModel.setSettingsBarVisible(false);
            }
            if (manualModeConsole != null && mSwipe != null && manualModeConsole.isPanelVisible()) {
                mSwipe.SwipeDown();
            }
        }
    };

    private View backTargetView() {
        if (cameraFragmentBinding == null) {
            return null;
        }
        if (backProgressTarget == BACK_TARGET_SETTINGS_BAR) {
            return cameraFragmentBinding.settingsBar;
        }
        if (backProgressTarget == BACK_TARGET_MANUAL_PANEL) {
            return cameraFragmentBinding.manualMode;
        }
        return null;
    }

    /** Enabled-state mirrors any open in-fragment surface, so back-to-home keeps the system animation when idle. */
    private void updateBackIntercept() {
        boolean intercept = (cameraFragmentViewModel != null && cameraFragmentViewModel.isSettingsBarVisible())
                || (manualModeConsole != null && manualModeConsole.isPanelVisible());
        cameraBackCallback.setEnabled(intercept);
    }

    private Observer manualPanelObserver;

    @Override
    public void onDestroyView() {
        if (textureView != null) {
            textureView.removeCallbacks(panelBlurTracker);
            textureView.setPanelBlur(null);
        }
        if (getView() != null) {
            getView().getViewTreeObserver().removeOnPreDrawListener(lensOffsetCorrection);
        }
        super.onDestroyView();
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
        if (manualModeConsole != null && manualPanelObserver != null) {
            ((ManualModeConsoleImpl) manualModeConsole).getManualModeModel().deleteObserver(manualPanelObserver);
            manualPanelObserver = null;
        }
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
            if (afDataMode == 1 || afDataMode == 2 || afDataMode == 4) {
                // Mode 1: HUD, Mode 2: HUD + Histogram, Mode 4: HUD + Waveform
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
            updateManualBarAutoValues(result);
        });
    }

    private long lastManualBarUpdateTime = 0;
    private static final long MANUAL_BAR_UPDATE_INTERVAL_MS = 150;

    /**
     * Pushes the live preview values of the auto-mode controls into the manual
     * panel labels (e.g. "A 800"); no work while the panel is closed. Runs on
     * the UI thread inside {@link #updateScreenLog(CaptureResult)}'s post.
     */
    private void updateManualBarAutoValues(CaptureResult result) {
        if (manualModeConsole == null || !manualModeConsole.isPanelVisible()) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastManualBarUpdateTime < MANUAL_BAR_UPDATE_INTERVAL_MS) return;
        lastManualBarUpdateTime = now;

        Long expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
        Float focusD = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
        if (captureController != null) {
            if (expNs == null) expNs = captureController.mPreviewExposureTime;
            if (iso == null) iso = captureController.mPreviewIso;
            if (focusD == null) focusD = captureController.mFocus;
        }

        Integer kelvin = null;
        try {
            Rational[] neutralPoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
            if ((neutralPoint == null || neutralPoint.length < 3) && captureController != null) {
                neutralPoint = captureController.mPreviewTemp;
            }
            if (neutralPoint != null && neutralPoint.length >= 3) {
                kelvin = ColorTemperatureConverter.neutralPointToKelvin(neutralPoint);
            }
        } catch (Exception ignored) {
        }

        manualModeConsole.setAutoValues(
                ManualAutoValues.formatFocus(focusD),
                ManualAutoValues.formatExposure(expNs),
                ManualAutoValues.formatIso(iso),
                ManualAutoValues.formatWb(kelvin));
    }

    private long lastHudUpdateTime = 0;
    private static final long HUD_UPDATE_INTERVAL_MS = 150; // Smooth 6.6 Hz update rate to eliminate jitter
    private long lastScreenLogMs = 0;

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
            float lens35mm = activeLens35mm(chars);
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
            if (lens35mm > 0f) {
                eqFocalLength = Math.round(lens35mm * zoom);
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

        // Trigger live scope sampling if mode 2 (histogram) or 4 (waveform) is active
        if (afDataMode == 2 || afDataMode == 4) {
            requestLiveScope(afDataMode);
        }
    }

    /**
     * 35mm-equivalent focal length of the active lens. Prefers the lens record
     * used by the pill (which carries the correction for ISZ virtual lenses and
     * logical members); falls back to the live characteristics when unavailable.
     */
    private float activeLens35mm(CameraCharacteristics chars) {
        float cached = mActiveLens35mm;
        if (cached > 0f) {
            return cached;
        }
        return equivalent35mmFromCharacteristics(chars);
    }

    /**
     * Resolves and caches the active lens's 35mm equivalent whenever the pill's
     * lens set or selection changes, keeping the HUD math binder-free per frame.
     */
    private void updateActiveLens35mm() {
        mActiveLens35mm = 0f;
        try {
            String activeId = captureController != null
                    ? captureController.getActiveLogicalMemberId() : null;
            if (activeId == null) {
                activeId = PreferenceKeys.getCameraID();
            }
            float resolved = 0f;
            if (activeId != null && mVideoLogicalMembers != null) {
                for (LogicalCameraResolver.Member member : mVideoLogicalMembers) {
                    if (activeId.equals(member.memberId)) {
                        resolved = member.focal35mm;
                        break;
                    }
                }
            }
            if (resolved <= 0f && activeId != null && mCameraLensDataMap != null) {
                CameraLensData lens = mCameraLensDataMap.get(activeId);
                if (lens != null) {
                    resolved = lens.getCamera35mmFocalLength();
                }
            }
            if (resolved > 0f) {
                mActiveLens35mm = resolved;
            }
        } catch (Exception ignored) {
        }
    }

    private static float equivalent35mmFromCharacteristics(CameraCharacteristics chars) {
        if (chars == null) return 0f;
        try {
            float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (focalLengths == null || focalLengths.length == 0 || sensorSize == null) {
                return 0f;
            }
            Rect activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Size pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            return FocalEquivalence.equivalent35mm(focalLengths[0],
                    sensorSize.getWidth(), sensorSize.getHeight(),
                    activeArray != null ? activeArray.width() : 0,
                    activeArray != null ? activeArray.height() : 0,
                    pixelArray != null ? pixelArray.getWidth() : 0,
                    pixelArray != null ? pixelArray.getHeight() : 0);
        } catch (Exception e) {
            return 0f;
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

    private long lastHistTime = 0;
    private static final long HIST_INTERVAL_MS = 120; // 8.3 Hz sampling rate for zero CPU load
    private final Object mWaveLock = new Object();
    private int[][] mWaveCounts;
    private int[][] mWavePixelBuffers;
    private int mWavePixelBufferIndex = 0;

    private void requestLiveScope(int afDataMode) {
        if (textureView == null || surfaceView == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHistTime < HIST_INTERVAL_MS) {
            return;
        }
        lastHistTime = now;
        int waveColumns = 0;
        int waveBins = 0;
        if (afDataMode == 4) {
            float density = getResources().getDisplayMetrics().density;
            waveColumns = Math.max(16, Math.round(
                    ViewfinderHudView.WAVEFORM_WIDTH_DP * density));
            waveBins = Math.max(8, Math.round(
                    ViewfinderHudView.WAVEFORM_HEIGHT_DP * density));
        }
        final int columns = waveColumns;
        final int bins = waveBins;
        textureView.requestAnalysisFrame((rgba, width, height) ->
                processExecutorService.execute(() ->
                        processScopeData(rgba, width, height, afDataMode, columns, bins)));
    }

    private void processScopeData(byte[] rgba, int width, int height,
                                  int afDataMode, int waveColumns, int waveBins) {
        if (afDataMode == 4) {
            processWaveformData(rgba, width, height, waveColumns, waveBins);
        } else {
            processHistogramData(rgba, width, height);
        }
    }

    private void processHistogramData(byte[] rgba, int width, int height) {
        int size = PreviewScopeAnalyzer.HISTOGRAM_BINS;
        int[][] bins = new int[3][size];
        PreviewScopeAnalyzer.fillHistogram(rgba, width, height, bins, size);
        int maxY = PreviewScopeAnalyzer.applySqrtScale(bins);

        final int calculatedMaxY = maxY;
        if (mViewfinderHudView != null) {
            mViewfinderHudView.post(() ->
                    mViewfinderHudView.setHistogramData(bins, calculatedMaxY, size));
        }
    }

    private void processWaveformData(byte[] rgba, int width, int height,
                                     int columns, int bins) {
        int[] pixels;
        synchronized (mWaveLock) {
            if (mWaveCounts == null || mWaveCounts[0].length != columns * bins) {
                mWaveCounts = new int[3][columns * bins];
                mWavePixelBuffers = new int[][]{
                        new int[columns * bins], new int[columns * bins]};
                mWavePixelBufferIndex = 0;
            }
            int max = PreviewScopeAnalyzer.fillWaveform(rgba, width, height, mWaveCounts, columns, bins);
            float normalizer = PreviewScopeAnalyzer.percentileNormalizer(mWaveCounts, max, 0.99f);
            pixels = mWavePixelBuffers[mWavePixelBufferIndex];
            mWavePixelBufferIndex ^= 1;
            PreviewScopeAnalyzer.renderWaveformBitmap(mWaveCounts, normalizer, pixels, columns, bins);
        }
        if (mViewfinderHudView != null) {
            mViewfinderHudView.post(() ->
                    mViewfinderHudView.setWaveformPixels(pixels, columns, bins));
        }
    }

    private RectF getScreenRectFromMeteringRect(MeteringRectangle meteringRectangle) {
        if (captureController.mImageReaderPreview == null) return new RectF();
        Size size = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        if (size == null) {
            size = new Size(captureController.mImageReaderPreview.getWidth(), captureController.mImageReaderPreview.getHeight());
        }
        // The viewfinder frame is the visible preview rect regardless of whether
        // the preview surface is frame-sized or full-bleed.
        View frame = cameraFragmentBinding.layoutViewfinder.viewfinderFrame;
        int frameW = frame != null ? frame.getWidth() : textureView.getWidth();
        int frameH = frame != null ? frame.getHeight() : textureView.getHeight();
        float left = (((float) meteringRectangle.getY() / size.getHeight()) * (frameW));
        float top = (((float) meteringRectangle.getX() / size.getWidth()) * (frameH));
        float width = (((float) meteringRectangle.getHeight() / size.getHeight()) * (frameW));
        float height = (((float) meteringRectangle.getWidth() / size.getWidth()) * (frameH));
        return new RectF(
                frameW - left - width,//Right
                top,  //Top
                frameW - left,
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
        if (secureSession) {
            promptUnlockForGallery();
            return;
        }
        launchGalleryInternal(false);
    }

    private void launchGalleryInternal(boolean unlockedJustNow) {
        Intent galleryIntent = new Intent(activity, GalleryActivity.class);
        // Create gallery bundle
        galleryIntent.putExtra("CameraFragment", true);
        if (unlockedJustNow) {
            galleryIntent.putExtra(SecureCameraHelper.EXTRA_UNLOCKED_JUST_NOW, true);
        }
        startActivity(galleryIntent, null);
    }

    /**
     * Lockscreen behavior: the gallery button requires device credentials.
     * Prefer {@code requestDismissKeyguard} (leaves the device genuinely unlocked
     * on success); fall back to the confirm-credential activity on older APIs or
     * dismiss errors.
     */
    private void promptUnlockForGallery() {
        if (activity != null) {
            boolean requested = SecureCameraHelper.requestDismissKeyguard(activity,
                    new SecureCameraHelper.DismissCallback() {
                        @Override
                        public void onDismissSucceeded() {
                            activity.runOnUiThread(() -> launchGalleryInternal(false));
                        }

                        @Override
                        public void onDismissCancelled() {
                            // Stay on the viewfinder; nothing to do.
                        }

                        @Override
                        public void onDismissError() {
                            activity.runOnUiThread(() -> fallbackToConfirmCredential());
                        }
                    });
            if (requested) return;
        }
        fallbackToConfirmCredential();
    }

    private void fallbackToConfirmCredential() {
        boolean launched = SecureCameraHelper.promptUnlock(this, REQUEST_UNLOCK_GALLERY,
                getString(R.string.secure_camera_unlock_title),
                getString(R.string.secure_camera_unlock_gallery_message));
        if (!launched) {
            // No secure lock enrolled: nothing to protect, open directly.
            launchGalleryInternal(true);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UNLOCK_GALLERY && resultCode == Activity.RESULT_OK) {
            launchGalleryInternal(true);
        }
    }

    /**
     * Called by {@link CameraActivity} when the secure session starts or ends
     * (device unlocked). Updates gated UI and the gallery thumbnail without restart.
     */
    public void onSecureSessionChanged(boolean secure) {
        secureSession = secure;
        if (!isAdded()) return;
        applySecureSessionUI();
        if (cameraFragmentViewModel != null) {
            if (secure) {
                cameraFragmentViewModel.clearGalleryThumb();
            } else {
                cameraFragmentViewModel.updateGalleryThumb(null);
            }
        }
    }

    /**
     * Hides/disables settings entry points while locked. The gallery button stays
     * visible so users can authenticate from it.
     */
    private void applySecureSessionUI() {
        if (cameraFragmentBinding == null) return;
        View settingsButton = findViewById(R.id.settings_button);
        if (settingsButton != null) {
            settingsButton.setVisibility(secureSession ? View.GONE : View.VISIBLE);
            settingsButton.setEnabled(!secureSession);
        }
        if (cameraFragmentBinding.settingsBar != null) {
            cameraFragmentBinding.settingsBar.setChildVisibility(
                    R.id.settings_bar_settings_button_container,
                    secureSession ? View.GONE : View.VISIBLE);
        }
    }

    public void launchSettings() {
        if (secureSession) {
            showSnackBar(getString(R.string.secure_camera_settings_locked));
            return;
        }
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
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        if (activity != null) {
                            activity.finish();
                        }
                    })
                    .create();
            BlurSupport.blurBehindOnShow(dialog);
            return dialog;
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
        public void onVideoRecordingStarted() {
            if (mCameraUIView != null) {
                mCameraUIView.setVideoRecordingInfoVisible(true);
            }
            Vibration vibration = PhotonCamera.getVibration();
            if (vibration != null) vibration.recordStart();
        }

        @Override
        public void onVideoRecordingTick(long elapsedMs, long estimatedBytes, long availableBytes) {
            if (mCameraUIView != null) {
                mCameraUIView.updateVideoRecordingInfo(elapsedMs, estimatedBytes, availableBytes);
            }
        }

        @Override
        public void onVideoRecordingStopped() {
            if (mCameraUIView != null) {
                mCameraUIView.setVideoRecordingInfoVisible(false);
            }
            Vibration vibration = PhotonCamera.getVibration();
            if (vibration != null) vibration.recordStop();
        }

        @Override
        public void onLogicalMemberChanged(String memberId) {
            if (memberId != null) {
                auxButtonsViewModel.setActiveId(memberId);
            }
            updateActiveLens35mm();
            Vibration vibration = PhotonCamera.getVibration();
            if (vibration != null) vibration.lensSwitch();
        }

        @Override
        public void onLogicalZoomProgress(float ratio) {
            if (cameraFragmentViewModel != null) {
                cameraFragmentViewModel.setZoomRatio(ratio);
                if (captureController != null) {
                    cameraFragmentViewModel.setZoomOffNative(
                            !captureController.isZoomOnNative(ratio));
                }
            }
        }

        @Override
        public void onFpsModeCorrected() {
            // The fps pref was corrected (e.g. 60 -> 30): refresh the top-bar
            // icon and quick-settings entries to the actual mode.
            try {
                if (activity != null) {
                    activity.runOnUiThread(() -> updateSettingsBar());
                } else {
                    updateSettingsBar();
                }
            } catch (Exception e) {
                Log.e(TAG, "onFpsModeCorrected sync failed", e);
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
            Vibration vibration = PhotonCamera.getVibration();
            if (vibration != null) vibration.error();
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

        private boolean isStillMode() {
            CameraMode mode = PhotonCamera.getSettings().selectedMode;
            return mode == CameraMode.PHOTO || mode == CameraMode.MOTION || mode == CameraMode.NIGHT;
        }

        private boolean isStillBurst() {
            return isStillMode() && PreferenceKeys.getFrameCountValue() > 1;
        }

        @Override
        public void onCaptureStillPictureStarted(Object o) {
            if (PhotonCamera.getSettings().selectedMode != CameraMode.RAWVIDEO) {
                mCameraUIView.setCaptureProgressBarOpacity(1.0f);
                mCameraUIView.lockUIForBurst(true);
            }
            if (isStillBurst()) {
                Vibration vibration = PhotonCamera.getVibration();
                if (vibration != null) vibration.captureStart();
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
                if (isStillBurst()) {
                    Vibration vibration = PhotonCamera.getVibration();
                    if (vibration != null) vibration.burstFrame();
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
            if (isStillMode()) {
                Vibration vibration = PhotonCamera.getVibration();
                if (vibration != null) vibration.captureComplete();
            }
            timerFrameCountViewModel.clearFrameTimeCnt();
            mCameraUIView.resetCaptureProgressBar();
            mCameraUIView.lockUIForBurst(false);
            mCameraUIView.setVideoRecordingInfoVisible(false);
            textureView.post(() -> textureView.setAlpha(1f));
        }

        @Override
        public void onPreviewCaptureCompleted(CaptureResult captureResult) {
            // While recording, the per-frame UI post (orientation, AF state,
            // HUD) runs at sensor rate for no visible benefit and pressures
            // the UI queue/GC at 30/60fps: throttle to the HUD interval.
            if (captureController != null && captureController.mIsRecordingVideo) {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastScreenLogMs < HUD_UPDATE_INTERVAL_MS) return;
                lastScreenLogMs = now;
            }
            updateScreenLog(captureResult);
        }

        /**
         * Implementation of abstract methods of {@link CameraEventsListener}
         */

        @Override
        public void onOpenCamera(CameraManager cameraManager) {
            initCameraIDLists(cameraManager);
            refreshLensPillAndZoomModel();
        }

        /**
         * Rebuilds the lens pill and the zoom-controller lens model for the
         * current mode: logical members in video logical mode, physical
         * lenses otherwise. Runs on cold open and on every restart-driven
         * open (e.g. photo&lt;-&gt;video mode switches), so neither side
         * keeps the other's stale pill. Idempotent while the target is
         * unchanged.
         */
        private void refreshLensPillAndZoomModel() {
            mVideoLogicalMembers = captureController != null
                    ? captureController.getEffectiveLogicalMembers()
                    : new ArrayList<>();
            if (!mVideoLogicalMembers.isEmpty()) {
                auxButtonsViewModel.showLogicalMembers(
                        com.particlesdevs.photoncamera.api.LogicalCameraResolver
                                .toCameraLensData(mVideoLogicalMembers));
            } else {
                auxButtonsViewModel.rebuildCameraLists(mCameraLensDataMap);
            }
            // Feed the lens model into the zoom controller so pinch can
            // switch lenses and zoom below 1.0x (ultra-wide).
            updateZoomLensModel();
        }

        private void updateZoomLensModel() {
            if (captureController == null || mCameraLensDataMap == null) return;
            if (!mVideoLogicalMembers.isEmpty()) {
                String anchor = captureController.configureLogicalZoomLenses(mVideoLogicalMembers);
                auxButtonsViewModel.setActiveId(anchor);
                updateActiveLens35mm();
                return;
            }
            CameraLensData active = mCameraLensDataMap.get(PreferenceKeys.getCameraID());
            int facing = active != null
                    ? active.getFacing()
                    : CameraCharacteristics.LENS_FACING_BACK;
            captureController.configureZoomLenses(mCameraLensDataMap, facing);
            auxButtonsViewModel.setActiveId(PreferenceKeys.getCameraID());
            updateActiveLens35mm();
        }

        @Override
        public void onCameraRestarted() {
            // Same-sensor ISZ transitions keep the overlay (grid/HUD stay
            // valid); everything else wipes it for a fresh lens.
            if (!CameraManager2.isIszVirtual(PhotonCamera.getSettings().mCameraID)) {
                surfaceView.clear();
                if (mViewfinderHudView != null) mViewfinderHudView.clear();
            }
            mCameraUIView.refresh(CaptureController.isProcessing);
            mTouchFocus.resetFocusCircle();
        }

        @Override
        public void onCharacteristicsUpdated(CameraCharacteristics characteristics) {
            // See onCameraRestarted: the overlay survives same-sensor ISZ entry.
            if (!CameraManager2.isIszVirtual(PhotonCamera.getSettings().mCameraID)) {
                surfaceView.clear();
                if (mViewfinderHudView != null) mViewfinderHudView.clear();
            }
            // Authoritative per-open refresh: mode-switch restarts bypass
            // onOpenCamera, so the pill/zoom model is rebuilt here too.
            refreshLensPillAndZoomModel();
            if (captureController != null) {
                if (captureController.isZoomDrivenLensSwitch()) {
                    // The zoom target changed because a lens switch occurred. Preserve
                    // the effective zoom so the new lens continues where the user left
                    // off, re-expressing any crop on the new sensor.
                    captureController.clearZoomDrivenLensSwitch();
                } else {
                    // A manual lens/camera switch invalidates the existing crop.
                    captureController.resetZoom();
                }
                cameraFragmentViewModel.setZoomRatio(captureController.getZoomRatio());
                cameraFragmentViewModel.setZoomOffNative(
                        !captureController.isZoomOnNative(captureController.getZoomRatio()));
                if (lensZoomBarController != null) lensZoomBarController.refreshZoomRange();
            }
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