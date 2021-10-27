package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.ImageReader;
import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.SaverImplementation;
import android.media.Image;


import java.util.ArrayList;

public class DebugSender extends SaverImplementation {
    public DebugSender(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void addRAW16(Image image) {
        image.getFormat();
        IMAGE_BUFFER.add(image);
    }

    public void runRaw(ImageReader imageReader, CameraCharacteristics characteristics, CaptureResult captureResult, ArrayList<GyroBurst> burstShakiness, int cameraRotation) {
        super.runRaw(imageReader,characteristics,captureResult,burstShakiness,cameraRotation);
        Log.d("DebugSender","RunDebug sender");
        PhotonCamera.getDebugger().debugClient.sendRaw(IMAGE_BUFFER.get(0));
        processingEventsListener.onProcessingFinished("Saved Unprocessed RAW");
        IMAGE_BUFFER.clear();
        clearImageReader(imageReader);
    }

}
