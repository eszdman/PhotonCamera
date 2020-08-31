package com.manual;


public class KnobInfo {
    public final int angleMax;
    public final int angleMin;
    public final int autoAngle;
    public final int tickMax;
    public final int tickMin;

    public KnobInfo(int angleMin2, int angleMax2, int tickMin2, int tickMax2) {
        this(angleMin2, angleMax2, tickMin2, tickMax2, 0);
    }

    public KnobInfo(int angleMin2, int angleMax2, int tickMin2, int tickMax2, int autoAngle2) {
        this.angleMax = angleMax2;
        this.angleMin = angleMin2;
        this.tickMax = tickMax2;
        this.tickMin = tickMin2;
        this.autoAngle = autoAngle2;
    }
}
