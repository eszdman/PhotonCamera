package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.camera.IsoExpoSelector;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderItem;

import java.util.ArrayList;
/**
 * Created by killerink, vibhorSrv, eszdman
 */
public class IsoModel extends ManualModel<Integer> {

    public IsoModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Integer> range,
                    ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent) {
        super(context,cameraCharacteristics, range, manualParamModel, valueChangedEvent);
    }

    @Override
    protected void fillSliderItems() {
        SliderItem auto = getNewAutoItem(ManualParamModel.ISO_AUTO, null);
        getSliderItems().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();
        Object isolow = range.getLower();
        Object isohigh = range.getUpper();
        int miniso = (int) isolow;
        int maxiso = (int) isohigh;
        Log.v("IsoModel", "Max iso:" + maxiso);
        Log.v("IsoModel", "Max iso cnt:" + Math.log10((double) maxiso / miniso) / Math.log10(2));
        for (double isoCnt = Math.log10(1) / Math.log10(2); isoCnt < Math.log10((double) maxiso / miniso) / Math.log10(2); isoCnt += 1.0 / 4.0) {
            int val = (int) (Math.pow(2.0, isoCnt) * miniso);
            candidates.add(String.valueOf(val));
            values.add((int) (val / IsoExpoSelector.getMPY(cameraCharacteristics)));
        }
        candidates.add(String.valueOf(isohigh));
        values.add((int)((int)isohigh / IsoExpoSelector.getMPY(cameraCharacteristics)));
        int tick = 0;
        int preferredIntervalCount = 4;
        while (tick < candidates.size()) {
            boolean isLastItem = tick == candidates.size() + -1;
            String label = null;
            if (tick % preferredIntervalCount == 0 || isLastItem) {
                label = candidates.get(tick);
            }
            getSliderItems().add(new SliderItem(candidates.get(tick), label, tick + 1, values.get(tick)));
            tick++;
        }
    }

    @Override
    public void onSelectedSliderItemChanged(SliderItem newItem) {
        currentInfo = newItem;
        manualParamModel.setCurrentISOValue(newItem.value);
    }
}
