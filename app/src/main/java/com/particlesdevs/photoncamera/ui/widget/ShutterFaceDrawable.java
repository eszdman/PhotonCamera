package com.particlesdevs.photoncamera.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapes;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;

import androidx.graphics.shapes.Morph;

/**
 * The shutter face for every mode: one drawable that morphs between the photo
 * shutter and the video record button instead of swapping backgrounds.
 *
 * <p>Photo face: a disc that reaches the ring, filled and stroked from the
 * camera palette state lists, morphing into a cookie while pressed. Video face:
 * a smaller dot inside the same ring, morphing into a red rounded square while
 * recording (with the pulse). The face morph only changes size and colour - both
 * idle forms are circles - so switching between them reads as the disc
 * shrinking into the dot, and a recording that is still unwinding blends with
 * the other face instead of jumping.
 */
public class ShutterFaceDrawable extends Drawable {

    private static final long PULSE_PERIOD_MS = 600L;
    private static final int PULSE_DIM_ALPHA = 150;
    /** Video face: ring radius and dot half-size as fractions of the radius. */
    private static final float VIDEO_RING_FRACTION = 0.90f;
    private static final float VIDEO_DOT_FRACTION = 0.55f;
    /** Video face: how much the dot shrinks while recording. */
    private static final float VIDEO_REC_SHRINK = 0.05f;
    /** Recording square: corner radius as a fraction of its half-size. */
    private static final float VIDEO_REC_CORNER = 0.65f;
    /** Press scale of the video dot. */
    private static final float PRESS_SCALE = 0.88f;
    /**
     * Face morph duration. Matches the viewfinder's aspect stretch, so the
     * shutter morphs for as long as the rest of the mode-switch animation.
     */
    private static final long MODE_MORPH_MS = 700L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();

    /** 0 = photo face, 1 = video face. */
    private float modeProgress;
    /** 0 = idle, 1 = pressed cookie (photo) or recording square (video). */
    private final FloatValueHolder formProgress = new FloatValueHolder(0f);
    @Nullable
    private ValueAnimator modeAnimator;
    private final SpringAnimation formSpring;
    private final TimeInterpolator modeInterpolator;

    private final ColorStateList photoFillColors;
    private final ColorStateList photoStrokeColors;
    private final Morph photoForm;
    private final float photoRingStrokePx;

    private int videoIdleColor;
    private int videoRecColor;

    private boolean videoMode;
    private boolean recording;
    private boolean pressed;
    private boolean pulseOn = true;
    private int alpha = 255;
    @Nullable
    private ColorFilter colorFilter;

    private final Runnable pulseTick = new Runnable() {
        @Override
        public void run() {
            pulseOn = !pulseOn;
            invalidateSelf();
            if (recording && getCallback() != null) {
                scheduleSelf(this, SystemClock.uptimeMillis() + PULSE_PERIOD_MS);
            }
        }
    };

    public ShutterFaceDrawable(@NonNull Context context) {
        photoFillColors = ContextCompat.getColorStateList(context, R.color.shutter_morph_fill);
        photoStrokeColors = ContextCompat.getColorStateList(context, R.color.shutter_morph_stroke);
        photoRingStrokePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f,
                context.getResources().getDisplayMetrics());
        photoForm = MorphShapeDrawable.morphBetween(MaterialShapes.CIRCLE, MaterialShapes.COOKIE_9);
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        modeInterpolator = Motion.standard(context);
        formSpring = new SpringAnimation(formProgress)
                .setSpring(MorphShapeDrawable.spatialSpring(context))
                .addUpdateListener((animation, value, velocity) -> invalidateSelf());
        refreshColors(context);
    }

    /**
     * (Re)resolves the video face's role colors from the activity theme so
     * Theme-Color accent changes apply: idle follows {@code ?attr/colorPrimary}
     * like the photo face, recording follows {@code ?attr/colorError}.
     */
    public void refreshColors(@NonNull Context context) {
        videoIdleColor = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFFFFFFFF);
        videoRecColor = MaterialColors.getColor(context, R.attr.colorError, 0xFFFF3B30);
        invalidateSelf();
    }

    /**
     * Morphs between the photo shutter (false) and the video record face
     * (true). Called by every mode state, so the face animates with the rest of
     * the switch instead of swapping backgrounds. Runs on the same curve and
     * duration as the viewfinder's aspect stretch, so the shutter morph lasts as
     * long as the mode-switch animation.
     */
    public void setMode(boolean video) {
        if (videoMode == video) {
            return;
        }
        videoMode = video;
        if (modeAnimator != null) {
            modeAnimator.cancel();
        }
        modeAnimator = ValueAnimator.ofFloat(modeProgress, video ? 1f : 0f);
        modeAnimator.setDuration(MODE_MORPH_MS);
        modeAnimator.setInterpolator(modeInterpolator);
        modeAnimator.addUpdateListener(animation -> {
            modeProgress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        modeAnimator.start();
        updateFormTarget();
        invalidateSelf();
    }

    /** Idle dot vs the recording stop glyph (video face), animated. */
    public void setRecording(boolean recording) {
        if (this.recording == recording) {
            return;
        }
        this.recording = recording;
        pulseOn = true;
        unscheduleSelf(pulseTick);
        if (recording && getCallback() != null) {
            scheduleSelf(pulseTick, SystemClock.uptimeMillis() + PULSE_PERIOD_MS);
        }
        updateFormTarget();
        invalidateSelf();
    }

    /**
     * The inner shape's target: the press cookie on the photo face, the record
     * square on the video face. They are exclusive, so one progress drives both.
     */
    private void updateFormTarget() {
        boolean engaged = videoMode ? recording : pressed;
        formSpring.animateToFinalPosition(engaged ? 1f : 0f);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean nowPressed = false;
        for (int state : stateSet) {
            if (state == android.R.attr.state_pressed) {
                nowPressed = true;
                break;
            }
        }
        if (nowPressed != pressed) {
            pressed = nowPressed;
            updateFormTarget();
            invalidateSelf();
        }
        return true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float r = Math.min(bounds.width(), bounds.height()) / 2f;
        float mode = clamp01(modeProgress);
        float form = clamp01(formProgress.getValue());

        int[] state = getState();
        int photoFill = photoFillColors.getColorForState(state, photoFillColors.getDefaultColor());
        int photoStroke = photoStrokeColors.getColorForState(state, photoStrokeColors.getDefaultColor());
        int videoFill = lerpColor(videoIdleColor, videoRecColor, form);

        // The photo disc fills up to the ring; the video dot stays small inside
        // it and shrinks a little further while recording.
        float photoHalf = Math.max(0f, r - photoRingStrokePx / 2f - 0.5f);
        float videoHalf = r * (VIDEO_DOT_FRACTION - VIDEO_REC_SHRINK * form);
        float half = lerp(photoHalf, videoHalf, mode);
        if (videoMode && pressed) {
            half *= PRESS_SCALE;
        }

        // The photo's outline sits on the disc's edge, the video's slightly
        // inside it, so the radius interpolates with the face.
        float ringStroke = Math.max(2f, lerp(photoRingStrokePx, r * 0.075f, mode));
        ringPaint.setColor(lerpColor(photoStroke, videoIdleColor, mode));
        ringPaint.setAlpha(alpha);
        ringPaint.setColorFilter(colorFilter);
        ringPaint.setStrokeWidth(ringStroke);
        canvas.drawCircle(cx, cy,
                Math.max(ringStroke, lerp(photoHalf, r * VIDEO_RING_FRACTION, mode)), ringPaint);

        if (half <= 0.5f) {
            return;
        }
        int fillAlpha = alpha;
        if (recording && form > 0.5f && !pulseOn) {
            fillAlpha = (alpha * PULSE_DIM_ALPHA) / 255;
        }
        fillPaint.setColorFilter(colorFilter);
        // Both faces' inner shapes are circles at rest, so blending them across
        // the face morph is exact there - and continuous even when a press or a
        // recording is still unwinding while the face changes.
        if (mode < 0.999f) {
            fillPaint.setColor(photoFill);
            fillPaint.setAlpha(Math.round(fillAlpha * (1f - mode)));
            drawInner(canvas, cx, cy, half, form, true);
        }
        if (mode > 0.001f) {
            fillPaint.setColor(videoFill);
            fillPaint.setAlpha(Math.round(fillAlpha * mode));
            drawInner(canvas, cx, cy, half, form, false);
        }
    }

    /**
     * Writes the inner shape into {@link #path} and fills it. The photo face
     * morphs the disc toward the cookie while pressed; the video face morphs the
     * dot toward a rounded square while recording.
     */
    private void drawInner(Canvas canvas, float cx, float cy, float half, float form, boolean photoFace) {
        if (photoFace) {
            MorphShapeDrawable.buildPath(photoForm, form, path);
            matrix.setScale(half * 2f, half * 2f);
            matrix.postTranslate(cx - half, cy - half);
            path.transform(matrix);
        } else {
            float corner = half * (1f - VIDEO_REC_CORNER * form);
            path.rewind();
            path.addRoundRect(cx - half, cy - half, cx + half, cy + half,
                    corner, corner, Path.Direction.CW);
        }
        canvas.drawPath(path, fillPaint);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static int lerpColor(int from, int to, float t) {
        float it = 1f - t;
        return (Math.round(((from >>> 24) * it + (to >>> 24) * t)) << 24)
                | (Math.round((((from >> 16) & 0xFF) * it + ((to >> 16) & 0xFF) * t)) << 16)
                | (Math.round((((from >> 8) & 0xFF) * it + ((to >> 8) & 0xFF) * t)) << 8)
                | Math.round(((from & 0xFF) * it + (to & 0xFF) * t));
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
