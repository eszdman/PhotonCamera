package com.manual;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.AttributeSet;
import android.util.Range;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

import java.util.ArrayList;

public class FocusKnobView extends KnobView {

    public FocusKnobView(Context context) {
        this(context, null);
    }

    public FocusKnobView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    protected boolean onSetupIcons() {
        Drawable drawable;
        Activity cameraActivity = (Activity) getContext();
        setIconPadding(cameraActivity.getResources().getDimensionPixelSize(R.dimen.manual_knob_icon_padding));
        Range<Float> range = super.range;
        if (range == null || range.getLower().equals(range.getUpper())) {
            return false;
        }
        status = cameraActivity.findViewById(R.id.focus_option_tv);
        status.setText(auto_sring);
        ArrayList<KnobItemInfo> arrayList = new ArrayList<KnobItemInfo>();
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
//        TODO float focusStep = ((Float) camera.get(Camera.PROP_FOCUS_STEP)).floatValue();
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
                drawable = cameraActivity.getDrawable(R.drawable.manual_icon_focus_near);
            } else if (i == arrayList2.size() - 1) {
                drawable = cameraActivity.getDrawable(R.drawable.manual_icon_focus_far);
            } else {
                drawable = new ShadowTextDrawable();
            }
            String manual_string = cameraActivity.getString(R.string.manual_mode_manual);
            arrayList.add(new KnobItemInfo(drawable, manual_string, i - arrayList2.size(), (double) arrayList2.get(i)));
            arrayList.add(new KnobItemInfo(drawable, manual_string, i + 1, (double) arrayList2.get(i)));
        }
        setDashAroundAutoEnabled(false);
        int angle = cameraActivity.getResources().getInteger(R.integer.manual_focus_knob_view_angle_half);
        setKnobInfo(new KnobInfo(-angle, angle, -arrayList2.size(), arrayList2.size(), cameraActivity.getResources().getInteger(R.integer.manual_focus_knob_view_auto_angle)));
        setKnobItems(arrayList);
        invalidate();
        return true;
    }

    @Override
    public void doWhatever() {
        super.doWhatever();
        CaptureRequest.Builder builder = Interface.i.camera.mPreviewRequestBuilder;
        if (getCurrentKnobItem().value == -1) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, (float) getCurrentKnobItem().value);
        }
        Interface.i.camera.rebuildPreviewBuilder();
    }
}
