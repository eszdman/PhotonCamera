package com.particlesdevs.photoncamera.ui.camera.views;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.capture.ZoomSliderMapper;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.CameraFragmentViewModel;

/**
 * Owns the centered lens pill ({@code lens_zoom_bar}): a horizontal lens switcher
 * that expands into a zoom slider on pinch-to-zoom and collapses back after
 * {@link #COLLAPSE_DELAY_MS} of no zoom interaction.
 *
 * <p>The lens buttons stay clickable while expanded; the slider thumb tracks the
 * live effective zoom, and dragging it drives the same
 * {@link CaptureController#setZoom(float, float, float)} path as the pinch gesture.
 * The total-zoom indicator above the pill is driven by data binding and is
 * intentionally left untouched here.
 */
public class LensZoomBarController {
    /** Idle time after the last zoom interaction before the slider collapses. */
    static final long COLLAPSE_DELAY_MS = 2000L;
    private static final int SLIDER_MAX = 1000;

    private final View bar;
    private final AuxButtonsLayout auxButtons;
    private final SeekBar slider;
    private final View lockPill;
    private final ImageButton lockButton;
    private final CaptureController captureController;
    private final CameraFragmentViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable collapseRunnable = this::collapse;

    private boolean expanded;
    private boolean settingsHidden;

    public LensZoomBarController(View bar, AuxButtonsLayout auxButtons, SeekBar slider,
                                 View lockPill, ImageButton lockButton,
                                 CaptureController captureController, CameraFragmentViewModel viewModel) {
        this.bar = bar;
        this.auxButtons = auxButtons;
        this.slider = slider;
        this.lockPill = lockPill;
        this.lockButton = lockButton;
        this.captureController = captureController;
        this.viewModel = viewModel;
    }

    public void init() {
        captureController.setLensSwitchLocked(PreferenceKeys.isZoomLockOn());
        updateLockIcon();
        lockButton.setOnClickListener(v -> toggleLock());
        slider.setMax(SLIDER_MAX);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || !expanded || CaptureController.isProcessing) return;
                float zoom = ZoomSliderMapper.progressToZoom(progress, SLIDER_MAX,
                        captureController.getMinZoom(), captureController.getMaxZoom());
                captureController.setZoom(zoom, 0.5f, 0.5f);
                viewModel.setZoomRatio(captureController.getZoomRatio());
                scheduleCollapse();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // The user grabbed the slider: hold the expanded state while dragging.
                handler.removeCallbacks(collapseRunnable);
                if (!expanded) expand();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleCollapse();
            }
        });
        // The lens set changes when the facing flips or lenses load; re-evaluate
        // whether the pill should be shown (single-lens devices hide it when collapsed).
        auxButtons.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateBarVisibility());
        updateBarVisibility();
    }

    /** Called on every handled pinch-to-zoom movement. Expands and restarts the 2s timer. */
    public void onPinchGesture() {
        if (settingsHidden) return;
        if (!expanded) expand();
        syncSlider();
        scheduleCollapse();
    }

    /** Called when the bound zoom ratio changes (pinch, slider, lens switch). */
    public void onZoomChanged(float zoomRatio) {
        if (expanded && !slider.isPressed()) {
            slider.setProgress(ZoomSliderMapper.zoomToProgress(zoomRatio, SLIDER_MAX,
                    captureController.getMinZoom(), captureController.getMaxZoom()));
        }
    }

    /** Called after the lens set or active camera changes (range may have changed). */
    public void refreshZoomRange() {
        syncSlider();
        updateBarVisibility();
        if (expanded) scheduleCollapse();
    }

    public void setSettingsHidden(boolean hidden) {
        settingsHidden = hidden;
        if (hidden) {
            handler.removeCallbacks(collapseRunnable);
            expanded = false;
            slider.setVisibility(View.GONE);
        }
        updateBarVisibility();
    }

    public void onPause() {
        handler.removeCallbacks(collapseRunnable);
        expanded = false;
        slider.setVisibility(View.GONE);
        updateBarVisibility();
    }

    private void expand() {
        expanded = true;
        slider.setVisibility(View.VISIBLE);
        syncSlider();
        updateBarVisibility();
        scheduleCollapse();
    }

    private void collapse() {
        expanded = false;
        slider.setVisibility(View.GONE);
        updateBarVisibility();
    }

    private void scheduleCollapse() {
        handler.removeCallbacks(collapseRunnable);
        handler.postDelayed(collapseRunnable, COLLAPSE_DELAY_MS);
    }

    private void syncSlider() {
        slider.setProgress(ZoomSliderMapper.zoomToProgress(viewModel.getZoomRatio(), SLIDER_MAX,
                captureController.getMinZoom(), captureController.getMaxZoom()));
    }

    private void updateBarVisibility() {
        // Single-lens devices keep the current behaviour (no pill) until a pinch
        // reveals the slider; the settings bar hides the whole pill.
        // The lock pill follows the lens pill (a lock is meaningless with one lens).
        boolean show = !settingsHidden && (expanded || auxButtons.getChildCount() > 1);
        int visibility = show ? View.VISIBLE : View.GONE;
        bar.setVisibility(visibility);
        lockPill.setVisibility(visibility);
    }

    private void toggleLock() {
        boolean locked = !captureController.isLensSwitchLocked();
        captureController.setLensSwitchLocked(locked);
        PreferenceKeys.setZoomLock(locked);
        updateLockIcon();
        // The zoom range changed (full facing range vs current lens window).
        refreshZoomRange();
    }

    private void updateLockIcon() {
        boolean locked = captureController.isLensSwitchLocked();
        lockButton.setImageResource(locked ? R.drawable.ic_zoom_lock : R.drawable.ic_zoom_lock_open);
        lockButton.setContentDescription(lockButton.getContext().getString(
                locked ? R.string.zoom_unlock_desc : R.string.zoom_lock_desc));
    }
}
