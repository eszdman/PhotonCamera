package com.manual.model;

import android.graphics.drawable.StateListDrawable;
import android.util.Range;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobViewChangedListener;
import com.manual.ManualMode;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;
import java.util.List;

public abstract class ManualModel<T extends Comparable<? super T>> implements KnobViewChangedListener, IModel {

    public interface ValueChangedEvent
    {
        void onValueChanged(IModel manualMode);
    }

    private List<KnobItemInfo> knobInfoList;
    protected KnobItemInfo currentInfo;
    protected Range<T> range;
    private ValueChangedEvent valueChangedEvent;
    protected KnobInfo knobInfo;

    public ManualModel(Range range, ValueChangedEvent valueChangedEvent){
        this.range =range;
        this.valueChangedEvent = valueChangedEvent;
        knobInfoList =new ArrayList<>();
        fillKnobInfoList();
    }

    protected void fireValueChangedEvent()
    {
        if (valueChangedEvent != null)
            valueChangedEvent.onValueChanged(this);
    }

    public KnobItemInfo getAutoItem(double defaultval)
    {
        ShadowTextDrawable autoDrawable = new ShadowTextDrawable();
        String auto_sring = Interface.i.mainActivity.getString(R.string.manual_mode_auto);
        autoDrawable.setText(auto_sring);
        autoDrawable.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobText);
        ShadowTextDrawable autoDrawableSelected = new ShadowTextDrawable();
        autoDrawableSelected.setText(auto_sring);
        autoDrawableSelected.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobTextSelected);
        StateListDrawable autoStateDrawable = new StateListDrawable();
        autoStateDrawable.addState(new int[]{-16842913}, autoDrawable);
        autoStateDrawable.addState(new int[]{-16842913}, autoDrawableSelected);
        return new KnobItemInfo(autoStateDrawable, auto_sring, 0, defaultval);
    }

    protected abstract void fillKnobInfoList();

    @Override
    public List<KnobItemInfo> getKnobInfoList()
    {
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
}
