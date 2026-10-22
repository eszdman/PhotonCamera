package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;


import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.api.ManualInstanceProvider;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.ManualSliderView;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderChangedListener;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Base model for a manual control attached to a {@link ManualSliderView}.
 *
 * <p>Also responsible for updating {@link ManualParamModel}.
 *
 * @param <T> the type of data contained by the model
 */
public abstract class ManualModel<T extends Comparable<? super T>> implements SliderChangedListener, IModel {
    protected final ManualParamModel manualParamModel;
    private final List<SliderItem> sliderItems;
    private final ValueChangedEvent valueChangedEvent;
    protected CameraCharacteristics cameraCharacteristics;
    protected Range<T> range;
    protected SliderItem currentInfo, autoModel;
    protected Context context;

    public ManualModel(Context context, CameraCharacteristics cameraCharacteristics, Range<T> range, ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent) {
        this.context = context;
        this.cameraCharacteristics = cameraCharacteristics;
        this.range = range;
        this.valueChangedEvent = valueChangedEvent;
        this.manualParamModel = manualParamModel;
        sliderItems = new ArrayList<>();
        fillSliderItems();
    }

    public void setAutoTxt() {
        fireValueChangedEvent(autoModel.text);
    }

    private void fireValueChangedEvent(final String txt) {
        if (valueChangedEvent != null)
            valueChangedEvent.onValueChanged(txt);
    }

    protected SliderItem getNewAutoItem(double defaultVal, String defaultText) {
        String autoString = context.getString(R.string.manual_mode_auto);
        if (defaultText != null) {
            autoString = defaultText;
        }
        autoModel = new SliderItem(autoString, autoString, 0, defaultVal);
        return autoModel;
    }

    protected abstract void fillSliderItems();

    @Override
    public List<SliderItem> getSliderItems() {
        return sliderItems;
    }

    @Override
    public SliderItem getCurrentInfo() {
        return currentInfo;
    }

    @Override
    public void onSelectedItemChanged(ManualSliderView view, SliderItem oldItem, final SliderItem newItem) {
        Log.d(ManualModel.class.getSimpleName(), "onSelectedItemChanged");
        ManualInstanceProvider.getHapticPerformer().tick();
        applySelection(oldItem, newItem);
    }

    public void resetModel() {
        ManualInstanceProvider.getHapticPerformer().click();
        resetModelSilently();
    }

    /**
     * Resets the model to auto without haptic feedback. Used when the manual
     * panel is closed, where up to four models reset at once and each tick
     * would stack into a multi-buzz.
     */
    public void resetModelSilently() {
        applySelection(null, autoModel);
    }

    private void applySelection(SliderItem oldItem, final SliderItem newItem) {
        if (oldItem == newItem) {
            return;
        }
        onSelectedSliderItemChanged(newItem);
        if (newItem != null) {
            fireValueChangedEvent(newItem.text);
        }
    }

    public abstract void onSelectedSliderItemChanged(SliderItem newItem);

    public interface ValueChangedEvent {
        void onValueChanged(String value);
    }
}
