package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderItem;

import java.util.ArrayList;

/**
 * Model responsible for managing a pure, strictly uniform 50K stepped Kelvin scale (2000K - 10000K).
 */
public class WbModel extends ManualModel<Integer> {

    public WbModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Integer> range,
                   ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent) {
        super(context, cameraCharacteristics, range, manualParamModel, valueChangedEvent);
    }

    @Override
    protected void fillSliderItems() {
        SliderItem auto = getNewAutoItem(ManualParamModel.WB_AUTO, null);
        getSliderItems().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();

        int minK = 2000;
        int maxK = 10000;
        int stepK = 50;

        // Generate uniform steps from 2000K to 10000K
        for (int k = minK; k <= maxK; k += stepK) {
            candidates.add(k + "K");
            values.add(k);

            // Major text label every 1000K; null renders a tick mark.
            if (k % 1000 == 0) {
                int thousand = k / 1000;
                labels.add(thousand + "K");
            } else {
                labels.add(null);
            }
        }

        int tick = 0;
        while (tick < candidates.size()) {
            getSliderItems().add(new SliderItem(candidates.get(tick), labels.get(tick), tick + 1, (double) values.get(tick)));
            tick++;
        }
    }

    @Override
    public void onSelectedSliderItemChanged(SliderItem newItem) {
        currentInfo = newItem;
        manualParamModel.setCurrentWbValue(newItem.value);
    }
}
