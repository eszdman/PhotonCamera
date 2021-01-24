package com.particlesdevs.photoncamera.manual.model;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Range;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.manual.*;

import java.util.ArrayList;
import java.util.List;

public abstract class ManualModel<T extends Comparable<? super T>> implements KnobViewChangedListener, IModel {

    public static final double SHUTTER_AUTO = 0;
    public static final double EV_AUTO = 0;
    public static final double ISO_AUTO = 0;
    public static final double FOCUS_AUTO = -1.0d;
    private final int SET_TO_CAM = 1;
    private final HandlerThread mBackgroundThread;
    private final List<KnobItemInfo> knobInfoList;
    private final ValueChangedEvent valueChangedEvent;
    private final Handler backgroundHandler;
    protected Range<T> range;
    protected KnobInfo knobInfo;
    protected KnobItemInfo currentInfo, autoModel;
    protected Context context;

    public ManualModel(Context context, Range<T> range, ValueChangedEvent valueChangedEvent) {
        this.context = context;
        this.range = range;
        this.valueChangedEvent = valueChangedEvent;
        knobInfoList = new ArrayList<>();
        mBackgroundThread = new HandlerThread(ManualMode.class.getName());
        mBackgroundThread.start();
        backgroundHandler = new Handler(mBackgroundThread.getLooper(), msg -> {
            if (msg.what == SET_TO_CAM) {
                onSelectedKnobItemChanged((KnobItemInfo) msg.obj);
            }
            return false;
        });
        fillKnobInfoList();
    }

    @Override
    protected void finalize() throws Throwable {
        mBackgroundThread.quitSafely();
        super.finalize();
    }

    public void fireValueChangedEvent(final String txt) {
        if (valueChangedEvent != null)
            valueChangedEvent.onValueChanged(txt);
    }

    public KnobItemInfo getAutoModel() {
        return autoModel;
    }

    protected KnobItemInfo getNewAutoItem(double defaultVal, String defaultText) {
        ShadowTextDrawable autoDrawable = new ShadowTextDrawable();
        String auto_string = context.getString(R.string.manual_mode_auto);
        if (defaultText != null)
            auto_string = defaultText;
        autoDrawable.setText(auto_string);
        autoDrawable.setTextAppearance(context, R.style.ManualModeKnobText);
        ShadowTextDrawable autoDrawableSelected = new ShadowTextDrawable();
        autoDrawableSelected.setText(auto_string);
        autoDrawableSelected.setTextAppearance(context, R.style.ManualModeKnobTextSelected);
        StateListDrawable autoStateDrawable = new StateListDrawable();
        autoStateDrawable.addState(new int[]{-android.R.attr.state_selected}, autoDrawable);
        autoStateDrawable.addState(new int[]{android.R.attr.state_selected}, autoDrawableSelected);
        autoModel = new KnobItemInfo(autoStateDrawable, auto_string, 0, defaultVal);
        return autoModel;
    }

//    public KnobItemInfo getItemInfo(String text, double val, int tick) {
//        ShadowTextDrawable autoDrawable = new ShadowTextDrawable();
//        autoDrawable.setText(text);
//        autoDrawable.setTextAppearance(context, R.style.ManualModeKnobText);
//        ShadowTextDrawable autoDrawableSelected = new ShadowTextDrawable();
//        autoDrawableSelected.setText(text);
//        autoDrawableSelected.setTextAppearance(context, R.style.ManualModeKnobTextSelected);
//        StateListDrawable autoStateDrawable = new StateListDrawable();
//        autoStateDrawable.addState(new int[]{-android.R.attr.state_selected}, autoDrawable);
//        autoStateDrawable.addState(new int[]{android.R.attr.state_selected}, autoDrawableSelected);
//        return new KnobItemInfo(autoStateDrawable, text, tick, val);
//    }

    protected abstract void fillKnobInfoList();

    @Override
    public List<KnobItemInfo> getKnobInfoList() {
        return knobInfoList;
    }

    @Override
    public KnobItemInfo getCurrentInfo() {
        return currentInfo;
    }

    @Override
    public KnobInfo getKnobInfo() {
        return knobInfo;
    }

    @Override
    public void onSelectedKnobItemChanged(KnobView knobView, KnobItemInfo knobItemInfo, final KnobItemInfo knobItemInfo2) {
        Log.d(ManualMode.class.getSimpleName(), "onSelectedKnobItemChanged");
        if (knobItemInfo == knobItemInfo2)
            return;
        backgroundHandler.obtainMessage(SET_TO_CAM, knobItemInfo2).sendToTarget();
        if (knobItemInfo != null) {
            knobItemInfo.drawable.setState(new int[]{-android.R.attr.state_selected});
        }
        knobItemInfo2.drawable.setState(new int[]{android.R.attr.state_selected});
        fireValueChangedEvent(knobItemInfo2.text);
    }

    public void resetModel() {
        onSelectedKnobItemChanged(null, null, autoModel);
    }

    public abstract void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo2);

    public interface ValueChangedEvent {
        void onValueChanged(String value);
    }
}
