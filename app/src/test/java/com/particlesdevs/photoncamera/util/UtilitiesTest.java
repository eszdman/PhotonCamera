package com.particlesdevs.photoncamera.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class UtilitiesTest {
    @Test
    public void testConvertToFloatArray() {
        int[] input = {1, 2, 3, 4, 5};
        float[] expected = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        float[] result = Utilities.convertToFloatArray(input);
        assertArrayEquals(expected, result);
    }

    @Test
    public void testFindMaxValue() {
        float[] input = {1.2f, 2.5f, 7.2f, 3.7f, 12.3f, 5.9f};
        float expectResult = 12.3f;
        float result = Utilities.findMaxValue(input);
        assertEquals(expectResult, result, 0.001f);
    }

    @Test
    public void testLinearRegressionK() {
        // Input data set
        float[] input = {1.2f, 2.5f, 3.7f, 4.1f, 5.9f};

        // expect value
        float expectedK = 8.99f;

        // call method
        float result = Utilities.linearRegressionK(input);

        // valid result
        assertEquals(expectedK, result, 0.001f);
    }

    @Test
    public void testLinearRegressionC() {
        // input data
        float[] input = {1.2f, 2.5f, 3.7f, 4.1f, 5.9f};

        // Expected y-intercept value
        float expectedC = -0.445f;

        // call method
        float result = Utilities.linearRegressionC(input);

        // valid result
        assertEquals(expectedC, result, 0.001f);
    }

}