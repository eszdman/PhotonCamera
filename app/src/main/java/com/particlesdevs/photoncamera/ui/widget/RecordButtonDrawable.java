package com.particlesdevs.photoncamera.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.google.android.material.color.MaterialColors;
import com.particlesdevs.photoncamera.R;

/**
 * Modern video-record shutter face: an outer ring with a center dot.
 *
 * <p>Idle shows a white ring + dot; recording morphs the dot into a red
 * rounded square with a gentle pulse. Entry and record transitions run on
 * M3E spatial springs. Replaces the old {@code unlimitedbutton} selector so
 * mode switches and record toggles animate instead of swapping resources.
 */
public class RecordButtonDrawable extends Drawable {

    private static final long PULSE_PERIOD_MS = 600L;
    private static final int PULSE_DIM_ALPHA = 150;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dotRect = new RectF();
    private final FloatValueHolder dotScale = new FloatValueHolder(0f);
    private final FloatValueHolder recProgress = new FloatValueHolder(0f);
    private final SpringAnimation dotSpring;
    private final SpringAnimation recSpring;

    private int idleColor;
    private int recColor;

    private int alpha = 255;
    @Nullable
    private ColorFilter colorFilter;
    private boolean pressed;
    private boolean recording;
    private boolean pulseOn = true;

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

    public RecordButtonDrawable(@NonNull Context context) {
        refreshColors(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        dotPaint.setStyle(Paint.Style.FILL);
        dotSpring = new SpringAnimation(dotScale)
                .setSpring(MorphShapeDrawable.spatialSpring(context))
                .addUpdateListener((animation, value, velocity) -> invalidateSelf());
        recSpring = new SpringAnimation(recProgress)
                .setSpring(MorphShapeDrawable.spatialSpring(context))
                .addUpdateListener((animation, value, velocity) -> invalidateSelf());
    }

    /**
     * (Re)resolves role colors from the activity theme so Theme-Color accent
     * changes apply: idle ring + dot follow {@code ?attr/colorPrimary} like
     * the photo shutter, recording follows {@code ?attr/colorError}.
     */
    public void refreshColors(@NonNull Context context) {
        idleColor = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFFFFFFFF);
        recColor = MaterialColors.getColor(context, R.attr.colorError, 0xFFFF3B30);
        invalidateSelf();
    }

    /**
     * Pops the dot in (used when entering a record mode). No-op while
     * already shown so refreshes don't replay it.
     */
    public void rewindEntry() {
        if (dotScale.getValue() < 1f) {
            dotSpring.animateToFinalPosition(1f);
        }
    }

    /** Switches between idle dot and recording stop-glyph (animated). */
    public void setRecording(boolean recording) {
        if (this.recording == recording && recProgress.getValue() == (recording ? 1f : 0f)) {
            return;
        }
        this.recording = recording;
        pulseOn = true;
        unscheduleSelf(pulseTick);
        recSpring.animateToFinalPosition(recording ? 1f : 0f);
        if (recording && getCallback() != null) {
            scheduleSelf(pulseTick, SystemClock.uptimeMillis() + PULSE_PERIOD_MS);
        }
        invalidateSelf();
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
        float rec = recProgress.getValue();
        float scale = dotScale.getValue() * (pressed ? 0.88f : 1f);

        ringPaint.setColor(idleColor);
        ringPaint.setAlpha(alpha);
        ringPaint.setColorFilter(colorFilter);
        ringPaint.setStrokeWidth(Math.max(2f, r * 0.075f));
        canvas.drawCircle(cx, cy, r * 0.90f, ringPaint);

        if (scale <= 0f) {
            return;
        }
        // Idle: circle of radius 0.55R. Recording: rounded square of
        // half-size 0.5R with 35%-radius corners. Corner radius == half-size
        // at rec=0 renders the circle, so the morph is continuous.
        float half = r * (0.55f - 0.05f * rec) * scale;
        float corner = half * (1f - 0.65f * rec);
        dotPaint.setColor(lerpColor(idleColor, recColor, rec));
        int dotAlpha = alpha;
        if (recording && rec > 0.5f && !pulseOn) {
            dotAlpha = (alpha * PULSE_DIM_ALPHA) / 255;
        }
        dotPaint.setAlpha(dotAlpha);
        dotPaint.setColorFilter(colorFilter);
        dotRect.set(cx - half, cy - half, cx + half, cy + half);
        canvas.drawRoundRect(dotRect, corner, corner, dotPaint);
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
