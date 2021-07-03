package com.particlesdevs.photoncamera.circularbarlib.ui;

import android.app.Activity;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.model.KnobModel;
import com.particlesdevs.photoncamera.circularbarlib.model.ManualModeModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;

import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by vibhorSrv
 */
public class ViewObserver implements Observer {
    private final Activity activity;
    private final RelativeLayout manualMode;
    private final KnobView knobView;
    private final TextView isoOption;
    private final TextView expOption;
    private final TextView evOption;
    private final TextView focusOption;
    private final List<TextView> textViews;
    private final OrientationEventListener orientationEventListener;
    private final LinearLayout buttonsContainer;
    private int rotation = 0;


    public ViewObserver(Activity activity) {
        this.activity = activity;
        manualMode = findViewById(R.id.manual_mode);
        buttonsContainer = findViewById(R.id.buttons_container);
        knobView = findViewById(R.id.knobView);
        isoOption = findViewById(R.id.iso_option_tv);
        expOption = findViewById(R.id.exposure_option_tv);
        evOption = findViewById(R.id.ev_option_tv);
        focusOption = findViewById(R.id.focus_option_tv);
        textViews = Arrays.asList(isoOption, evOption, expOption, focusOption);
        orientationEventListener = new OrientationEventListener(activity.getBaseContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 340 || orientation < 20 && rotation != 0) {
                    rotation = 0;

                } else if (orientation >= 70 && orientation < 110 && rotation != 90) {
                    rotation = -90;

                } else if (orientation >= 160 && orientation < 200 && rotation != 180) {
                    rotation = 180;

                } else if (orientation >= 250 && orientation < 290 && rotation != 270) {
                    rotation = 90;
                }
                Binding.rotateKnobView(knobView, rotation);
                Binding.rotateViewGroupChild(buttonsContainer, rotation, 0);
            }
        };
    }

    public void enableOrientationListener() {
        if (orientationEventListener != null && orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    public void disableOrientationListener() {
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
    }

    private <T extends View> T findViewById(int id) {
        return activity.findViewById(id);
    }


    @Override
    public void update(Observable o, Object arg) {
        if (o != null && arg != null) {
            if (o instanceof KnobModel) {
                KnobModel knobModel = (KnobModel) o;
                switch ((KnobModel.KnobModelFields) arg) {
                    case RESET:
                        Binding.resetKnob(knobView, knobModel.isKnobResetCalled());
                        break;
                    case VISIBILITY:
                        Binding.setKnobVisibility(knobView, knobModel.isKnobVisible());
                        break;
                    case MANUAL_MODEL:
                        Binding.setModelToKnob(knobView, knobModel.getManualModel());
                        break;
                }
            }
            if (o instanceof ManualModeModel) {
                ManualModeModel manualModeModel = (ManualModeModel) o;
                switch ((ManualModeModel.ManualModelFields) arg) {
                    case EV_TEXT:
                        evOption.setText(manualModeModel.getEvText());
                        break;
                    case EXP_TEXT:
                        expOption.setText(manualModeModel.getExposureText());
                        break;
                    case ISO_TEXT:
                        isoOption.setText(manualModeModel.getIsoText());
                        break;
                    case FOCUS_TEXT:
                        focusOption.setText(manualModeModel.getFocusText());
                        break;
                    case EV_LISTENER:
                        evOption.setOnClickListener(manualModeModel.getEvTextClicked());
                        break;
                    case EXP_LISTENER:
                        expOption.setOnClickListener(manualModeModel.getExposureTextClicked());
                        break;
                    case FOCUS_LISTENER:
                        focusOption.setOnClickListener(manualModeModel.getFocusTextClicked());
                        break;
                    case ISO_LISTENER:
                        isoOption.setOnClickListener(manualModeModel.getIsoTextClicked());
                        break;
                    case SELECTED_TV:
                        View v = findViewById(manualModeModel.getSelectedTextViewId());
                        if (v != null) {
                            for (TextView textView : textViews) {
                                textView.setSelected(v.equals(textView));
                            }
                        } else {
                            for (TextView textView : textViews) {
                                textView.setSelected(false);
                            }
                        }
                        break;
                    case PANEL_VISIBILITY:
                        Binding.togglePanelVisibility(manualMode, manualModeModel.isManualPanelVisible());
                        break;

                }
            }
        }

    }
}
