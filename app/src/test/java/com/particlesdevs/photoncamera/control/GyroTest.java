package com.particlesdevs.photoncamera.control;

import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GyroTest {
    private static final long MS = 1_000_000;

    private CameraCharacteristics camera() {
        CameraCharacteristics camera = mock(CameraCharacteristics.class);
        when(camera.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE))
                .thenReturn(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME);
        return camera;
    }

    private CaptureResult result(long timestamp, long exposure, long skew) {
        CaptureResult result = mock(CaptureResult.class);
        // The JVM's mockable Android jar leaves static Key objects null. Supply
        // the three metadata reads in order instead of stubbing equal null keys.
        when(result.<Long>get(any())).thenReturn(timestamp, exposure, skew);
        return result;
    }

    private Gyro recorder() {
        GyroHistory history = new GyroHistory(64);
        for (int i = 0; i <= 20; i++) history.add(i*10*MS,0,0,1);
        return new Gyro(mock(SensorManager.class),history);
    }

    @Test public void overlappingFramesUseResultWindowsAndKeepFinalFrame() {
        Gyro gyro = recorder();
        ArrayList<GyroBurst> records = new ArrayList<>();
        gyro.PrepareGyroBurst(records);
        gyro.CaptureGyroBurst(10*MS,40*MS);
        gyro.CaptureGyroBurst(30*MS,40*MS);
        gyro.CaptureGyroBurst(30*MS,40*MS); // duplicate start must not append a frame
        gyro.recordCaptureResult(result(30*MS,20*MS,10*MS),camera());
        gyro.recordCaptureResult(result(10*MS,30*MS,20*MS),camera());
        gyro.CompleteSequence();
        assertEquals(2,records.size());
        assertEquals(2,gyro.capturingNumber);
        assertEquals(10*MS,records.get(0).frameTimestamp);
        assertEquals(35*MS,records.get(0).orientationTimestamp);
        assertEquals(30*MS,records.get(1).frameTimestamp);
        assertEquals(45*MS,records.get(1).orientationTimestamp);
        assertTrue(records.get(1).orientationValid);
        gyro.CompleteSequence();
        assertEquals(2,records.size());
    }

    @Test public void zslUsesEachFramesMetadataAndMissingRecordStaysInvalid() {
        Gyro gyro = recorder();
        gyro.recordCaptureResult(result(10*MS,10*MS,0),camera());
        gyro.recordCaptureResult(result(30*MS,20*MS,10*MS),camera());
        ArrayList<GyroBurst> records = new ArrayList<>();
        gyro.buildZslBurstShakiness(new long[]{30*MS,10*MS,80*MS},records);
        gyro.CompleteSequence();
        assertEquals(3,records.size());
        assertEquals(45*MS,records.get(0).orientationTimestamp);
        assertEquals(15*MS,records.get(1).orientationTimestamp);
        assertFalse(records.get(2).orientationValid);
    }
}
