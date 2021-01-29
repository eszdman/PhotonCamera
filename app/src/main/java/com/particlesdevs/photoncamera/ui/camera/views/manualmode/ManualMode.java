package com.particlesdevs.photoncamera.ui.camera.views.manualmode;


import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.manual.ManualParamModel;
import com.particlesdevs.photoncamera.manual.model.EvModel;
import com.particlesdevs.photoncamera.manual.model.FocusModel;
import com.particlesdevs.photoncamera.manual.model.IsoModel;
import com.particlesdevs.photoncamera.manual.model.ManualModel;
import com.particlesdevs.photoncamera.manual.model.ShutterModel;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.KnobView;
import com.particlesdevs.photoncamera.util.Timer;

import java.util.Arrays;

/**
 * Created by Vibhor on 10/08/2020
 */
public final class ManualMode extends RelativeLayout {

    private static final String TAG = "ManualModeImpl";
    private final Context mContext;
    private ManualParamModel manualParamModel;
    private TextView mfTextView, isoTextView, expoTextView, evTextView;
    private KnobView knobView;
    private View[] textViews;
    private ManualModel mfModel, isoModel, expoTimeModel, evModel, selectedModel;


    public ManualMode(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.knobView = findViewById(R.id.knobView);
        this.mfTextView = findViewById(R.id.focus_option_tv);
        this.evTextView = findViewById(R.id.ev_option_tv);
        this.expoTextView = findViewById(R.id.exposure_option_tv);
        this.isoTextView = findViewById(R.id.iso_option_tv);
        this.textViews = new View[]{mfTextView, evTextView, expoTextView, isoTextView};
    }

    public void reInit() {
        post(() -> {
            addKnobs();
            setupOnClickListeners();
        });
    }

    public void retractAllKnobs() {
        hideKnob();
        knobView.resetKnob();
        selectedModel = null;
        mfModel.resetModel();
        expoTimeModel.resetModel();
        isoModel.resetModel();
        evModel.resetModel();
        unSelectOthers(null);
    }

    private void addKnobs() {
        Timer timer = Timer.InitTimer(TAG, "addKnobs");
        CaptureController.CameraProperties cameraProperties = new CaptureController.CameraProperties();
        if (manualParamModel != null) {
            manualParamModel.reset();
            mfModel = new FocusModel(mContext, cameraProperties.focusRange, manualParamModel, value -> updateText(mfTextView, value));
            evModel = new EvModel(mContext, cameraProperties.evRange, manualParamModel, value -> updateText(evTextView, value));
            isoModel = new IsoModel(mContext, cameraProperties.isoRange, manualParamModel, value -> updateText(isoTextView, value));
            expoTimeModel = new ShutterModel(mContext, cameraProperties.expRange, manualParamModel, value -> updateText(expoTextView, value));
            hideKnob();
            unSelectOthers(null);
        } else {
            try {
                throw new NullPointerException("manualParamModel is null, make sure to call setManualParamModel()");
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        timer.endTimer();
    }

    private void updateText(TextView tv, String value) {
        tv.post(() -> tv.setText(value));
    }

    private void setupOnClickListeners() {
        setListeners(mfTextView, mfModel);
        setListeners(expoTextView, expoTimeModel);
        setListeners(isoTextView, isoModel);
        setListeners(evTextView, evModel);
    }

    private void setListeners(TextView tv, ManualModel model) {
        tv.setOnClickListener(v -> setModelToKnob(v, model));
        tv.setOnLongClickListener(v -> {
            if (selectedModel == model)
                knobView.resetKnob();
            model.resetModel();
            return true;
        });
        model.setAutoTxt();
    }

    private void setModelToKnob(View view, ManualModel modelToKnob) {
        view.setSelected(!view.isSelected());
        unSelectOthers(view);
        if (modelToKnob == selectedModel) {
//            knobView.resetKnob();
            knobView.setKnobViewChangedListener(null);
            hideKnob();
            selectedModel = null;
        } else {
//            knobView.resetKnob();
            if (modelToKnob.getKnobInfoList().size() > 1) {
                knobView.setKnobViewChangedListener(modelToKnob);
                knobView.setKnobInfo(modelToKnob.getKnobInfo());
                knobView.setKnobItems(modelToKnob.getKnobInfoList());
                knobView.setTickByValue(modelToKnob.getCurrentInfo().value);
                showKnob();
                selectedModel = modelToKnob;
            }
        }
    }

    private void showKnob() {
        knobView.animate().translationY(0).scaleY(1).scaleX(1).setDuration(200).alpha(1f).start();
        knobView.setVisibility(View.VISIBLE);
    }

    private void hideKnob() {
        knobView.animate().translationY(knobView.getHeight() / 2.5f)
                .scaleY(.2f).scaleX(.2f).setDuration(200).alpha(0f).withEndAction(() -> knobView.setVisibility(View.GONE)).start();
    }

    public boolean showPanel(boolean panelShowing) {
        if (!panelShowing) {
            post(() -> {
                animate().translationY(0).setDuration(100).alpha(1f).start();
                setVisibility(View.VISIBLE);
            });
        }
        return true;
    }

    public boolean hidePanel(boolean panelShowing) {
        if (panelShowing) {
            post(() -> animate()
                    .translationY(getResources().getDimension(R.dimen.standard_20))
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction(() -> setVisibility(View.GONE))
                    .start());
            manualParamModel.reset();
        }
        return false;
    }

    private void unSelectOthers(@Nullable View v) {
        Arrays.stream(textViews).forEach(view -> {
            if (v == null || !v.equals(view))
                view.setSelected(false);
        });
    }

    @Override
    protected void finalize() throws Throwable {
        knobView = null;
        textViews = null;
        mfTextView = null;
        isoTextView = null;
        expoTextView = null;
        evTextView = null;
        super.finalize();
    }

    public void setManualParamModel(ManualParamModel manualParamModel) {
        this.manualParamModel = manualParamModel;
    }
}
