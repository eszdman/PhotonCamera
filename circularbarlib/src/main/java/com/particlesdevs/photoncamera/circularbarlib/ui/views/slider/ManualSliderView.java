package com.particlesdevs.photoncamera.circularbarlib.ui.views.slider;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Center-locked horizontal value strip replacing the old circular {@code KnobView}.
 *
 * <p>The selected item is always drawn centered (M3E emphasis: larger, primary
 * indicator pill underneath); drag/fling slides the items underneath and the
 * view snaps to the nearest tick on release. Values fade out toward the left
 * and right edges via distance-based alpha.
 */
public class ManualSliderView extends View {
    private List<SliderItem> items = Collections.emptyList();
    private SliderChangedListener listener;
    private int selectedIndex = 0;

    private float scrollPx = 0f;
    private float itemPitchPx;

    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unselectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF indicatorRect = new RectF();

    private float textSizeSelected;
    private float textSizeUnselected;
    private float gainTextSize;
    private float indicatorWidthPx;
    private float indicatorHeightPx;
    private float tickWidthPx;
    private float tickHeightPx;
    private float gainRampPx;
    private int primaryColor;
    private int secondaryColor;
    private boolean isSecondary;
    private float currentGain = 1f;

    private OverScroller scroller;
    private VelocityTracker velocityTracker;
    private ValueAnimator snapAnimator;
    private float lastTouchX;
    private float downX;
    private float downY;
    private boolean isDragging;
    private int touchSlop;
    private int minFlingVelocity;
    private int maxFlingVelocity;

    public ManualSliderView(Context context) {
        this(context, null);
    }

    public ManualSliderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        itemPitchPx = resolveDimen(R.dimen.manual_slider_item_pitch, 72f * density);
        textSizeSelected = resolveDimen(R.dimen.manual_slider_text_size_selected, 18f * density / 1.5f);
        // resolveDimen returns px for dimens; text sizes stored as sp-converted px already.
        textSizeUnselected = resolveDimen(R.dimen.manual_slider_text_size, 14f * density / 1.5f);
        indicatorWidthPx = 24f * density;
        indicatorHeightPx = 4f * density;
        tickWidthPx = 2f * density;
        tickHeightPx = 12f * density;
        gainTextSize = 12f * density;
        // Full coarse gain reached by pressing this far below the touch-down
        // point toward the option bar; keeps a whole-range sweep inside a
        // short controlled drag.
        gainRampPx = 120f * density;

        int selectedColor = resolveThemeColor(android.R.attr.colorControlActivated, 0xFFFFFFFF);
        primaryColor = selectedColor;
        secondaryColor = resolveSecondaryColor(selectedColor);
        int unselectedColor = 0xFFFFFFFF;

        selectedPaint.setTextAlign(Paint.Align.CENTER);
        selectedPaint.setTextSize(textSizeSelected);
        selectedPaint.setColor(selectedColor);
        selectedPaint.setFakeBoldText(true);

        unselectedPaint.setTextAlign(Paint.Align.CENTER);
        unselectedPaint.setTextSize(textSizeUnselected);
        unselectedPaint.setColor(unselectedColor);

        tickPaint.setColor(unselectedColor);
        tickPaint.setStrokeWidth(tickWidthPx);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        indicatorPaint.setColor(selectedColor);

        gainPaint.setTextAlign(Paint.Align.CENTER);
        gainPaint.setTextSize(gainTextSize);
        gainPaint.setColor(selectedColor);
        gainPaint.setFakeBoldText(true);

        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        setClickable(true);
        setFocusable(true);
    }

    private float resolveDimen(int resId, float fallbackPx) {
        try {
            return getResources().getDimension(resId);
        } catch (Exception e) {
            return fallbackPx;
        }
    }

    private int resolveThemeColor(int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                try {
                    TypedArray a = getContext().obtainStyledAttributes(new int[]{attr});
                    int c = a.getColor(0, fallback);
                    a.recycle();
                    return c;
                } catch (Exception ignored) {
                    return fallback;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private int resolveSecondaryColor(int fallback) {
        // Same fill as the remembered bar cell (activated state in
        // manual_option_background): ?attr/colorSecondaryContainer.
        // Looked up by name so no compile-time dependency on the Material R
        // (whose attr ids differ from the merged app theme).
        try {
            int attrId = getResources().getIdentifier(
                    "colorSecondaryContainer", "attr", getContext().getPackageName());
            if (attrId != 0) {
                int resolved = resolveThemeColor(attrId, 0);
                if (resolved != 0) {
                    return resolved;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /**
     * Role of this strip: the lower (primary) row uses the primary selection
     * color, the upper (remembered) row the secondary selection color — the
     * same distinction as the option bar's selected vs. remembered pills.
     */
    public void setSecondary(boolean secondary) {
        if (this.isSecondary != secondary) {
            this.isSecondary = secondary;
            int accent = secondary ? secondaryColor : primaryColor;
            selectedPaint.setColor(accent);
            indicatorPaint.setColor(accent);
            gainPaint.setColor(accent);
            invalidate();
        }
    }

    public boolean isSecondary() {
        return isSecondary;
    }

    public void setListener(SliderChangedListener listener) {
        this.listener = listener;
    }

    public void setItems(List<SliderItem> newItems) {
        if (newItems == null) {
            newItems = Collections.emptyList();
        }
        cancelSnap();
        scroller.abortAnimation();
        items = new ArrayList<>(newItems);
        if (selectedIndex >= items.size()) {
            selectedIndex = Math.max(0, items.size() - 1);
        }
        scrollPx = selectedIndex * itemPitchPx;
        updateContentDescription();
        invalidate();
    }

    public List<SliderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Nullable
    public SliderItem getSelectedItem() {
        if (items.isEmpty() || selectedIndex < 0 || selectedIndex >= items.size()) {
            return null;
        }
        return items.get(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Snap to the item whose value matches, e.g. when the model changes externally. */
    public void setSelectedByValue(double value, boolean animate) {
        int index = indexForValue(value);
        if (index >= 0) {
            setSelectedIndex(index, animate);
        }
    }

    public void setSelectedIndex(int index, boolean animate) {
        if (items.isEmpty()) {
            return;
        }
        int clamped = Math.max(0, Math.min(index, items.size() - 1));
        if (!animate) {
            cancelSnap();
            scroller.abortAnimation();
            setIndexInternal(clamped);
            scrollPx = clamped * itemPitchPx;
            invalidate();
            return;
        }
        animateToIndex(clamped);
    }

    private int indexForValue(double value) {
        for (int i = 0; i < items.size(); i++) {
            if (Math.abs(items.get(i).value - value) < 1.0E-4d) {
                return i;
            }
        }
        return -1;
    }

    private int indexForTick(int tick) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).tick == tick) {
                return i;
            }
        }
        return -1;
    }

    /** Used by Binding when a ManualModel is attached: snap to its current value. */
    public void bindToTick(int tick, boolean animate) {
        int index = indexForTick(tick);
        if (index >= 0) {
            setSelectedIndex(index, animate);
        } else if (!items.isEmpty()) {
            setSelectedIndex(0, false);
        }
    }

    private void setIndexInternal(int newIndex) {
        if (newIndex == selectedIndex) {
            updateContentDescription();
            return;
        }
        SliderItem oldItem = getSelectedItem();
        selectedIndex = newIndex;
        SliderItem newItem = getSelectedItem();
        updateContentDescription();
        if (listener != null && newItem != null && oldItem != newItem) {
            listener.onSelectedItemChanged(this, oldItem, newItem);
        }
    }

    private void updateContentDescription() {
        SliderItem item = getSelectedItem();
        setContentDescription(item != null ? item.text : null);
    }

    private float maxScroll() {
        return SliderMath.maxScroll(items.size(), itemPitchPx);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (items.isEmpty()) {
            return;
        }
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float halfW = getWidth() / 2f;

        // Center indicator pill below the text baseline area.
        float pillTop = centerY + textSizeSelected * 0.7f;
        indicatorRect.set(centerX - indicatorWidthPx / 2f, pillTop,
                centerX + indicatorWidthPx / 2f, pillTop + indicatorHeightPx);
        float pillRadius = indicatorHeightPx / 2f;
        canvas.drawRoundRect(indicatorRect, pillRadius, pillRadius, indicatorPaint);

        Paint.FontMetrics fmSelected = selectedPaint.getFontMetrics();
        float selectedBaseline = centerY - (fmSelected.ascent + fmSelected.descent) / 2f - indicatorHeightPx;
        Paint.FontMetrics fmUnselected = unselectedPaint.getFontMetrics();
        float unselectedBaseline = centerY - (fmUnselected.ascent + fmUnselected.descent) / 2f - indicatorHeightPx;

        int first = Math.max(0, (int) ((scrollPx - halfW) / itemPitchPx) - 1);
        int last = Math.min(items.size() - 1, (int) ((scrollPx + halfW) / itemPitchPx) + 1);
        for (int i = first; i <= last; i++) {
            float x = centerX + (i * itemPitchPx - scrollPx);
            float distance = Math.abs(x - centerX) / Math.max(1f, halfW);
            // Fade to 0 at the edges so values dissolve into the scrim.
            float alpha = 1f - distance * distance;
            alpha = Math.max(0f, Math.min(1f, alpha));
            // Keep the centered item fully opaque even while settling.
            boolean isSelected = (i == selectedIndex);
            SliderItem item = items.get(i);
            if (item.label == null || item.label.isEmpty()) {
                tickPaint.setAlpha((int) (255 * (isSelected ? 1f : 0.55f * alpha + 0.15f)));
                float top = centerY - tickHeightPx / 2f;
                canvas.drawLine(x, top, x, top + tickHeightPx, tickPaint);
                continue;
            }
            Paint paint = isSelected ? selectedPaint : unselectedPaint;
            int baseAlpha = isSelected ? 255 : (int) (255 * (0.25f + 0.75f * alpha));
            // Work on a copy of alpha so neighbor paints don't accumulate.
            int prevAlpha = paint.getAlpha();
            paint.setAlpha(baseAlpha);
            canvas.drawText(item.label, x, isSelected ? selectedBaseline : unselectedBaseline, paint);
            paint.setAlpha(prevAlpha);
        }

        // Coarse-gain readout while pressed: mirrors the old wheel's
        // toward-center speedup for a controlled whole-range sweep.
        if (currentGain >= 1.5f) {
            int prevAlpha = gainPaint.getAlpha();
            gainPaint.setAlpha(255);
            canvas.drawText(formatGain(currentGain), centerX,
                    gainPaint.getTextSize() + 4f * getResources().getDisplayMetrics().density,
                    gainPaint);
            gainPaint.setAlpha(prevAlpha);
        }
    }

    private static String formatGain(float gain) {
        if (gain < 2.5f) {
            return String.format(java.util.Locale.US, "×%.1f", gain);
        }
        return "×" + Math.round(gain);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (items.size() <= 1) {
            return super.onTouchEvent(event);
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSnap();
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                lastTouchX = event.getX();
                downX = event.getX();
                downY = event.getY();
                isDragging = false;
                setGain(1f);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                float dx = lastTouchX - x;
                // Press-to-go-coarse, the slider analog of the old wheel's
                // toward-center speedup: finger depth below touch-down toward
                // the option bar scales horizontal travel up to 8x for a
                // controlled whole-range sweep.
                setGain(SliderMath.gainForPress(event.getY() - downY, gainRampPx));
                if (!isDragging && (Math.abs(x - downX) > touchSlop
                        || currentGain >= 1.5f && Math.abs(downY - event.getY()) > touchSlop)) {
                    isDragging = true;
                }
                if (isDragging) {
                    scrollPx = SliderMath.clampScroll(
                            scrollPx + dx * currentGain, items.size(), itemPitchPx);
                    lastTouchX = x;
                    updateSelectionFromScroll();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                float vx = velocityTracker.getXVelocity();
                getParent().requestDisallowInterceptTouchEvent(false);
                recycleTracker();
                // Fling runs on raw velocity; gain only shapes the drag itself.
                setGain(1f);
                if (isDragging && Math.abs(vx) > minFlingVelocity) {
                    scroller.fling((int) scrollPx, 0, (int) -vx, 0,
                            0, (int) maxScroll(), 0, 0);
                    // Drive fling frames through the snap path: clamp + settle.
                    post(flingRunner);
                } else {
                    settleToNearest();
                }
                isDragging = false;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                recycleTracker();
                isDragging = false;
                setGain(1f);
                settleToNearest();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private final Runnable flingRunner = new Runnable() {
        @Override
        public void run() {
            if (scroller.computeScrollOffset()) {
                scrollPx = SliderMath.clampScroll(scroller.getCurrX(), items.size(), itemPitchPx);
                updateSelectionFromScroll();
                invalidate();
                postOnAnimation(this);
            } else {
                settleToNearest();
            }
        }
    };

    private void setGain(float gain) {
        float clamped = Math.max(1f, Math.min(SliderMath.MAX_GAIN, gain));
        if (Math.abs(clamped - currentGain) > 0.01f) {
            currentGain = clamped;
            invalidate();
        }
    }

    private void updateSelectionFromScroll() {        if (items.isEmpty()) {
            return;
        }
        int nearest = SliderMath.snapIndex(scrollPx, itemPitchPx, items.size());
        if (nearest != selectedIndex) {
            setIndexInternal(nearest);
            invalidate();
        }
    }

    private void settleToNearest() {
        if (items.isEmpty()) {
            return;
        }
        int target = SliderMath.snapIndex(scrollPx, itemPitchPx, items.size());
        animateToIndex(target);
    }

    private void animateToIndex(int target) {
        cancelSnap();
        final float from = scrollPx;
        final float to = target * itemPitchPx;
        if (Math.abs(from - to) < 0.5f) {
            scrollPx = to;
            setIndexInternal(target);
            invalidate();
            return;
        }
        snapAnimator = ValueAnimator.ofFloat(from, to);
        snapAnimator.setDuration(Motion.durationShort2(getContext()));
        snapAnimator.setInterpolator(Motion.emphasized(getContext()));
        final int targetIndex = target;
        snapAnimator.addUpdateListener(animation -> {
            scrollPx = (float) animation.getAnimatedValue();
            updateSelectionFromScroll();
            invalidate();
        });
        snapAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                scrollPx = to;
                setIndexInternal(targetIndex);
                invalidate();
            }
        });
        snapAnimator.start();
    }

    private void cancelSnap() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
        removeCallbacks(flingRunner);
    }

    private void recycleTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelSnap();
        scroller.abortAnimation();
        recycleTracker();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
