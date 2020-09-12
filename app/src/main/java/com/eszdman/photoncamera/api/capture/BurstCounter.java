package com.eszdman.photoncamera.api.capture;

public class BurstCounter
{
    private int max_burst;
    private int current_burst;
    private boolean isBurst;

    public void setMax_burst(int max_burst)
    {
        this.max_burst = max_burst;
    }

    public int getMax_burst() {
        return max_burst;
    }

    public int getCurrent_burst() {
        return current_burst;
    }

    public void setCurrent_burst(int current_burst) {
        this.current_burst = current_burst;
    }

    public boolean isBurst() {
        return isBurst;
    }

    public void setBurst(boolean burst) {
        isBurst = burst;
    }

    public void increase()
    {
        current_burst++;
    }
}
