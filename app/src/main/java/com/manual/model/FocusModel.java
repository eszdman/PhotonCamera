package com.manual.model;

import android.graphics.drawable.Drawable;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;

import androidx.core.content.ContextCompat;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class FocusModel extends ManualModel<Float> {

    public FocusModel(Range range, ValueChangedEvent valueChangedEvent) {
        super(range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        Drawable drawable;
        KnobItemInfo auto;
        if (range == null) {
            auto = getNewAutoItem(-1.0d, PhotonCamera.getCameraActivity().getString(R.string.manual_mode_fixed));
            getKnobInfoList().add(auto);
            currentInfo = auto;
            return;
        }
        auto = getNewAutoItem(-1.0d, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;
        float focusStep = (range.getUpper() - range.getLower()) / 20;
        ArrayList<Float> values = new ArrayList<>();
        for (float fValue = range.getUpper(); fValue >= range.getLower(); fValue -= focusStep) {
            values.add(fValue);
        }
        if (values.size() > 0) {
            values.set(values.size() - 1, range.getLower());
        }
        for (int tick = 0; tick < values.size(); tick++) {
            if (tick == 0) {
                drawable = ContextCompat.getDrawable(PhotonCamera.getCameraActivity(), R.drawable.manual_icon_focus_near);
            } else if (tick == values.size() - 1) {
                drawable = ContextCompat.getDrawable(PhotonCamera.getCameraActivity(), R.drawable.manual_icon_focus_far);
            } else {
                drawable = new ShadowTextDrawable();
            }
            String text = String.format("%.2f", values.get(tick));
//            getKnobInfoList().add(new KnobItemInfo(drawable, text, tick - values.size(), (double) values.get(tick)));
            getKnobInfoList().add(new KnobItemInfo(drawable, text, tick + 1, (double) values.get(tick)));
        }
        int angle = PhotonCamera.getCameraActivity().getResources().getInteger(R.integer.manual_focus_knob_view_angle_half);
        knobInfo = new KnobInfo(0, angle, 0, values.size(), PhotonCamera.getCameraActivity().getResources().getInteger(R.integer.manual_focus_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        CaptureRequest.Builder builder = PhotonCamera.getCameraFragment().getCaptureController().mPreviewRequestBuilder;
        if (knobItemInfo.equals(autoModel)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, (float) knobItemInfo.value);
        }
        PhotonCamera.getCameraFragment().getCaptureController().rebuildPreviewBuilder();
        //fireValueChangedEvent(knobItemInfo.text);
    }
}
