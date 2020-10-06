package com.eszdman.photoncamera.ui.camera.model;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

/**
 * Class that holds the ui state, for now the orientation
 *
 */
public class CameraFragmentModel extends BaseObservable  {
    private int orientation;
    private int duration;

    @Bindable
    public int getOrientation() {
        return orientation;
    }

    /**
     * set the orientation and note the binded views about the change
     * @param orientation
     */
    public void setOrientation(int orientation) {
        this.orientation = orientation;
        notifyChange();
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
