package com.particlesdevs.photoncamera.processing.render;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.Size;

import org.junit.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ParametersLensShadingTest {
    @Test
    public void binnedAndUnbinnedCropsUseSameSensorRegion() {
        Parameters full = parameters(40, 20, false);
        Parameters binned = parameters(20, 10, true);
        full.cropLensShadingMap();
        binned.cropLensShadingMap();
        assertArrayEquals(full.gainMap, binned.gainMap, 0f);
        // First and last nodes refer to sensor pixels (20,10) and (59,29).
        assertEquals(1 + 20 / 99f + 2 * 10 / 59f, full.gainMap[0], 1e-6f);
        assertEquals(1 + 59 / 99f + 2 * 29 / 59f, full.gainMap[12], 1e-6f);
        assertEquals(2, full.mapSize.x);
        assertEquals(2, full.mapSize.y);
    }

    @Test
    public void unzoomedMapIsUnchanged() {
        Parameters p = parameters(100, 60, false);
        p.isCropped = false;
        float[] original = p.gainMap;
        p.cropLensShadingMap();
        assertSame(original, p.gainMap);
    }

    @Test
    public void absentFullFrameMetadataLeavesMapUnchanged() {
        Parameters p = parameters(40, 20, false);
        p.fullRawSize = null;
        float[] original = p.gainMap;
        p.cropLensShadingMap();
        assertSame(original, p.gainMap);
    }

    @Test
    public void topLeftCropIsDetectedAndDngBoundsAreBufferLocal() {
        Parameters p = parameters(40, 20, false);
        try (MockedConstruction<Rect> rects = mockConstruction(Rect.class, (rect, context) ->
                assertEquals(Arrays.asList(0, 0, 40, 20), context.arguments()))) {
            p.setCropDetails(0, 0);
            assertTrue(p.isCropped);
            assertEquals(1, rects.constructed().size());
        }
    }

    @Test
    public void binningAloneDoesNotCountAsZoom() {
        Parameters p = parameters(50, 30, true);
        p.setCropDetails(0, 0);
        assertFalse(p.isCropped);
    }

    @Test
    public void activeArrayBordersAndSmallerRawStreamAreAccountedFor() throws Exception {
        Parameters p = parameters(40, 20, false);
        Size pixelArray = mock(Size.class);
        when(pixelArray.getWidth()).thenReturn(200);
        when(pixelArray.getHeight()).thenReturn(120);
        Rect active = mock(Rect.class);
        active.left = 10;
        active.top = 6;
        when(active.width()).thenReturn(180);
        when(active.height()).thenReturn(108);
        setField(p, "lensShadingPixelArraySize", pixelArray);
        setField(p, "lensShadingBounds", active);
        p.cropLensShadingMap();
        assertEquals(1 + (40 - 10) / 179f + 2 * (20 - 6) / 107f, p.gainMap[0], 1e-6f);
        assertEquals(1 + (118 - 10) / 179f + 2 * (58 - 6) / 107f, p.gainMap[12], 1e-6f);
    }

    private static Parameters parameters(int width, int height, boolean binned) {
        // Skip the app-settings constructor; Android Point/Rect methods are
        // stubbed in local JVM tests, so populate coordinates explicitly.
        Parameters p = mock(Parameters.class, CALLS_REAL_METHODS);
        p.rawSize = point(width, height);
        p.fullRawSize = point(100, 60);
        p.cropOrigin = point(20, 10);
        p.mapSize = point(2, 2);
        p.binned = binned;
        p.isCropped = true;
        p.cameraIntrinsic = new double[9];
        p.cameraIntrinsicRev = new double[9];
        p.gainMap = new float[]{1, 2, 3, 4, 2, 3, 4, 5, 3, 4, 5, 6, 4, 5, 6, 7};
        return p;
    }

    private static Point point(int x, int y) {
        Point point = new Point();
        point.x = x;
        point.y = y;
        return point;
    }

    private static void setField(Parameters p, String name, Object value) throws Exception {
        Field field = Parameters.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(p, value);
    }
}
