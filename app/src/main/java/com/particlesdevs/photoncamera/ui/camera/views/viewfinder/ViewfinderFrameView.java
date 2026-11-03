package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.circularbarlib.util.Motion;

/**
 * Invisible anchor for the live viewfinder rectangle. It is measured to the
 * camera preview aspect (the same numbers the preview view used before the
 * preview surface became full-bleed) and every HUD overlay is constrained to
 * it, so the preview surface itself can extend across the whole screen and
 * paint the edge-blur bands around this frame.
 *
 * <p>Aspect changes are animated: the frame — and with it every overlay
 * constrained to it — stretches from the old ratio to the new one. While that
 * runs {@link ViewfinderEdgeBlurController} keeps the preview surface
 * full-bleed, so the renderer letterboxes the sharp image into the moving rect
 * instead of resizing its surface every frame.
 *
 * <p>The stretch is one self-contained animation that completes on its own, so
 * the viewfinder never crawls or parks half-way while the camera reopens. The
 * reveal is a separate step: the held capture is crossfaded into the new
 * preview once the new camera's frames are live, and never over a half-way
 * frame — if the frames arrive while the stretch is still running,
 * {@link #finishStretch(Runnable)} first lands the remaining travel with a
 * short distance-scaled ease-out and only then is the crossfade started.
 *
 * <p>The listener is notified just before the stretch starts, so the caller can
 * snapshot the current capture and hold it while the incoming camera's frames
 * come up behind it.
 */
public class ViewfinderFrameView extends View {

    /**
     * Duration of an aspect stretch. Sized to be a complete, readable motion on
     * its own: the standard curve spreads it over the whole span (the
     * emphasized token reached 100% at a quarter of its duration and then ran
     * flat). The camera-open lag is not part of this — the reveal waits for the
     * stretch to land and then crossfades, so the viewfinder is never left
     * creeping or parked half-way to fill a reopen.
     */
    private static final long STRETCH_DURATION_MS = 700L;
    /** Pace, then bounds, of the ease-out that lands a stretch cut short. */
    private static final float STRETCH_FINISH_PX_PER_MS = 0.8f;
    private static final long STRETCH_FINISH_MIN_MS = 200L;
    private static final long STRETCH_FINISH_MAX_MS = 400L;

    /** Notified just before a changed aspect starts stretching. */
    public interface Listener {
        /**
         * A changed aspect is about to stretch: the caller should snapshot the
         * current capture now, hold it, and crossfade it away once the new
         * camera's frames are live.
         */
        void onAspectChangeStarting();
    }

    /**
     * Notified every frame of a stretch with its progress: 0 at the height the
     * stretch started from, 1 at the target aspect (and while idle). The
     * chrome's FLIP translations follow this, so nothing in the mode switch
     * arrives before the viewfinder does.
     */
    public interface ProgressListener {
        void onStretchProgress(float progress);
    }

    private int mRatioWidth;
    private int mRatioHeight;
    /** Height the frame is currently drawn with; the target when idle. */
    private float mAnimatedHeight;
    /** Height the running stretch started from and the height it heads for. */
    private float mStretchStartHeight;
    private float mStretchTargetHeight;
    private ValueAnimator mAnimator;
    private boolean mAnimating;
    private boolean mFinishing;
    /** Completion of the landing, run once the frame is exactly on target. */
    @Nullable
    private Runnable finishCallback;
    @Nullable
    private Listener listener;
    @Nullable
    private ProgressListener progressListener;

    public ViewfinderFrameView(Context context) {
        this(context, null);
    }

    public ViewfinderFrameView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Replaces the progress listener. The listener is expected to clear itself
     * (pass null) once the stretch lands, so a later stretch cannot be driven
     * with stale translations.
     */
    public void setProgressListener(@Nullable ProgressListener listener) {
        progressListener = listener;
    }

    /** True while the frame is stretching to a new aspect. */
    public boolean isAspectAnimating() {
        return mAnimating;
    }

    /**
     * Progress of the running stretch: 0 at the height it started from, 1 at
     * the target aspect (also 1 while idle).
     */
    public float getStretchProgress() {
        float travel = mStretchTargetHeight - mStretchStartHeight;
        if (Math.abs(travel) < 1f) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, (mAnimatedHeight - mStretchStartHeight) / travel));
    }

    /**
     * Stretches to a target aspect (width:height) keeping the current width:
     * used at a mode switch so the viewfinder morphs together with the rest of
     * the UI instead of waiting for the new camera's preview size. The
     * post-open preview size then matches this target and is a no-op.
     */
    public void animateToAspect(int ratioWidth, int ratioHeight) {
        if (ratioWidth <= 0 || ratioHeight <= 0) {
            return;
        }
        int width = mRatioWidth > 0 ? mRatioWidth : getWidth();
        if (width <= 0 || !isLaidOut()) {
            return;
        }
        int height = Math.round(width * (float) ratioHeight / (float) ratioWidth);
        setAspectRatio(width, height);
    }

    /**
     * Sets the viewfinder aspect from the camera preview's pixel dimensions.
     * The measured size is those dimensions, exactly like the historical
     * preview view. A changed aspect animates (width fixed, height
     * interpolated); the first set and negligible deltas snap.
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        // The post-open preview size repeats the mode's target: nothing to do.
        if (width == mRatioWidth && height == mRatioHeight) {
            return;
        }
        boolean first = mRatioWidth == 0 || mRatioHeight == 0;
        float previousHeight = first ? height : currentHeightPx();
        mRatioWidth = width;
        mRatioHeight = height;
        if (first || !isLaidOut() || Math.abs(height - previousHeight) < 2f) {
            snapTo(height);
            return;
        }
        if (listener != null) {
            listener.onAspectChangeStarting();
        }
        startAspectStretch(previousHeight, height);
    }

    /**
     * Lands the running stretch on its target with a distance-scaled ease-out,
     * then reports completion. Called when the new camera's frames are live (or
     * when the fallback fires): the reveal waits for the viewfinder — and the
     * chrome that glides on its progress — to finish arriving, instead of
     * crossfading over a half-stretched frame. Runs the callback immediately
     * when there is nothing left to land.
     */
    public void finishStretch(@Nullable Runnable onLanded) {
        if (!mAnimating) {
            if (onLanded != null) {
                onLanded.run();
            }
            return;
        }
        if (mFinishing) {
            // Already landing: report when that landing completes.
            finishCallback = onLanded;
            return;
        }
        float remaining = Math.abs(mStretchTargetHeight - mAnimatedHeight);
        if (remaining < 1f) {
            finishCallback = onLanded;
            endStretch();
            return;
        }
        cancelAnimator();
        mFinishing = true;
        finishCallback = onLanded;
        long duration = Math.max(STRETCH_FINISH_MIN_MS, Math.min(STRETCH_FINISH_MAX_MS,
                (long) (remaining / STRETCH_FINISH_PX_PER_MS)));
        startPhase(mAnimatedHeight, mStretchTargetHeight, duration,
                Motion.standard(getContext()), this::endStretch);
    }

    private float currentHeightPx() {
        return mAnimatedHeight > 0f ? mAnimatedHeight : getMeasuredHeight();
    }

    private void snapTo(int height) {
        cancelAnimator();
        mAnimatedHeight = height;
        mStretchStartHeight = height;
        mStretchTargetHeight = height;
        mAnimating = false;
        mFinishing = false;
        post(this::requestLayout);
        notifyProgress();
        // The frame is exactly on target: anything waiting on the stretch
        // (the reveal) must not be left hanging.
        runFinishCallback();
    }

    private void startAspectStretch(float from, float to) {
        cancelAnimator();
        mStretchStartHeight = from;
        mStretchTargetHeight = to;
        mAnimating = true;
        mFinishing = false;
        // One complete motion: it runs to the target on its own, and the reveal
        // waits for it (landing it early only if the new frames arrive first).
        startPhase(from, to, STRETCH_DURATION_MS, Motion.standard(getContext()),
                this::endStretch);
    }

    /** Runs one stretch animation (the stretch itself, or its landing). */
    private void startPhase(float from, float to, long durationMs,
                            TimeInterpolator interpolator, @Nullable Runnable onEnd) {
        mAnimator = ValueAnimator.ofFloat(from, to);
        mAnimator.setDuration(Math.max(1L, durationMs));
        mAnimator.setInterpolator(interpolator);
        mAnimator.addUpdateListener(animation -> {
            mAnimatedHeight = (float) animation.getAnimatedValue();
            requestLayout();
            notifyProgress();
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                // The phase was re-targeted (a new stretch, a landing or a
                // teardown): the animating flags belong to the caller now.
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) {
                    return;
                }
                mAnimatedHeight = to;
                requestLayout();
                if (onEnd != null) {
                    onEnd.run();
                }
            }
        });
        mAnimator.start();
    }

    /** The stretch reached its target (or was cut short): it is over. */
    private void endStretch() {
        mAnimating = false;
        mFinishing = false;
        mAnimatedHeight = mStretchTargetHeight;
        requestLayout();
        notifyProgress();
        runFinishCallback();
    }

    /** Runs and clears whatever was waiting for the stretch to land. */
    private void runFinishCallback() {
        Runnable callback = finishCallback;
        finishCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void notifyProgress() {
        ProgressListener listener = progressListener;
        if (listener != null) {
            listener.onStretchProgress(getStretchProgress());
        }
    }

    private void cancelAnimator() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    /** Abandons a running stretch without landing it (view teardown). */
    public void cancelStretch() {
        cancelAnimator();
        mAnimating = false;
        mFinishing = false;
        finishCallback = null;
        progressListener = null;
    }

    public int getRatioWidth() {
        return mRatioWidth;
    }

    public int getRatioHeight() {
        return mRatioHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mRatioWidth == 0 || mRatioHeight == 0) {
            setMeasuredDimension(width, height);
        } else {
            int animatedHeight = mAnimatedHeight > 0f ? Math.round(mAnimatedHeight) : mRatioHeight;
            setMeasuredDimension(mRatioWidth, animatedHeight);
        }
    }
}
