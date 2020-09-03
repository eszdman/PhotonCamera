package com.manual;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.*;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.view.MotionEvent;
import android.view.View;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KnobView extends View {
    public static final double EPSILON = 1.0E-4d;
    private static final String TAG = KnobView.class.getSimpleName();
    public Range range;
    protected double defaultValue = -1.0d;
    private final Paint m_BackgroundPaint;
    private boolean m_DashAroundAutoEnabled;
    private final Rect m_DashBounds;
    private final int m_DashLength;
    private final int m_DashPadding;
    private double m_DrawableCurrentDegree;
    private double m_DrawableLastDegree;
    private int m_IconPadding;
    private double m_InitRadius;
    private boolean m_IsSetupIcons;
    private boolean m_IsTouching;
    private KnobInfo m_KnobInfo;
    private List<KnobItemInfo> m_KnobItems;
    private float m_KnobItemsSelfRotation;
    private KnobViewChangedListener m_KnobViewChangedListener;
    private final Paint m_Paint;
    private PointF m_RotationCenter;
    private RotationState m_RotationState;
    private int m_Tick;
    private KnobItemInfo m_Value;
    private final boolean dolog = true;

    public KnobView(Context context) {
        this(context, null);
    }

    public KnobView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.m_DashAroundAutoEnabled = true;
        this.m_DashBounds = new Rect();
        this.m_KnobItems = new ArrayList();
        this.m_RotationCenter = new PointF();
        this.m_RotationState = RotationState.IDLE;
        this.m_Tick = 0;
        this.m_BackgroundPaint = new Paint();
        this.m_BackgroundPaint.setStyle(Style.FILL);
        this.m_BackgroundPaint.setColor(Color.argb(64, 0, 0, 0));
        this.m_BackgroundPaint.setAntiAlias(true);
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

    @Override
    public void draw(Canvas canvas) {
        long startTime = System.nanoTime();
        super.draw(canvas);
        if (this.m_RotationCenter != null && this.m_KnobInfo != null) {
            canvas.drawCircle(this.m_RotationCenter.x, this.m_RotationCenter.y, this.m_RotationCenter.y, this.m_BackgroundPaint);
            //canvas.save();
            double drawRotation;
            if (this.m_KnobItems != null) {
                double startAngle = Double.NaN;
                double endAngle = Double.NaN;
                for (int i = 0; i < this.m_KnobItems.size(); i++) {
                    KnobItemInfo item = this.m_KnobItems.get(i);
                    KnobItemInfo nextItem = i + 1 < this.m_KnobItems.size() ? this.m_KnobItems.get(i + 1) : null;
                    drawRotation = (-this.m_DrawableCurrentDegree) + item.rotationCenter;
                    canvas.rotate((float) drawRotation, this.m_RotationCenter.x, this.m_RotationCenter.y);
                    canvas.rotate(-this.m_KnobItemsSelfRotation, item.drawable.getBounds().exactCenterX(), item.drawable.getBounds().exactCenterY());
                    item.drawable.draw(canvas);
                    canvas.rotate(this.m_KnobItemsSelfRotation, item.drawable.getBounds().exactCenterX(), item.drawable.getBounds().exactCenterY());
                    canvas.rotate((float) (-drawRotation), this.m_RotationCenter.x, this.m_RotationCenter.y);
                    if (!(this.m_DashBounds == null || nextItem == null || (!this.m_DashAroundAutoEnabled && (item.tick == 0 || nextItem.tick == 0)))) {
                        if (item.rotationRight - item.rotationLeft > 0.001d) {
                            startAngle = (-this.m_DrawableCurrentDegree) + item.rotationRight + 2.0d;
                        }
                        if (nextItem.rotationRight - nextItem.rotationLeft > 0.001d) {
                            endAngle = ((-this.m_DrawableCurrentDegree) + nextItem.rotationLeft) - 2.0d;
                        }
                        if (!Double.isNaN(startAngle) && !Double.isNaN(endAngle)) {
                            for (double currentAngle = startAngle; currentAngle < endAngle; currentAngle += 1.0d) {
                                canvas.rotate((float) currentAngle, this.m_RotationCenter.x, this.m_RotationCenter.y);
                                canvas.drawLine((float) this.m_DashBounds.centerX(), (float) this.m_DashBounds.top, (float) this.m_DashBounds.centerX(), (float) this.m_DashBounds.bottom, this.m_Paint);
                                canvas.rotate((float) (-currentAngle), this.m_RotationCenter.x, this.m_RotationCenter.y);
                            }
                            startAngle = Double.NaN;
                            endAngle = Double.NaN;
                        }
                    }
                }
            }
            //canvas.restore();
        }
        log("drawTime:" + (System.nanoTime() - startTime) + "ns");
    }

    private double evaluateRotation(float x, float y) {
        log("evaluateRotation");
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
        return this.m_Value;
    }

    private KnobItemInfo getKnobItemFromTick(int tick) {
        if (this.m_KnobItems == null) {
            return null;
        }
        for (KnobItemInfo item : this.m_KnobItems) {
            if (item.tick == tick) {
                return item;
            }
        }
        return null;
    }

    private KnobItemInfo getKnobItemFromValue(double value) {
        if (this.m_KnobItems == null) {
            log("getKnobItemFromValue() - knobItems is null");
            return null;
        }
        for (KnobItemInfo item : this.m_KnobItems) {
            if (Math.abs(item.value - value) < 1.0E-4d) {
                return item;
            }
        }
        log("getKnobItemFromValue() - no match value. or no knobItems, size: " + this.m_KnobItems.size());
        return null;
    }

    public double getKnobValueFromTick(int tick) {
        if (this.m_KnobItems == null) {
            log("getKnobValueFromTick() - knobItems is null");
            return 0.0d;
        }
        for (KnobItemInfo item : this.m_KnobItems) {
            if (item.tick == tick) {
                return item.value;
            }
        }
        log("getKnobValueFromTick() - no match value. or no knobItems, size: " + this.m_KnobItems.size());
        return 0.0d;
    }

    public double getKnobValueFromText(String text) {
        if (this.m_KnobItems == null) {
            log("getKnobValueFromText() - knobItems is null");
            return 0.0d;
        }
        for (KnobItemInfo item : this.m_KnobItems) {
            if (item.text.equalsIgnoreCase(text)) {
                return item.value;
            }
        }
        log("getKnobValueFromText() - no match value. or no knobItems, size: " + this.m_KnobItems.size());
        return 0.0d;
    }

    public int getTick() {
        return this.m_Tick;
    }

    private void setTick(int tick) {
        log("setTick " + tick);
        if (this.m_Tick != tick) {
            int oldTick = this.m_Tick;
            this.m_Tick = tick;
            onSelectedKnobItemChanged(getKnobItemFromTick(oldTick), getKnobItemFromTick(tick));
        }
    }

    private boolean isTooCloseToCenter(float x, float y) {
        return Math.sqrt(Math.pow(x - this.m_RotationCenter.x, 2.0d) + Math.pow(y - this.m_RotationCenter.y, 2.0d)) < 50.0d;
    }

    private int mapRotationToTick(double rotation) {
        if (this.m_KnobInfo == null) {
            return 0;
        }
        double includedAngle = ((double) ((this.m_KnobInfo.angleMax - this.m_KnobInfo.angleMin) - this.m_KnobInfo.autoAngle)) / ((double) (this.m_KnobInfo.tickMax - this.m_KnobInfo.tickMin));
        double preDiffAngle = Double.MAX_VALUE;
        for (int i = this.m_KnobInfo.tickMin; i <= this.m_KnobInfo.tickMax; i++) {
            double diff = Math.abs(((((double) ((float) i)) * includedAngle) + ((double) ((Integer.signum(i) * this.m_KnobInfo.autoAngle) / 2))) - rotation);
            if (diff < preDiffAngle) {
                preDiffAngle = diff;
            } else if (diff >= preDiffAngle) {
                return validateTick(i - 1);
            }
        }
        return this.m_KnobInfo.tickMax;
    }

    private double mapTickToValue(int tick) {
        if (this.m_KnobItems == null) {
            return 0.0d;
        }
        for (KnobItemInfo item : this.m_KnobItems) {
            if (item.tick == tick) {
                return item.value;
            }
        }
        return 0.0d;
    }

    private double mapToKnobRotationDegree(double rotation) {
        return -Math.toDegrees(rotation);
    }

    private double mapTickToRotation(int tick) {
        if (this.m_KnobInfo == null) {
            return 0.0d;
        }
        return validateRotation((((double) tick) * (((double) ((this.m_KnobInfo.angleMax - this.m_KnobInfo.angleMin) - this.m_KnobInfo.autoAngle)) / ((double) (this.m_KnobInfo.tickMax - this.m_KnobInfo.tickMin)))) + ((double) ((Integer.signum(tick) * this.m_KnobInfo.autoAngle) / 2)));
    }

    private void onActionDown(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (isTooCloseToCenter(x, y)) {
            log("onActionDown() - Too close to center");
            return;
        }
        this.m_InitRadius = evaluateRotation(x, y);
        this.m_IsTouching = true;
        onRotationStartFromTouch();
    }

    private void onActionMove(MotionEvent event) {
        if (this.m_IsTouching) {
            float x = event.getX();
            float y = event.getY();
            if (isTooCloseToCenter(x, y)) {
                log("onActionMove() - Too close to center, stop running");
                this.m_IsTouching = false;
                onRotationEndFromTouch();
                return;
            }
            onRotationUpdateFromTouch(evaluateRotation(x, y) - this.m_InitRadius);
        }
    }

    private void onActionUp(MotionEvent event) {
        if (this.m_IsTouching) {
            this.m_IsTouching = false;
            onRotationEndFromTouch();
            //updateText();
        }
    }

    @Override
    public void onCancelPendingInputEvents() {
        super.onCancelPendingInputEvents();
        onActionUp(null);
    }

    public void onRotationEndFromTouch() {
        setRotationState(RotationState.STOPPING);
        this.m_DrawableLastDegree = this.m_DrawableCurrentDegree;
        setTick(mapRotationToTick(this.m_DrawableCurrentDegree));
        setKnobViewRotation(mapTickToRotation(this.m_Tick));
        if (getKnobItemFromTick(this.m_Tick) != null) {
            getKnobItemFromTick(this.m_Tick).drawable.setState(SELECTED_STATE_SET);
        }
        setRotationState(RotationState.IDLE);
    }

    public void onRotationStartFromTouch() {
        setRotationState(RotationState.STARTING);
        this.m_DrawableCurrentDegree = this.m_DrawableLastDegree;
       /* if (getKnobItemFromTick(this.m_Tick) != null) {
            getKnobItemFromTick(this.m_Tick).drawable.setState(new int[]{-16842913});
        }*/
    }

    public void onRotationUpdateFromTouch(double radiusDiff) {
        if (this.m_KnobInfo != null) {
            setRotationState(RotationState.ROTATING);
            this.m_DrawableCurrentDegree = this.m_DrawableLastDegree + mapToKnobRotationDegree(radiusDiff);
            if (this.m_DrawableCurrentDegree >= 360.0d) {
                this.m_DrawableCurrentDegree -= 360.0d;
            } else if (this.m_DrawableCurrentDegree <= -360.0d) {
                this.m_DrawableCurrentDegree += 360.0d;
            }
            this.m_DrawableCurrentDegree = validateRotation(this.m_DrawableCurrentDegree);
            setTick(mapRotationToTick(this.m_DrawableCurrentDegree));
            log("invalidate onRotationUpdateFromTouch");
            invalidate();
        }
    }

    private void onSelectedKnobItemChanged(KnobItemInfo oldItem, KnobItemInfo newItem) {
        if (newItem != null && oldItem != newItem) {
            this.m_Value = newItem;
            if (this.m_KnobViewChangedListener != null) {
                this.m_KnobViewChangedListener.onSelectedKnobItemChanged(this, oldItem, newItem);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        log("insSizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
        this.m_RotationCenter = evaluateRotationCenter();
        updateDashBounds();
        updateKnobItemsBounds();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        /*if (!isEnabled() || getVisibility() != 0) {
            if (this.m_IsTouching) {
                onActionUp(event);
            }
        } else if (event.getPointerCount() > 1) {
            onActionUp(event);
        } else {*/
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
        //super.onTouchEvent(event);
        //}
        return true;
    }

    public void setDashAroundAutoEnabled(boolean enable) {
        this.m_DashAroundAutoEnabled = enable;
    }

    public void setIconPadding(int padding) {
        this.m_IconPadding = padding;
        updateKnobItemsBounds();
        invalidate();
    }

    public void setKnobInfo(KnobInfo info) {
        this.m_KnobInfo = info;
        updateKnobItemsBounds();
        log("invalidate setKnobInfo");
        invalidate();
    }

    public void setKnobItems(List<KnobItemInfo> items) {
        log("setKnobItems " + items.size());
        this.m_KnobItems = items;
        updateKnobItemsBounds();
        updateKnobItemSelection();
        /*KnobItemInfo info = getKnobItemFromTick(this.m_Tick);
        if (info != null && info.drawable != null)
            info.drawable.setState(SELECTED_STATE_SET);*/
        log("invalidate setKnobItems");
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
            updateKnobItemsBounds();
            log("invalidate setKnobItemsRotation");
            invalidate();
        }
    }

    public void setKnobViewBackgroundColor(int color) {
        this.m_BackgroundPaint.setColor(color);
        invalidate();
    }

    public void setKnobViewChangedListener(KnobViewChangedListener listener) {
        this.m_KnobViewChangedListener = listener;
    }


    private void setKnobViewRotation(double rotation) {
        this.m_DrawableCurrentDegree = rotation;
        this.m_DrawableLastDegree = rotation;
        log("invalidate setKnobViewRotation");
        invalidate();
    }

    private void setKnobViewRotationSmooth(double rotation) {
        ValueAnimator animation = ValueAnimator.ofFloat((float) this.m_DrawableCurrentDegree, (float) rotation);
        animation.setDuration(100);
        animation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                KnobView.this.setKnobViewRotation((double) (Float) animation.getAnimatedValue());
            }
        });
        animation.start();
    }

    private void setRotationState(RotationState state) {
        if (this.m_RotationState != state) {
            this.m_RotationState = state;
            if (this.m_KnobViewChangedListener != null) {
                this.m_KnobViewChangedListener.onRotationStateChanged(this, state);
            }
        }
    }

    public void resetKnob() {
        setTickByValue(getKnobValueFromTick(0));
    }

    public void setTickByValue(double value) {
        KnobItemInfo item = getKnobItemFromValue(value);
        if (item != null) {
            setTick(item.tick);
            setKnobViewRotationSmooth(mapTickToRotation(item.tick));
            return;
        }
        log("setTickByValue() - item is null, " + this);
    }


    public void setValueByTick(int tick) {
        setTick(tick);
        setKnobViewRotationSmooth(mapTickToRotation(tick));
    }

    private void updateDashBounds() {
        log("updateDashBounds");
        this.m_DashBounds.set((getWidth() / 2) - 1, this.m_DashPadding, (getWidth() / 2) + 1, this.m_DashPadding + this.m_DashLength);
    }

    private void updateKnobItemsBounds() {
        log("updateKnobItemsBounds");
        if (this.m_KnobItems != null) {
            for (KnobItemInfo item : this.m_KnobItems) {
                int left = (getWidth() / 2) - (item.drawable.getIntrinsicWidth() / 2);
                int top = this.m_IconPadding;
                if (this.m_KnobItemsSelfRotation % 180.0f != 0.0f) {
                    top = (this.m_IconPadding + (item.drawable.getIntrinsicWidth() / 2)) - (item.drawable.getIntrinsicHeight() / 2);
                }
                item.drawable.setBounds(left, top, left + item.drawable.getIntrinsicWidth(), top + item.drawable.getIntrinsicHeight());
                if (this.m_KnobInfo != null) {
                    double includedAngle = ((double) ((this.m_KnobInfo.angleMax - this.m_KnobInfo.angleMin) - this.m_KnobInfo.autoAngle)) / ((double) (this.m_KnobInfo.tickMax - this.m_KnobInfo.tickMin));
                    double radius = this.m_RotationCenter.y;
                    double edgeY = item.drawable.getIntrinsicWidth() / 2;
                    double edgeX = (radius - ((double) this.m_IconPadding)) - ((double) (item.drawable.getIntrinsicHeight() / 2));
                    if (this.m_KnobItemsSelfRotation % 180.0f != 0.0f) {
                        edgeY = item.drawable.getIntrinsicHeight() / 2;
                        edgeX = (radius - ((double) this.m_IconPadding)) - ((double) (item.drawable.getIntrinsicWidth() / 2));
                    }
                    double drawableAngleHalf = Math.toDegrees(Math.atan(edgeY / edgeX));
                    item.rotationCenter = (((double) item.tick) * includedAngle) + ((double) ((Integer.signum(item.tick) * this.m_KnobInfo.autoAngle) / 2));
                    item.rotationLeft = item.rotationCenter - drawableAngleHalf;
                    item.rotationRight = item.rotationCenter + drawableAngleHalf;
                } else {
                    return;
                }
            }
            Collections.sort(this.m_KnobItems);
        }
    }

    private void updateKnobItemSelection() {
        if (this.m_KnobItems != null) {
            for (KnobItemInfo item : this.m_KnobItems) {
                if (item.tick == this.m_Tick) {
                    item.isSelected = true;
                    this.m_Value = item;
                } else {
                    item.isSelected = false;
                }
            }
        }
    }

    private double validateRotation(double rotation) {
        if (this.m_KnobInfo == null) {
            return rotation;
        }
        if (rotation > ((double) this.m_KnobInfo.angleMax)) {
            rotation = this.m_KnobInfo.angleMax;
        } else if (rotation < ((double) this.m_KnobInfo.angleMin)) {
            rotation = this.m_KnobInfo.angleMin;
        }
        return rotation;
    }

    private int validateTick(int tick) {
        log("validateTick " + tick);
        if (this.m_KnobInfo == null) {
            return tick;
        }
        if (tick > this.m_KnobInfo.tickMax) {
            tick = this.m_KnobInfo.tickMax;
        } else if (tick < this.m_KnobInfo.tickMin) {
            tick = this.m_KnobInfo.tickMin;
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