package com.particlesdevs.photoncamera.ui.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CustomOrientationEventListenerTest {
    private int rotation = 0;
    public boolean orientationPosition(int orientation, int value, int ROTATION) {
        int angleThreshold = 20;
        if (orientation < 20 || orientation >= 340)
            return orientation >= (360 + value - angleThreshold) || orientation < (value + angleThreshold) && rotation != ROTATION;
        else
            return orientation >= (value - angleThreshold) && orientation < (value + angleThreshold) && rotation != ROTATION;
    }
    @Test
    public void testOrientationPosition() {
        int orientation = 0;
        int value = 100;
        int ROTATION = 1;
        boolean expectedResult = true;
        // Test case 1: orientation < 20
        orientation = 10;
        boolean result = orientationPosition(orientation, value, ROTATION);
        assertEquals(expectedResult, result);

        // Test case 2: orientation >= 340
        orientation = 350;
        expectedResult = false;
        result = orientationPosition(orientation, value, ROTATION);
        assertEquals(expectedResult, result);

        // Test case 3: value - angleThreshold <= orientation < value + angleThreshold
        orientation = 95;
        expectedResult = true;

        result = orientationPosition(orientation, value, ROTATION);
        assertEquals(expectedResult, result);

        // Test case 4: orientation < value - angleThreshold
        orientation = 70;
        expectedResult = false;

        result = orientationPosition(orientation, value, ROTATION);
        assertEquals(expectedResult, result);

        // Test case 5: orientation >= value + angleThreshold
        orientation = 145;
        expectedResult = false;

        result = orientationPosition(orientation, value, ROTATION);
        assertEquals(expectedResult, result);

        // Test case 6: rotation != ROTATION
        orientation = 150;
        rotation = 1;
        expectedResult = false;

        result = orientationPosition(orientation, value, ROTATION);
        assertEquals(expectedResult, result);
    }
}