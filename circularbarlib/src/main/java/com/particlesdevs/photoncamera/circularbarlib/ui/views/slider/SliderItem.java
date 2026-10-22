package com.particlesdevs.photoncamera.circularbarlib.ui.views.slider;

/**
 * Plain value holder for the horizontal manual slider.
 *
 * <p>{@link #text} is the full value string reported to the option bar and the
 * camera stack (e.g. "4500K", "1/125", "+0.50"). {@link #label} is what the
 * slider strip itself draws: null renders a small tick mark instead of text,
 * preserving the fine granularity of the old wheel (e.g. WB every 50K with a
 * label every 1000K) without cluttering the strip.
 */
public class SliderItem {
    public final String text;
    public final String label;
    public final int tick;
    public final double value;

    public SliderItem(String text, String label, int tick, double value) {
        this.text = text;
        this.label = label;
        this.tick = tick;
        this.value = value;
    }

    @Override
    public String toString() {
        return "SliderItem [Tick: " + tick + ", Text: " + text + ", Value: " + value + "]";
    }
}
