/*
 * Copyright 2014 Blaž Šolar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.particlesdevs.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.*;
import android.os.*;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import androidx.annotation.RequiresApi;
import androidx.core.text.TextDirectionHeuristicCompat;
import androidx.core.text.TextDirectionHeuristicsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.ui.widget.MorphShapeDrawable;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Blaž Šolar on 24/01/14.
 */
public class HorizontalPicker extends View {

    public static final String TAG = "HorizontalTimePicker";

    /**
     * The coefficient by which to adjust (divide) the max fling velocity.
     */
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 4;

    private final int overscrollDistance;
    private final PickerTouchHelper touchHelper;
    private final Vibration vibration;
    /**
     * Determines speed during touch scrolling.
     */
    private VelocityTracker mVelocityTracker;
    /**
     * @see ViewConfiguration#getScaledMinimumFlingVelocity()
     */
    private final int mMinimumFlingVelocity;
    /**
     * @see ViewConfiguration#getScaledMaximumFlingVelocity()
     */
    private final int maximumFlingVelocity;
    private final int touchSlop;
    private CharSequence[] values;
    private BoringLayout[] layouts;
    private final TextPaint textPaint;
    private final BoringLayout.Metrics boringMetrics;
    private TextUtils.TruncateAt ellipsize;
    private int itemWidth;
    private RectF itemClipBounds;
    private RectF itemClipBoundsOffset;
    private float lastDownEventX;
    private final OverScroller flingScrollerX;
    /**
     * M3E spring snap for the selector movement: tap/key moves and the final
     * settle after a drag or fling. The playful spring glides slightly past
     * the target before settling, unlike the fixed-duration OverScroller snap.
     */
    private final FloatValueHolder springScrollX = new FloatValueHolder(0f);
    private final SpringAnimation springScroll;
    private Runnable springScrollEndAction;
    private int previousScrollerX;
    private boolean scrollingX;
    private int pressedItem = -1;
    private int lastTickItem = -1;
    private ColorStateList textColor;
    private final int selectedTextColor;
    private final int selectedBackgroundColor;
    private OnItemSelected onItemSelected;
    private OnItemClicked onItemClicked;
    private int selectedItem;
    private EdgeEffect leftEdgeEffect;
    private EdgeEffect rightEdgeEffect;
    private Marquee marquee;
    private int marqueeRepeatLimit = 3;
    private float dividerSize = 0;
    private int sideItems = 1;
    private TextDirectionHeuristicCompat textDir;
    private final Matrix fadeMatrix = new Matrix();
    private float fadeFraction;

    /**
     * Selection highlight: a manual-bar-style squircle that briefly scales in
     * with an M3E spring whenever the centred item changes.
     */
    private final float pillCornerRadius;
    private final FloatValueHolder pillScale = new FloatValueHolder(1f);
    private final SpringAnimation pillScaleSpring;
    private int pillPopItem = Integer.MIN_VALUE;

    public HorizontalPicker(Context context) {
        this(context, null);
    }

    public HorizontalPicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.horizontalPickerStyle);
    }

    public HorizontalPicker(Context context, AttributeSet attributeSet, int defStyle) {
        super(context, attributeSet, defStyle);

        ViewConfiguration configuration = ViewConfiguration.get(context);
        // In the layout editor the PhotonCamera Application instance is never created.
        vibration = isInEditMode() ? null : PhotonCamera.getVibration();
        touchSlop = configuration.getScaledTouchSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity()
                / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;
        overscrollDistance = configuration.getScaledOverscrollDistance();
        touchHelper = new PickerTouchHelper(this);
        boringMetrics = new BoringLayout.Metrics();

        // create the selector wheel paint
        textPaint = getTextPaint(context);

        // Selection highlight: manual-bar squircle radius + a light spring pop.
        pillCornerRadius = context.getResources().getDimension(R.dimen.m3_sys_shape_corner_value_large);
        pillScaleSpring = new SpringAnimation(pillScale)
                .setSpring(MorphShapeDrawable.spatialSpring(context))
                .addUpdateListener((animation, value, velocity) -> invalidate());

        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attributeSet,
                R.styleable.HorizontalPicker,
                defStyle, 0
        );

        CharSequence[] values;
        int ellipsize = 3; // END default value
        int sideItems = this.sideItems;

        try {
            textColor = typedArray.getColorStateList(R.styleable.HorizontalPicker_android_textColor);
            if (textColor == null) {
                textColor = ColorStateList.valueOf(0xFF000000);
            }

            values = typedArray.getTextArray(R.styleable.HorizontalPicker_values);
            ellipsize = typedArray.getInt(R.styleable.HorizontalPicker_android_ellipsize, ellipsize);
            marqueeRepeatLimit = typedArray.getInt(R.styleable.HorizontalPicker_android_marqueeRepeatLimit, marqueeRepeatLimit);
            dividerSize = typedArray.getDimension(R.styleable.HorizontalPicker_dividerSize, dividerSize);
            sideItems = typedArray.getInt(R.styleable.HorizontalPicker_sideItems, sideItems);
            selectedTextColor = typedArray.getColor(R.styleable.HorizontalPicker_selectedColor, 0XFFFFFFFF);
            selectedBackgroundColor = typedArray.getColor(R.styleable.HorizontalPicker_selectedBackgroundColor, 0X00FFFFFF);
            float textSize = typedArray.getDimension(R.styleable.HorizontalPicker_android_textSize, -1);
            if (textSize > -1) {
                setTextSize(textSize);
            }
        } finally {
            typedArray.recycle();
        }

        selectEllipsize(ellipsize);

        setBorginMetrics();

        setWillNotDraw(false);

        flingScrollerX = new OverScroller(context);
        springScroll = new SpringAnimation(springScrollX)
                .setSpring(MorphShapeDrawable.playfulSpring())
                .addUpdateListener((animation, value, velocity) -> applySpringScroll(value))
                .addEndListener((animation, canceled, value, velocity) -> onSpringScrollEnd(canceled));

        initializeConstants(context, values, sideItems);

        // The layout editor can't run the fragment, so populate the picker with the
        // same camera mode names that CameraUIViewImpl.initModeSwitcher() sets at runtime.
        if (isInEditMode()) {
            setValues(Arrays.stream(CameraMode.nameIds())
                    .map(getContext()::getString)
                    .toArray(String[]::new));
            setSelectedItem(CameraMode.PHOTO.ordinal());
        }
    }

    private static TextPaint getTextPaint(Context context) {
        TextPaint paint = new TextPaint();
        paint.setAntiAlias(true);
        // M3E Roboto: the selected item is drawn with fake bold on top of this.
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        return paint;
    }

    private void initializeConstants(Context context, CharSequence[] values, int sideItems) {
        previousScrollerX = Integer.MIN_VALUE;

        setValues(values);
        setSideItems(sideItems);

        ViewCompat.setAccessibilityDelegate(this, touchHelper);
        leftEdgeEffect = new EdgeEffect(context);
        rightEdgeEffect = new EdgeEffect(context);
    }

    private void setBorginMetrics() {
        Paint.FontMetricsInt fontMetricsInt = textPaint.getFontMetricsInt();
        boringMetrics.ascent = fontMetricsInt.ascent;
        boringMetrics.bottom = fontMetricsInt.bottom;
        boringMetrics.descent = fontMetricsInt.descent;
        boringMetrics.leading = fontMetricsInt.leading;
        boringMetrics.top = fontMetricsInt.top;
        boringMetrics.width = itemWidth;
    }

    private void selectEllipsize(int ellipsize) {
        switch (ellipsize) {
            case 1:
                setEllipsize(TextUtils.TruncateAt.START);
                break;
            case 2:
                setEllipsize(TextUtils.TruncateAt.MIDDLE);
                break;
            case 3:
                setEllipsize(TextUtils.TruncateAt.END);
                break;
            case 4:
                setEllipsize(TextUtils.TruncateAt.MARQUEE);
                break;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int height;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            int heightText = (int) (Math.abs(fontMetrics.ascent) + Math.abs(fontMetrics.descent));
            heightText += getPaddingTop() + getPaddingBottom();

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(heightSize, heightText);
            } else {
                height = heightText;
            }
        }

        setMeasuredDimension(width, height);
    }

    private final RectF background = new RectF();
    private final Rect canvasClipBounds = new Rect();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int saveCount = canvas.getSaveCount();
        canvas.save();

        // Resolve the currently selected item from the live scroll position so the
        // bubble tracks the finger during drag/fling instead of only on release.
        int selectedItem = getSelectedItem();

        // Pop the highlight whenever the centred item changes.
        if (selectedItem != pillPopItem) {
            boolean firstDraw = pillPopItem == Integer.MIN_VALUE;
            pillPopItem = selectedItem;
            if (!firstDraw) {
                pillScale.setValue(0.92f);
                pillScaleSpring.animateToFinalPosition(1f);
            }
        }

        float itemWithPadding = itemWidth + dividerSize;

        ensureFadeFraction();

        // translate horizontal to center
        canvas.translate(itemWithPadding * sideItems, 0);

        if (values != null) {
            for (int i = 0; i < values.length; i++) {

                // set text color for item
                textPaint.setColor(getTextColor(i));
                textPaint.setFakeBoldText(true);

                // get text layout
                BoringLayout layout = layouts[i];

                int saveCountHeight = canvas.getSaveCount();
                canvas.save();

                float x = 0;

                float lineWidth = layout.getLineWidth(0);
                if (lineWidth > itemWidth) {
                    if (isRtl(values[i])) {
                        x += (lineWidth - itemWidth) / 2;
                    } else {
                        x -= (lineWidth - itemWidth) / 2;
                    }
                }

                if (marquee != null && i == selectedItem) {
                    x += marquee.getScroll();
                }

                // translate vertically to center
                canvas.translate(-x, (float) (getHeight() - layout.getHeight()) / 2);

                RectF clipBounds;
                if (x == 0) {
                    clipBounds = itemClipBounds;
                } else {
                    clipBounds = itemClipBoundsOffset;
                    clipBounds.set(itemClipBounds);
                    clipBounds.offset(x, 0);
                }
                if (i == selectedItem) {
                    canvas.getClipBounds(canvasClipBounds);
                    background.bottom = canvasClipBounds.bottom;
                    background.left = canvasClipBounds.left;
                    background.right = canvasClipBounds.right;
                    background.top = canvasClipBounds.top;

                    float width = itemClipBounds.width();
                    float margin = (width - getTextWidth(values[i], textPaint)) / 8;
                    background.left = itemClipBounds.left + margin;
                    background.right = itemClipBounds.right - margin;

                    paint.setColor(selectedBackgroundColor);
                    // Manual-bar-style squircle, scaled about its centre by the pop.
                    float scale = pillScale.getValue();
                    float halfW = background.width() * scale * 0.5f;
                    float halfH = background.height() * scale * 0.5f;
                    canvas.drawRoundRect(background.centerX() - halfW, background.centerY() - halfH,
                            background.centerX() + halfW, background.centerY() + halfH,
                            pillCornerRadius, pillCornerRadius, paint);
                }
                canvas.clipRect(clipBounds);
                // apply the view-fixed edge fade so text smoothly fades out at the edges.
                // the gradient carries the item's own color so the theme text color is preserved.
                if (fadeFraction > 0f) {
                    LinearGradient itemFade = createItemFadeShader(textPaint.getColor());
                    float originX = itemWithPadding * sideItems + i * itemWithPadding - getScrollX();
                    fadeMatrix.setTranslate(-originX, 0);
                    itemFade.setLocalMatrix(fadeMatrix);
                    textPaint.setShader(itemFade);
                }
                layout.draw(canvas);
                textPaint.setShader(null);

                if (marquee != null && i == selectedItem && marquee.shouldDrawGhost()) {
                    canvas.translate(marquee.getGhostOffset(), 0);
                    layout.draw(canvas);
                }

                // restore vertical translation
                canvas.restoreToCount(saveCountHeight);

                // translate horizontal for 1 item
                canvas.translate(itemWithPadding, 0);
            }
        }

        // restore horizontal translation
        canvas.restoreToCount(saveCount);

        drawEdgeEffect(canvas, leftEdgeEffect, 270);
        drawEdgeEffect(canvas, rightEdgeEffect, 90);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private float getTextWidth(CharSequence text, Paint paint) {
        Rect rect = new Rect();
        paint.getTextBounds(text, 0, text.length(), rect);
        return rect.width();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        textDir = getTextDirectionHeuristic();
    }

    private boolean isRtl(CharSequence text) {
        if (textDir == null) {
            textDir = getTextDirectionHeuristic();
        }

        return textDir.isRtl(text, 0, text.length());
    }

    private TextDirectionHeuristicCompat getTextDirectionHeuristic() {

        // Always need to resolve layout direction first
        final boolean defaultIsRtl = (getLayoutDirection() == LAYOUT_DIRECTION_RTL);

        switch (getTextDirection()) {
            default:
            case TEXT_DIRECTION_FIRST_STRONG:
                return (defaultIsRtl ? TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL :
                        TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR);
            case TEXT_DIRECTION_ANY_RTL:
                return TextDirectionHeuristicsCompat.ANYRTL_LTR;
            case TEXT_DIRECTION_LTR:
                return TextDirectionHeuristicsCompat.LTR;
            case TEXT_DIRECTION_RTL:
                return TextDirectionHeuristicsCompat.RTL;
            case TEXT_DIRECTION_LOCALE:
                return TextDirectionHeuristicsCompat.LOCALE;
        }
    }

    private void remakeLayout() {

        if (layouts != null && layouts.length > 0 && getWidth() > 0) {
            for (int i = 0; i < layouts.length; i++) {
                layouts[i].replaceOrMake(values[i], textPaint, itemWidth,
                        Layout.Alignment.ALIGN_CENTER, 1f, 1f, boringMetrics, false, ellipsize,
                        itemWidth);
            }
        }

    }

    private void drawEdgeEffect(Canvas canvas, EdgeEffect edgeEffect, int degrees) {

        if (canvas == null || edgeEffect == null || (degrees != 90 && degrees != 270)) {
            return;
        }

        if (!edgeEffect.isFinished()) {
            final int restoreCount = canvas.getSaveCount();
            final int width = getWidth();
            final int height = getHeight();

            canvas.rotate(degrees);

            if (degrees == 270) {
                canvas.translate(-height, Math.max(0, getScrollX()));
            } else { // 90
                canvas.translate(0, -(Math.max(getScrollRange(), getScaleX()) + width));
            }

            edgeEffect.setSize(height, width);
            if (edgeEffect.draw(canvas)) {
                postInvalidateOnAnimation();
            }
            canvas.restoreToCount(restoreCount);
        }

    }

    /**
     * Width (in item units) of the edge fade band on each side.
     */
    private static final float EDGE_FADE_ITEMS = 0.7f;

    /**
     * Fraction of the picker width that is interactive (touch zone). The faded
     * edges outside this central band are ignored for touch. Kept separate from
     * {@link #EDGE_FADE_ITEMS} so the fade size and touch area can be tuned
     * independently.
     */
    private static final float TOUCH_AREA_FRACTION = 0.7f;

    /**
     * How strongly touch movement is compressed around item centers and expanded
     * between them. The per-position gain is {@code 1 - amplitude} exactly at an
     * item center and {@code 1 + amplitude} halfway between items, so the average
     * gain over one full item period is exactly 1 and the overall travel distance
     * (items scrolled per distance of finger travel) is preserved.
     */
    private static final float TOUCH_WARP_AMPLITUDE = 0.25f;

    /**
     * Global scale applied to touch movement before the periodic warp. Values
     * greater than 1 make the picker scroll further for a given finger travel
     * (more items per swipe); values below 1 reduce travel. Combine with
     * {@link #TOUCH_WARP_AMPLITUDE} to tune feel.
     */
    private static final float TOUCH_TRAVEL_MULTIPLIER = 1.0f;

    /**
     * Recomputes the fraction of the picker width that fades out at each edge.
     */
    private void ensureFadeFraction() {
        int width = getWidth();
        if (width <= 0 || itemWidth <= 0) {
            fadeFraction = 0f;
            return;
        }
        float band = (itemWidth + dividerSize) * EDGE_FADE_ITEMS;
        fadeFraction = Math.min(0.45f, band / width);
    }

    /**
     * Builds a view-fixed horizontal gradient that fades the given item color out
     * towards the left/right edges of the picker, preserving its RGB. The alpha
     * drops off steeply (via intermediate stops) so the edges are clearly faded.
     */
    private LinearGradient createItemFadeShader(int color) {
        int width = getWidth();
        float f = fadeFraction;
        int transparent = color & 0x00FFFFFF;
        int dim = (color & 0x00FFFFFF) | 0x2E000000;
        return new LinearGradient(0, 0, width, 0,
                new int[]{transparent, dim, color, color, dim, transparent},
                new float[]{0f, f * 0.5f, f, 1f - f, 1f - f * 0.5f, 1f},
                Shader.TileMode.CLAMP);
    }

    /**
     * Returns true when a touch at the given x coordinate lands outside the
     * reduced interactive area (touch zone) in the center of the picker.
     */
    private boolean isTouchOutsideInteractiveArea(float x) {
        int width = getWidth();
        if (width <= 0 || TOUCH_AREA_FRACTION <= 0f || TOUCH_AREA_FRACTION >= 1f) {
            return false;
        }
        float inset = width * (1f - TOUCH_AREA_FRACTION) / 2f;
        return x < inset || x > width - inset;
    }

    /**
     * Maps a raw finger movement into a scroll delta using a periodic, non-uniform
     * gain. Movement is compressed while an item is centered and decompressed in
     * between, but the gain integrates to 1 over each full item period so the same
     * distance of finger travel still scrolls the same overall number of items.
     *
     * @param rawDelta Raw finger movement (pixels) since the last move event
     * @return Scroll delta (pixels) to apply
     */
    private int getWarpedScrollDelta(float rawDelta) {
        float period = itemWidth + dividerSize;
        if (period <= 0f || rawDelta == 0f) {
            return (int) rawDelta;
        }

        // Subdivide the movement so the position-dependent gain is integrated
        // accurately instead of being applied once against the start position.
        int steps = Math.max(1, (int) Math.ceil(Math.abs(rawDelta) / (period / 32f)));
        float step = rawDelta / steps;
        float total = 0f;
        float x = getScrollX();
        for (int i = 0; i < steps; i++) {
            float phase = x % period;
            if (phase < 0f) {
                phase += period;
            }
            float gain = 1f - TOUCH_WARP_AMPLITUDE
                    * (float) Math.cos(Math.PI * 2f * phase / period);
            float d = step * gain * TOUCH_TRAVEL_MULTIPLIER;
            total += d;
            x += d;
        }
        return Math.round(total);
    }

    /**
     * Calculates text color for specified item based on its position and state.
     *
     * @param item Index of item to get text color for
     * @return Item text color
     */
    private int getTextColor(int item) {

        int scrollX = getScrollX();

        // set color of text
        int color = textColor.getDefaultColor();
        int itemWithPadding = (int) (itemWidth + dividerSize);
        int center = (int) (itemWithPadding * item + getItemCenterBias());
        if (scrollX > center - itemWithPadding / 2 &&
                scrollX < center + itemWithPadding / 2) {
            int position = scrollX - (center - itemWithPadding / 2);
            color = getColor(position, item);
        } else if (item == pressedItem) {
            color = textColor.getColorForState(new int[]{android.R.attr.state_pressed}, color);
        }

        return color;

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        calculateItemSize(w, h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!isEnabled()) {
            return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:

                float currentMoveX = event.getX();

                int deltaMoveX = getWarpedScrollDelta(lastDownEventX - currentMoveX);

                if (scrollingX ||
                        (Math.abs(deltaMoveX) > touchSlop) && values != null && values.length > 0) {

                    if (!scrollingX) {
                        deltaMoveX = 0;
                        pressedItem = -1;
                        scrollingX = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        stopMarqueeIfNeeded();
                    }

                    final int range = getScrollRange();

                    if (overScrollBy(deltaMoveX, 0, getScrollX(), 0, range, 0,
                            overscrollDistance, 0, true)) {
                        mVelocityTracker.clear();
                    }

                    final float pulledToX = getScrollX() + deltaMoveX;
                    if (pulledToX < (int) getItemCenterBias()) {
                        if (leftEdgeEffect != null) {
                            leftEdgeEffect.onPull((float) deltaMoveX / getWidth());
                            if (!rightEdgeEffect.isFinished()) {
                                rightEdgeEffect.onRelease();
                            }
                        }
                    } else if (pulledToX > range) {
                        if (rightEdgeEffect != null) {
                            rightEdgeEffect.onPull((float) deltaMoveX / getWidth());
                            if (!leftEdgeEffect.isFinished()) {
                                leftEdgeEffect.onRelease();
                            }
                        }
                    }

                    lastDownEventX = currentMoveX;

                    // tick whenever a new item becomes selected while dragging
                    int currentItem = getSelectedItem();
                    if (currentItem != lastTickItem) {
                        lastTickItem = currentItem;
                        vibration.sliderTick();
                    }

                    invalidate();

                }

                break;
            case MotionEvent.ACTION_DOWN:

                // only handle touches in the central touch zone;
                // the edges should not respond to touch
                if (isTouchOutsideInteractiveArea(event.getX())) {
                    return false;
                }

                if (springScroll.isRunning()) {
                    // Cancel without running the end action, matching the
                    // scroller force-finish behavior below.
                    springScroll.cancel();
                } else if (!flingScrollerX.isFinished()) {
                    flingScrollerX.forceFinished(true);
                } else {
                    scrollingX = false;
                }

                lastDownEventX = event.getX();

                lastTickItem = getSelectedItem();

                if (!scrollingX) {
                    pressedItem = getPositionFromTouch(event.getX());
                }
                invalidate();

                break;
            case MotionEvent.ACTION_UP:

                VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
                int initialVelocityX = (int) velocityTracker.getXVelocity();

                if (scrollingX && Math.abs(initialVelocityX) > mMinimumFlingVelocity) {
                    flingX(initialVelocityX);
                } else if (values != null) {
                    float positionX = event.getX();
                    if (!scrollingX) {

                        int itemPos = getPositionOnScreen(positionX);
                        int relativePos = itemPos - getSelectedItem();

                        if (relativePos == 0) {
                            selectItem();
                        } else {
                            // tapped a mode directly: vibrate right away so the
                            // feedback is immediate instead of waiting for the
                            // scroll animation to settle
                            int tappedItem = getSelectedItem() + relativePos;
                            if (tappedItem >= 0 && tappedItem < values.length
                                    && tappedItem != lastTickItem) {
                                lastTickItem = tappedItem;
                                vibration.sliderTick();
                            }
                            smoothScrollBy(relativePos);
                        }

                    } else {
                        finishScrolling();
                    }
                }

                mVelocityTracker.recycle();
                mVelocityTracker = null;

                if (leftEdgeEffect != null) {
                    leftEdgeEffect.onRelease();
                    rightEdgeEffect.onRelease();
                }

            case MotionEvent.ACTION_CANCEL:
                pressedItem = -1;
                invalidate();

                if (leftEdgeEffect != null) {
                    leftEdgeEffect.onRelease();
                    rightEdgeEffect.onRelease();
                }

                break;
        }

        return true;
    }

    private void selectItem() {
        // post to the UI Thread to avoid potential interference with the OpenGL Thread
        if (onItemClicked != null) {

            post(() -> {
                vibration.modeChange();
                onItemClicked.onItemClicked(getSelectedItem());
            });
        }

        adjustToNearestItemX();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (!isEnabled()) {
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                selectItem();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                smoothScrollBy(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                smoothScrollBy(1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }

    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {

        if (touchHelper.dispatchHoverEvent(event)) {
            return true;
        }

        return super.dispatchHoverEvent(event);
    }

    @Override
    public void computeScroll() {
        computeScrollX();
    }

    @Override
    public void getFocusedRect(Rect r) {
        super.getFocusedRect(r); // TODO this should only be current item
    }

    public void setOnItemSelectedListener(OnItemSelected onItemSelected) {
        this.onItemSelected = onItemSelected;
    }

    public void setOnItemClickedListener(OnItemClicked onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    public int getSelectedItem() {
        int x = getScrollX();
        return getPositionFromCoordinates(x);
    }

    public void setSelectedItem(int index) {
        selectedItem = index;
        scrollToItem(index);
    }

    public int getMarqueeRepeatLimit() {
        return marqueeRepeatLimit;
    }

    public void setMarqueeRepeatLimit(int marqueeRepeatLimit) {
        this.marqueeRepeatLimit = marqueeRepeatLimit;
    }

    /**
     * @return Number of items on each side of current item.
     */
    public int getSideItems() {
        return sideItems;
    }

    public void setSideItems(int sideItems) {
        if (this.sideItems < 0) {
            throw new IllegalArgumentException("Number of items on each side must be grater or equal to 0.");
        } else if (this.sideItems != sideItems) {
            this.sideItems = sideItems;
            calculateItemSize(getWidth(), getHeight());
        }
    }


    public CharSequence[] getValues() {
        return values;
    }

    /**
     * Sets values to choose from
     *
     * @param values New values to choose from
     */
    public void setValues(CharSequence[] values) {

        if (this.values != values) {
            this.values = values;

            if (this.values != null) {
                layouts = new BoringLayout[this.values.length];
                for (int i = 0; i < layouts.length; i++) {
                    layouts[i] = new BoringLayout(this.values[i], textPaint, itemWidth, Layout.Alignment.ALIGN_CENTER,
                            1f, 1f, boringMetrics, false, ellipsize, itemWidth);
                }
            } else {
                layouts = new BoringLayout[0];
            }

            // start marque only if has already been measured
            if (getWidth() > 0) {
                startMarqueeIfNeeded();
            }

            requestLayout();
            invalidate();
        }

    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {

        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        setSelectedItem(ss.mSelItem);


    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState savedState = new SavedState(superState);
        savedState.mSelItem = selectedItem;

        return savedState;

    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if (overScrollMode != OVER_SCROLL_NEVER) {
            Context context = getContext();
            leftEdgeEffect = new EdgeEffect(context);
            rightEdgeEffect = new EdgeEffect(context);
        } else {
            leftEdgeEffect = null;
            rightEdgeEffect = null;
        }

        super.setOverScrollMode(overScrollMode);
    }

    public TextUtils.TruncateAt getEllipsize() {
        return ellipsize;
    }

    public void setEllipsize(TextUtils.TruncateAt ellipsize) {
        if (this.ellipsize != ellipsize) {
            this.ellipsize = ellipsize;

            remakeLayout();
            invalidate();
        }
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.scrollTo(scrollX, scrollY);

        if (!flingScrollerX.isFinished() && clampedX) {
            flingScrollerX.springBack(scrollX, scrollY,
                    (int) getItemCenterBias(), getScrollRange(), 0, 0);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged(); //TODO
    }

    private int getPositionFromTouch(float x) {
        float itemWithPadding = itemWidth + dividerSize;
        float content = x + getScrollX() - itemWithPadding * (sideItems + 0.5f);
        return Math.round(content / itemWithPadding);
    }

    private void computeScrollX() {
        OverScroller scroller = flingScrollerX;
        if (scroller.isFinished()) {
            return;
        }

        if (scroller.computeScrollOffset()) {

            int currentScrollerX = scroller.getCurrX();
            if (previousScrollerX == Integer.MIN_VALUE) {
                previousScrollerX = scroller.getStartX();
            }

            int range = getScrollRange();
            int minX = (int) getItemCenterBias();
            if (previousScrollerX >= minX && currentScrollerX < minX) {
                if (leftEdgeEffect != null)//Vibhor
                    leftEdgeEffect.onAbsorb((int) scroller.getCurrVelocity());
            } else if (previousScrollerX <= range && currentScrollerX > range) {
                if (rightEdgeEffect != null)//Vibhor
                    rightEdgeEffect.onAbsorb((int) scroller.getCurrVelocity());
            }

            overScrollBy(currentScrollerX - previousScrollerX, 0, previousScrollerX, getScrollY(),
                    getScrollRange(), 0, overscrollDistance, 0, false);
            previousScrollerX = currentScrollerX;

            if (scroller.isFinished()) {
                onScrollerFinishedX(scroller);
            }

            postInvalidate();
//            postInvalidateOnAnimation(); // TODO
        }
    }

    private void flingX(int velocityX) {

        previousScrollerX = Integer.MIN_VALUE;
        int minX = (int) getItemCenterBias();
        int maxX = minX + (int) (itemWidth + dividerSize) * (values.length - 1);
        flingScrollerX.fling(getScrollX(), getScrollY(), -velocityX, 0, minX, maxX,
                0, 0, getWidth() / 2, 0);

        invalidate();
    }

    private void adjustToNearestItemX() {

        int x = getScrollX();
        int item = getPositionFromCoordinates(x);

        if (item < 0) {
            item = 0;
        } else if (item > values.length - 1) {
            item = values.length - 1;
        }

        selectedItem = item;

        // tick when a new item settles after a scroll/fling/press
        if (item != lastTickItem) {
            lastTickItem = item;
            vibration.sliderTick();
        }

        int itemX = (int) ((itemWidth + dividerSize) * item + getItemCenterBias());

        int deltaX = itemX - x;

        if (deltaX == 0) {
            return;
        }
        // Spring the remaining snap with the playful overshoot. No end action:
        // finishScrolling() is already running on the caller's side.
        startSpringScroll(itemX, null);
    }

    /**
     * Starts the M3E spring snap toward {@code targetX}. Re-targeting cancels
     * the in-flight spring (without running its end action) and restarts from
     * the live scroll position.
     */
    private void startSpringScroll(int targetX, Runnable endAction) {
        springScroll.cancel();
        springScrollEndAction = endAction;
        springScrollX.setValue(getScrollX());
        springScroll.animateToFinalPosition(targetX);
    }

    /**
     * Applies one spring frame through {@code overScrollBy}, so clamping and the
     * {@code onOverScrolled} → {@code scrollTo} path stay identical to the
     * scroller-driven movement.
     */
    private void applySpringScroll(float value) {
        int target = Math.round(value);
        int current = getScrollX();
        int delta = target - current;
        if (delta == 0) {
            return;
        }
        overScrollBy(delta, 0, current, getScrollY(), getScrollRange(), 0,
                overscrollDistance, 0, false);
        postInvalidateOnAnimation();
    }

    /** Runs the snap's completion (the fling-equivalent finish) unless cancelled. */
    private void onSpringScrollEnd(boolean canceled) {
        Runnable action = springScrollEndAction;
        springScrollEndAction = null;
        if (!canceled && action != null) {
            action.run();
        }
    }

    private void calculateItemSize(int w, int h) {

        int items = sideItems * 2 + 1;
        int totalPadding = ((int) dividerSize * (items - 1));
        int sizeBasedWidth = (w - totalPadding) / items;

        itemWidth = Math.max(sizeBasedWidth, computeRequiredItemWidth());

        itemClipBounds = new RectF(0, 0, itemWidth, h);
        itemClipBoundsOffset = new RectF(itemClipBounds);

        scrollToItem(selectedItem);

        remakeLayout();
        startMarqueeIfNeeded();

    }

    /**
     * Computes the minimum item (bubble) width required to fully render the
     * widest value, so text is never truncated by ellipsis/clipping even at high
     * DPI. Measurement mirrors the drawing state (fake bold included) and adds
     * breathing room so the bubble stays slightly larger than the text.
     */
    private int computeRequiredItemWidth() {
        if (values == null || values.length == 0) {
            return 0;
        }
        textPaint.setFakeBoldText(true);
        float maxTextWidth = 0f;
        for (CharSequence value : values) {
            maxTextWidth = Math.max(maxTextWidth,
                    textPaint.measureText(value, 0, value.length()));
        }
        textPaint.setFakeBoldText(false);
        float density = getResources().getDisplayMetrics().density;
        return (int) Math.ceil(maxTextWidth + 12f * density);
    }

    /**
     * Scroll offset that must be added to {@code index * itemWithPadding} so the
     * bubble of item {@code index} sits exactly in the middle of the view. This is
     * zero only when the item grid exactly fills the view width
     * ({@code itemWidth * (2 * sideItems + 1) == width}); once items are sized to
     * fit their text and are wider than the per-slot width, the resting scroll
     * position needs this offset to keep the selected bubble centered.
     */
    private float getItemCenterBias() {
        int width = getWidth();
        if (width <= 0) {
            return 0f;
        }
        float itemWithPadding = itemWidth + dividerSize;
        return itemWithPadding * (sideItems + 0.5f) - width / 2f;
    }

    private void onScrollerFinishedX(OverScroller scroller) {
        if (scroller == flingScrollerX) {
            finishScrolling();
        }
    }

    private void finishScrolling() {

        adjustToNearestItemX();
        scrollingX = false;
        startMarqueeIfNeeded();
        // post to the UI Thread to avoid potential interference with the OpenGL Thread
        if (onItemSelected != null) {
            post(() -> onItemSelected.onItemSelected(getPositionFromCoordinates(getScrollX())));
        }
    }

    private void startMarqueeIfNeeded() {

        stopMarqueeIfNeeded();

        int item = getSelectedItem();

        if (layouts != null && layouts.length > item && item >= 0) {
            Layout layout = layouts[item];
            if (ellipsize == TextUtils.TruncateAt.MARQUEE
                    && itemWidth < layout.getLineWidth(0)) {
                marquee = new Marquee(this, layout, isRtl(values[item]));
                marquee.start(marqueeRepeatLimit);
            }
        }

    }

    private void stopMarqueeIfNeeded() {

        if (marquee != null) {
            marquee.stop();
            marquee = null;
        }

    }

    private int getPositionOnScreen(float x) {
        return getPositionFromTouch(x);
    }

    private void smoothScrollBy(int i) {
        int deltaMoveX = (itemWidth + (int) dividerSize) * i;
        deltaMoveX = getRelativeInBound(deltaMoveX);

        stopMarqueeIfNeeded();
        // M3E spring snap: glides to the neighbouring mode, overshoots a touch
        // and settles, then runs the same finish path a fling would.
        startSpringScroll(getScrollX() + deltaMoveX, this::finishScrolling);
    }

    /**
     * Calculates color for specific position on time picker
     *
     * @param scrollX position
     * @return Integer
     */
    private int getColor(int scrollX, int position) {
        int itemWithPadding = (int) (itemWidth + dividerSize);
        float proportion = Math.abs(((1f * scrollX % itemWithPadding) / 2) / (itemWithPadding / 2f));
        if (proportion > .5) {
            proportion = (proportion - .5f);
        } else {
            proportion = .5f - proportion;
        }
        proportion *= 2;

        int defaultColor;
        int selectedColor;

        if (pressedItem == position) {
            defaultColor = textColor.getColorForState(new int[]{android.R.attr.state_pressed}, textColor.getDefaultColor());
            selectedColor = selectedTextColor;
        } else if (position == getSelectedItem()) {
            defaultColor = textColor.getDefaultColor();
            selectedColor = selectedTextColor;
        } else {
            defaultColor = textColor.getDefaultColor();
            selectedColor = textColor.getColorForState(new int[]{android.R.attr.state_selected}, defaultColor);
        }
        return (Integer) new ArgbEvaluator().evaluate(proportion, selectedColor, defaultColor);
    }

    /**
     * Sets text size for items
     *
     * @param size New item text size in px.
     */
    private void setTextSize(float size) {
        if (size != textPaint.getTextSize()) {
            textPaint.setTextSize(size);

            requestLayout();
            invalidate();
        }
    }

    /**
     * Calculates item from x coordinate position.
     *
     * @param x Scroll position to calculate.
     * @return Selected item from scrolling position in {param x}
     */
    private int getPositionFromCoordinates(int x) {
        return Math.round((x - getItemCenterBias()) / (itemWidth + dividerSize));
    }

    /**
     * Scrolls to specified item.
     *
     * @param index Index of an item to scroll to
     */
    private void scrollToItem(int index) {
        springScroll.cancel();
        scrollTo((int) ((itemWidth + dividerSize) * index + getItemCenterBias()), 0);
        // invalidate() not needed because scrollTo() already invalidates the view
    }

    /**
     * Calculates relative horizontal scroll position to be within our scroll bounds.
     * {@link com.particlesdevs.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker#getInBoundsX(int)}
     *
     * @param x Relative scroll position to calculate
     * @return Current scroll position + {param x} if is within our scroll bounds, otherwise it
     * will return min/max scroll position.
     */
    private int getRelativeInBound(int x) {
        int scrollX = getScrollX();
        return getInBoundsX(scrollX + x) - scrollX;
    }

    /**
     * Calculates x scroll position that is still in range of view scroller
     *
     * @param x Scroll position to calculate.
     * @return {param x} if is within bounds of over scroller, otherwise it will return min/max
     * value of scroll position.
     */
    private int getInBoundsX(int x) {

        int minX = (int) getItemCenterBias();
        int maxX = minX + (int) ((itemWidth + dividerSize) * (values.length - 1));

        if (x < minX) {
            x = minX;
        } else if (x > maxX) {
            x = maxX;
        }
        return x;
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if (values != null && values.length != 0) {
            scrollRange = (int) getItemCenterBias()
                    + (int) ((itemWidth + dividerSize) * (values.length - 1));
        }
        return scrollRange;
    }

    public interface OnItemSelected {

        void onItemSelected(int index);

    }

    public interface OnItemClicked {

        void onItemClicked(int index);

    }

    private static final class Marquee extends Handler {
        // TODO: Add an option to configure this
        private static final float MARQUEE_DELTA_MAX = 0.07f;
        private static final int MARQUEE_DELAY = 1200;
        private static final int MARQUEE_RESTART_DELAY = 1200;
        private static final int MARQUEE_RESOLUTION = 1000 / 30;
        private static final int MARQUEE_PIXELS_PER_SECOND = 30;

        private static final byte MARQUEE_STOPPED = 0x0;
        private static final byte MARQUEE_STARTING = 0x1;
        private static final byte MARQUEE_RUNNING = 0x2;

        private static final int MESSAGE_START = 0x1;
        private static final int MESSAGE_TICK = 0x2;
        private static final int MESSAGE_RESTART = 0x3;

        private final WeakReference<HorizontalPicker> mView;
        private final WeakReference<Layout> mLayout;
        private final float mScrollUnit;
        private byte mStatus = MARQUEE_STOPPED;
        private float mMaxScroll;
        private float mMaxFadeScroll;
        private float mGhostStart;
        private float mGhostOffset;
        private float mFadeStop;
        private int mRepeatLimit;

        private float mScroll;

        private final boolean mRtl;

        Marquee(HorizontalPicker v, Layout l, boolean rtl) {
            final float density = v.getContext().getResources().getDisplayMetrics().density;
            float scrollUnit = (MARQUEE_PIXELS_PER_SECOND * density) / MARQUEE_RESOLUTION;
            if (rtl) {
                mScrollUnit = -scrollUnit;
            } else {
                mScrollUnit = scrollUnit;
            }

            mView = new WeakReference<>(v);
            mLayout = new WeakReference<>(l);
            mRtl = rtl;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_START:
                    mStatus = MARQUEE_RUNNING;
                    tick();
                    break;
                case MESSAGE_TICK:
                    tick();
                    break;
                case MESSAGE_RESTART:
                    if (mStatus == MARQUEE_RUNNING) {
                        if (mRepeatLimit >= 0) {
                            mRepeatLimit--;
                        }
                        start(mRepeatLimit);
                    }
                    break;
            }
        }

        void tick() {
            if (mStatus != MARQUEE_RUNNING) {
                return;
            }

            removeMessages(MESSAGE_TICK);

            final HorizontalPicker view = mView.get();
            final Layout layout = mLayout.get();
            if (view != null && layout != null && (view.isFocused() || view.isSelected())) {
                mScroll += mScrollUnit;
                if (Math.abs(mScroll) > mMaxScroll) {
                    mScroll = mMaxScroll;
                    if (mRtl) {
                        mScroll *= -1;
                    }
                    sendEmptyMessageDelayed(MESSAGE_RESTART, MARQUEE_RESTART_DELAY);
                } else {
                    sendEmptyMessageDelayed(MESSAGE_TICK, MARQUEE_RESOLUTION);
                }
                view.invalidate();
            }
        }

        void stop() {
            mStatus = MARQUEE_STOPPED;
            removeMessages(MESSAGE_START);
            removeMessages(MESSAGE_RESTART);
            removeMessages(MESSAGE_TICK);
            resetScroll();
        }

        private void resetScroll() {
            mScroll = 0.0f;
            final HorizontalPicker view = mView.get();
            if (view != null) view.invalidate();
        }

        void start(int repeatLimit) {
            if (repeatLimit == 0) {
                stop();
                return;
            }
            mRepeatLimit = repeatLimit;
            final HorizontalPicker view = mView.get();
            final Layout layout = mLayout.get();
            if (view != null && layout != null) {
                mStatus = MARQUEE_STARTING;
                mScroll = 0.0f;
                final int textWidth = view.itemWidth;
                final float lineWidth = layout.getLineWidth(0);
                final float gap = textWidth / 3.0f;
                mGhostStart = lineWidth - textWidth + gap;
                mMaxScroll = mGhostStart + textWidth;
                mGhostOffset = lineWidth + gap;
                mFadeStop = lineWidth + textWidth / 6.0f;
                mMaxFadeScroll = mGhostStart + lineWidth + lineWidth;

                if (mRtl) {
                    mGhostOffset *= -1;
                }

                view.invalidate();
                sendEmptyMessageDelayed(MESSAGE_START, MARQUEE_DELAY);
            }
        }

        float getGhostOffset() {
            return mGhostOffset;
        }

        float getScroll() {
            return mScroll;
        }

        float getMaxFadeScroll() {
            return mMaxFadeScroll;
        }

        boolean shouldDrawLeftFade() {
            return mScroll <= mFadeStop;
        }

        boolean shouldDrawGhost() {
            return mStatus == MARQUEE_RUNNING && Math.abs(mScroll) > mGhostStart;
        }

        boolean isRunning() {
            return mStatus == MARQUEE_RUNNING;
        }

        boolean isStopped() {
            return mStatus == MARQUEE_STOPPED;
        }
    }

    public static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        private int mSelItem;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mSelItem = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeInt(mSelItem);
        }

        @Override
        public @NotNull String toString() {
            return "HorizontalPicker.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " selItem=" + mSelItem
                    + "}";
        }
    }

    private static class PickerTouchHelper extends ExploreByTouchHelper {

        private final HorizontalPicker mPicker;

        public PickerTouchHelper(HorizontalPicker picker) {
            super(picker);
            mPicker = picker;
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {

            float itemWidth = mPicker.itemWidth + mPicker.dividerSize;
            float position = mPicker.getScrollX() + x - itemWidth * mPicker.sideItems;

            float item = position / itemWidth;

            if (item < 0 || item > mPicker.values.length) {
                return INVALID_ID;
            }

            return (int) item;

        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {

            float itemWidth = mPicker.itemWidth + mPicker.dividerSize;
            float position = mPicker.getScrollX() - itemWidth * mPicker.sideItems;

            int first = (int) (position / itemWidth);

            int items = mPicker.sideItems * 2 + 1;

            if (position % itemWidth != 0) { // if start next item is starting to appear on screen
                items++;
            }

            if (first < 0) {
                items += first;
                first = 0;
            } else if (first + items > mPicker.values.length) {
                items = mPicker.values.length - first;
            }

            for (int i = 0; i < items; i++) {
                virtualViewIds.add(first + i);
            }

        }

        @Override
        protected void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
            event.setContentDescription(mPicker.values[virtualViewId]);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NotNull AccessibilityNodeInfoCompat node) {

            float itemWidth = mPicker.itemWidth + mPicker.dividerSize;
            float scrollOffset = mPicker.getScrollX() - itemWidth * mPicker.sideItems;

            int left = (int) (virtualViewId * itemWidth - scrollOffset);
            int right = left + mPicker.itemWidth;
            if (mPicker.values.length > 0) {
                node.setContentDescription(mPicker.values[virtualViewId]);
                node.setBoundsInParent(new Rect(left, 0, right, mPicker.getHeight()));
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, Bundle arguments) {
            return false;
        }

    }

}
