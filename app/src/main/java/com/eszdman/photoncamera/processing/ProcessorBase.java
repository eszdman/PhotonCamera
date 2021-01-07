package com.eszdman.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;

import com.eszdman.photoncamera.app.PhotonCamera;

import java.nio.file.Path;

public abstract class ProcessorBase {
    protected final ProcessingEventsListener processingEventsListener;
    protected Path dngFile;
    protected Path jpgFile;
    protected CameraCharacteristics characteristics;
    protected CaptureResult captureResult;
    protected ProcessingCallback callback;
    public static float FAKE_WL = 65535.f;
    public ProcessorBase(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
    }
    public void process() {}

    public interface ProcessingCallback {
        void onFinished();
    }

    public void IncreaseWLBL(){
        //Increase WL and BL for processing
        for (int i = 0; i < 4; i++) {
            PhotonCamera.getParameters().blackLevel[i] *= FAKE_WL / PhotonCamera.getParameters().whiteLevel;
        }
        PhotonCamera.getParameters().whiteLevel = (int) (FAKE_WL);
    }

}
