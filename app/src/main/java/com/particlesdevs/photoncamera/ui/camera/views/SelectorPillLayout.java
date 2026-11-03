package com.particlesdevs.photoncamera.ui.camera.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.material.color.MaterialColors;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.ui.widget.MorphShapeDrawable;

/**
 * A LinearLayout that marks its selected child with one highlight pill, which
 * slides between the children on an M3E spring (slight overshoot) instead of
 * every child toggling its own background. Callers keep the children's selected
 * states in sync and then call {@link #refreshSelection()} (or
 * {@link #setSelectedIndex(int)}); the pill is painted behind the children and
 * never participates in layout.
 */
public class SelectorPillLayout extends LinearLayout {

    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final FloatValueHolder pillIndex = new FloatValueHolder(0f);
    private final SpringAnimation pillSpring;
    /** Target child index; {@link #pillAppliedIndex} is what the spring has. */
    private int pillTarget = -1;
    private int pillAppliedIndex = Integer.MIN_VALUE;
    private int pillChildCount = -1;
    private boolean pillPlaced;
    private float pillRadius;

    public SelectorPillLayout(Context context) {
        this(context, null);
    }

    public SelectorPillLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        pillPaint.setColor(MaterialColors.getColor(context, R.attr.colorPrimaryContainer,
                Color.TRANSPARENT));
        pillSpring = new SpringAnimation(pillIndex)
                .setSpring(createPillSpring())
                .addUpdateListener((animation, value, velocity) -> invalidate());
        // The pill is painted by this container, behind its children.
        setWillNotDraw(false);
    }

    /**
     * Spring driving the slide. Subclasses may slow it so the slide reads as one
     * motion with their other animations (the lens bar matches the viewfinder's
     * aspect stretch).
     */
    @NonNull
    protected SpringForce createPillSpring() {
        return MorphShapeDrawable.playfulSpring();
    }

    /** Animates the pill to the first selected child ({@code -1} hides it). */
    public void refreshSelection() {
        int index = -1;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).isSelected()) {
                index = i;
                break;
            }
        }
        setSelectedIndex(index);
    }

    /** Points the pill at {@code index}, animating from the previous child. */
    public void setSelectedIndex(int index) {
        pillTarget = index;
        // The children may be rebuilt between selections, so a changed set can
        // never be interpolated from the old index: snap in that case.
        if (getChildCount() != pillChildCount) {
            pillChildCount = getChildCount();
            pillPlaced = false;
            pillAppliedIndex = Integer.MIN_VALUE;
        }
        if (pillPlaced) {
            applyPillTarget();
        }
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        applyPillTarget();
    }

    /**
     * Moves the pill toward the selected child once the children have bounds.
     * The first placement (and a rebuilt child set) snaps; every later change
     * glides on the playful spring.
     */
    private void applyPillTarget() {
        if (pillTarget < 0 || pillTarget >= getChildCount()) {
            return;
        }
        View active = getChildAt(pillTarget);
        if (active.getWidth() <= 0 || active.getHeight() <= 0) {
            return;
        }
        pillRadius = Math.min(active.getWidth(), active.getHeight()) / 2f;
        if (!pillPlaced) {
            pillSpring.cancel();
            pillIndex.setValue(pillTarget);
            pillPlaced = true;
            pillAppliedIndex = pillTarget;
        } else if (pillAppliedIndex != pillTarget) {
            pillAppliedIndex = pillTarget;
            pillSpring.animateToFinalPosition(pillTarget);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Behind the children: onDraw runs before dispatchDraw. A single-child
        // selector has nothing to slide between.
        if (pillPlaced && pillTarget >= 0 && getChildCount() > 1) {
            boolean vertical = getOrientation() == VERTICAL;
            float along = pillCenterAlongAxis();
            float cx = vertical ? getWidth() / 2f : along;
            float cy = vertical ? along : getHeight() / 2f;
            canvas.drawCircle(cx, cy, pillRadius, pillPaint);
        }
        super.onDraw(canvas);
    }

    /**
     * The pill's centre along the layout axis, interpolated between the real
     * child centres by the spring's animated index, so it tracks both the
     * horizontal and the docked vertical arrangements.
     */
    private float pillCenterAlongAxis() {
        boolean vertical = getOrientation() == VERTICAL;
        View first = getChildAt(0);
        if (first == null) {
            return 0f;
        }
        float firstCenter = vertical ? first.getTop() + first.getHeight() / 2f
                : first.getLeft() + first.getWidth() / 2f;
        float step = 0f;
        if (getChildCount() > 1) {
            View second = getChildAt(1);
            float secondCenter = vertical ? second.getTop() + second.getHeight() / 2f
                    : second.getLeft() + second.getWidth() / 2f;
            step = secondCenter - firstCenter;
        }
        return firstCenter + step * pillIndex.getValue();
    }
}
