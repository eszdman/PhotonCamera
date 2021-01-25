package com.particlesdevs.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.nio.file.Path;

public abstract class ProcessorBase {
    public static float FAKE_WL = 65535.f;
    protected final ProcessingEventsListener processingEventsListener;
    protected Path dngFile;
    protected Path jpgFile;
    protected CameraCharacteristics characteristics;
    protected CaptureResult captureResult;
    protected ProcessingCallback callback;
    protected ParseExif.ExifData exifData;
    protected int cameraRotation;

    public ProcessorBase(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
    }

    public void process() {
    }

    public void IncreaseWLBL() {
        //Increase WL and BL for processing
        for (int i = 0; i < 4; i++) {
            PhotonCamera.getParameters().blackLevel[i] *= FAKE_WL / PhotonCamera.getParameters().whiteLevel;
        }
        IncreaseWL();
    }

    public void IncreaseWL() {
        PhotonCamera.getParameters().whiteLevel = (int) (FAKE_WL);
    }

    public interface ProcessingCallback {
        void onStarted();

        void onFailed();

        void onFinished();
    }

}
