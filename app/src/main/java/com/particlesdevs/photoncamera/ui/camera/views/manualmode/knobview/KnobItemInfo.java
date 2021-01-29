package com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview;

import android.graphics.drawable.Drawable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class KnobItemInfo implements Comparable<KnobItemInfo> {
    public final Drawable drawable;
    public final String text;
    public final int tick;
    public final double value;
    public boolean isSelected;
    public double rotationCenter;
    public double rotationLeft;
    public double rotationRight;

    public KnobItemInfo(Drawable drawable2, String text, int tick, double value) {
        this.drawable = drawable2;
        this.text = text;
        this.tick = tick;
        this.value = value;
    }

    public static List<KnobItemInfo> createItemList(Drawable[] drawables, String[] texts, int[] ticks, double[] values) {
        List<KnobItemInfo> items = null;
        if (!(drawables == null || texts == null || ticks == null || values == null || drawables.length != texts.length || texts.length != ticks.length || ticks.length != values.length)) {
            items = new ArrayList<>();
            for (int i = 0; i < drawables.length; i++) {
                items.add(new KnobItemInfo(drawables[i], texts[i], ticks[i], values[i]));
            }
        }
        return items;
    }

    @Override
    public int compareTo(KnobItemInfo another) {
        if (Math.abs(this.rotationCenter - another.rotationCenter) < 0.01d) {
            return 0;
        }
        if (this.rotationCenter > another.rotationCenter) {
            return 1;
        }
        return -1;
    }

    @Override
    public @NotNull String toString() {
        return "KnobItemInfo [Tick: " + this.tick + ", Text: " + this.text + ", Value: " + this.value + ", Rotation: " + this.rotationCenter + ", Rotation left: " + this.rotationLeft + ", Rotation right: " + this.rotationRight + "]";
    }
}
