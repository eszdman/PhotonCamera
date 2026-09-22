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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.graphics.shapes.Cubic;
import androidx.graphics.shapes.Morph;
import androidx.graphics.shapes.RoundedPolygon;

import com.google.android.material.motion.MotionUtils;
import com.google.android.material.shape.MaterialShapes;
import com.particlesdevs.photoncamera.R;

import java.util.List;

/**
 * A stateful drawable that morphs between two Material Shapes with an M3E
 * spatial spring while switching its fill and stroke colors per drawable
 * state.
 *
 * <p>The pressed state animates the morph progress to 1 (the second shape);
 * releasing springs it back to 0 (the rest shape). Colors come from two
 * {@link ColorStateList}s, so callers keep full control of the palette.
 */
public class MorphShapeDrawable extends Drawable {

    /** Shared coordinate space both shapes are normalized into before morphing. */
    private static final RectF UNIT_BOUNDS = new RectF(0f, 0f, 1f, 1f);

    private final Morph morph;
    private final ColorStateList fillColors;
    private final ColorStateList strokeColors;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final FloatValueHolder progress = new FloatValueHolder(0f);
    private final SpringAnimation progressSpring;

    private int alpha = 255;
    @Nullable
    private ColorFilter colorFilter;
    private boolean pressed;

    public MorphShapeDrawable(@NonNull RoundedPolygon restShape, @NonNull RoundedPolygon pressedShape,
                              float strokeWidthPx, @NonNull ColorStateList fillColors,
                              @NonNull ColorStateList strokeColors, @NonNull SpringForce springForce) {
        this.fillColors = fillColors;
        this.strokeColors = strokeColors;
        // Normalize both into one unit space with morph-aware bounds so the
        // shapes (and their overshoot) share a coordinate system.
        RoundedPolygon rest = MaterialShapes.normalize(restShape, true, UNIT_BOUNDS);
        RoundedPolygon pressedShapeNormalized = MaterialShapes.normalize(pressedShape, true, UNIT_BOUNDS);
        this.morph = new Morph(rest, pressedShapeNormalized);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidthPx);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        progressSpring = new SpringAnimation(progress)
                .setSpring(springForce)
                .addUpdateListener((animation, value, velocity) -> invalidateSelf());
    }

    /** Circle &harr; cookie shutter drawable painted with the camera palette. */
    @NonNull
    public static MorphShapeDrawable shutter(@NonNull Context context) {
        float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f,
                context.getResources().getDisplayMetrics());
        return new MorphShapeDrawable(
                MaterialShapes.CIRCLE, MaterialShapes.COOKIE_9, strokeWidth,
                ContextCompat.getColorStateList(context, R.color.shutter_morph_fill),
                ContextCompat.getColorStateList(context, R.color.shutter_morph_stroke),
                spatialSpring(context));
    }

    /** M3E fast spatial spring from the theme, with a spec fallback. */
    @NonNull
    public static SpringForce spatialSpring(@NonNull Context context) {
        try {
            return MotionUtils.resolveThemeSpringForce(context,
                    R.attr.motionSpringFastSpatial, R.attr.motionSpringFastSpatial);
        } catch (RuntimeException e) {
            SpringForce fallback = new SpringForce(1f);
            fallback.setStiffness(1500f);
            fallback.setDampingRatio(0.9f);
            return fallback;
        }
    }

    /**
     * M3E expressive "playful" spring: the fast spatial spring's stiffness with
     * a lighter damping ratio, so motion overshoots slightly (~4%) before
     * settling. The Material spring tokens only ship the standard (critically
     * damped) set, so this is defined here. Used by the lens selection pill and
     * the mode picker's snap.
     */
    @NonNull
    public static SpringForce playfulSpring() {
        SpringForce spring = new SpringForce(1f);
        spring.setStiffness(1400f);
        spring.setDampingRatio(0.7f);
        return spring;
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
            progressSpring.animateToFinalPosition(pressed ? 1f : 0f);
        }
        return true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        int[] state = getState();
        fillPaint.setColor(fillColors.getColorForState(state, fillColors.getDefaultColor()));
        fillPaint.setAlpha(alpha);
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColor(strokeColors.getColorForState(state, strokeColors.getDefaultColor()));
        strokePaint.setAlpha(alpha);
        strokePaint.setColorFilter(colorFilter);

        updatePath();
        // Keep the centered outline fully inside the bounds (its outer half
        // would otherwise be clipped at the shape's cardinal points) plus a
        // hair for antialiasing.
        float inset = strokePaint.getStrokeWidth() / 2f + 0.5f;
        matrix.setScale(Math.max(1f, bounds.width() - inset * 2f),
                Math.max(1f, bounds.height() - inset * 2f));
        matrix.postTranslate(bounds.left + inset, bounds.top + inset);
        path.transform(matrix);
        canvas.drawPath(path, fillPaint);
        canvas.drawPath(path, strokePaint);
    }

    private void updatePath() {
        buildPath(morph, progress.getValue(), path);
    }

    /**
     * Writes the morphed silhouette for {@code progress} into {@code out}
     * (unit space; the draw pass scales it to the target bounds).
     */
    private static void buildPath(@NonNull Morph morph, float progress, @NonNull Path out) {
        out.rewind();
        List<Cubic> cubics = morph.asCubics(progress);
        for (int i = 0; i < cubics.size(); i++) {
            Cubic cubic = cubics.get(i);
            if (i == 0) {
                out.moveTo(cubic.getAnchor0X(), cubic.getAnchor0Y());
            }
            out.cubicTo(cubic.getControl0X(), cubic.getControl0Y(),
                    cubic.getControl1X(), cubic.getControl1Y(),
                    cubic.getAnchor1X(), cubic.getAnchor1Y());
        }
        out.close();
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
