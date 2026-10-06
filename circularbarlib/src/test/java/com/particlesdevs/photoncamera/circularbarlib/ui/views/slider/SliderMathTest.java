package com.particlesdevs.photoncamera.circularbarlib.ui.views.slider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SliderMathTest {
    private static final float PITCH = 72f;
    private static final int COUNT = 5;

    @Test
    public void maxScrollScalesWithCount() {
        assertEquals(0f, SliderMath.maxScroll(0, PITCH), 0.001f);
        assertEquals(0f, SliderMath.maxScroll(1, PITCH), 0.001f);
        assertEquals(4f * PITCH, SliderMath.maxScroll(COUNT, PITCH), 0.001f);
    }

    @Test
    public void clampScrollKeepsBounds() {
        assertEquals(0f, SliderMath.clampScroll(-10f, COUNT, PITCH), 0.001f);
        assertEquals(4f * PITCH, SliderMath.clampScroll(10f * PITCH, COUNT, PITCH), 0.001f);
        assertEquals(2f * PITCH, SliderMath.clampScroll(2f * PITCH, COUNT, PITCH), 0.001f);
    }

    @Test
    public void snapIndexRoundsToNearest() {
        assertEquals(0, SliderMath.snapIndex(0f, PITCH, COUNT));
        assertEquals(0, SliderMath.snapIndex(PITCH * 0.4f, PITCH, COUNT));
        assertEquals(1, SliderMath.snapIndex(PITCH * 0.6f, PITCH, COUNT));
        assertEquals(2, SliderMath.snapIndex(PITCH * 2f, PITCH, COUNT));
        assertEquals(COUNT - 1, SliderMath.snapIndex(PITCH * 10f, PITCH, COUNT));
        assertEquals(0, SliderMath.snapIndex(-PITCH, PITCH, COUNT));
    }

    @Test
    public void scrollForIndexCentersSelection() {
        assertEquals(0f, SliderMath.scrollForIndex(0, PITCH, COUNT), 0.001f);
        assertEquals(2f * PITCH, SliderMath.scrollForIndex(2, PITCH, COUNT), 0.001f);
        assertEquals(4f * PITCH, SliderMath.scrollForIndex(99, PITCH, COUNT), 0.001f);
        assertEquals(0f, SliderMath.scrollForIndex(-3, PITCH, COUNT), 0.001f);
    }

    @Test
    public void sliderItemKeepsTextLabelTickValue() {
        SliderItem item = new SliderItem("4500K", "4K", 7, 4500.0);
        assertEquals("4500K", item.text);
        assertEquals("4K", item.label);
        assertEquals(7, item.tick);
        assertEquals(4500.0, item.value, 0.0001);
    }

    @Test
    public void pressGainRestsAtOne() {
        assertEquals(1f, SliderMath.gainForPress(0f, 120f), 0.001f);
        assertEquals(1f, SliderMath.gainForPress(-50f, 120f), 0.001f);
        assertEquals(1f, SliderMath.gainForPress(60f, 0f), 0.001f);
    }

    @Test
    public void pressGainCapsAtMax() {
        assertEquals(SliderMath.MAX_GAIN, SliderMath.gainForPress(120f, 120f), 0.001f);
        assertEquals(SliderMath.MAX_GAIN, SliderMath.gainForPress(500f, 120f), 0.001f);
    }

    @Test
    public void pressGainRisesMonotonically() {
        float quarter = SliderMath.gainForPress(30f, 120f);
        float half = SliderMath.gainForPress(60f, 120f);
        float threeQuarter = SliderMath.gainForPress(90f, 120f);
        assertTrue(quarter > 1f && half > quarter && threeQuarter > half
                && SliderMath.MAX_GAIN >= threeQuarter);
        // Smoothstep midpoint lands halfway between 1x and max.
        assertEquals(1f + (SliderMath.MAX_GAIN - 1f) / 2f, half, 0.001f);
    }
}
