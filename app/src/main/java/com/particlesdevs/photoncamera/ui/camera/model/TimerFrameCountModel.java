package com.particlesdevs.photoncamera.ui.camera.model;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

public class TimerFrameCountModel extends BaseObservable {
    private String frameCount;
    private String timerCount;

    @Bindable
    public String getFrameCount() {
        return frameCount;
    }

    public void setFrameCount(String frameCount) {
        this.frameCount = frameCount;
        notifyChange();
    }

    @Bindable
    public String getTimerCount() {
        return timerCount;
    }


    public void setTimerCount(String timerCount) {
        this.timerCount = timerCount;
        notifyChange();
    }
}
