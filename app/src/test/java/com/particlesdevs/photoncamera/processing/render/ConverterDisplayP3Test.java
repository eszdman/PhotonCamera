package com.particlesdevs.photoncamera.processing.render;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConverterDisplayP3Test {

    @Test
    public void srgbPrimariesMapToKnownDisplayP3Values() {
        float[] red = new float[3];
        Converter.map(Converter.sRGBtoDisplayP3, new float[]{1f, 0f, 0f}, red);
        assertArrayEquals(new float[]{0.8224621f, 0.0331942f, 0.0170826f},
                red, 2e-5f);

        float[] green = new float[3];
        Converter.map(Converter.sRGBtoDisplayP3, new float[]{0f, 1f, 0f}, green);
        assertArrayEquals(new float[]{0.1775379f, 0.9668058f, 0.0723974f},
                green, 2e-5f);

        float[] blue = new float[3];
        Converter.map(Converter.sRGBtoDisplayP3, new float[]{0f, 0f, 1f}, blue);
        assertArrayEquals(new float[]{0f, 0f, 0.9105199f}, blue, 2e-5f);
    }

    @Test
    public void whiteStaysNeutral() {
        float[] white = new float[3];
        Converter.map(Converter.sRGBtoDisplayP3, new float[]{1f, 1f, 1f}, white);
        assertArrayEquals(new float[]{1f, 1f, 1f}, white, 2e-5f);
    }

    @Test
    public void roundTripIsIdentity() {
        float[] inverse = new float[9];
        assertTrue(Converter.invert(Converter.sRGBtoDisplayP3, inverse));
        float[] product = new float[9];
        Converter.multiply(inverse, Converter.sRGBtoDisplayP3, product);
        assertArrayEquals(new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, product, 2e-5f);
    }

    @Test
    public void toDisplayP3PreMultipliesWithoutMutatingInput() {
        float[] input = new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        float[] original = input.clone();
        float[] out = Converter.toDisplayP3(input);
        float[] expected = new float[9];
        Converter.multiply(Converter.sRGBtoDisplayP3, input, expected);
        assertArrayEquals(expected, out, 1e-6f);
        assertArrayEquals(original, input, 0f);
    }

    @Test
    public void p3LumaIsNormalized() {
        float sum = Converter.sDisplayP3Luma[0]
                + Converter.sDisplayP3Luma[1]
                + Converter.sDisplayP3Luma[2];
        assertEquals(1f, sum, 1e-6f);
    }
}
