package com.particlesdevs.photoncamera.control;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.views.FocusCircleView;
import com.particlesdevs.photoncamera.ui.camera.views.viewfinder.GLPreview;
import com.particlesdevs.photoncamera.ui.camera.views.SpotWbIndicatorView;
import com.particlesdevs.photoncamera.util.Log;

/**
 * Tap-to-focus running a sequenced 3A protocol on top of Camera2
 * (see TOUCH_FOCUS_CORRECTED.md for the authoritative spec):
 * <ol>
 *   <li>the tap point is rotated by the live sensor orientation and mapped into the
 *       current zoom crop region (not the full active array), producing AF/AE
 *       metering rectangles — a square of half-side 6.125% of the smaller crop
 *       dimension, weight 122, clamped to the crop bounds. The AWB region array is
 *       never written on tap;</li>
 *   <li>regions ride on every request: the repeating preview request and each
 *       one-shot trigger request;</li>
 *   <li>AF trigger sequence: CANCEL one-shot with the new regions → repeating
 *       update (trigger IDLE) so the regions are live in the preview → wait for the
 *       cancel <em>acknowledgment</em> (AF_STATE in a per-mode membership set) →
 *       [continuous modes only, divergence: wait for a complete passive scan cycle
 *       — PASSIVE_SCAN observed, then settled — with a {@link #PASSIVE_DWELL_MS}
 *       time fallback when no scan appears] → bare START one-shot, sent
 *       unconditionally after the passive phase (no skip path, no second CANCEL
 *       inside the sequence) → repeating update → wait for the trigger
 *       acknowledgment (AF_STATE in {ACTIVE_SCAN, FOCUSED_LOCKED}). Both waits are
 *       acknowledgments, not convergence guarantees; partial and completed results
 *       both feed them. Beyond the passive phase the sequence purely trusts
 *       AF_STATE: instant locks are valid acks, no retries — verification of the
 *       actual image lives downstream (the still-capture path re-runs lock +
 *       precapture with the inherited region);</li>
 *   <li>acknowledgment is gated by submission identity: every request of the tap
 *       carries a per-tap tag, and only tag-matching results may ack (lenient
 *       region readback only as fallback when the tag is missing). One 2 s /
 *       30-frames-per-watcher budget per tap, shared by both waits. Watchers
 *       accept membership matches only — a null state consumes a frame and never
 *       resolves. Deadline resolution is non-destructive: the sequence completes
 *       as unknown, regions persist, nothing is reset or re-triggered
 *       (NOT_FOCUSED_LOCKED rides to the deadline the same way);</li>
 *   <li>everything is gated on device capabilities: AF/AE mode lists,
 *       CONTROL_MAX_REGIONS_*, and LENS_INFO_MINIMUM_FOCUS_DISTANCE (fixed-focus
 *       lenses are metered on but never triggered);</li>
 *   <li>AUTO fallback (divergence, user-requested): when {@link #AUTO_FALLBACK_TAPS}
 *       consecutive continuous-mode taps fail to be acknowledged, tap-to-focus
 *       switches to trigger-driven AF_MODE_AUTO for the camera session and retries
 *       the current tap — for HALs whose continuous AF cannot lock with a tap
 *       region.</li>
 * </ol>
 */
public class TouchFocus {
    private static final String TAG = "TouchFocus";
    private static final int AUTO_HIDE_DELAY_MS = 3000;

    static final long STATE_DEADLINE_MS = 2000;
    static final int STATE_MAX_FRAMES = 30;
    static final float REGION_HALF_SIDE_RATIO = 0.06125f;
    static final int REGION_WEIGHT = 122;
    // Divergence from the reference (user-requested; rev3 advises against): after the
    // cancel-ack, continuous modes wait for a COMPLETE passive scan cycle — PASSIVE_SCAN
    // observed, then settled — before the locking START, so the lens actually sweeps the
    // new region. PASSIVE_DWELL_MS is only the fallback when no scan is ever observed.
    // Never locks mid-scan; bounded by the shared 2 s budget.
    static final long PASSIVE_DWELL_MS = 400;

    private static final int SEQ_IDLE = 0;
    private static final int SEQ_WAIT_CANCEL_ACK = 1;
    private static final int SEQ_WAIT_START_ACK = 2;
    private static final int SEQ_PASSIVE_DWELL = 3;

    // Acknowledgment membership sets — never predicates, never resolved by null.
    /** After CANCEL, before START — AUTO mode (PASSIVE_UNFOCUSED mirrors the reference; unreachable in AUTO). */
    static final int[] ACK_AFTER_CANCEL_AUTO = {
            CameraMetadata.CONTROL_AF_STATE_INACTIVE,
            CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED};
    /** After CANCEL, before START — continuous modes. PASSIVE_UNFOCUSED is included
     *  (divergence pending creator confirmation): HALs whose passive AF cannot settle
     *  report it indefinitely after a CANCEL, and a tag-gated PASSIVE_UNFOCUSED still
     *  proves the CANCEL was processed. The reference's AUTO set also lists it. */
    static final int[] ACK_AFTER_CANCEL_CONTINUOUS = {
            CameraMetadata.CONTROL_AF_STATE_INACTIVE,
            CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED,
            CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED,
            CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED};
    /** After START: the lens entered its active scan, or locked immediately.
     *  Deliberately excludes INACTIVE (stale for 1-2 frames after the trigger)
     *  and NOT_FOCUSED_LOCKED (a failed lock rides to the deadline as unknown). */
    static final int[] ACK_AFTER_START = {
            CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN,
            CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED};

    private final CaptureController captureController;
    private final GLPreview textureView;
    private final View focusCircleView;
    private final View spotWbIndicatorView;
    private int currentOrientation = 0;
    private final Runnable hideFocusCircleRunnable = this::hideFocusCircleView;
    private final Runnable hideSpotWbRunnable = this::hideSpotWbIndicatorView;
    public volatile boolean isTouchFocus = false;

    public TouchFocus(CaptureController captureController, View focusCircle, View spotWbIndicator, GLPreview textureView) {
        this.captureController = captureController;
        this.focusCircleView = focusCircle;
        this.spotWbIndicatorView = spotWbIndicator;
        this.textureView = textureView;
        if (focusCircleView != null) {
            focusCircleView.setClickable(false);
            focusCircleView.setFocusable(false);
        }
        if (spotWbIndicatorView != null) {
            spotWbIndicatorView.setClickable(false);
            spotWbIndicatorView.setFocusable(false);
        }
        resetFocusCircle();
        resetSpotWbIndicator();
    }

    private final Object seqLock = new Object();
    private int seqState = SEQ_IDLE;
    // Invalidate in-flight continuations (timeouts, ack waits) on re-tap or reset.
    private int seqGeneration = 0;
    // One shared deadline per tap: both acknowledgment waits spend the same budget.
    private long seqDeadlineElapsed;
    // Dwell deadline (continuous modes): fallback START time if no passive scan appears.
    private long dwellDeadlineElapsed;
    // A PASSIVE_SCAN was observed on a current-tap frame since the cancel-ack.
    private boolean passiveScanSeen = false;
    // Divergence (user-requested): HALs whose continuous-mode AF cannot lock with a
    // tap region (passive never settles; trigger ends NOT_FOCUSED_LOCKED or silent)
    // get tap-to-focus switched to AF_MODE_AUTO after this many consecutive failed
    // taps. Scoped to this TouchFocus instance, i.e. the camera session.
    static final int AUTO_FALLBACK_TAPS = 3;
    private int consecutiveTouchFailures = 0;
    private boolean touchAfAutoOverride = false;
    @Nullable
    private MeteringRectangle lastTapRegion;
    // A NOT_FOCUSED_LOCKED was observed on a current-tap frame during the start-ack
    // wait: the trigger executed but the lock failed, leaving the lens frozen and
    // passive AF suspended until a CANCEL releases it.
    private boolean failedLockObserved = false;
    // Submission identity for the ack gate: every request built after the tap starts
    // (one-shots and repeating) carries this tag, which never leaves the app process,
    // so results gate by exact identity on every HAL.
    private Integer seqIdTag;
    // The region array written with this tap — only the LENIENT fallback of the ack
    // gate (spec rev2: a present region array counts as ours even when the HAL
    // normalized the rect; tags are the primary gate).
    @Nullable
    private MeteringRectangle[] armedRegionsTag;
    private int activeAfMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    // Diagnostics for the "FOCUSED_LOCKED but unfocused on region" case: how long
    // the HAL actually scanned, and whether the lens physically moved. A fake scan
    // reports ACTIVE_SCAN then locks with an unchanged LENS_FOCUS_DISTANCE.
    private volatile boolean scanWatchArmed = false;
    private volatile long scanAckAtElapsed;
    private volatile float focusAtScanAck;
    private final StateWatcher cancelAck = new StateWatcher("AF/cancel-ack");
    private final StateWatcher startAck = new StateWatcher("AF/start-ack");
    // Last AF state logged for the active sequence (Integer.MIN_VALUE = none/null).
    private int lastLoggedSeqState = Integer.MIN_VALUE;
    private Runnable timeoutRunnable;
    private boolean timeoutOnBackground;


    /**
     * Executes Live Spot WB measurement on Long Press.
     * Displays a dedicated sampling reticle with "WB" label and handles real-time error feedback.
     */
    public void processSpotWb(float fx, float fy) {
        if (textureView == null || captureController == null) return;

        // 1. Tactile haptic feedback
        textureView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

        // 2. Position, animate and reset dedicated Spot WB indicator box to measuring state
        if (spotWbIndicatorView != null) {
            spotWbIndicatorView.removeCallbacks(hideSpotWbRunnable);
            spotWbIndicatorView.post(() -> showSpotWbIndicator(fx, fy));
        }

        // 3. Measure True Linear RAW Spot WB directly using canonical field-of-view ratio
        SpotWhiteBalanceHelper.measureSpotWbRaw(
                textureView,
                captureController,
                fx, fy,
                new SpotWhiteBalanceHelper.SpotWbCallback() {
                    @Override
                    public void onSpotWbMeasured(int kelvin, String tintStr) {
                        if (spotWbIndicatorView != null) {
                            spotWbIndicatorView.removeCallbacks(hideSpotWbRunnable);
                            spotWbIndicatorView.postDelayed(hideSpotWbRunnable, 2000);
                        }
                    }

                    @Override
                    public void onSpotWbFailed(String reason) {
                        if (spotWbIndicatorView != null) {
                            spotWbIndicatorView.removeCallbacks(hideSpotWbRunnable);
                            if (spotWbIndicatorView instanceof SpotWbIndicatorView) {
                                ((SpotWbIndicatorView) spotWbIndicatorView).setErrorState(reason);
                            }
                            spotWbIndicatorView.postDelayed(hideSpotWbRunnable, 1500);
                        }
                    }
                }
        );
    }

    private void showSpotWbIndicator(float fx, float fy) {
        if (spotWbIndicatorView == null) return;
        if (spotWbIndicatorView instanceof SpotWbIndicatorView) {
            ((SpotWbIndicatorView) spotWbIndicatorView).setMeasuringState();
            ((SpotWbIndicatorView) spotWbIndicatorView).setOrientation(currentOrientation);
        }
        spotWbIndicatorView.setX(fx - spotWbIndicatorView.getMeasuredWidth() / 2.0f);
        spotWbIndicatorView.setY(fy - spotWbIndicatorView.getMeasuredHeight() / 2.0f);
        spotWbIndicatorView.setVisibility(View.VISIBLE);
        spotWbIndicatorView.animate().scaleX(1.25f).scaleY(1.25f).setDuration(150)
                .withEndAction(() -> spotWbIndicatorView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start())
                .start();
    }

    public void processTouchToFocus(float fx, float fy) {
        MeteringRectangle region = computeTapRegion(fx, fy);
        if (region == null) {
            Log.w(TAG, "processTouchToFocus(): camera or viewfinder not ready, ignoring tap");
            return;
        }
        focusCircleView.removeCallbacks(hideFocusCircleRunnable);
        focusCircleView.post(() -> showFocusCircle(fx, fy));
        startFocusSequence(region);
        focusCircleView.postDelayed(hideFocusCircleRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void showFocusCircle(float fx, float fy) {
        focusCircleView.setX(fx - focusCircleView.getMeasuredWidth() / 2.0f);
        focusCircleView.setY(fy - focusCircleView.getMeasuredHeight() / 2.0f);
        focusCircleView.setVisibility(View.VISIBLE);
        focusCircleView.animate().scaleY(1.2f).scaleX(1.2f).setDuration(250)
                .withEndAction(() -> focusCircleView.animate().scaleY(1f).scaleX(1f).setDuration(250).start())
                .start();
    }

    /**
     * Sets state of focus circle view based on AF State. Also closes the scan-watch
     * diagnostic: time from the ACTIVE_SCAN acknowledgment to the first live
     * FOCUSED_LOCKED, with lens motion across it.
     */
    public void setState(@Nullable Integer afstate) {
        if (afstate != null) {
            if (scanWatchArmed && afstate == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                scanWatchArmed = false;
                Log.w(TAG, "lock after " + (SystemClock.elapsedRealtime() - scanAckAtElapsed)
                        + "ms of scan; lens " + focusAtScanAck + " -> " + captureController.mFocus + " diopters");
            }
            ((FocusCircleView) focusCircleView).setAfState(afstate);
        }
    }

    private void setInitialAFAE() {
        captureController.reset3Aparams();
    }

    /**
     * Updates current device orientation and forwards it to the Spot WB indicator.
     */
    public void setOrientation(int orientation) {
        this.currentOrientation = orientation;
        if (spotWbIndicatorView instanceof SpotWbIndicatorView) {
            ((SpotWbIndicatorView) spotWbIndicatorView).setOrientation(orientation);
        }
    }

    // ------------------------------------------------------------------ mapping

    /**
     * Maps a viewfinder tap to a metering region inside the current zoom crop.
     */
    @Nullable
    private MeteringRectangle computeTapRegion(float viewX, float viewY) {
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        if (characteristics == null) return null;
        Rect crop = currentCropRegion(characteristics);
        if (crop == null) return null;
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        boolean mirrored = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        int gravityRotation = currentGravityRotation();
        logPreCorrectionMismatch(characteristics);
        int[] r = mapTapToCrop(viewX, viewY, textureView.getWidth(), textureView.getHeight(),
                crop.left, crop.top, crop.width(), crop.height(),
                captureController.mSensorOrientation, gravityRotation, mirrored);
        if (r == null) return null;
        Log.d(TAG, "tap (" + (int) viewX + "," + (int) viewY + ")v" + textureView.getWidth() + "x" + textureView.getHeight()
                + " so=" + captureController.mSensorOrientation + " rot=" + gravityRotation + " mirror=" + mirrored
                + " crop=" + crop.width() + "x" + crop.height() + " -> rect=[" + r[0] + "," + r[1] + " " + r[2] + "x" + r[2] + "]");
        return new MeteringRectangle(r[0], r[1], r[2], r[2], REGION_WEIGHT);
    }

    /**
     * Display rotation is preferred when the view is attached (accurate even
     * flat-on-table); the gravity sensor is the fallback. Values are normalized
     * to the gravity scale, where 90 = natural portrait upright.
     */
    private int currentGravityRotation() {
        try {
            Display display = textureView.getDisplay();
            if (display != null) return display.getRotation() * 90 + 90;
        } catch (Exception ignored) {
        }
        Gravity gravity = PhotonCamera.getGravity();
        return gravity != null ? gravity.getRotation() : 90;
    }

    /**
     * Diagnostic (spec rev3, Part III): when the precorrection active array differs
     * from the active array, many HALs expect region coordinates in precorrection
     * space — the strongest single candidate for "real scan, wrong distance".
     * Detected and logged only; the mapping itself is not branched.
     */
    private static void logPreCorrectionMismatch(CameraCharacteristics characteristics) {
        Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Rect precorrection = characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
        if (activeArray != null && precorrection != null && !activeArray.equals(precorrection)) {
            Log.w(TAG, "precorrection array " + precorrection + " differs from active array " + activeArray
                    + " — HAL may expect region coords in precorrection space (A/B the mapping)");
        }
    }

    @Nullable
    private static Rect currentCropRegion(CameraCharacteristics characteristics) {
        // Map into the live zoom crop when one is active, so the focus point stays
        // under the user's finger at any zoom level.
        Rect crop = null;
        CaptureRequest last = CaptureController.mPreviewCaptureRequest;
        if (last != null) crop = last.get(CaptureRequest.SCALER_CROP_REGION);
        if (crop == null) crop = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (crop == null) {
            Size pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            if (pixelArray != null) crop = new Rect(0, 0, pixelArray.getWidth(), pixelArray.getHeight());
        }
        return crop;
    }

    /**
     * Pure view-tap → crop-rectangle geometry (JVM-testable).
     * <p>
     * The sensor image must be rotated by {@code (sensorOrientation + gravityRotation + 270) % 360}
     * clockwise to appear upright on screen, so the inverse rotation maps a tap back
     * into sensor space. Front-camera previews are mirrored first.
     *
     * @return {@code [x, y, side]} of the square region, fully clamped inside the crop
     */
    @Nullable
    static int[] mapTapToCrop(float viewX, float viewY, int viewWidth, int viewHeight,
                              int cropLeft, int cropTop, int cropWidth, int cropHeight,
                              int sensorOrientation, int gravityRotation, boolean mirrored) {
        if (viewWidth <= 0 || viewHeight <= 0 || cropWidth <= 0 || cropHeight <= 0) return null;
        int rotation = ((90 - sensorOrientation - gravityRotation) % 360 + 360) % 360;
        float u = viewX / viewWidth;
        float v = viewY / viewHeight;
        if (mirrored) u = 1f - u;
        double rad = Math.toRadians(rotation);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        float dx = u - 0.5f;
        float dy = v - 0.5f;
        // Visually-clockwise rotation in a y-down coordinate system.
        float rx = (float) (dx * cos - dy * sin) + 0.5f;
        float ry = (float) (dx * sin + dy * cos) + 0.5f;
        int side = Math.max(2, Math.round(REGION_HALF_SIDE_RATIO * 2f * Math.min(cropWidth, cropHeight)));
        int half = side / 2;
        int cx = cropLeft + Math.round(rx * cropWidth);
        int cy = cropTop + Math.round(ry * cropHeight);
        int x = Math.max(cropLeft, Math.min(cx - half, cropLeft + cropWidth - side));
        int y = Math.max(cropTop, Math.min(cy - half, cropTop + cropHeight - side));
        return new int[]{x, y, side};
    }

    // ------------------------------------------------------------------ sequence

    private void startFocusSequence(MeteringRectangle region) {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        if (builder == null || characteristics == null) {
            Log.w(TAG, "startFocusSequence(): camera not ready");
            return;
        }
        synchronized (seqLock) {
            if (CaptureController.burst) return;
            // Invalidate any sequence still in flight from a previous tap/reset.
            seqGeneration++;
            abortSequenceLocked();

            // Submission identity for the ack gate (spec rev2): every request built
            // from now on — one-shots and the repeating update — carries this tag.
            seqIdTag = seqGeneration;
            builder.setTag(seqIdTag);

            // Capability gating: unsupported requested modes silently fall back to the current ones.
            // With the AUTO fallback engaged (continuous-mode touch focus repeatedly failed on
            // this HAL), the sequence runs in trigger-driven AF_MODE_AUTO instead.
            int currentAfMode = valueOr(builder.get(CaptureRequest.CONTROL_AF_MODE), activeAfMode);
            int requestedAfMode = touchAfAutoOverride
                    ? CameraMetadata.CONTROL_AF_MODE_AUTO : PreferenceKeys.getAfMode();
            int afMode = supportedMode(characteristics, CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                    requestedAfMode, currentAfMode);
            int aeMode = supportedMode(characteristics, CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES,
                    Math.max(PreferenceKeys.getAeMode(), 1),
                    valueOr(builder.get(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON));
            Float minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            boolean lensCanFocus = minFocusDistance == null || minFocusDistance > 0;
            MeteringRectangle[] regions = {region};
            lastTapRegion = region;

            activeAfMode = afMode;
            isTouchFocus = true;
            // The tap writes only the AF and AE region arrays; AWB stays untouched.
            boolean afRegionsWritten = false, aeRegionsWritten = false;
            if (maxRegions(characteristics, CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
                afRegionsWritten = true;
            }
            if (maxRegions(characteristics, CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
                aeRegionsWritten = true;
            }
            armedRegionsTag = (afRegionsWritten || aeRegionsWritten) ? regions : null;
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
            builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode);

            if (!lensCanFocus || afMode == CameraMetadata.CONTROL_AF_MODE_OFF) {
                // Fixed-focus lens: meter on the tapped region but never trigger the lens.
                Log.d(TAG, "startFocusSequence(): metering-only, lens cannot refocus");
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                captureController.rebuildPreviewBuilder();
                return;
            }

            // 1. Cancel the running AF as a discrete one-shot with the new regions attached.
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            captureController.rebuildPreviewBuilderOneShot();
            // 2. Repeating update: the new regions are now active in the preview stream.
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            captureController.rebuildPreviewBuilder();

            // 3. Wait for the cancel acknowledgment — the CANCEL was processed and the
            //    region-bearing repeating request has produced a frame. Normally 1-2
            //    frames on the INACTIVE that follows a CANCEL; not a convergence wait.
            seqState = SEQ_WAIT_CANCEL_ACK;
            seqDeadlineElapsed = SystemClock.elapsedRealtime() + STATE_DEADLINE_MS;
            cancelAck.arm(isContinuousAf(afMode) ? ACK_AFTER_CANCEL_CONTINUOUS : ACK_AFTER_CANCEL_AUTO,
                    seqDeadlineElapsed);
            scheduleTimeoutLocked();
        }
    }

    /**
     * Steps 4-5 of the sequence: a bare START, sent unconditionally after the
     * pre-START wait resolves by ack or deadline — no skip path, and no second
     * CANCEL inside the sequence (spec rev2). Callers must hold {@link #seqLock}.
     */
    private void sendAfStartLocked() {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        if (builder == null || CaptureController.burst) {
            abortSequenceLocked();
            return;
        }
        Log.d(TAG, "sending AF START");
        // 4. The trigger rides its own one-shot frame. The repeating request already
        //    carries regions + TRIGGER_IDLE from step 2 and is deliberately NOT
        //    resubmitted here: a back-to-back content-identical repeating replacement
        //    can race the one-shot on strict HALs and drop the trigger frame.
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        captureController.rebuildPreviewBuilderOneShot();
        // Builder hygiene only (no submission attached): later rebuilds by other
        // components must not fold a stale trigger into their repeating updates.
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        // Acknowledgment only: the lens entered its active scan (or locked immediately).
        // Success/failure is read from the live per-frame AF_STATE afterward.
        seqState = SEQ_WAIT_START_ACK;
        failedLockObserved = false;
        startAck.arm(ACK_AFTER_START, seqDeadlineElapsed);
    }

    private static boolean isContinuousAf(int afMode) {
        return afMode == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                || afMode == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    }

    /** Engage the AUTO fallback after enough consecutive continuous-mode failures. */
    static boolean shouldEngageAutoFallback(int consecutiveFailures, boolean autoModeSupported) {
        return autoModeSupported && consecutiveFailures >= AUTO_FALLBACK_TAPS;
    }

    private static boolean isAutoModeSupported() {
        CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
        return characteristics != null && supportedMode(characteristics,
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                CameraMetadata.CONTROL_AF_MODE_AUTO, -1) == CameraMetadata.CONTROL_AF_MODE_AUTO;
    }

    /**
     * Completion rule for the passive phase (divergence, user-requested): once a
     * PASSIVE_SCAN was observed, wait for it to settle — the lens is never locked
     * mid-scan, and a scan that starts at dwell expiry still runs to completion.
     * Without any observed scan, the time dwell is the fallback. The shared budget
     * always wins.
     */
    static boolean passivePhaseComplete(@Nullable Integer afState, boolean scanSeen,
                                        boolean dwellOver, boolean budgetOver) {
        if (budgetOver) return true;
        if (scanSeen) return afState != null && afState != CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN;
        return dwellOver && (afState == null || afState != CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN);
    }

    /**
     * Fed from CaptureController's session callback on the background handler.
     * Both waits are acknowledgments with membership sets; null states never
     * resolve, they only consume frames toward the deadline. Acknowledgment is
     * additionally restricted to results captured with the new region — results
     * from requests carrying a different (pre-tap) region are stale.
     */
    public void onCaptureResult(CaptureResult result) {
        if (seqState == SEQ_IDLE) return;
        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
        synchronized (seqLock) {
            if (CaptureController.burst) {
                abortSequenceLocked();
                return;
            }
            boolean regionLive = isRegionLiveIn(result.getRequest());
            logSeqStateChange(afState, regionLive);
            switch (seqState) {
                case SEQ_WAIT_CANCEL_ACK:
                    if (cancelAck.onFrame(afState, regionLive)) {
                        if (isContinuousAf(activeAfMode)) {
                            // Divergence (user-requested): wait for a full passive scan
                            // cycle on the new region instead of locking at the first
                            // settled state — the settle reached too fast on this HAL
                            // class locks at the wrong distance.
                            seqState = SEQ_PASSIVE_DWELL;
                            passiveScanSeen = false;
                            dwellDeadlineElapsed = Math.min(
                                    SystemClock.elapsedRealtime() + PASSIVE_DWELL_MS, seqDeadlineElapsed);
                            Log.d(TAG, "cancel acked, waiting for a passive scan cycle before START (fallback "
                                    + PASSIVE_DWELL_MS + "ms)");
                        } else {
                            sendAfStartLocked();
                        }
                    }
                    break;
                case SEQ_PASSIVE_DWELL:
                    if (regionLive && Integer.valueOf(CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN).equals(afState))
                        passiveScanSeen = true;
                    long now = SystemClock.elapsedRealtime();
                    if (passivePhaseComplete(afState, passiveScanSeen,
                            now >= dwellDeadlineElapsed, now >= seqDeadlineElapsed)) {
                        Log.d(TAG, "passive phase complete (scanCycle=" + passiveScanSeen + "), sending AF START");
                        sendAfStartLocked();
                    }
                    break;
                case SEQ_WAIT_START_ACK:
                    if (regionLive && Integer.valueOf(CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED).equals(afState))
                        failedLockObserved = true;
                    if (startAck.onFrame(afState, regionLive)) {
                        boolean acknowledged = startAck.resolvedState() != null;
                        Log.d(TAG, "AF trigger " + (acknowledged ? "acknowledged" : (failedLockObserved
                                ? "executed but FAILED to lock" : "UNACKNOWLEDGED")) + ", sequence complete");
                        if (Integer.valueOf(CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN).equals(startAck.resolvedState())) {
                            scanWatchArmed = true;
                            scanAckAtElapsed = SystemClock.elapsedRealtime();
                            focusAtScanAck = captureController.mFocus;
                        }
                        // AUTO fallback (divergence, user-requested): consecutive failed
                        // continuous-mode taps mean this HAL cannot lock with a tap region
                        // in continuous mode — switch tap-to-focus to AF_MODE_AUTO and
                        // retry the current tap immediately.
                        if (isContinuousAf(activeAfMode)) {
                            consecutiveTouchFailures = acknowledged ? 0 : consecutiveTouchFailures + 1;
                            if (!acknowledged && !touchAfAutoOverride && lastTapRegion != null
                                    && shouldEngageAutoFallback(consecutiveTouchFailures, isAutoModeSupported())) {
                                touchAfAutoOverride = true;
                                Log.w(TAG, "continuous touch focus failed " + consecutiveTouchFailures
                                        + " consecutive taps — switching tap-to-focus to AF_MODE_AUTO for this session");
                                startFocusSequence(lastTapRegion);
                                return;
                            }
                        }
                        if (!acknowledged) {
                            CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
                            if (builder != null && !CaptureController.burst) {
                                if (failedLockObserved) {
                                    // Divergence: the trigger executed but failed to lock —
                                    // the lens is frozen at a failed distance and passive AF
                                    // is suspended until a CANCEL. Release the failed lock so
                                    // passive AF keeps trying; the reference leaves it and
                                    // relies on its downstream refocus.
                                    Log.w(TAG, "releasing failed lock back to passive AF");
                                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                                    captureController.rebuildPreviewBuilderOneShot();
                                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                                } else {
                                    // Silent-HAL recovery: re-assert the AF mode through the
                                    // repeating request (same mechanism the still-capture
                                    // path uses to revive AF); no CANCEL, no region change.
                                    Log.w(TAG, "re-asserting AF mode to revive preview AF");
                                    builder.set(CaptureRequest.CONTROL_AF_MODE, activeAfMode);
                                    captureController.rebuildPreviewBuilder();
                                }
                            }
                        }
                        seqState = SEQ_IDLE;
                        cancelTimeoutLocked();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * True when the frame was produced by a request of the current tap. Primary
     * gate is submission identity (spec rev2): every request built after the tap
     * starts carries the tap's sequence tag, so comparison is exact on every HAL.
     * Fallback (tag missing): lenient region readback — a present region array
     * counts as ours even when the HAL normalized the rect; an absent one matches
     * only taps that wrote no regions. Gated-out frames consume deadline frames
     * and nothing else.
     */
    private boolean isRegionLiveIn(@Nullable CaptureRequest request) {
        if (request == null) return false;
        Object tag = request.getTag();
        if (tag != null) return tag.equals(seqIdTag);
        MeteringRectangle[] af = request.get(CaptureRequest.CONTROL_AF_REGIONS);
        MeteringRectangle[] ae = request.get(CaptureRequest.CONTROL_AE_REGIONS);
        if (af == null && ae == null) return armedRegionsTag == null;
        return true;
    }

    private void abortSequenceLocked() {
        seqState = SEQ_IDLE;
        scanWatchArmed = false;
        armedRegionsTag = null;
        lastLoggedSeqState = Integer.MIN_VALUE;
        passiveScanSeen = false;
        failedLockObserved = false;
        cancelAck.dismiss();
        startAck.dismiss();
        cancelTimeoutLocked();
    }

    /** Logs each DISTINCT AF state observed during a sequence, with eligibility. */
    private void logSeqStateChange(@Nullable Integer afState, boolean eligible) {
        int value = afState != null ? afState : Integer.MIN_VALUE;
        if (value == lastLoggedSeqState) return;
        lastLoggedSeqState = value;
        Log.d(TAG, "seq afState=" + (afState != null ? afStateName(afState) : "null") + " eligible=" + eligible);
    }

    private static String afStateName(int afState) {
        switch (afState) {
            case CameraMetadata.CONTROL_AF_STATE_INACTIVE: return "INACTIVE";
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN: return "PASSIVE_SCAN";
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED: return "PASSIVE_FOCUSED";
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED: return "PASSIVE_UNFOCUSED";
            case CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN: return "ACTIVE_SCAN";
            case CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED: return "FOCUSED_LOCKED";
            case CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED: return "NOT_FOCUSED_LOCKED";
            default: return String.valueOf(afState);
        }
    }

    /**
     * One hard backstop per tap for the case where frames stop arriving entirely.
     * Resolution is non-destructive: the sequence completes as unknown and nothing
     * is sent or reset — regions persist and the circle keeps following live AF_STATE.
     */
    private void scheduleTimeoutLocked() {
        cancelTimeoutLocked();
        final int generation = seqGeneration;
        timeoutRunnable = () -> {
            synchronized (seqLock) {
                if (generation != seqGeneration || seqState == SEQ_IDLE) return;
                Log.w(TAG, "tap focus budget expired, resolving as unknown (state=" + seqState + ")");
                abortSequenceLocked();
            }
        };
        // Exactly one host: a duplicate firing after a phase transition would
        // abort the next phase's watchers early.
        Handler background = captureController.mBackgroundHandler;
        timeoutOnBackground = background != null;
        if (background != null) background.postDelayed(timeoutRunnable, STATE_DEADLINE_MS + 100);
        else focusCircleView.postDelayed(timeoutRunnable, STATE_DEADLINE_MS + 100);
    }

    private void cancelTimeoutLocked() {
        if (timeoutRunnable == null) return;
        if (timeoutOnBackground && captureController.mBackgroundHandler != null)
            captureController.mBackgroundHandler.removeCallbacks(timeoutRunnable);
        else focusCircleView.removeCallbacks(timeoutRunnable);
        timeoutRunnable = null;
    }

    private void resetAutoFocus() {
        synchronized (seqLock) {
            seqGeneration++;
            abortSequenceLocked();
            isTouchFocus = false;
            if (CaptureController.burst) return;
            CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
            CameraCharacteristics characteristics = CaptureController.mCameraCharacteristics;
            if (builder == null || characteristics == null) return;
            // Cancel any in-flight trigger as a discrete one-shot, old regions riding on it.
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            captureController.rebuildPreviewBuilderOneShot();
            // Never send an empty region array — a zero-area, zero-weight rectangle
            // clears the region without tripping strict HALs.
            MeteringRectangle zero = new MeteringRectangle(0, 0, 0, 0, 0);
            if (maxRegions(characteristics, CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0)
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, regionsOr(captureController.mPreviewMeteringAF, zero));
            if (maxRegions(characteristics, CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0)
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, regionsOr(captureController.mPreviewMeteringAE, zero));
            builder.set(CaptureRequest.CONTROL_AF_MODE, captureController.mPreviewAFMode);
            builder.set(CaptureRequest.CONTROL_AE_MODE, captureController.mPreviewAEMode);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            captureController.rebuildPreviewBuilder();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int supportedMode(CameraCharacteristics characteristics,
                                     CameraCharacteristics.Key<int[]> availableKey,
                                     int requested, int fallback) {
        int[] supported = characteristics.get(availableKey);
        if (supported == null) return fallback;
        for (int mode : supported) if (mode == requested) return requested;
        return fallback;
    }

    private static int maxRegions(CameraCharacteristics characteristics, CameraCharacteristics.Key<Integer> key) {
        Integer max = characteristics.get(key);
        return max != null ? max : 0;
    }

    private static MeteringRectangle[] regionsOr(@Nullable MeteringRectangle[] regions, MeteringRectangle fallback) {
        return regions != null && regions.length > 0 ? regions : new MeteringRectangle[]{fallback};
    }

    private static int valueOr(@Nullable Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Watches one 3A state key for a positive membership match. A null state or an
     * ineligible (stale-region) frame counts as one frame toward the deadline and
     * nothing else. Deadline resolution is non-destructive and reported identically
     * to a match — the caller cannot distinguish and must not react differently.
     */
    static final class StateWatcher {
        private final String name;
        private int[] acceptedStates = new int[0];
        private long deadlineElapsed;
        private int frameCount;
        private boolean active;
        @Nullable
        private Integer resolvedState;

        StateWatcher(String name) {
            this.name = name;
        }

        void arm(int[] acceptedStates, long deadlineElapsed) {
            this.acceptedStates = acceptedStates;
            this.deadlineElapsed = deadlineElapsed;
            this.frameCount = 0;
            this.active = true;
            this.resolvedState = null;
        }

        void dismiss() {
            active = false;
        }

        /** The state that resolved the wait, or null when it resolved by deadline. */
        @Nullable
        Integer resolvedState() {
            return resolvedState;
        }

        boolean onFrame(@Nullable Integer state, boolean eligible) {
            if (!active) return false;
            frameCount++;
            if (eligible && state != null && containsState(state)) {
                active = false;
                resolvedState = state;
                Log.d(TAG, name + " resolved: state " + state + " after " + frameCount + " frames");
                return true;
            }
            if (frameCount >= STATE_MAX_FRAMES || SystemClock.elapsedRealtime() >= deadlineElapsed) {
                active = false;
                resolvedState = null;
                Log.d(TAG, name + " resolved: deadline after " + frameCount + " frames");
                return true;
            }
            return false;
        }

        private boolean containsState(int state) {
            for (int accepted : acceptedStates) if (accepted == state) return true;
            return false;
        }
    }

    // ------------------------------------------------------------------ UI

    //Thread safe
    //call when focus circle needs to be hidden immediately
    public void resetFocusCircle() {
        focusCircleView.removeCallbacks(hideFocusCircleRunnable);
        focusCircleView.post(hideFocusCircleRunnable);
        resetAutoFocus();
    }

    //Must be run on UI Thread
    private void hideFocusCircleView() {
        if (focusCircleView.getVisibility() == View.VISIBLE) {
            focusCircleView.animate().alpha(0f).scaleY(1.8f).scaleX(1.8f).setDuration(100)
                    .withEndAction(() -> {
                        focusCircleView.setVisibility(View.GONE);
                        focusCircleView.setX((float) textureView.getWidth() / 2.f);
                        focusCircleView.setY((float) textureView.getHeight() / 2.f);
                        focusCircleView.setScaleY(1f);
                        focusCircleView.setScaleX(1f);
                        focusCircleView.setAlpha(1f);
                    })
                    .start();
        }
    }

    //Thread safe
    //call when spot WB indicator needs to be hidden immediately
    public void resetSpotWbIndicator() {
        if (spotWbIndicatorView != null) {
            spotWbIndicatorView.removeCallbacks(hideSpotWbRunnable);
            spotWbIndicatorView.post(hideSpotWbRunnable);
        }
    }

    //Must be run on UI Thread
    private void hideSpotWbIndicatorView() {
        if (spotWbIndicatorView != null && spotWbIndicatorView.getVisibility() == View.VISIBLE) {
            spotWbIndicatorView.animate().alpha(0f).scaleX(1.4f).scaleY(1.4f).setDuration(120)
                    .withEndAction(() -> {
                        spotWbIndicatorView.setVisibility(View.GONE);
                        spotWbIndicatorView.setX((float) textureView.getWidth() / 2.f);
                        spotWbIndicatorView.setY((float) textureView.getHeight() / 2.f);
                        spotWbIndicatorView.setScaleX(1f);
                        spotWbIndicatorView.setScaleY(1f);
                        spotWbIndicatorView.setAlpha(1f);
                    })
                    .start();
        }
    }
}
