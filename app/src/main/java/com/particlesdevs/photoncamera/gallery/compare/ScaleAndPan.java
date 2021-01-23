package com.particlesdevs.photoncamera.gallery.compare;

import android.graphics.PointF;

import java.util.Observable;

/**
 * Simple observable class that stores current zoom and pan state
 * <p>
 * Created by Vibhor Srivastava 18 Jan 2021
 */
public class ScaleAndPan extends Observable {
    private float scale;
    private PointF center;
    private int origin;

    public ScaleAndPan() {
    }

    public int getOrigin() {
        return origin;
    }

    public void setOrigin(int origin) {
        this.origin = origin;
        notifyObservers();
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        notifyObservers();
    }

    public PointF getCenter() {
        return center;
    }

    public void setCenter(PointF center) {
        this.center = center;
        notifyObservers();
    }

    @Override
    public void notifyObservers() {
        setChanged();
        super.notifyObservers();
    }
}
