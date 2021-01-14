package com.particlesdevs.photoncamera.manual;


public class KnobInfo {
    public final int angleMax;
    public final int angleMin;
    public final int autoAngle;
    public final int tickMax;
    public final int tickMin;

    public KnobInfo(int angleMin, int angleMax, int tickMin, int tickMax) {
        this(angleMin, angleMax, tickMin, tickMax, 0);
    }

    public KnobInfo(int angleMin, int angleMax, int tickMin, int tickMax, int autoAngle) {
        this.angleMax = angleMax;
        this.angleMin = angleMin;
        this.tickMax = tickMax;
        this.tickMin = tickMin;
        this.autoAngle = autoAngle;
    }
}
