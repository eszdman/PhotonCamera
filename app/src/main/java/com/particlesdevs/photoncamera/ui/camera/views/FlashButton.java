package com.particlesdevs.photoncamera.ui.camera.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

public class FlashButton extends AppCompatButton {
    private static final int[] STATE_FLASH_ON = {R.attr.flash_on};
    private static final int[] STATE_FLASH_OFF = {R.attr.flash_off};
    private static final int[] STATE_FLASH_AUTO = {R.attr.flash_auto};
    private static final int[] STATE_FLASH_TORCH = {R.attr.flash_torch};
    private boolean flash_off;
    private boolean flash_on;
    private boolean flash_auto;
    private boolean flash_torch;

    public FlashButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFlashValueState(PreferenceKeys.getAeMode());
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 4);
        if (flash_off)
            mergeDrawableStates(drawableState, STATE_FLASH_OFF);
        if (flash_on)
            mergeDrawableStates(drawableState, STATE_FLASH_ON);
        if (flash_auto)
            mergeDrawableStates(drawableState, STATE_FLASH_AUTO);
        if (flash_torch)
            mergeDrawableStates(drawableState, STATE_FLASH_TORCH);
        return drawableState;
    }

    public void setFlashValueState(int flashmode) {
        flash_off = false;
        flash_on = false;
        flash_auto = false;
        flash_torch = false;
        switch (flashmode) {
            case 0:
                flash_torch = true;
                break;
            case 1:
                flash_off = true;
                break;
            case 2:
                flash_auto = true;
                break;
            case 3:
                flash_on = true;
                break;
        }
    }
}
