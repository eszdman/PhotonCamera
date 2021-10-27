package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.SaverImplementation;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;

import java.nio.file.Path;
import java.util.ArrayList;

public class DebugSender extends SaverImplementation {


    public DebugSender(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }
    void addRAW16(Image image) {
        image.getFormat();
        IMAGE_BUFFER.add(image);
    }
    public void runRaw(ImageReader imageReader, CameraCharacteristics characteristics, CaptureResult captureResult, ArrayList<GyroBurst> burstShakiness, int cameraRotation) {
        super.runRaw(imageReader,characteristics,captureResult,burstShakiness,cameraRotation);
        Path dngFile = ImageSaver.Util.newDNGFilePath();
        boolean imageSaved = ImageSaver.Util.saveSingleRaw(dngFile, IMAGE_BUFFER.get(0),
                characteristics, captureResult, cameraRotation);
        processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
        PhotonCamera.getDebugger().debugClient.sendRaw(IMAGE_BUFFER.get(0));
        processingEventsListener.onProcessingFinished("Saved Unprocessed RAW");
        IMAGE_BUFFER.clear();
        clearImageReader(imageReader);
    }

}
