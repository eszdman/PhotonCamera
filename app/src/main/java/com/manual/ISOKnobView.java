package com.manual;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CaptureRequest;
import android.util.AttributeSet;
import android.util.Range;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;

import java.util.ArrayList;


public class ISOKnobView extends KnobView {
    public static final String[] ISO_CANDIDATES = {"100", "125", "160", "200", "250", "320", "400", "500", "640", "800", "1000", "1250", "1600", "2000", "2500", "3200", "4000", "5000", "6400", "12800"};

    public ISOKnobView(Context context) {
        this(context, null);
    }

    public ISOKnobView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    protected boolean onSetupIcons() {
        Activity cameraActivity = (Activity) getContext();
        setIconPadding(cameraActivity.getResources().getDimensionPixelSize(R.dimen.manual_knob_icon_padding));
//    //TODO    Camera camera = (Camera) cameraActivity.get(CameraActivity.PROP_CAMERA);
//        if (camera == null) {
//            return false;
//        }
        Range<Integer> range = super.range;
        if (range == null || range.getLower().equals(range.getUpper())) {
            return false;
        }
        ArrayList<KnobItemInfo> arrayList = new ArrayList<>();
        status = cameraActivity.findViewById(R.id.iso_option_tv);
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
        ArrayList<Integer> arrayList3 = new ArrayList<>();
        for (String isoCandidate : ISO_CANDIDATES) {
            int isoValue = Integer.parseInt(isoCandidate);
            if (isoValue >= range.getLower() && isoValue - 50 <= range.getUpper()) {
                arrayList2.add(isoCandidate);
                arrayList3.add(isoValue);
            }
        }
        int i2 = 0;
        while (i2 < arrayList2.size()) {
            boolean isLastItem = i2 == arrayList2.size() + -1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(cameraActivity, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(cameraActivity, R.style.ManualModeKnobTextSelected);
            if (i2 % 3 == 0 || isLastItem) {
                drawable.setText(arrayList2.get(i2));
                drawableSelected.setText(arrayList2.get(i2));
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-16842913}, drawable);
            stateDrawable.addState(new int[]{-16842913}, drawableSelected);
            arrayList.add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 - arrayList2.size(), arrayList3.get(i2)));
            arrayList.add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 + 1, arrayList3.get(i2)));
            i2++;
        }
        int angle = cameraActivity.getResources().getInteger(R.integer.manual_iso_knob_view_angle_half);
        setKnobInfo(new KnobInfo(-angle, angle, -arrayList2.size(), arrayList2.size(), cameraActivity.getResources().getInteger(R.integer.manual_iso_knob_view_auto_angle)));
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
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, (int) getCurrentKnobItem().value);
        }
        Interface.i.camera.rebuildPreviewBuilder();
    }
}
