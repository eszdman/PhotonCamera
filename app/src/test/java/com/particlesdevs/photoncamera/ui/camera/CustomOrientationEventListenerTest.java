package com.particlesdevs.photoncamera.ui.camera;

import android.view.OrientationEventListener;
import android.view.Surface;

import org.junit.Test;

import static org.junit.Assert.*;

public class CustomOrientationEventListenerTest {
    private int rotation = 0;
    private int prevOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    public boolean orientationPosition(int orientation, int value, int ROTATION) {
        int angleThreshold = 20;
        if (orientation < 20 || orientation >= 340)
            return orientation >= (360 + value - angleThreshold) || orientation < (value + angleThreshold) && rotation != ROTATION;
        else
            return orientation >= (value - angleThreshold) && orientation < (value + angleThreshold) && rotation != ROTATION;
    }

    public void onOrientationChanged(int orientation) {
        int currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

        int ROTATION_0 = 1;
        int ROTATION_90 = 2;
        int ROTATION_180 = 3;
        int ROTATION_270 = 4;

        // Express the process of determining the range of angles more clearly
        if (orientationPosition(orientation, 0, ROTATION_0)) {
            currentOrientation = Surface.ROTATION_0;
            rotation = ROTATION_0;
        } else if (orientationPosition(orientation, 90, ROTATION_90)) {
            currentOrientation = Surface.ROTATION_90;
            rotation = ROTATION_90;
        } else if (orientationPosition(orientation, 180, ROTATION_180)) {
            currentOrientation = Surface.ROTATION_180;
            rotation = ROTATION_180;
        } else if (orientationPosition(orientation, 270, ROTATION_270)) {
            currentOrientation = Surface.ROTATION_270;
            rotation = ROTATION_270;
        }
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

    @Test
    public void testonOrientationChanged() {
        int ROTATION_0 = 1;
        int ROTATION_90 = 2;
        int ROTATION_180 = 3;
        int ROTATION_270 = 4;

        // Test for rotation = ROTATION_0
        assertTrue(orientationPosition(0, 0, ROTATION_0));
        assertFalse(orientationPosition(30, 0, ROTATION_0));

        // Test for rotation = ROTATION_90
        assertTrue(orientationPosition(90, 90, ROTATION_90));
        assertFalse(orientationPosition(120, 90, ROTATION_90));

        // Test for rotation = ROTATION_180
        assertTrue(orientationPosition(180, 180, ROTATION_180));
        assertFalse(orientationPosition(210, 180, ROTATION_180));

        // Test for rotation = ROTATION_270
        assertTrue(orientationPosition(270, 270, ROTATION_270));
        assertFalse(orientationPosition(300, 270, ROTATION_270));
    }


}