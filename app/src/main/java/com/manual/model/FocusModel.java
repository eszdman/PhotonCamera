package com.manual.model;

import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class FocusModel extends ManualModel<Float> {

    public FocusModel(Range range,ValueChangedEvent valueChangedEvent) {
        super(range,valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        Drawable drawable;
        KnobItemInfo auto = getAutoItem(-1.0d);
        getKnobInfoList().add(auto);
        currentInfo = auto;
        Float min = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        Float max = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        float focusStep = 1.0f;
        if (max != null && min != null)
            focusStep = Math.abs(max - min) / 10f;
        ArrayList<Float> arrayList2 = new ArrayList<>();
        for (float fValue = range.getUpper(); fValue >= range.getLower().floatValue(); fValue -= focusStep) {
            arrayList2.add(fValue);
        }
        if (arrayList2.size() > 0) {
            arrayList2.set(arrayList2.size() - 1, range.getLower());
        }
        for (int i = 0; i < arrayList2.size(); i++) {
            if (i == 0) {
                drawable = Interface.i.mainActivity.getDrawable(R.drawable.manual_icon_focus_near);
            } else if (i == arrayList2.size() - 1) {
                drawable = Interface.i.mainActivity.getDrawable(R.drawable.manual_icon_focus_far);
            } else {
                drawable = new ShadowTextDrawable();
            }
            String manual_string = Interface.i.mainActivity.getString(R.string.manual_mode_manual);
            getKnobInfoList().add(new KnobItemInfo(drawable, arrayList2.get(i).toString(), i - arrayList2.size(), (double) arrayList2.get(i)));
            getKnobInfoList().add(new KnobItemInfo(drawable,  arrayList2.get(i).toString(), i + 1, (double) arrayList2.get(i)));
        }
        int angle = Interface.i.mainActivity.getResources().getInteger(R.integer.manual_focus_knob_view_angle_half);
        knobInfo = new KnobInfo(-angle, angle, -arrayList2.size(), arrayList2.size(), Interface.i.mainActivity.getResources().getInteger(R.integer.manual_focus_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobView knobView, KnobItemInfo knobItemInfo, KnobItemInfo knobItemInfo2) {
        currentInfo = knobItemInfo2;
        CaptureRequest.Builder builder = Interface.i.camera.mPreviewRequestBuilder;
        if (knobItemInfo2.value == -1) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, (float) knobItemInfo2.value);
        }
        Interface.i.camera.rebuildPreviewBuilder();
        fireValueChangedEvent();
    }
}
