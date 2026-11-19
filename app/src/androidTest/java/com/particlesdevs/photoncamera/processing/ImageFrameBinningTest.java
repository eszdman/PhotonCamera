package com.particlesdevs.photoncamera.processing;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.particlesdevs.photoncamera.util.Allocator;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ImageFrameBinningTest {
    @Test
    public void binnedCapture_retainsModeAndPacksFourTapRangeExactly() {
        ByteBuffer sensor = ByteBuffer.allocateDirect(8 * 8 * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 8 * 8; i++) sensor.putShort((short) 400);
        sensor.rewind();
        boolean previousMode = Allocator.binning;
        ImageFrame frame = null;
        try {
            // The buffer's mode is explicit, regardless of the live global setting.
            Allocator.binning = false;
            frame = new ImageFrame(sensor, android.graphics.ImageFormat.RAW_SENSOR,
                    8, 16, 0, sensor.capacity(), 0, true);
            frame.width = frame.height = 4;
            assertTrue(frame.binned);
            ImageFrame.packBurstAtArrival(frame, 1023, true);
            assertEquals(12, frame.packedBits);
            try (ImageFrame.Upload upload = frame.upload()) {
                ByteBuffer pixels = upload.buffer.duplicate().order(ByteOrder.nativeOrder());
                for (int i = 0; i < 4 * 4; i++) assertEquals(1600, pixels.getShort(i * 2) & 0xffff);
            }
        } finally {
            if (frame != null && frame.buffer != null) Allocator.free(frame.buffer);
            ImageFrame.releaseUploadStaging();
            Allocator.binning = previousMode;
        }
    }
}
