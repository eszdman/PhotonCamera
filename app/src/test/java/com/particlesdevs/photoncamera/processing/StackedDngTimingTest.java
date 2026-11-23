//
// Created by eszdman on 20.11.2026.
//

package com.particlesdevs.photoncamera.processing;

import com.particlesdevs.photoncamera.processing.render.Parameters;

import org.junit.Test;
import org.mockito.MockedStatic;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class StackedDngTimingTest {
    @Test
    public void dngAndRenderedStillReportTheStagesCompletedForEachOutput() {
        Path path = Paths.get("shot.dng");
        ByteBuffer pixels = ByteBuffer.allocate(2);
        Parameters parameters = mock(Parameters.class, CALLS_REAL_METHODS);
        parameters.mergeTimeNs = 250_000_000L;
        try (MockedStatic<ImageSaver.Util> saver = mockStatic(ImageSaver.Util.class)) {
            saver.when(() -> ImageSaver.Util.saveSingleRaw(path, pixels, parameters)).thenAnswer(call -> {
                assertEquals("TotalProcessingTime=250ms\n MergeTime=250ms\n PostProcessingTime=0ms",
                        parameters.processingTimeDescription());
                return true;
            });
            saver.when(() -> ImageSaver.Util.saveStackedRaw(path, pixels, parameters))
                    .thenCallRealMethod();
            assertTrue(ImageSaver.Util.saveStackedRaw(path, pixels, parameters));
        }
        parameters.postProcessingTimeNs = 85_000_000L;
        assertEquals("TotalProcessingTime=335ms\n MergeTime=250ms\n PostProcessingTime=85ms",
                parameters.processingTimeDescription());
    }

    @Test
    public void renderedSingleFrameShowsZeroMergeTime() {
        Parameters parameters = mock(Parameters.class, CALLS_REAL_METHODS);
        parameters.postProcessingTimeNs = 85_000_000L;
        assertEquals("TotalProcessingTime=85ms\n MergeTime=0ms\n PostProcessingTime=85ms",
                parameters.processingTimeDescription());
    }
}
