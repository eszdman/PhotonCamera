package com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.view.MotionEvent;
import android.view.View;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The manual value wheel. Besides the ruler of the currently selected control
 * (drawn on the dome's arc, full size), it can also show the previously
 * selected control as a smaller ruler INSIDE it — same dome, same rotation
 * centre, scaled by {@link #INNER_WHEEL_SCALE} — so both controls stay
 * adjustable at once. Touches are routed to a wheel by their radial distance
 * from the shared centre.
 */
public class KnobView extends View {
    private static final String TAG = KnobView.class.getSimpleName();
    private static final boolean dolog = false;
    /** Radius of the remembered (inner) ruler relative to the current one's. */
    private static final float INNER_WHEEL_SCALE = 0.75f;
    private final Rect m_DashBounds;
    private final int m_DashLength;
    private final int m_DashPadding;
    private final Paint m_Paint;
    public Range range;
    private boolean m_DashAroundAutoEnabled;
    private int m_IconPadding;
    private double m_InitRadius;
    private boolean m_IsTouching;
    private float m_KnobItemsSelfRotation;
    private PointF m_RotationCenter;
    private RotationState m_RotationState;
    private final Wheel m_Primary = new Wheel();
    private Wheel m_Secondary;
    private Wheel m_TouchWheel;
    private float m_DomeProgress = 1f;

    /** State of one ruler: geometry, rotation, tick and the model listening to it. */
    private class Wheel {
        KnobInfo info;
        List<KnobItemInfo> items;
        KnobViewChangedListener listener;
        double currentDegree;
        double lastDegree;
        int tick;
        KnobItemInfo value;
    }

    public KnobView(Context context) {
        this(context, null);
    }

    public KnobView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.m_DashAroundAutoEnabled = true;
        this.m_DashBounds = new Rect();
        this.m_RotationCenter = new PointF();
        this.m_RotationState = RotationState.IDLE;
        this.m_Paint = new Paint();
        this.m_Paint.setStyle(Style.STROKE);
        this.m_Paint.setStrokeWidth(2.0f);
        this.m_Paint.setColor(-1);
        this.m_Paint.setAntiAlias(true);
        this.m_DashLength = context.getResources().getDimensionPixelSize(R.dimen.manual_knob_dash_length);
        this.m_DashPadding = context.getResources().getDimensionPixelSize(R.dimen.manual_knob_dash_padding);
        this.m_IconPadding = context.getResources().getDimensionPixelSize(R.dimen.manual_knob_icon_padding);
    }

    private void log(String msg) {
        if (dolog)
            Log.v(TAG, msg);
    }

    public void cancelTouchEvent() {
        onActionUp(null);
    }

    public void setRange(Range range) {
        this.range = range;
        invalidate();
    }

    /**
     * Inflation of the wheel dome the scrim background draws: the items are
     * squashed toward the bar's top edge so they ride the dome as it grows.
     */
    public void setDomeProgress(float progress) {
        float clamped = Math.min(1f, Math.max(0f, progress));
        if (m_DomeProgress != clamped) {
            m_DomeProgress = clamped;
            invalidate();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (this.m_RotationCenter == null || this.m_Primary.info == null || m_DomeProgress <= 0f) {
            return;
        }
        boolean squashed = m_DomeProgress < 1f;
        if (squashed) {
            canvas.save();
            canvas.translate(0f, getHeight());
            canvas.scale(1f, m_DomeProgress);
            canvas.translate(0f, -getHeight());
        }
        if (this.m_Secondary != null && this.m_Secondary.info != null) {
            canvas.save();
            canvas.scale(INNER_WHEEL_SCALE, INNER_WHEEL_SCALE, this.m_RotationCenter.x, this.m_RotationCenter.y);
            drawWheel(canvas, this.m_Secondary);
            canvas.restore();
        }
        drawWheel(canvas, this.m_Primary);
        if (squashed) {
            canvas.restore();
        }
    }

    private void drawWheel(Canvas canvas, Wheel wheel) {
        double drawRotation;
        if (wheel.items == null) {
            return;
        }
        double startAngle = Double.NaN;
        double endAngle = Double.NaN;
        for (int i = 0; i < wheel.items.size(); i++) {
            KnobItemInfo item = wheel.items.get(i);
            KnobItemInfo nextItem = i + 1 < wheel.items.size() ? wheel.items.get(i + 1) : null;
            drawRotation = (-wheel.currentDegree) + item.rotationCenter;
            canvas.rotate((float) drawRotation, this.m_RotationCenter.x, this.m_RotationCenter.y);
            canvas.rotate(-this.m_KnobItemsSelfRotation, item.drawable.getBounds().exactCenterX(), item.drawable.getBounds().exactCenterY());
            item.drawable.draw(canvas);
            canvas.rotate(this.m_KnobItemsSelfRotation, item.drawable.getBounds().exactCenterX(), item.drawable.getBounds().exactCenterY());
            canvas.rotate((float) (-drawRotation), this.m_RotationCenter.x, this.m_RotationCenter.y);
            if (!(this.m_DashBounds == null || nextItem == null || (!this.m_DashAroundAutoEnabled && (item.tick == 0 || nextItem.tick == 0)))) {
                if (item.rotationRight - item.rotationLeft > 0.001d) {
                    startAngle = (-wheel.currentDegree) + item.rotationRight + 2.0d;
                }
                if (nextItem.rotationRight - nextItem.rotationLeft > 0.001d) {
                    endAngle = ((-wheel.currentDegree) + nextItem.rotationLeft) - 2.0d;
                }
                if (!Double.isNaN(startAngle) && !Double.isNaN(endAngle)) {
                    for (double currentAngle = startAngle; currentAngle < endAngle; currentAngle += 1.0d) {
                        canvas.rotate((float) currentAngle, this.m_RotationCenter.x, this.m_RotationCenter.y);
                        canvas.drawLine((float) this.m_DashBounds.centerX(), (float) this.m_DashBounds.top, (float) this.m_DashBounds.centerX(), (float) this.m_DashBounds.bottom, m_Paint);
                        canvas.rotate((float) (-currentAngle), this.m_RotationCenter.x, this.m_RotationCenter.y);
                    }
                    startAngle = Double.NaN;
                    endAngle = Double.NaN;
                }
            }
        }
    }

    /**
     * Wires the ruler of the currently selected control (the outer, full-size
     * wheel) and snaps it to the model's current value.
     */
    public void setPrimaryWheel(KnobInfo info, List<KnobItemInfo> items, double value, KnobViewChangedListener listener) {
        this.m_Primary.info = info;
        this.m_Primary.listener = listener;
        setKnobItems(this.m_Primary, items);
        setTickByValue(this.m_Primary, value);
    }

    /**
     * Shows the remembered previous control as the smaller inner ruler,
     * snapped to its current value.
     */
    public void setSecondaryWheel(KnobInfo info, List<KnobItemInfo> items, double value, KnobViewChangedListener listener) {
        this.m_Secondary = new Wheel();
        this.m_Secondary.info = info;
        this.m_Secondary.listener = listener;
        setKnobItems(this.m_Secondary, items);
        setTickByValue(this.m_Secondary, value);
    }

    public void clearSecondaryWheel() {
        if (this.m_Secondary != null) {
            this.m_Secondary = null;
            invalidate();
        }
    }

    private double evaluateRotation(float x, float y) {
        //log("evaluateRotation");
        return Math.atan2(x - this.m_RotationCenter.x, -(y - this.m_RotationCenter.y));
    }

    private PointF evaluateRotationCenter() {
        log("evaluateRotationCenter");
        int width = getWidth();
        int height = getHeight();
        double fanEdge = Math.sqrt(Math.pow(((float) width) / 2.0f, 2.0d) + Math.pow(height, 2.0d));
        return new PointF(((float) width) / 2.0f, (float) ((fanEdge / 2.0d) / (((double) height) / fanEdge)));
    }

    public KnobItemInfo getCurrentKnobItem() {
        return this.m_Primary.value;
    }

    public int getTick() {
        return this.m_Primary.tick;
    }

    private KnobItemInfo getKnobItemFromTick(Wheel wheel, int tick) {
        if (wheel.items == null) {
            return null;
        }
        for (KnobItemInfo item : wheel.items) {
            if (item.tick == tick) {
                return item;
            }
        }
        return null;
    }

    private KnobItemInfo getKnobItemFromValue(Wheel wheel, double value) {
        if (wheel.items == null) {
            log("getKnobItemFromValue() - knobItems is null");
            return null;
        }
        for (KnobItemInfo item : wheel.items) {
            if (Math.abs(item.value - value) < 1.0E-4d) {
                return item;
            }
        }
        log("getKnobItemFromValue() - no match value. or no knobItems, size: " + wheel.items.size());
        return null;
    }

    public double getKnobValueFromTick(Wheel wheel, int tick) {
        if (wheel.items == null) {
            log("getKnobValueFromTick() - knobItems is null");
            return 0.0d;
        }
        for (KnobItemInfo item : wheel.items) {
            if (item.tick == tick) {
                return item.value;
            }
        }
        log("getKnobValueFromTick() - no match value. or no knobItems, size: " + wheel.items.size());
        return 0.0d;
    }

    private void setTick(Wheel wheel, int tick) {
        //log("setTick " + tick);
        if (wheel.tick != tick) {
            int oldTick = wheel.tick;
            wheel.tick = tick;
            onSelectedKnobItemChanged(wheel, getKnobItemFromTick(wheel, oldTick), getKnobItemFromTick(wheel, tick));
        }
    }

    private boolean isTooCloseToCenter(float x, float y) {
        return Math.sqrt(Math.pow(x - this.m_RotationCenter.x, 2.0d) + Math.pow(y - this.m_RotationCenter.y, 2.0d)) < 50.0d;
    }

    /**
     * Routes a touch to a wheel by radial distance from the shared rotation
     * centre: closer than the midpoint between the two rulers' ITEM bands
     * goes to the inner ruler, farther goes to the outer one. The items sit
     * well inside the arc radius (icon padding + half their height), so the
     * band midpoint — not the arc midpoint — keeps taps on the outer ruler's
     * lower half from reaching the inner wheel.
     */
    private Wheel wheelForTouch(float x, float y) {
        if (this.m_Secondary == null || this.m_Secondary.info == null) {
            return this.m_Primary;
        }
        double d = Math.sqrt(Math.pow(x - this.m_RotationCenter.x, 2.0d) + Math.pow(y - this.m_RotationCenter.y, 2.0d));
        double outerR = effectiveItemRadius(this.m_Primary);
        double innerR = INNER_WHEEL_SCALE * effectiveItemRadius(this.m_Secondary);
        double boundary = (outerR + innerR) / 2.0;
        return d < boundary ? this.m_Secondary : this.m_Primary;
    }

    /** Mean distance of a ruler's items from the rotation centre. */
    private double effectiveItemRadius(Wheel wheel) {
        if (wheel.items == null || wheel.items.isEmpty()) {
            return this.m_RotationCenter.y;
        }
        double sum = 0;
        for (KnobItemInfo item : wheel.items) {
            sum += this.m_RotationCenter.y - (this.m_IconPadding + item.drawable.getBounds().height() / 2.0);
        }
        return sum / wheel.items.size();
    }

    private int mapRotationToTick(double rotation, KnobInfo info) {
        if (info == null) {
            return 0;
        }
        double includedAngle = ((double) ((info.angleMax - info.angleMin) - info.autoAngle)) / ((double) (info.tickMax - info.tickMin));
        double preDiffAngle = Double.MAX_VALUE;
        for (int i = info.tickMin; i <= info.tickMax; i++) {
            double diff = Math.abs(((((double) ((float) i)) * includedAngle) + ((double) ((Integer.signum(i) * info.autoAngle) / 2))) - rotation);
            if (diff < preDiffAngle) {
                preDiffAngle = diff;
            } else if (diff >= preDiffAngle) {
                return validateTick(i - 1, info);
            }
        }
        return info.tickMax;
    }

    private double mapToKnobRotationDegree(double rotation) {
        return -Math.toDegrees(rotation);
    }

    private double mapTickToRotation(int tick, KnobInfo info) {
        if (info == null) {
            return 0.0d;
        }
        return validateRotation((((double) tick) * (((double) ((info.angleMax - info.angleMin) - info.autoAngle)) / ((double) (info.tickMax - info.tickMin)))) + ((double) ((Integer.signum(tick) * info.autoAngle) / 2)), info);
    }

    private void onActionDown(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (isTooCloseToCenter(x, y)) {
            log("onActionDown() - Too close to center");
            return;
        }
        this.m_TouchWheel = wheelForTouch(x, y);
        this.m_InitRadius = evaluateRotation(x, y);
        this.m_IsTouching = true;
        onRotationStartFromTouch(this.m_TouchWheel);
    }

    private void onActionMove(MotionEvent event) {
        if (this.m_IsTouching) {
            float x = event.getX();
            float y = event.getY();
            if (isTooCloseToCenter(x, y)) {
                log("onActionMove() - Too close to center, stop running");
                this.m_IsTouching = false;
                onRotationEndFromTouch(this.m_TouchWheel);
                return;
            }
            onRotationUpdateFromTouch(this.m_TouchWheel, evaluateRotation(x, y) - this.m_InitRadius);
        }
    }

    private void onActionUp(MotionEvent event) {
        if (this.m_IsTouching) {
            this.m_IsTouching = false;
            onRotationEndFromTouch(this.m_TouchWheel);
        }
    }

    @Override
    public void onCancelPendingInputEvents() {
        super.onCancelPendingInputEvents();
        onActionUp(null);
    }

    public void onRotationEndFromTouch(Wheel wheel) {
        setRotationState(RotationState.STOPPING, wheel);
        wheel.lastDegree = wheel.currentDegree;
        setTick(wheel, mapRotationToTick(wheel.currentDegree, wheel.info));
        setKnobViewRotation(wheel, mapTickToRotation(wheel.tick, wheel.info));
        if (getKnobItemFromTick(wheel, wheel.tick) != null) {
            getKnobItemFromTick(wheel, wheel.tick).drawable.setState(SELECTED_STATE_SET);
        }
        setRotationState(RotationState.IDLE, wheel);
    }

    public void onRotationStartFromTouch(Wheel wheel) {
        setRotationState(RotationState.STARTING, wheel);
        wheel.lastDegree = wheel.currentDegree;
    }

    public void onRotationUpdateFromTouch(Wheel wheel, double radiusDiff) {
        if (wheel.info != null) {
            setRotationState(RotationState.ROTATING, wheel);
            wheel.currentDegree = wheel.lastDegree + mapToKnobRotationDegree(radiusDiff);
            if (wheel.currentDegree >= 360.0d) {
                wheel.currentDegree -= 360.0d;
            } else if (wheel.currentDegree <= -360.0d) {
                wheel.currentDegree += 360.0d;
            }
            wheel.currentDegree = validateRotation(wheel.currentDegree, wheel.info);
            setTick(wheel, mapRotationToTick(wheel.currentDegree, wheel.info));
            //log("invalidate onRotationUpdateFromTouch");
            invalidate();
        }
    }

    private void onSelectedKnobItemChanged(Wheel wheel, KnobItemInfo oldItem, KnobItemInfo newItem) {
        if (newItem != null && oldItem != newItem) {
            wheel.value = newItem;
            if (wheel.listener != null) {
                wheel.listener.onSelectedKnobItemChanged(this, oldItem, newItem);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        long startTime = System.nanoTime();
        log("insSizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
        this.m_RotationCenter = evaluateRotationCenter();
        updateDashBounds();
        updateKnobItemsBounds(this.m_Primary);
        if (this.m_Secondary != null) {
            updateKnobItemsBounds(this.m_Secondary);
        }
        log("onSizeChangedTime:" + (System.nanoTime() - startTime) + "ns");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                onActionDown(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onActionUp(event);
                return false;
            case MotionEvent.ACTION_MOVE:
                onActionMove(event);
                return true;
        }
        return true;
    }

    public void setDashAroundAutoEnabled(boolean enable) {
        this.m_DashAroundAutoEnabled = enable;
    }

    public void setIconPadding(int padding) {
        this.m_IconPadding = padding;
        updateKnobItemsBounds(this.m_Primary);
        if (this.m_Secondary != null) {
            updateKnobItemsBounds(this.m_Secondary);
        }
        invalidate();
    }

    public void setKnobItemsRotation(Rotation rotation) {
        float oldSelfRotation = this.m_KnobItemsSelfRotation;
        switch (rotation) {
            case LANDSCAPE:
                this.m_KnobItemsSelfRotation = 270.0f;
                break;
            case PORTRAIT:
                this.m_KnobItemsSelfRotation = 0.0f;
                break;
            case INVERSE_LANDSCAPE:
                this.m_KnobItemsSelfRotation = 90.0f;
                break;
            case INVERSE_PORTRAIT:
                this.m_KnobItemsSelfRotation = 180.0f;
                break;
        }
        if (oldSelfRotation != this.m_KnobItemsSelfRotation) {
            updateKnobItemsBounds(this.m_Primary);
            if (this.m_Secondary != null) {
                updateKnobItemsBounds(this.m_Secondary);
            }
            log("invalidate setKnobItemsRotation");
            invalidate();
        }
    }

    private void setKnobViewRotation(Wheel wheel, double rotation) {
        wheel.currentDegree = rotation;
        wheel.lastDegree = rotation;
        //log("invalidate setKnobViewRotation");
        invalidate();
    }

    private void setKnobViewRotationSmooth(Wheel wheel, double rotation) {
        ValueAnimator animation = ValueAnimator.ofFloat((float) wheel.currentDegree, (float) rotation);
        animation.setDuration(Motion.durationShort2(getContext()));
        animation.setInterpolator(Motion.emphasized(getContext()));
        animation.addUpdateListener(animation1 -> KnobView.this.setKnobViewRotation(wheel, (double) (Float) animation1.getAnimatedValue()));
        animation.start();
    }

    private void setRotationState(RotationState state, Wheel wheel) {
        if (this.m_RotationState != state) {
            this.m_RotationState = state;
            if (wheel != null && wheel.listener != null) {
                wheel.listener.onRotationStateChanged(this, state);
            }
        }
    }

    public void resetKnob() {
        setTickByValue(this.m_Primary, getKnobValueFromTick(this.m_Primary, 0));
    }

    public void setTickByValue(Wheel wheel, double value) {
        KnobItemInfo item = getKnobItemFromValue(wheel, value);
        if (item != null) {
            setTick(wheel, item.tick);
            setKnobViewRotationSmooth(wheel, mapTickToRotation(item.tick, wheel.info));
            return;
        }
        log("setTickByValue() - item is null, " + this);
    }

    private void updateDashBounds() {
        log("updateDashBounds");
        this.m_DashBounds.set((getWidth() / 2) - 1, this.m_DashPadding, (getWidth() / 2) + 1, this.m_DashPadding + this.m_DashLength);
    }

    private void setKnobItems(Wheel wheel, List<KnobItemInfo> items) {
        log("setKnobItems " + items.size());
        wheel.items = items;
        updateKnobItemsBounds(wheel);
        updateKnobItemSelection(wheel);
        log("invalidate setKnobItems");
        invalidate();
    }

    private void updateKnobItemsBounds(Wheel wheel) {
        log("updateKnobItemsBounds");
        if (wheel.items != null) {
            for (KnobItemInfo item : wheel.items) {
                int left = (getWidth() / 2) - (item.drawable.getIntrinsicWidth() / 2);
                int top = this.m_IconPadding;
                if (this.m_KnobItemsSelfRotation % 180.0f != 0.0f) {
                    top = (this.m_IconPadding + (item.drawable.getIntrinsicWidth() / 2)) - (item.drawable.getIntrinsicHeight() / 2);
                }
                item.drawable.setBounds(left, top, left + item.drawable.getIntrinsicWidth(), top + item.drawable.getIntrinsicHeight());
                if (wheel.info != null) {
                    double includedAngle = ((double) ((wheel.info.angleMax - wheel.info.angleMin) - wheel.info.autoAngle)) / ((double) (wheel.info.tickMax - wheel.info.tickMin));
                    double radius = this.m_RotationCenter.y;
                    double edgeY = item.drawable.getIntrinsicWidth() / 2.0;
                    double edgeX = (radius - ((double) this.m_IconPadding)) - ((double) (item.drawable.getIntrinsicHeight() / 2));
                    if (this.m_KnobItemsSelfRotation % 180.0f != 0.0f) {
                        edgeY = item.drawable.getIntrinsicHeight() / 2.0;
                        edgeX = (radius - ((double) this.m_IconPadding)) - ((double) (item.drawable.getIntrinsicWidth() / 2));
                    }
                    double drawableAngleHalf = Math.toDegrees(Math.atan(edgeY / edgeX));
                    item.rotationCenter = (((double) item.tick) * includedAngle) + ((double) ((Integer.signum(item.tick) * wheel.info.autoAngle) / 2));
                    item.rotationLeft = item.rotationCenter - drawableAngleHalf;
                    item.rotationRight = item.rotationCenter + drawableAngleHalf;
                } else {
                    return;
                }
            }
            Collections.sort(wheel.items);
        }
    }

    private void updateKnobItemSelection(Wheel wheel) {
        if (wheel.items != null) {
            for (KnobItemInfo item : wheel.items) {
                if (item.tick == wheel.tick) {
                    item.isSelected = true;
                    wheel.value = item;
                } else {
                    item.isSelected = false;
                }
            }
        }
    }

    private double validateRotation(double rotation, KnobInfo info) {
        if (info == null) {
            return rotation;
        }
        if (rotation > ((double) info.angleMax)) {
            rotation = info.angleMax;
        } else if (rotation < ((double) info.angleMin)) {
            rotation = info.angleMin;
        }
        return rotation;
    }

    private int validateTick(int tick, KnobInfo info) {
        //log("validateTick " + tick);
        if (info == null) {
            return tick;
        }
        if (tick > info.tickMax) {
            tick = info.tickMax;
        } else if (tick < info.tickMin) {
            tick = info.tickMin;
        }
        return tick;
    }

    public enum RotationState {
        IDLE,
        STARTING,
        ROTATING,
        STOPPING
    }
}
