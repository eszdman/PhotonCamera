package com.manual;


import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Range;
import android.util.Rational;
import androidx.annotation.RequiresApi;
import com.eszdman.photoncamera.Parameters.ExposureIndex;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;

import java.util.ArrayList;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ExposureTimeKnobView extends KnobView {
    public static final String[] EXPOSURE_TIME_CANDIDATES = {"1/30000", "1/25000", "1/20000", "1/18000", "1/16000", "1/14000", "1/12000", "1/10000", "1/8000", "1/6400", "1/5000", "1/4000", "1/3200", "1/2500", "1/2000", "1/1600", "1/1250", "1/1000", "1/800", "1/640", "1/500", "1/400", "1/320", "1/250", "1/200", "1/160", "1/125", "1/100", "1/80", "1/60", "1/50", "1/40", "1/30", "1/25", "1/20", "1/15", "1/13", "1/10", "1/8", "1/6", "1/5", "1/4", "1/3", "0.4", "0.5", "0.6", "0.8", "1", "1.3", "1.6", "2", "2.5", "3", "4", "5", "6", "8", "10", "13", "15", "20", "25", "30"};

    public ExposureTimeKnobView(Context context) {
        this(context, null);
    }

    public ExposureTimeKnobView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    private int findPreferredKnobViewAngle(int indicatorCount) {
        return (indicatorCount - 1) * 25;
    }

    private int findPreferredIntervalCount(int totalCount) {
        int result = 9;
        int minRemainder = Integer.MAX_VALUE;
        int i = 9;
        while (i >= 5 && (((float) (totalCount - 1)) / ((float) i)) + 1.0f <= 7.0f) {
            int remainder = ((totalCount % i) + (i - 1)) % i;
            if (minRemainder > remainder) {
                minRemainder = remainder;
                result = i;
            }
            i--;
        }
        return result;
    }

    @Override
    protected boolean onSetupIcons() {
        setDashAroundAutoEnabled(true);
        long exposureTimeValue;
        Activity cameraActivity = (Activity) getContext();
        setIconPadding(cameraActivity.getResources().getDimensionPixelSize(R.dimen.manual_knob_icon_padding));
        Range<Long> range = super.range;
        if (range == null || (range.getLower() == 0 && range.getUpper() == 0)) {
            return false;
        }
        ArrayList<KnobItemInfo> arrayList = new ArrayList<>();
        status = cameraActivity.findViewById(R.id.exposure_option_tv);
        status.setText(auto_sring);
        ShadowTextDrawable autoDrawable = new ShadowTextDrawable();
        autoDrawable.setText(auto_sring);
        autoDrawable.setTextAppearance(cameraActivity, R.style.ManualModeKnobText);
        ShadowTextDrawable autoDrawableSelected = new ShadowTextDrawable();
        autoDrawableSelected.setText(auto_sring);
        autoDrawableSelected.setTextAppearance(cameraActivity, R.style.ManualModeKnobTextSelected);
        StateListDrawable autoStateDrawable = new StateListDrawable();
        autoStateDrawable.addState(new int[]{-16842913}, autoDrawable);
        autoStateDrawable.addState(new int[]{-16842913}, autoDrawableSelected);
        arrayList.add(new KnobItemInfo(autoStateDrawable, auto_sring, 0, defaultValue));
        ArrayList<String> arrayList2 = new ArrayList<>();
        ArrayList<Long> arrayList3 = new ArrayList<>();
        for (String exposureTimeCandidate : EXPOSURE_TIME_CANDIDATES) {
            if (exposureTimeCandidate.contains("/")) {
                exposureTimeValue = (long) (Rational.parseRational(exposureTimeCandidate).doubleValue() * 1000.0d * 1000.0d * 1000.0d);
            } else {
                exposureTimeValue = (long) (Double.parseDouble(exposureTimeCandidate) * 1000.0d * 1000.0d * 1000.0d);
            }
            if (exposureTimeValue >= range.getLower() && exposureTimeValue <= range.getUpper()) {
                arrayList2.add(exposureTimeCandidate);
                arrayList3.add(exposureTimeValue);
            }
        }
        int indicatorCount = 0;
        int preferredIntervalCount = findPreferredIntervalCount(arrayList2.size());
        int i2 = 0;
        while (i2 < arrayList2.size()) {
            boolean isLastItem = i2 == arrayList2.size() + -1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(cameraActivity, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(cameraActivity, R.style.ManualModeKnobTextSelected);
            if (i2 % preferredIntervalCount == 0 || isLastItem) {
                String text = arrayList2.get(i2);
                drawable.setText(text);
                drawableSelected.setText(text);
                indicatorCount++;
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-16842913}, drawable);
            stateDrawable.addState(new int[]{-16842913}, drawableSelected);
            arrayList.add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 - arrayList2.size(), (double) arrayList3.get(i2)));
            arrayList.add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 + 1, (double) arrayList3.get(i2)));
            i2++;
        }
        int angle = findPreferredKnobViewAngle(indicatorCount);
        int angleMax = cameraActivity.getResources().getInteger(R.integer.manual_exposure_knob_view_angle_half);
        if (angle > angleMax) {
            angle = angleMax;
        }
        setKnobInfo(new KnobInfo(-angle, angle, -arrayList2.size(), arrayList2.size(), cameraActivity.getResources().getInteger(R.integer.manual_exposure_knob_view_auto_angle)));
        setKnobItems(arrayList);
        invalidate();
        return true;
    }

    @Override
    public void doWhatever() {
        super.doWhatever();
        CaptureRequest.Builder builder = Interface.i.camera.mPreviewRequestBuilder;
        if (getCurrentKnobItem().value == -1) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) getCurrentKnobItem().value);
        }
        Interface.i.camera.rebuildPreviewBuilder();
    }
}
