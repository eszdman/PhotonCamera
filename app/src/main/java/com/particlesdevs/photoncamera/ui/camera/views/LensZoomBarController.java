package com.particlesdevs.photoncamera.ui.camera.views;

import android.animation.LayoutTransition;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;

import com.google.android.material.slider.Slider;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.capture.ZoomSliderMapper;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.control.Swipe;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.CameraFragmentViewModel;

/**
 * Owns the lens pill ({@code lens_zoom_bar}): a lens switcher that expands into
 * a zoom slider on pinch-to-zoom and collapses back after
 * {@link #COLLAPSE_DELAY_MS} of no zoom interaction.
 *
 * <p>The pill's position is user-configurable: {@link #POSITION_CENTER} keeps
 * the horizontal, screen-centered pill; {@link #POSITION_RIGHT} and
 * {@link #POSITION_LEFT} render it vertically docked to that edge, with the
 * zoom-lock pill directly above it and the slider/indicator stacked above the
 * pill, edge-aligned. The slider itself turns vertical in those modes
 * (unzoomed at the bottom, zoomed at the top). It can also remain visible
 * between interactions when the persistent-slider preference is enabled.
 * Position changes are applied through {@link #applyPosition(String, boolean)},
 * which can animate the transition.
 *
 * <p>The lens buttons stay clickable while expanded; the slider thumb tracks the
 * live effective zoom, and dragging it drives the same zoom path as the pinch
 * gesture but with {@code sticky=false} so it stays smooth and skips the lens
 * detent snap and hysteresis.
 * The total-zoom indicator above the pill is driven by data binding; only its
 * constraints are managed here.
 */
public class LensZoomBarController implements Swipe.ZoomGestureListener {
    /** Pill docked to the right edge, rendered vertically. */
    public static final String POSITION_RIGHT = "right";
    /** Pill centered horizontally, rendered horizontally. */
    public static final String POSITION_CENTER = "center";
    /** Pill docked to the left edge, rendered vertically. */
    public static final String POSITION_LEFT = "left";

    /** Idle time after the last zoom interaction before the slider collapses. */
    static final long COLLAPSE_DELAY_MS = 2000L;
    private static final int SLIDER_MAX = 1000;
    private static final float SLIDER_TRANSLATION_DP = 8f;

    private final ConstraintLayout container;
    private final LinearLayout bar;
    private final AuxButtonsLayout auxButtons;
    private final View sliderContainer;
    private final Slider slider;
    private final View lockPill;
    private final ImageButton lockButton;
    private final CaptureController captureController;
    private final CameraFragmentViewModel viewModel;
    private final Vibration haptics;
    private final ZoomHapticGate zoomHapticGate = new ZoomHapticGate();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable collapseRunnable = this::collapse;

    private boolean expanded;
    private boolean alwaysShowZoomBar;
    private boolean sliderTouched;
    private boolean settingsHidden;
    private boolean resumed;
    /** Tracks the visibility target so repeated layout callbacks don't restart animations. */
    private boolean barShown = true;
    private String currentPosition;

    public LensZoomBarController(ConstraintLayout container, LinearLayout bar, AuxButtonsLayout auxButtons,
                                 View sliderContainer, Slider slider, View lockPill, ImageButton lockButton,
                                 CaptureController captureController, CameraFragmentViewModel viewModel) {
        this.container = container;
        this.bar = bar;
        this.auxButtons = auxButtons;
        this.sliderContainer = sliderContainer;
        this.slider = slider;
        this.lockPill = lockPill;
        this.lockButton = lockButton;
        this.captureController = captureController;
        this.viewModel = viewModel;
        this.haptics = PhotonCamera.getVibration();
    }

    public void init() {
        // The container animates child visibility changes by itself, and its
        // disappear pass resets alpha to 1 before fading out; that fights our
        // own fades (the pill/slider flash back in before hiding). Every
        // visibility-changing child of the container owns its animation, so
        // drop the appear/disappear passes and keep CHANGING for repositioning.
        LayoutTransition transition = container.getLayoutTransition();
        if (transition != null) {
            transition.disableTransitionType(LayoutTransition.APPEARING);
            transition.disableTransitionType(LayoutTransition.DISAPPEARING);
        }
        syncEffectiveLockState();
        updateLockIcon();
        lockButton.setOnClickListener(v -> toggleLock());
        slider.setValueFrom(0f);
        slider.setValueTo(SLIDER_MAX);
        slider.addOnChangeListener((seekBar, value, fromUser) -> {
            if (!fromUser || !isZoomSliderInteractive() || CaptureController.isProcessing) return;
            float zoom = ZoomSliderMapper.progressToZoom(Math.round(value), SLIDER_MAX,
                    captureController.getMinZoom(), captureController.getMaxZoom());
            // The slider is smooth: it skips the pinch detent snap and
            // hysteresis so dragging feels continuous, but still
            // auto-switches lenses exactly at the native boundary.
            captureController.setZoom(zoom, 0.5f, 0.5f, false);
            zoomHaptic(captureController.getZoomRatio());
            viewModel.setZoomRatio(captureController.getZoomRatio());
            viewModel.setZoomOffNative(
                    !captureController.isZoomOnNative(captureController.getZoomRatio()));
            if (expanded) scheduleCollapse();
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider seekBar) {
                if (!resumed || settingsHidden) {
                    sliderTouched = false;
                    return;
                }
                // The user grabbed the slider: hold the expanded state while dragging.
                sliderTouched = true;
                zoomHapticGate.prime(captureController.getZoomRatio());
                handler.removeCallbacks(collapseRunnable);
                if (!expanded && !shouldShowPersistentSlider()) expand();
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider seekBar) {
                sliderTouched = false;
                if (haptics != null) haptics.confirm();
                if (expanded) scheduleCollapse();
            }
        });
        // The lens set changes when the facing flips or lenses load; re-evaluate
        // whether the pill should be shown (single-lens devices hide it when collapsed).
        auxButtons.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateBarVisibility(true));
        applyPosition(PreferenceKeys.getLensBarPosition(), false);
        setAlwaysShowZoomBar(PreferenceKeys.isAlwaysShowZoomBarOn(), false);
    }

    /**
     * Applies the configured pill position ({@code "right"}, {@code "center"} or
     * {@code "left"}). Unknown values fall back to {@code "right"}. When
     * {@code animate} is set and the position actually changes, the pill,
     * lock pill, slider and indicator glide to their new anchors.
     */
    public void applyPosition(String position, boolean animate) {
        String normalized = normalize(position);
        if (normalized.equals(currentPosition)) {
            // Constraints are already correct; re-sync visibility so a pill
            // hidden by onPause() animates back in on resume.
            updateBarVisibility(animate);
            return;
        }
        boolean vertical = !POSITION_CENTER.equals(normalized);
        boolean left = POSITION_LEFT.equals(normalized);
        if (animate && currentPosition != null) {
            TransitionManager.beginDelayedTransition(container, new ChangeBounds()
                    .setDuration(Motion.durationMedium1(container.getContext()))
                    .setInterpolator(Motion.emphasized(container.getContext())));
        }
        bar.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        auxButtons.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        auxButtons.setVerticalOrder(vertical);
        applySliderGeometry(vertical);
        ConstraintSet set = new ConstraintSet();
        set.clone(container);
        if (vertical) {
            applyVerticalConstraints(set, left);
        } else {
            applyCenteredConstraints(set);
        }
        set.applyTo(container);
        currentPosition = normalized;
        updateBarVisibility(animate);
    }

    private static String normalize(String position) {
        if (POSITION_LEFT.equals(position)) return POSITION_LEFT;
        if (POSITION_CENTER.equals(position)) return POSITION_CENTER;
        return POSITION_RIGHT;
    }

    /**
     * Applies the opt-in persistent zoom-slider setting without changing the
     * independent lens-pill or zoom-lock visibility rules.
     */
    public void setAlwaysShowZoomBar(boolean enabled, boolean animate) {
        alwaysShowZoomBar = enabled;
        handler.removeCallbacks(collapseRunnable);
        if (shouldShowPersistentSlider()) {
            showSlider(animate);
        } else if (enabled) {
            // Quick settings owns the screen while it is open.
            resetSlider();
        } else {
            expanded = false;
            if (sliderContainer.getVisibility() == View.VISIBLE) {
                hideSlider(animate);
            }
        }
        updateBarVisibility(animate);
    }

    /**
     * Vertical modes render the auto-hiding slider vertically too: the container
     * becomes a tall capsule and the slider inside is rotated counter-clockwise
     * so the start (unzoomed) sits at the bottom and the end (zoomed) at the top.
     * The slider's height is always the capsule thickness, so the row matches
     * the lens pill's min dimension in every mode.
     */
    private void applySliderGeometry(boolean vertical) {
        int lengthPx = (int) container.getResources().getDimension(R.dimen.lens_zoom_slider_width);
        int thicknessPx = (int) container.getResources().getDimension(R.dimen.lens_zoom_slider_thickness);
        ViewGroup.LayoutParams containerParams = sliderContainer.getLayoutParams();
        ViewGroup.LayoutParams sliderParams = slider.getLayoutParams();
        if (vertical) {
            containerParams.width = thicknessPx;
            containerParams.height = lengthPx;
            sliderParams.width = lengthPx;
            sliderParams.height = thicknessPx;
            if (sliderParams instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) sliderParams).gravity = Gravity.CENTER;
            }
            slider.setRotation(-90f);
        } else {
            containerParams.width = lengthPx;
            containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            sliderParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            sliderParams.height = thicknessPx;
            slider.setRotation(0f);
        }
        sliderContainer.setLayoutParams(containerParams);
        slider.setLayoutParams(sliderParams);
    }

    /** Docked to the edge: vertical pill, lock pill above it, slider/indicator above the stack. */
    private void applyVerticalConstraints(ConstraintSet set, boolean left) {
        int edge = (int) container.getResources().getDimension(R.dimen.aux_container_margin);
        int gap = (int) container.getResources().getDimension(R.dimen.lens_zoom_stack_gap);
        int edgeAnchor = left ? ConstraintSet.START : ConstraintSet.END;
        int innerAnchor = left ? ConstraintSet.END : ConstraintSet.START;

        set.connect(R.id.lens_zoom_bar, edgeAnchor, R.id.camera_container, edgeAnchor);
        set.clear(R.id.lens_zoom_bar, innerAnchor);
        set.setMargin(R.id.lens_zoom_bar, edgeAnchor, edge);
        set.setMargin(R.id.lens_zoom_bar, innerAnchor, 0);

        set.connect(R.id.zoom_lock_pill, ConstraintSet.BOTTOM, R.id.lens_zoom_bar, ConstraintSet.TOP);
        set.connect(R.id.zoom_lock_pill, edgeAnchor, R.id.lens_zoom_bar, edgeAnchor);
        set.clear(R.id.zoom_lock_pill, innerAnchor);
        set.clear(R.id.zoom_lock_pill, ConstraintSet.TOP);
        set.setMargin(R.id.zoom_lock_pill, ConstraintSet.BOTTOM, gap);
        set.setMargin(R.id.zoom_lock_pill, edgeAnchor, 0);
        set.setMargin(R.id.zoom_lock_pill, innerAnchor, 0);

        set.connect(R.id.zoom_slider_container, ConstraintSet.BOTTOM, R.id.zoom_lock_pill, ConstraintSet.TOP);
        set.connect(R.id.zoom_slider_container, edgeAnchor, R.id.lens_zoom_bar, edgeAnchor);
        set.clear(R.id.zoom_slider_container, innerAnchor);
        set.setMargin(R.id.zoom_slider_container, ConstraintSet.BOTTOM, gap);

        set.connect(R.id.zoom_indicator, ConstraintSet.BOTTOM, R.id.zoom_slider_container, ConstraintSet.TOP);
        set.connect(R.id.zoom_indicator, edgeAnchor, R.id.lens_zoom_bar, edgeAnchor);
        set.clear(R.id.zoom_indicator, innerAnchor);
    }

    /** Centered horizontal pill with the lock pill beside it (original layout). */
    private void applyCenteredConstraints(ConstraintSet set) {
        int gap = (int) container.getResources().getDimension(R.dimen.lens_zoom_stack_gap);

        set.connect(R.id.lens_zoom_bar, ConstraintSet.START, R.id.camera_container, ConstraintSet.START);
        set.connect(R.id.lens_zoom_bar, ConstraintSet.END, R.id.camera_container, ConstraintSet.END);
        set.setHorizontalBias(R.id.lens_zoom_bar, 0.5f);
        set.setMargin(R.id.lens_zoom_bar, ConstraintSet.START, 0);
        set.setMargin(R.id.lens_zoom_bar, ConstraintSet.END, 0);

        set.connect(R.id.zoom_lock_pill, ConstraintSet.START, R.id.lens_zoom_bar, ConstraintSet.END);
        set.connect(R.id.zoom_lock_pill, ConstraintSet.TOP, R.id.lens_zoom_bar, ConstraintSet.TOP);
        set.connect(R.id.zoom_lock_pill, ConstraintSet.BOTTOM, R.id.lens_zoom_bar, ConstraintSet.BOTTOM);
        set.clear(R.id.zoom_lock_pill, ConstraintSet.END);
        set.setMargin(R.id.zoom_lock_pill, ConstraintSet.START, gap);
        set.setMargin(R.id.zoom_lock_pill, ConstraintSet.BOTTOM, 0);

        set.connect(R.id.zoom_slider_container, ConstraintSet.BOTTOM, R.id.lens_zoom_bar, ConstraintSet.TOP);
        set.connect(R.id.zoom_slider_container, ConstraintSet.START, R.id.lens_zoom_bar, ConstraintSet.START);
        set.connect(R.id.zoom_slider_container, ConstraintSet.END, R.id.lens_zoom_bar, ConstraintSet.END);
        set.setMargin(R.id.zoom_slider_container, ConstraintSet.BOTTOM, gap);

        set.connect(R.id.zoom_indicator, ConstraintSet.BOTTOM, R.id.zoom_slider_container, ConstraintSet.TOP);
        set.connect(R.id.zoom_indicator, ConstraintSet.START, R.id.lens_zoom_bar, ConstraintSet.START);
        set.connect(R.id.zoom_indicator, ConstraintSet.END, R.id.lens_zoom_bar, ConstraintSet.END);
    }

    /** Called on every handled pinch-to-zoom movement. Expands and restarts the 2s timer. */
    @Override
    public void onZoomGesture() {
        if (!resumed || settingsHidden) return;
        zoomHaptic(captureController.getZoomRatio());
        if (!expanded) expand();
        syncSlider();
        scheduleCollapse();
    }

    /** Primes the fallback gate so a pinch only ticks once the indicator steps. */
    @Override
    public void onZoomGestureStart() {
        zoomHapticGate.prime(captureController.getZoomRatio());
    }

    /**
     * Zoom detent feedback: composed primitives play on every movement, while
     * fallback effects are gated to displayed indicator steps.
     */
    private void zoomHaptic(float zoomRatio) {
        if (haptics == null) return;
        if (haptics.usesComposedPrimitives()) {
            haptics.zoomDetent();
            return;
        }
        if (zoomHapticGate.accept(zoomRatio)) {
            haptics.zoomDetent();
        }
    }

    /** Called when the bound zoom ratio changes (pinch, slider, lens switch). */
    public void onZoomChanged(float zoomRatio) {
        if (resumed && !settingsHidden && !sliderTouched
                && sliderContainer.getVisibility() == View.VISIBLE) {
            slider.setValue(ZoomSliderMapper.zoomToProgress(zoomRatio, SLIDER_MAX,
                    captureController.getMinZoom(), captureController.getMaxZoom()));
        }
    }

    /** Called after the lens set or active camera changes (range may have changed). */
    public void refreshZoomRange() {
        syncEffectiveLockState();
        syncSlider();
        if (shouldShowPersistentSlider()) showSlider(false);
        updateBarVisibility(true);
        if (expanded) scheduleCollapse();
    }

    public void setSettingsHidden(boolean hidden) {
        // BR._all notifications are frequent (bitmap/orientation updates). Do not
        // treat repeated false notifications as a new visibility transition, or
        // they would cancel a pending auto-hide without rescheduling it.
        if (settingsHidden == hidden) return;
        settingsHidden = hidden;
        if (hidden) {
            handler.removeCallbacks(collapseRunnable);
            expanded = false;
            resetSlider();
        } else if (shouldShowPersistentSlider()) {
            showSlider(true);
        }
        updateBarVisibility(true);
    }

    /** Marks the controller active; CameraFragment reapplies the preference afterward. */
    public void onResume() {
        resumed = true;
    }

    public void onPause() {
        resumed = false;
        handler.removeCallbacks(collapseRunnable);
        expanded = false;
        zoomHapticGate.reset();
        resetSlider();
        updateBarVisibility(false);
    }

    private void resetSlider() {
        sliderTouched = false;
        sliderContainer.animate().cancel();
        sliderContainer.setAlpha(1f);
        sliderContainer.setTranslationY(0f);
        sliderContainer.setVisibility(View.GONE);
    }

    private boolean shouldShowPersistentSlider() {
        return resumed && alwaysShowZoomBar && !settingsHidden;
    }

    private boolean isZoomSliderInteractive() {
        return sliderContainer.getVisibility() == View.VISIBLE
                && resumed && !settingsHidden && (expanded || shouldShowPersistentSlider());
    }

    private void showSlider(boolean animate) {
        boolean wasVisible = sliderContainer.getVisibility() == View.VISIBLE;
        sliderContainer.animate().cancel();
        sliderContainer.setVisibility(View.VISIBLE);
        if (animate) {
            if (!wasVisible) {
                sliderContainer.setAlpha(0f);
                sliderContainer.setTranslationY(translationPx());
            }
            sliderContainer.animate().alpha(1f).translationY(0f)
                    .setDuration(Motion.durationShort3(sliderContainer.getContext()))
                    .setInterpolator(Motion.emphasized(sliderContainer.getContext())).start();
        } else {
            sliderContainer.setAlpha(1f);
            sliderContainer.setTranslationY(0f);
        }
        syncSlider();
    }

    private void hideSlider(boolean animate) {
        sliderContainer.animate().cancel();
        if (!animate) {
            resetSlider();
            return;
        }
        sliderContainer.animate().alpha(0f).translationY(translationPx())
                .setDuration(Motion.durationShort2(sliderContainer.getContext()))
                .setInterpolator(Motion.emphasizedDecelerate(sliderContainer.getContext()))
                .withEndAction(() -> {
                    if (!expanded && !shouldShowPersistentSlider()) {
                        sliderContainer.setVisibility(View.GONE);
                        sliderContainer.setAlpha(1f);
                        sliderContainer.setTranslationY(0f);
                    }
                }).start();
    }

    private void expand() {
        expanded = true;
        showSlider(true);
        updateBarVisibility(true);
        scheduleCollapse();
    }

    private void collapse() {
        expanded = false;
        if (shouldShowPersistentSlider()) {
            showSlider(false);
        } else {
            hideSlider(true);
        }
        updateBarVisibility(true);
    }

    private float translationPx() {
        return SLIDER_TRANSLATION_DP * sliderContainer.getResources().getDisplayMetrics().density;
    }

    private void scheduleCollapse() {
        handler.removeCallbacks(collapseRunnable);
        handler.postDelayed(collapseRunnable, COLLAPSE_DELAY_MS);
    }

    private void syncSlider() {
        slider.setValue(ZoomSliderMapper.zoomToProgress(viewModel.getZoomRatio(), SLIDER_MAX,
                captureController.getMinZoom(), captureController.getMaxZoom()));
    }

    private void updateBarVisibility(boolean animate) {
        // Single-lens devices keep the current behaviour (no pill) until a pinch
        // reveals the slider; the settings bar hides the whole pill.
        // The lock pill follows the lens pill (a lock is meaningless with one lens),
        // and stays hidden in video logical mode (member switches never reopen).
        boolean show = !settingsHidden && (expanded || auxButtons.getChildCount() > 1);
        boolean showLock = show && !isVideoLogicalActive() && PreferenceKeys.isAutoZoomSwitchOn();
        if (show == barShown) {
            // The lens pill is unchanged, but the lock pill may still need a
            // visibility update (e.g. entering/leaving logical mode).
            syncLockVisibility(animate, showLock);
            return;
        }
        barShown = show;
        if (show) {
            if (animate) {
                animateIn(bar);
                syncLockPillPivot();
            } else {
                showNow(bar);
            }
            if (showLock) {
                if (animate) {
                    animateIn(lockPill);
                } else {
                    showNow(lockPill);
                }
            } else {
                hideLockPill(false);
            }
        } else if (animate) {
            animateOut(bar);
            syncLockPillPivot();
            animateOut(lockPill);
        } else {
            bar.animate().cancel();
            lockPill.animate().cancel();
            bar.setVisibility(View.GONE);
            lockPill.setVisibility(View.GONE);
        }
    }

    private boolean isVideoLogicalActive() {
        try {
            return captureController != null && captureController.isVideoLogicalActive();
        } catch (Exception e) {
            return false;
        }
    }

    /** Updates only the lock pill when the lens pill visibility is unchanged. */
    private void syncLockVisibility(boolean animate, boolean showLock) {
        boolean lockShown = lockPill.getVisibility() == View.VISIBLE;
        if (showLock == lockShown) return;
        if (showLock) {
            syncLockPillPivot();
            if (animate) {
                animateIn(lockPill);
            } else {
                showNow(lockPill);
            }
        } else {
            hideLockPill(animate);
        }
    }

    /** Hides the lock pill independently of the lens pill state. */
    private void hideLockPill(boolean animate) {
        if (animate) {
            syncLockPillPivot();
            lockPill.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f)
                    .setDuration(Motion.durationShort4(lockPill.getContext()))
                    .setInterpolator(Motion.emphasizedDecelerate(lockPill.getContext()))
                    .withEndAction(() -> lockPill.setVisibility(View.GONE)).start();
        } else {
            lockPill.animate().cancel();
            lockPill.setVisibility(View.GONE);
        }
    }

    /**
     * Scales the lock pill around the lens pill's centre (instead of its own),
     * so on show/hide the two read as one rigid unit — the lock pill glides
     * toward the lens pill as it shrinks rather than shrinking in place.
     */
    private void syncLockPillPivot() {
        if (bar.getWidth() <= 0 || bar.getHeight() <= 0) {
            return;
        }
        lockPill.setPivotX(bar.getLeft() + bar.getWidth() / 2f - lockPill.getLeft());
        lockPill.setPivotY(bar.getTop() + bar.getHeight() / 2f - lockPill.getTop());
    }

    private void showNow(View view) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setVisibility(View.VISIBLE);
    }

    private void animateIn(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0f);
            view.setScaleX(0.85f);
            view.setScaleY(0.85f);
        }
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(Motion.durationShort4(view.getContext()))
                .setInterpolator(Motion.emphasized(view.getContext()))
                .start();
    }

    private void animateOut(View view) {
        view.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f)
                .setDuration(Motion.durationShort4(view.getContext()))
                .setInterpolator(Motion.emphasizedDecelerate(view.getContext()))
                .withEndAction(() -> {
                    if (!barShown) view.setVisibility(View.GONE);
                }).start();
    }

    private void toggleLock() {
        boolean locked = !PreferenceKeys.isZoomLockOn();
        if (haptics != null) haptics.toggle(locked);
        captureController.setLensSwitchLocked(locked);
        PreferenceKeys.setZoomLock(locked);
        syncEffectiveLockState();
        updateLockIcon();
        // The zoom range changed (full facing range vs current lens window).
        refreshZoomRange();
    }

    /**
     * Effective zoom-lock state: locked when the auto-switch setting is off
     * or the pill lock is on. Re-read on every open so Settings changes
     * apply without restart.
     */
    private void syncEffectiveLockState() {
        try {
            captureController.setLensSwitchLocked(
                    !PreferenceKeys.isAutoZoomSwitchOn() || PreferenceKeys.isZoomLockOn());
        } catch (Exception e) {
            // Controller not ready; init/refresh paths retry later.
        }
    }

    private void updateLockIcon() {
        boolean locked = captureController.isLensSwitchLocked();
        lockButton.setImageResource(locked ? R.drawable.ic_zoom_lock : R.drawable.ic_zoom_lock_open);
        lockButton.setContentDescription(lockButton.getContext().getString(
                locked ? R.string.zoom_unlock_desc : R.string.zoom_lock_desc));
    }
}
