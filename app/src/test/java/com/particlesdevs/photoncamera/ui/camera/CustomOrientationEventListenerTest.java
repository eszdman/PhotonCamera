package com.particlesdevs.photoncamera.ui.camera;

import android.content.Context;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CustomOrientationEventListenerTest {

    public class TestOrientationEventListener extends CustomOrientationEventListener {

        private boolean onSimpleOrientationChangedCalled = false;

        public TestOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onSimpleOrientationChanged(int orientation) {
            onSimpleOrientationChangedCalled = true;
        }

        public boolean isOnSimpleOrientationChangedCalled() {
            return onSimpleOrientationChangedCalled;
        }
    }

    @Test
    public void testOnOrientationChanged_CallsOnSimpleOrientationChanged() {
        TestOrientationEventListener listener = new TestOrientationEventListener(null); // Pass null for context

        int orientation = 30;

        listener.onOrientationChanged(orientation);

        assertTrue(listener.isOnSimpleOrientationChangedCalled());
    }
}