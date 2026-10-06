package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderItem;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by killerink, vibhorSrv, eszdman
 */
public class EvModel extends ManualModel<Float> {

    private final String TAG = EvModel.class.getSimpleName();
    private float evStep;

    public EvModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Float> range,
                   ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent) {
        super(context, cameraCharacteristics, range, manualParamModel, valueChangedEvent);
    }

    public void setEvStep(float evStep) {
        this.evStep = evStep;
    }

    @Override
    protected void fillSliderItems() {
        Range<Float> evRange = range;
        if (evRange == null || (evRange.getLower() == 0.0f && evRange.getUpper() == 0.0f)) {
            Log.d(TAG, "fillSliderItems() - evRange is not valid.");
            return;
        }
        SliderItem auto = getNewAutoItem(ManualParamModel.EV_AUTO, null);
        getSliderItems().add(auto);
        currentInfo = auto;
        int positiveValueCount = 0;
        int negativeValueCount = 0;
        float step = 0.25f;
        ArrayList<Float> values = new ArrayList<>();
        for (float fValue = evRange.getUpper(); fValue >= evRange.getLower(); fValue -= step) {
            float roundedValue = ((float) Math.round(10000.0f * fValue)) / 10000.0f;
            if (!isZero(fValue)) {
                if (fValue > 0.0f) {
                    positiveValueCount++;
                } else {
                    negativeValueCount++;
                }
            }
            values.add(roundedValue);
        }
        if (values.size() > 0) {
            values.set(values.size() - 1, evRange.getLower());
        }
        for (int tick = 0; tick < values.size(); tick++) {
            float value = values.get(tick);
            if (!isZero(value)) {
                String fullText = (value > 0.0f ? "+" : "")
                        + String.format(Locale.ROOT, "%.2f", value);
                String label = null;
                if (isInteger(value)) {
                    String valueStr = String.valueOf((int) value);
                    if (value > 0.0f) {
                        valueStr = "+" + valueStr;
                    }
                    label = valueStr;
                }
                int itemTick;
                if (value > 0.0f) {
                    itemTick = positiveValueCount - tick;
                } else {
                    itemTick = negativeValueCount - tick;
                }
                getSliderItems().add(new SliderItem(fullText, label, itemTick, value));
            }
        }
    }

    @Override
    public void onSelectedSliderItemChanged(SliderItem newItem) {
        currentInfo = newItem;
        manualParamModel.setCurrentEvValue((int) (newItem.value / evStep));
    }

    private boolean isZero(float value) {
        return ((double) Math.abs(value)) <= 0.001d;
    }

    private boolean isInteger(float value) {
        int checkNumber = ((int) (Math.abs(value) * 10000.0f)) % 10000;
        return checkNumber == 0 || checkNumber == 9999;
    }
}
