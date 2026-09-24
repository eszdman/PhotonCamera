package com.particlesdevs.photoncamera.ui.camera;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.widget.TextView;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraManager2;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.databinding.LayoutBottombuttonsBinding;
import com.particlesdevs.photoncamera.databinding.LayoutMainTopbarBinding;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.TunableInjector;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.ViewfinderFrameView;
import com.particlesdevs.photoncamera.ui.widget.ShutterFaceDrawable;
import com.particlesdevs.photoncamera.util.Utilities;

import java.util.ArrayList;
import java.util.List;

import static androidx.constraintlayout.widget.ConstraintSet.GONE;

/**
 * This Class is a dumb 'View' which contains view components visible in the main Camera User Interface
 * <p>
 * It gets instantiated in {@link CameraFragment#onViewCreated(View, Bundle)}
 */
public class CameraUIViewImpl implements CameraUIView {
    private static final String TAG = "CameraUIView";

    @Tunable(
            title = "Enable Quad Resolution",
            description = "Show Quad Resolution toggle in camera controls. When off, Quad Res is forced disabled.",
            category = "UI",
            min = 0.0f,
            max = 1.0f,
            defaultValue = 0.0f,
            step = 1.0f
    )
    boolean enableQuadRes = false;

    private final CameraFragment cameraFragment;
    private final ProgressBar mCaptureProgressBar;
    private final ImageButton mShutterButton;
    private final ProgressBar mProcessingProgressBar;
    private final HorizontalPicker mModePicker;
    private final TextView mVideoRecordingInfo;
    private final ShutterFaceDrawable mShutterFace;
    private LayoutMainTopbarBinding topbar;
    private LayoutBottombuttonsBinding bottombuttons;
    private CameraUIEventsListener uiEventsListener;
    private CameraModeState currentState;
    private List<CameraMode> mVisibleModes;

    CameraUIViewImpl(CameraFragment cameraFragment) {
        this.cameraFragment = cameraFragment;
        this.topbar = cameraFragment.cameraFragmentBinding.layoutTopbar;
        this.bottombuttons = cameraFragment.cameraFragmentBinding.layoutBottombar.bottomButtons;
        this.mCaptureProgressBar = cameraFragment.cameraFragmentBinding.layoutViewfinder.captureProgressBar;
        this.mProcessingProgressBar = bottombuttons.processingProgressBar;
        this.mShutterButton = bottombuttons.shutterButton;
        this.mShutterFace = new ShutterFaceDrawable(cameraFragment.requireContext());
        this.mShutterButton.setBackground(mShutterFace);
        this.mModePicker = cameraFragment.cameraFragmentBinding.layoutBottombar.modeSwitcher.modePickerView;
        this.mVideoRecordingInfo = cameraFragment.cameraFragmentBinding.getRoot().findViewById(R.id.video_recording_info);
        this.initListeners();
        this.initModeSwitcher();
        this.currentState = new PhotoMotionModeState(); //init mode
        initModeState(CameraMode.valueOf(PreferenceKeys.getCameraModeOrdinal()));
    }

    private void initModeState(CameraMode mode) {
        switch (mode) {
            case VIDEO:
                currentState = new VideoModeState();
                break;
            case UNLIMITED:
            case RAWVIDEO:
                currentState = new UnlimitedModeState();
                break;
            case NIGHT:
                currentState = new NightModeState();
                break;
            default:
                currentState = new PhotoMotionModeState();
                break;
        }
        currentState.reConfigureModeViews(mode);
    }

    /**
     * Quad Bayer controls are unavailable when the tunable disables them or on
     * an ISZ virtual lens, where Quad Bayer is always off.
     */
    private boolean isQuadResAvailable() {
        return enableQuadRes && !CameraManager2.isIszVirtual(PreferenceKeys.getCameraID());
    }

    private void initListeners() {
        TunableInjector.inject(this);
        if (!enableQuadRes) {
            PreferenceKeys.setQuadBayer(false);
        }
        this.topbar.setTopBarClickListener(v -> this.uiEventsListener.onClick(v));
        this.bottombuttons.setBottomBarClickListener(v -> this.uiEventsListener.onClick(v));
        this.topbar.setQuadVisible(isQuadResAvailable());
    }

    private void initModeSwitcher() {
        mVisibleModes = PreferenceKeys.getVisibleModes();
        CameraMode current = CameraMode.valueOf(PreferenceKeys.getCameraModeOrdinal());
        if (!mVisibleModes.contains(current)) {
            current = PreferenceKeys.getFallbackMode(current);
            PreferenceKeys.setCameraModeOrdinal(current.ordinal());
        }
        updateModePickerValues();
        this.mModePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        this.mModePicker.setOnItemSelectedListener(index -> {
            if (index < 0 || index >= mVisibleModes.size()) {
                return;
            }
            switchToMode(mVisibleModes.get(index));
        });
        this.mModePicker.setSelectedItem(mVisibleModes.indexOf(current));
    }

    /**
     * Rebuilds the picker labels from the currently visible modes. Called on
     * init and on {@link #refresh(boolean)} so changes made in Settings apply
     * without restarting the camera.
     */
    private void updateModePickerValues() {
        mVisibleModes = PreferenceKeys.getVisibleModes();
        Integer[] allNameIds = CameraMode.nameIds();
        String[] names = new String[mVisibleModes.size()];
        for (int i = 0; i < mVisibleModes.size(); i++) {
            names[i] = cameraFragment.activity.getString(allNameIds[mVisibleModes.get(i).ordinal()]);
        }
        this.mModePicker.setValues(names);
    }

    /**
     * Ensures the persisted mode is visible, falling back to the next visible
     * mode when the selected one was hidden. Returns the effective mode.
     */
    private CameraMode resolveVisibleMode() {
        mVisibleModes = PreferenceKeys.getVisibleModes();
        CameraMode current = CameraMode.valueOf(PreferenceKeys.getCameraModeOrdinal());
        if (!mVisibleModes.contains(current)) {
            current = PreferenceKeys.getFallbackMode(current);
            PreferenceKeys.setCameraModeOrdinal(current.ordinal());
        }
        return current;
    }

    @Override
    public void activateShutterButton(boolean status) {
        this.mShutterButton.post(() -> {
            this.mShutterButton.setActivated(status);
            this.mShutterButton.setClickable(status);
        });
    }

    @Override
    public void setShutterRecording(boolean recording) {
        if (mShutterFace != null) {
            mShutterFace.setRecording(recording);
        }
        if (mShutterButton != null) {
            mShutterButton.post(() -> mShutterButton.setContentDescription(
                    cameraFragment.getString(recording
                            ? R.string.stop_recording_desc
                            : R.string.record_video_desc)));
        }
    }

    /**
     * Switches the shared shutter face to the record design (video, unlimited
     * and RAW video modes) and syncs it with the live capture state so refreshes
     * during a recording don't reset it to idle. The face is never swapped as a
     * background: it morphs, so the photo/video change animates.
     */
    private void syncShutterFace() {
        mShutterFace.refreshColors(cameraFragment.requireContext());
        mShutterFace.setMode(true);
        boolean recording = cameraFragment.captureController != null
                && (cameraFragment.captureController.mIsRecordingVideo
                        || cameraFragment.captureController.onUnlimited);
        setShutterRecording(recording);
    }


    private void switchToMode(CameraMode cameraMode) {
        Log.d(TAG, "Current Mode:" + cameraMode.name());
        switch (cameraMode) {
            case VIDEO:
                currentState = new VideoModeState();
                break;
            case UNLIMITED:
            case RAWVIDEO:
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

        // Animate the mode's layout changes with translation FLIPs instead of a
        // TransitionManager (its delayed transition suppresses layout for the
        // whole tree, freezing the viewfinder's stretch) or a per-frame dummy
        // aspect animation (re-solving the whole container every frame starved
        // the shutter and mode-picker animations). The viewfinder stretch
        // starts with the rest of the UI; the camera reopens behind the held
        // capture.
        cameraFragment.beginAspectSwitchForMode(cameraMode);
        List<View> flippedViews = modeSwitchFlippedViews();
        int[] flippedTops = new int[flippedViews.size()];
        for (int i = 0; i < flippedViews.size(); i++) {
            flippedTops[i] = flippedViews.get(i).getTop();
        }
        currentState.reConfigureModeViews(cameraMode);
        flipModeSwitchViews(flippedViews, flippedTops);
        if (uiEventsListener != null) uiEventsListener.onCameraModeChanged(cameraMode);
    }

    /** Every view a mode switch may offset, whether visible in it or not. */
    private static final int[] MODE_SWITCH_FLIP_IDS = {
            R.id.camera_container,
            R.id.layout_bottombar,
            R.id.lens_zoom_bar,
            R.id.zoom_slider_container,
            R.id.zoom_indicator,
            R.id.zoom_lock_pill,
            // The manual-palette opener hangs off the same bottom-bar anchor,
            // so it must glide with the bar instead of jumping when the anchor
            // moves.
            R.id.open_close_manual};

    /** Views whose layout position changes with the mode's anchors. */
    private List<View> modeSwitchFlippedViews() {
        View root = cameraFragment.cameraFragmentBinding.getRoot();
        List<View> views = new ArrayList<>(MODE_SWITCH_FLIP_IDS.length);
        for (int id : MODE_SWITCH_FLIP_IDS) {
            addIfVisible(views, root.findViewById(id));
        }
        return views;
    }

    private static void addIfVisible(List<View> views, View view) {
        if (view != null && view.getVisibility() == View.VISIBLE) {
            views.add(view);
        }
    }

    /**
     * Applies each view's layout delta as a translation as soon as the new
     * layout lands (in the pre-draw, before the frame is shown), then follows
     * the viewfinder's stretch progress to zero, so the chrome glides in
     * lockstep with the frame instead of arriving early — the bottom bar's top
     * must never cross the frame's bottom while the viewfinder shrinks. The
     * panel-blur regions read the translations, so the frosted backdrops
     * follow along.
     */
    private void flipModeSwitchViews(List<View> views, int[] tops) {
        if (views.isEmpty()) {
            return;
        }
        View root = cameraFragment.cameraFragmentBinding.getRoot();
        ViewfinderFrameView frame =
                cameraFragment.cameraFragmentBinding.layoutViewfinder.viewfinderFrame;
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                // Drop any listener left by an interrupted switch before this
                // one installs its own deltas.
                frame.setProgressListener(null);
                if (!frame.isAspectAnimating()) {
                    // No stretch to follow (the aspect did not change, or it
                    // snapped): the views belong at their final positions.
                    clearModeSwitchTranslations(root);
                    return true;
                }
                float progress = frame.getStretchProgress();
                for (int i = 0; i < views.size(); i++) {
                    View view = views.get(i);
                    view.setTranslationY((tops[i] - view.getTop()) * (1f - progress));
                }
                frame.setProgressListener(stretchProgress -> {
                    float remaining = 1f - stretchProgress;
                    for (int i = 0; i < views.size(); i++) {
                        View view = views.get(i);
                        // The delta is re-anchored every frame: a view can
                        // re-lay out while the switch is still gliding (the new
                        // camera's lens set arrives mid-switch), and a delta
                        // frozen at the switch would then start the glide from a
                        // position the view was never at.
                        view.setTranslationY((tops[i] - view.getTop()) * remaining);
                    }
                    if (stretchProgress >= 1f) {
                        frame.setProgressListener(null);
                        // Also clear candidates that were hidden for this
                        // switch: a stale translation would shift them when
                        // they reappear.
                        clearModeSwitchTranslations(root);
                    }
                });
                return true;
            }
        });
    }

    /** Zeroes the mode-switch translation on every candidate view. */
    private static void clearModeSwitchTranslations(View root) {
        for (int id : MODE_SWITCH_FLIP_IDS) {
            View view = root.findViewById(id);
            if (view != null) {
                view.setTranslationY(0f);
            }
        }
    }

    /**
     * Crossfades the root background to a new mode gradient instead of swapping
     * it instantly. Nested TransitionDrawables are flattened first, so repeated
     * mode switches cannot stack layers.
     */
    private void animateRootBackground(@Nullable Drawable next) {
        View root = cameraFragment.cameraFragmentBinding.getRoot();
        if (next == null) {
            return;
        }
        Drawable current = root.getBackground();
        if (current == null) {
            root.setBackground(next);
            return;
        }
        if (current instanceof TransitionDrawable) {
            TransitionDrawable previous = (TransitionDrawable) current;
            int layerCount = previous.getNumberOfLayers();
            if (layerCount > 0) {
                current = previous.getDrawable(layerCount - 1);
            }
        }
        TransitionDrawable crossfade = new TransitionDrawable(new Drawable[]{current, next});
        crossfade.setCrossFadeEnabled(true);
        root.setBackground(crossfade);
        crossfade.startTransition((int) Motion.durationMedium2(root.getContext()));
    }

    private void animateRootBackground(@DrawableRes int resId) {
        // Load as a drawable resource: Utilities.resolveDrawable() resolves a
        // theme attribute, so a drawable id would resolve to 0 and crash.
        animateRootBackground(AppCompatResources.getDrawable(
                cameraFragment.requireContext(), resId));
    }

    private void toggleConstraints(CameraMode mode) {
        // Gated to <=16:9 screens on purpose: on taller screens the 16:9
        // column is arranged by adjustTopBar (topbar pushed down) with
        // camera_container below it. Yanking the container full-bleed there
        // stretches the finder under the topbar and leaves the bottom scrim
        // overlapping the video.
        if (cameraFragment.displayAspectRatio <= 16f / 9f) {
            ConstraintLayout.LayoutParams camera_containerLP =
                    (ConstraintLayout.LayoutParams) cameraFragment.cameraFragmentBinding
                            .textureHolder
                            .findViewById(R.id.camera_container)
                            .getLayoutParams();
            switch (mode) {
                case RAWVIDEO:
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

    /**
     * Bottom chrome for 16:9/video layouts: no scrim behind the button row
     * so the viewfinder shows through between the buttons; the mode
     * selector's own background follows the viewfinder background option
     * (black for none, theme gradient for gradient, transparent for blurred
     * edges). Photo 4:3 layouts clear the selector and float over the themed
     * root instead.
     */
    private void applyBottomChrome(boolean scrimmed) {
        android.view.View buttons =
                cameraFragment.cameraFragmentBinding.layoutBottombar.bottomButtons.getRoot();
        android.view.View selector =
                cameraFragment.cameraFragmentBinding.layoutBottombar.modeSwitcher.getRoot();
        buttons.setBackground(null);
        if (scrimmed) {
            String bg = PreferenceKeys.getViewfinderBackground();
            if (PreferenceKeys.VIEWFINDER_BACKGROUND_GRADIENT.equals(bg)) {
                selector.setBackgroundResource(R.drawable.gradient_vector);
            } else if (PreferenceKeys.VIEWFINDER_BACKGROUND_BLUR.equals(bg)) {
                selector.setBackground(null);
            } else {
                selector.setBackgroundColor(0xFF000000);
            }
        } else {
            selector.setBackground(null);
        }
    }

    /**
     * Anchors the bottom bar below the 16:9 finder in video modes. This is the
     * same geometry the aspect169 photo mode uses; the indirection exists so
     * the intent ("16:9 video layout") reads at the call site instead of a
     * magic ratio string.
     */
    private void setVideoDummyAspect() {
        if (cameraFragment.displayAspectRatio <= 16f / 9f)
            cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("3:4");
        else {
            float avg = ((4f / 3f) + (16f / 9f)) / 2f;
            cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio(String.valueOf(1.0f / avg));
        }
    }

    @Override
    public void refresh(boolean processing) {
        TunableInjector.inject(this);
        if (!enableQuadRes) {
            PreferenceKeys.setQuadBayer(false);
        }
        this.topbar.setQuadVisible(isQuadResAvailable());
        cameraFragment.cameraFragmentBinding.invalidateAll();
        CameraMode current = resolveVisibleMode();
        updateModePickerValues();
        // The picker's own scroll is the source of truth for the mode: re-seat
        // it only when it is somewhere else, and never while the user is
        // dragging or its snap is still settling (that snapped the selector
        // back under the finger right after a switch).
        int targetIndex = mVisibleModes.indexOf(current);
        if (targetIndex >= 0 && mModePicker.getSelectedItem() != targetIndex
                && !mModePicker.isUserScrolling()) {
            this.mModePicker.setSelectedItem(targetIndex);
        }
        currentState.reConfigureModeViews(current);
        this.resetCaptureProgressBar();
        if (!processing) {
            this.activateShutterButton(true);
            this.setProcessingProgressBarIndeterminate(false);
            this.lockUIForBurst(false);
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
    public void lockUIForBurst(boolean locked) {

        // Lock/unlock bottom bar buttons (except shutter button)
        if (this.bottombuttons != null) {
                this.bottombuttons.galleryImageButton.post(() -> this.bottombuttons.galleryImageButton.setEnabled(!locked));
            // Note: shutter button remains enabled for burst control
        }

        // Lock/unlock mode picker
        if (this.mModePicker != null) {
            this.mModePicker.post(() -> this.mModePicker.setEnabled(!locked));
        }

        // Lock/unlock aux buttons container - disable touch events
        if (cameraFragment.cameraFragmentBinding != null) {
            cameraFragment.cameraFragmentBinding.auxButtonsContainer.post(() -> {
                cameraFragment.cameraFragmentBinding.auxButtonsContainer.setEnabled(!locked);
                // Also set alpha to visually indicate disabled state
                cameraFragment.cameraFragmentBinding.auxButtonsContainer.setAlpha(locked ? 0.5f : 1.0f);
                cameraFragment.auxButtonsViewModel.setEnabled(!locked);
            });
        }

        // Lock/unlock settings bar - disable touch events and reduce alpha
        if (cameraFragment.cameraFragmentBinding != null) {
            cameraFragment.cameraFragmentBinding.settingsBar.post(() -> {
                cameraFragment.cameraFragmentBinding.settingsBar.setEnabled(!locked);
                cameraFragment.cameraFragmentBinding.settingsBar.setAlpha(locked ? 0.5f : 1.0f);
            });
        }

        // Lock/unlock manual mode console - disable swipe gestures
        if (cameraFragment.cameraFragmentBinding != null) {
            cameraFragment.cameraFragmentBinding.manualMode.post(() -> {
                cameraFragment.cameraFragmentBinding.manualMode.setEnabled(!locked);
                cameraFragment.cameraFragmentBinding.manualMode.setAlpha(locked ? 0.5f : 1.0f);
            });
        }

        // Lock/unlock touch focus by disabling the swipe controls
        if (cameraFragment.textureView != null) {
            // Disable touch events on the texture view to prevent focus/swipe during burst
            cameraFragment.textureView.post(() -> cameraFragment.textureView.setEnabled(!locked));
        }
    }

    @Override
    public void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener) {
        this.uiEventsListener = cameraUIEventsListener;
    }

    @Override
    @android.annotation.SuppressLint("DefaultLocale")
    public void updateVideoRecordingInfo(long elapsedMs, long estimatedBytes, long availableBytes) {
        if (mVideoRecordingInfo == null) return;
        long totalSeconds = elapsedMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        double estimatedGB = estimatedBytes / 1_073_741_824.0;
        double availableGB = availableBytes / 1_073_741_824.0;
        String text = String.format("● REC %02d:%02d  %.2f/%.1f GB", minutes, seconds, estimatedGB, availableGB);
        mVideoRecordingInfo.post(() -> {
            mVideoRecordingInfo.setText(text);
            mVideoRecordingInfo.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void setVideoRecordingInfoVisible(boolean visible) {
        if (mVideoRecordingInfo == null) return;
        mVideoRecordingInfo.post(() ->
                mVideoRecordingInfo.setVisibility(visible ? View.VISIBLE : View.GONE));
    }

    @Override
    public void destroy() {
        topbar = null;
        bottombuttons = null;
    }

    private void syncFpsButton() {
        try {
            cameraFragment.cameraFragmentBinding.layoutTopbar.fpsToggleButton
                    .setFpsModeState(PreferenceKeys.getCurrentLensVideoFpsMode());
        } catch (Exception e) {
            Log.w(TAG, "syncFpsButton failed", e);
        }
    }

    public class VideoModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setEisVisible(true);
            // cameraUIView.cameraFragmentBinding.textureHolder.setBackgroundResource(R.drawable.gradient_vector_video);
            // Video topbar shows frame rate where quad res sits elsewhere.
            topbar.setQuadVisible(false);
            topbar.setFpsVisible(true);
            topbar.setTimerVisible(false);
            syncFpsButton();
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.GONE);
            // Video mode: RAW, Quad Bayer, Battery Saver and Exposure bracketing do not apply.
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.saveraw_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.quad_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.batterysaver_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.bracketing_entry_layout, View.GONE);
            syncShutterFace();
            // VIDEO has its own REC badge (video_recording_info); the photo
            // countdown timer and burst progress ring do not apply here.
            cameraFragment.cameraFragmentBinding.layoutViewfinder.frameTimer.setVisibility(View.GONE);
            cameraFragment.cameraFragmentBinding.layoutViewfinder.captureProgressBar.setVisibility(View.GONE);
            setVideoRecordingInfoVisible(false);
            // Anchor the bottom bar below the 16:9 finder (same layout as the
            // aspect169 photo mode); see setVideoDummyAspect().
            setVideoDummyAspect();
            applyBottomChrome(true);
            animateRootBackground(R.drawable.gradient_vector_video);

            toggleConstraints(mode);
            cameraFragment.reassertManualPanelState();
        }
    }

    //
    public class UnlimitedModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setFpsVisible(false);
            topbar.setTimerVisible(false);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.saveraw_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.batterysaver_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.bracketing_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.quad_entry_layout, isQuadResAvailable() ? View.VISIBLE : View.GONE);
            syncShutterFace();
            if (mode == CameraMode.RAWVIDEO) {
                cameraFragment.cameraFragmentBinding.layoutViewfinder.frameTimer.setVisibility(View.GONE);
                cameraFragment.cameraFragmentBinding.layoutViewfinder.captureProgressBar.setVisibility(View.GONE);
            } else {
                cameraFragment.cameraFragmentBinding.layoutViewfinder.frameTimer.setVisibility(View.VISIBLE);
                cameraFragment.cameraFragmentBinding.layoutViewfinder.captureProgressBar.setVisibility(View.VISIBLE);
                setVideoRecordingInfoVisible(false);
            }
            if(PhotonCamera.getSettings().aspect169 || mode == CameraMode.RAWVIDEO) {
                // 16:9 video-style layout; see setVideoDummyAspect().
                setVideoDummyAspect();
                applyBottomChrome(true);
                animateRootBackground(R.drawable.gradient_vector_video);
            } else {
                cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("3:4");
                applyBottomChrome(false);
                animateRootBackground(Utilities.resolveDrawable(cameraFragment.requireActivity(), R.attr.cameraFragmentBackground));
            }
            toggleConstraints(mode);
            cameraFragment.reassertManualPanelState();
        }
    }

    //
    public class PhotoMotionModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setEisVisible(true);
            topbar.setFpsVisible(false);
            topbar.setTimerVisible(true);
            cameraFragment.cameraFragmentBinding.layoutViewfinder.frameTimer.setVisibility(View.VISIBLE);
            cameraFragment.cameraFragmentBinding.layoutViewfinder.captureProgressBar.setVisibility(View.VISIBLE);
            setVideoRecordingInfoVisible(false);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.eis_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.hdrx_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.saveraw_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.batterysaver_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.bracketing_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.quad_entry_layout, isQuadResAvailable() ? View.VISIBLE : View.GONE);
            mShutterFace.setMode(false);
            //cameraFragment.cameraFragmentBinding.layoutBottombar.layoutBottombar.setBackground(null);
            //cameraFragment.cameraFragmentBinding.getRoot().setBackground(Utilities.resolveDrawable(cameraFragment.requireActivity(), R.attr.cameraFragmentBackground));

            if(PhotonCamera.getSettings().aspect169) {
                // Set the dummy view's aspect ratio to 16:9
                if(cameraFragment.displayAspectRatio <= 16f / 9f)
                    cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("3:4");
                else {
                    float avg = ((4f/3f) + (16f / 9f)) / 2f;
                    cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio(String.valueOf(1.0f/avg));
                    //cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("0.580");
                }
                applyBottomChrome(true);
                animateRootBackground(R.drawable.gradient_vector_video);
            } else {
                cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("3:4");
                applyBottomChrome(false);
                animateRootBackground(Utilities.resolveDrawable(cameraFragment.requireActivity(), R.attr.cameraFragmentBackground));
            }

            toggleConstraints(mode);
            cameraFragment.reassertManualPanelState();
        }
    }

    public class NightModeState implements CameraModeState {
        @Override
        public void reConfigureModeViews(CameraMode mode) {
            topbar.setEisVisible(false);
            topbar.setFpsVisible(false);
            topbar.setTimerVisible(true);
            cameraFragment.cameraFragmentBinding.layoutViewfinder.frameTimer.setVisibility(View.VISIBLE);
            cameraFragment.cameraFragmentBinding.layoutViewfinder.captureProgressBar.setVisibility(View.VISIBLE);
            setVideoRecordingInfoVisible(false);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.eis_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.fps_entry_layout, View.GONE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.timer_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.saveraw_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.batterysaver_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.bracketing_entry_layout, View.VISIBLE);
            cameraFragment.cameraFragmentBinding.settingsBar.setChildVisibility(R.id.quad_entry_layout, isQuadResAvailable() ? View.VISIBLE : View.GONE);
            mShutterFace.setMode(false);
            if(PhotonCamera.getSettings().aspect169) {
                // Set the dummy view's aspect ratio to 16:9
                if(cameraFragment.displayAspectRatio <= 16f / 9f)
                    cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("3:4");
                else {
                    float avg = ((4f/3f) + (16f / 9f)) / 2f;
                    cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio(String.valueOf(1.0f/avg));
                    //cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("0.580");
                }
                applyBottomChrome(true);
                animateRootBackground(R.drawable.gradient_vector_video);
            } else {
                cameraFragment.cameraFragmentBinding.getUimodel().setDummyAspectRatio("3:4");
                applyBottomChrome(false);
                animateRootBackground(Utilities.resolveDrawable(cameraFragment.requireActivity(), R.attr.cameraFragmentBackground));
            }

            toggleConstraints(mode);
            cameraFragment.reassertManualPanelState();
        }
    }

}

