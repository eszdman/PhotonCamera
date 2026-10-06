package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderItem;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by killerink, vibhorSrv, eszdman
 */
public class FocusModel extends ManualModel<Float> {

    public FocusModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Float> range,
                      ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent) {
        super(context, cameraCharacteristics, range, manualParamModel, valueChangedEvent);
    }

    @Override
    protected void fillSliderItems() {
        SliderItem auto;
        if (range == null) {
            auto = getNewAutoItem(-1.0d, context.getString(R.string.manual_mode_fixed));
            getSliderItems().add(auto);
            currentInfo = auto;
            return;
        }
        auto = getNewAutoItem(ManualParamModel.FOCUS_AUTO, null);
        getSliderItems().add(auto);
        currentInfo = auto;
        float focusStep = (range.getUpper() - range.getLower()) / 40;
        ArrayList<Float> values = new ArrayList<>();
        for (float fValue = range.getUpper(); fValue >= range.getLower(); fValue -= focusStep) {
            values.add(fValue);
        }
        if (values.size() > 0) {
            values.set(values.size() - 1, range.getLower());
        }
        for (int tick = 0; tick < values.size(); tick++) {
            String text = String.format(Locale.ROOT, "%.2f", values.get(tick));
            // Slider shows numeric distance for every step; the old wheel used
            // near/far icons at the ends with blank intermediates.
            getSliderItems().add(new SliderItem(text, text, tick + 1, (double) values.get(tick)));
        }
    }

    @Override
    public void onSelectedSliderItemChanged(SliderItem newItem) {
        currentInfo = newItem;
        manualParamModel.setCurrentFocusValue(newItem.value);
    }
}
