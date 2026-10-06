package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.camera.ExposureIndex;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderItem;

import java.util.ArrayList;

/**
 * Created by killerink, vibhorSrv, eszdman
 */
public class ShutterModel extends ManualModel<Long> {

    public ShutterModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Long> range,
                        ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent) {
        super(context, cameraCharacteristics, range, manualParamModel, valueChangedEvent);
    }

    @Override
    protected void fillSliderItems() {

        long exposureTimeValue;
        Range<Long> range = super.range;
        if (range == null || (range.getLower() == 0 && range.getUpper() == 0)) {
            return;
        }

        SliderItem auto = getNewAutoItem(ManualParamModel.EXPOSURE_AUTO, null);
        getSliderItems().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();

        long minexp = range.getLower();
        if (minexp < 1000) minexp = 1000;
        long maxexp = range.getUpper();
        Log.v("ExpModel", "Max exp:" + maxexp);
        Log.v("ExpModel", "Min exp:" + minexp);
        double maxcnt = Math.log10((double) maxexp) / Math.log10(2);
        double mincnt = Math.log10((double) minexp) / Math.log10(2);
        Log.v("ExpModel", "Max exp cnt:" + maxcnt);
        // split to negative and positive log list
        ArrayList<String> candidatesPos = new ArrayList<>();
        ArrayList<Long> valuesPos = new ArrayList<>();
        double shortExp = Math.log10(ExposureIndex.sec) / Math.log10(2);
        if (shortExp > maxcnt) shortExp = Math.log10(ExposureIndex.sec/4.0) / Math.log10(2);
        for (double expCnt = shortExp; expCnt < maxcnt; expCnt += 1.0 / 4.0) {
            long val = (long) (Math.pow(2.0, expCnt));
            // round val to 1000 from both sides
            if (val % 250000000 != 0) {
                long val1 = val - val % 250000000;
                long val2 = val1 + 250000000;
                if (val - val1 > val2 - val) val = val2;
                else val = val1;
            }
            String out = ExposureIndex.sec2string(ExposureIndex.time2sec(val));
            candidatesPos.add(out);
            valuesPos.add(val);
        }
        candidatesPos.add(ExposureIndex.sec2string(ExposureIndex.time2sec(maxexp)));
        valuesPos.add(maxexp);

        ArrayList<String> candidatesNeg = new ArrayList<>();
        ArrayList<Long> valuesNeg = new ArrayList<>();
        for (double expCnt = shortExp - 1.0 / 4.0; expCnt > mincnt; expCnt -= 1.0 / 4.0) {
            long val = (long) (Math.pow(2.0, expCnt));
            if(val > maxexp) continue;
            String out = ExposureIndex.sec2string(ExposureIndex.time2sec(val));
            candidatesNeg.add(out);
            valuesNeg.add(val);
        }
        candidatesNeg.add(ExposureIndex.sec2string(ExposureIndex.time2sec(minexp)));
        valuesNeg.add(minexp);
        // invert negative list
        for (int i = candidatesNeg.size() - 1; i >= 0; i--) {
            candidates.add(candidatesNeg.get(i));
            values.add(valuesNeg.get(i));
        }
        // add positive list
        for (int i = 0; i < candidatesPos.size(); i++) {
            candidates.add(candidatesPos.get(i));
            values.add(valuesPos.get(i));
        }

        int preferredIntervalCount = 4;
        int tick = 0;
        int tickShift = candidatesNeg.size()%preferredIntervalCount;
        while (tick < candidates.size()) {
            int prefMpy = 1;
            if(candidates.get(tick).length() > 5) prefMpy = 2;
            String label = null;
            if ((tick-tickShift) % (preferredIntervalCount*prefMpy) == 0) {
                label = candidates.get(tick);
            }
            getSliderItems().add(new SliderItem(candidates.get(tick), label, tick + 1, (double) values.get(tick)));
            tick++;
        }
    }

    @Override
    public void onSelectedSliderItemChanged(SliderItem newItem) {
        currentInfo = newItem;
        manualParamModel.setCurrentExposureValue(newItem.value);
    }
}
