package com.particlesdevs.photoncamera.ui.camera.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

/**
 * Created by Vibhor on 19-Jan-2021
 */
public class TimerButton extends AppCompatButton {
    private static final int[] STATE_TIMER_3S = {R.attr.timer_3s};
    private static final int[] STATE_TIMER_10S = {R.attr.timer_10s};
    private static final int[] STATE_TIMER_OFF = {R.attr.timer_off};
    private boolean timer_off;
    private boolean timer_3s;
    private boolean timer_10s;

    public TimerButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTimerIconState(PreferenceKeys.getCountdownTimerIndex());
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 3);
        if (timer_off)
            mergeDrawableStates(drawableState, STATE_TIMER_OFF);
        if (timer_3s)
            mergeDrawableStates(drawableState, STATE_TIMER_3S);
        if (timer_10s)
            mergeDrawableStates(drawableState, STATE_TIMER_10S);
        return drawableState;
    }

    public void setTimerIconState(int index) {
        timer_off = false;
        timer_3s = false;
        timer_10s = false;
        switch (index) {
            case 0:
            default:
                timer_off = true;
                break;
            case 1:
                timer_3s = true;
                break;
            case 2:
                timer_10s = true;
                break;
        }
    }
}
