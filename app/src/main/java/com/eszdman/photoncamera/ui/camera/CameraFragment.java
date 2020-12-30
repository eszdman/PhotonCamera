/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eszdman.photoncamera.ui.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.RectF;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Pair;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraEventsListener;
import com.eszdman.photoncamera.api.CameraManager2;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.api.CameraReflectionApi;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.capture.CaptureEventsListener;
import com.eszdman.photoncamera.control.Swipe;
import com.eszdman.photoncamera.control.TouchFocus;
import com.eszdman.photoncamera.databinding.CameraFragmentBinding;
import com.eszdman.photoncamera.gallery.ui.GalleryActivity;
import com.eszdman.photoncamera.processing.ProcessingEventsListener;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.ui.camera.viewmodel.CameraFragmentViewModel;
import com.eszdman.photoncamera.ui.camera.viewmodel.TimerFrameCountViewModel;
import com.eszdman.photoncamera.ui.camera.views.viewfinder.SurfaceViewOverViewfinder;
import com.eszdman.photoncamera.ui.settings.SettingsActivity;
import com.eszdman.photoncamera.util.log.CustomLogger;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT;

public class CameraFragment extends Fragment {
    public static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final String FRAGMENT_DIALOG = "dialog";
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = CameraFragment.class.getSimpleName();
    private static final String ACTIVE_BACKCAM_ID = "ACTIVE_BACKCAM_ID"; //key for savedInstanceState
    private static final String ACTIVE_FRONTCAM_ID = "ACTIVE_FRONTCAM_ID"; //key for savedInstanceState
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    public SurfaceViewOverViewfinder surfaceView;
    public String[] mAllCameraIds;
    public Set<String> mFrontCameraIDs;
    public Set<String> mBackCameraIDs;
    public Map<String, Pair<Float, Float>> mFocalLengthAperturePairList;
    private TimerFrameCountViewModel timerFrameCountViewModel;
    private CameraUIView mCameraUIView;
    private CaptureController captureController;
    private CameraFragmentViewModel cameraFragmentViewModel;
    private CameraFragmentBinding cameraFragmentBinding;
    private TouchFocus mTouchFocus;
    private Swipe mSwipe;
    private MediaPlayer burstPlayer;


    private CameraFragment() {
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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //create the ui binding
        cameraFragmentBinding = DataBindingUtil.inflate(inflater, R.layout.camera_fragment, container, false);

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
        surfaceView = cameraFragmentBinding.layoutViewfinder.surfaceView;
    }

    private void setModelsToLayout() {
        //bind the model to the ui, it applies changes when the model values get changed
        cameraFragmentBinding.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.layoutTopbar.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.manualMode.manualPalette.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        cameraFragmentBinding.layoutBottombar.bottomButtons.setUimodel(cameraFragmentViewModel.getCameraFragmentModel());
        // associating timer model with layouts
        cameraFragmentBinding.layoutBottombar.bottomButtons.setTimermodel(timerFrameCountViewModel.getTimerFrameCountModel());
        cameraFragmentBinding.layoutViewfinder.setTimermodel(timerFrameCountViewModel.getTimerFrameCountModel());
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        this.mCameraUIView = new CameraUIViewImpl(view);
        this.mCameraUIView.setCameraUIEventsListener(new CameraUIController(this));
        mTouchFocus = new TouchFocus(this);
        mSwipe = new Swipe(this);
        captureController = new CaptureController(getActivity(), new CameraEventsListenerImpl());
        PhotonCamera.setCaptureController(captureController);
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
        PhotonCamera.getSensors().register();
        PhotonCamera.getGravity().register();
        mTouchFocus.reInit();
        this.mCameraUIView.refresh();
        burstPlayer = MediaPlayer.create(getActivity(), R.raw.sound_burst);

        cameraFragmentViewModel.updateGalleryThumb();
        cameraFragmentViewModel.onResume();

        captureController.startBackgroundThread();
        captureController.resumeCamera();

        PhotonCamera.getSupportedDevice().loadCheck();
    }

    @Override
    public void onPause() {
        PhotonCamera.getGravity().unregister();
        PhotonCamera.getSensors().unregister();
        PhotonCamera.getSettings().saveID();
        captureController.closeCamera();
//        stopBackgroundThread();
        cameraFragmentViewModel.onPause();
        mTouchFocus.resetFocusCircle();
        burstPlayer.release();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            captureController.stopBackgroundThread();
            captureController.mImageSaver.stopProcessingThread();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateScreenLog(CaptureResult result) {
        mainHandler.post(() -> {
            CustomLogger cl = new CustomLogger(getActivity(), R.id.screen_log_focus);
            if (PhotonCamera.getSettings().aFDebugData) {
                IsoExpoSelector.ExpoPair expoPair = IsoExpoSelector.GenerateExpoPair(-1);
                LinkedHashMap<String, String> dataset = new LinkedHashMap<>();
                dataset.put("AF_MODE", getResultFieldName("CONTROL_AF_MODE_", result.get(CaptureResult.CONTROL_AF_MODE)));
                dataset.put("AF_TRIGGER", getResultFieldName("CONTROL_AF_TRIGGER_", result.get(CaptureResult.CONTROL_AF_TRIGGER)));
                dataset.put("AF_STATE", getResultFieldName("CONTROL_AF_STATE_", result.get(CaptureResult.CONTROL_AF_STATE)));
                dataset.put("FOCUS_DISTANCE", String.valueOf(result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
                dataset.put("EXPOSURE_TIME", expoPair.ExposureString() + "s");
//            dataset.put("EXPOSURE_TIME_CR", String.format(Locale.ROOT,"%.5f",result.get(CaptureResult.SENSOR_EXPOSURE_TIME).doubleValue()/1E9)+ "s");
                dataset.put("ISO", String.valueOf(expoPair.iso));
//            dataset.put("ISO_CR", String.valueOf(result.get(CaptureResult.SENSOR_SENSITIVITY)));
                dataset.put("Shakeness", String.valueOf(PhotonCamera.getSensors().getShakiness()));
                dataset.put("FOCUS_RECT", Arrays.deepToString(result.get(CaptureResult.CONTROL_AF_REGIONS)));
                MeteringRectangle[] rectobj = result.get(CaptureResult.CONTROL_AF_REGIONS);
                if (rectobj != null && rectobj.length > 0) {
                    RectF rect = getScreenRectFromMeteringRect(rectobj[0]);
                    dataset.put("F_RECT(px)", rect.toString());
                    surfaceView.update(rect);
                }
                surfaceView.setVisibility(View.VISIBLE);
                cl.setVisibility(View.VISIBLE);
                cl.updateText(cl.createTextFrom(dataset));
            } else {
                if (surfaceView.rectToDraw != null) {
                    surfaceView.rectToDraw = null;
                    surfaceView.invalidate();
                    cl.setVisibility(View.GONE);
                    surfaceView.setVisibility(View.GONE);
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
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }
    }

    public void showSnackBar(final String text) {
        final View v = getView();
        if (v != null) {
            new Handler(Looper.getMainLooper()).post(() -> Snackbar.make(v, text, Snackbar.LENGTH_SHORT).show());
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
        CameraManager2 manager2 = new CameraManager2(cameraManager, PhotonCamera.getSettingsManager());
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
        PhotonCamera.getSettings().setLastPicture(imageToSave.getAbsolutePath());
        Uri contentUri = Uri.fromFile(imageToSave);
//        Bitmap bitmap = BitmapDecoder.from(Uri.fromFile(imageToSave)).scaleBy(0.1f).decode();
        mediaScanIntent.setData(contentUri);
        if (getActivity() != null)
            getActivity().sendBroadcast(mediaScanIntent);
    }

    public void launchGallery() {
        Intent galleryIntent = new Intent(getActivity(), GalleryActivity.class);
        startActivity(galleryIntent);
    }

    public void launchSettings() {
        Intent settingsIntent = new Intent(getActivity(), SettingsActivity.class);
        startActivity(settingsIntent);
    }

    public <T extends View> T findViewById(@IdRes int id) {
        return getActivity().findViewById(id);
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

    public class CameraEventsListenerImpl extends CameraEventsListener {
        /**
         * Implementation of {@link ProcessingEventsListener}
         */
        @Override
        public void onProcessingStarted(Object obj) {
            logD("onProcessingStarted: " + obj);
            mainHandler.post(() -> {
                mCameraUIView.setProcessingProgressBarIndeterminate(true);
                mCameraUIView.activateShutterButton(false);
            });
        }

        @Override
        public void onProcessingChanged(Object obj) {
        }

        @Override
        public void onProcessingFinished(Object obj) {
            logD("onProcessingFinished: " + obj);
            mainHandler.post(() -> {
                mCameraUIView.resetProcessingProgressBar();
                mCameraUIView.activateShutterButton(true);
            });
        }

        @Override
        public void onSaveImage(Object obj) {
        }

        @Override
        public void onImageSaved(Object obj) {
            if (obj instanceof File) {
                logD("onImageSaved: " + ((File) obj).getAbsolutePath());
                triggerMediaScanner((File) obj);
            }
            cameraFragmentViewModel.updateGalleryThumb();
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
        }

        @Override
        public void onFrameCaptureStarted(Object o) {
            burstPlayer.seekTo(0);
        }

        @Override
        public void onFrameCaptureProgressed(Object o) {
            if (o instanceof TimerFrameCountViewModel.FrameCntTime) {
                timerFrameCountViewModel.setFrameTimeCnt((TimerFrameCountViewModel.FrameCntTime) o);
            }
        }

        @Override
        public void onFrameCaptureCompleted(Object o) {
            if (o instanceof Integer) {
                mCameraUIView.incrementCaptureProgressBar((Integer) o);
            }
            if (PreferenceKeys.isCameraSoundsOn()) {
                burstPlayer.start();
            }
        }

        @Override
        public void onCaptureSequenceCompleted(Object o) {
            timerFrameCountViewModel.clearFrameTimeCnt();
            mCameraUIView.resetCaptureProgressBar();
            mTouchFocus.resetFocusCircle();
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
            mCameraUIView.refresh();
        }

        @Override
        public void onCharacteristicsUpdated() {
            mCameraUIView.initAuxButtons(mBackCameraIDs, mFocalLengthAperturePairList, mFrontCameraIDs);
            PhotonCamera.getManualMode().init();
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
            getActivity().finish();
        }

        @Override
        public void onRequestTriggerMediaScanner(File file) {
            triggerMediaScanner(file);
        }
    }

}