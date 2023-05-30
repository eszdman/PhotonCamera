package com.particlesdevs.photoncamera.ui.camera;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.databinding.LayoutBottombuttonsBinding;
import com.particlesdevs.photoncamera.databinding.LayoutMainTopbarBinding;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker;
import com.particlesdevs.photoncamera.util.Utilities;

import java.util.Arrays;

import static androidx.constraintlayout.widget.ConstraintSet.GONE;

/**
 * This Class is a dumb 'View' which contains view components visible in the main Camera User Interface
 * <p>
 * It gets instantiated in {@link CameraFragment#onViewCreated(View, Bundle)}
 */
class CameraUIViewImpl implements CameraUIView {
    private static final String TAG = "CameraUIView";
    private final CameraFragment cameraFragment;
    private final ProgressBar mCaptureProgressBar;
    private final ImageButton mShutterButton;
    private final ProgressBar mProcessingProgressBar;
    private final HorizontalPicker mModePicker;
    private LayoutMainTopbarBinding topbar;
    private LayoutBottombuttonsBinding bottombuttons;
    private CameraUIEventsListener uiEventsListener;
    private CameraModeState currentState;

    CameraUIViewImpl(CameraFragment cameraFragment) {
        this.cameraFragment = cameraFragment;
        this.topbar = cameraFragment.cameraFragmentBinding.layoutTopbar;
        this.bottombuttons = cameraFragment.cameraFragmentBinding.layoutBottombar.bottomButtons;
        this.mCaptureProgressBar = cameraFragment.cameraFragmentBinding.layoutViewfinder.captureProgressBar;
        this.mProcessingProgressBar = bottombuttons.processingProgressBar;
        this.mShutterButton = bottombuttons.shutterButton;
        this.mModePicker = cameraFragment.cameraFragmentBinding.layoutBottombar.modeSwitcher.modePickerView;
        this.initListeners();
        this.initModeSwitcher();
        this.currentState = new PhotoMotionModeState(); //init mode
    }

    private void initListeners() {
        this.topbar.setTopBarClickListener(v -> this.uiEventsListener.onClick(v));
        this.bottombuttons.setBottomBarClickListener(v -> this.uiEventsListener.onClick(v));
    }

    private void initModeSwitcher() {
        this.mModePicker.setValues(Arrays.stream(CameraMode.nameIds()).map(cameraFragment.activity::getString).toArray(String[]::new));
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
        Log.d(TAG, "Current Mode:" + cameraMode.name());
        switch (cameraMode) {
            case VIDEO:
                currentState = new VideoModeState();
                break;
            case UNLIMITED:
                currentState = new UnlimitedModeState();
                break;
            case PHOTO:
            case MOTION:
                currentState = new PhotoMotionModeState();
                break;
            case NIGHT:
                currentState = new NightModeState();
                break;
        }

        currentState.reConfigureModeViews(cameraMode);
        uiEventsListener.onCameraModeChanged(cameraMode);
    }

    private void toggleConstraints(CameraMode mode) {
        if (cameraFragment.displayAspectRatio <= 16f / 9f) {
            ConstraintLayout.LayoutParams camera_containerLP =
                    (ConstraintLayout.LayoutParams) cameraFragment.cameraFragmentBinding
                            .textureHolder
                            .findViewById(R.id.camera_container)
                            .getLayoutParams();
            switch (mode) {
                case VIDEO:
                    camera_containerLP.topToTop = R.id.textureHolder;
                    camera_containerLP.topToBottom = -1;
                    break;
                case UNLIMITED:
                case PHOTO:
                case MOTION:
                case NIGHT:
                    camera_containerLP.topToTop = -1;
                    camera_containerLP.topToBottom = R.id.layout_topbar;
            }

        }
    }

    @Override
    public void refresh(boolean processing) {
        cameraFragment.cameraFragmentBinding.invalidateAll();
        currentState.reConfigureModeViews(CameraMode.valueOf(PreferenceKeys.getCameraModeOrdinal()));
        this.resetCaptureProgressBar();
        if (!processing) {
            this.activateShutterButton(true);
            this.setProcessingProgressBarIndeterminate(false);
        }
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
        cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.flash_entry_layout, flashAvailable ? View.VISIBLE : GONE);
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

    public class VideoModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setEisVisible(true);
            // cameraUIView.cameraFragmentBinding.textureHolder.setBackgroundResource(R.drawable.gradient_vector_video);
            topbar.setFpsVisible(true);
            topbar.setTimerVisible(false);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.GONE);
            mShutterButton.setBackgroundResource(R.drawable.unlimitedbutton);
            cameraFragment.cameraFragmentBinding.layoutBottombar.layoutBottombar.setBackgroundResource(R.color.panel_transparency);
            cameraFragment.cameraFragmentBinding.getRoot().setBackgroundResource(android.R.color.black);

            toggleConstraints(mode);
        }
    }

    //
    public class UnlimitedModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setFpsVisible(true);
            topbar.setTimerVisible(false);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.GONE);
            mShutterButton.setBackgroundResource(R.drawable.unlimitedbutton);
            cameraFragment.cameraFragmentBinding.layoutBottombar.layoutBottombar.setBackground(null);
            cameraFragment.cameraFragmentBinding.getRoot().setBackground(Utilities.resolveDrawable(cameraFragment.requireActivity(), R.attr.cameraFragmentBackground));

            toggleConstraints(mode);
        }
    }

    //
    public class PhotoMotionModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setEisVisible(true);
            topbar.setFpsVisible(true);
            topbar.setTimerVisible(true);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.eis_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.hdrx_entry_layout, View.GONE);
            mShutterButton.setBackgroundResource(R.drawable.roundbutton);
            cameraFragment.cameraFragmentBinding.layoutBottombar.layoutBottombar.setBackground(null);
            cameraFragment.cameraFragmentBinding.getRoot().setBackground(Utilities.resolveDrawable(cameraFragment.requireActivity(), R.attr.cameraFragmentBackground));

            toggleConstraints(mode);
        }
    }

    public class NightModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setEisVisible(false);
            topbar.setFpsVisible(true);
            topbar.setTimerVisible(true);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.eis_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.VISIBLE);
            mShutterButton.setBackgroundResource(R.drawable.roundbutton);
            cameraFragment.cameraFragmentBinding.layoutBottombar.layoutBottombar.setBackground(null);
            cameraFragment.cameraFragmentBinding.getRoot().setBackground(Utilities.resolveDrawable(cameraFragment.requireActivity(), R.attr.cameraFragmentBackground));

            toggleConstraints(mode);
        }
    }

}

