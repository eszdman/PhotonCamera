package com.eszdman.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;

import java.nio.file.Path;

public abstract class ImageProcessorAbstract {
    protected final ProcessingEventsListener processingEventsListener;
    protected Path dngFile;
    protected Path jpgFile;
    protected CameraCharacteristics characteristics;
    protected CaptureResult captureResult;
    protected ProcessingCallback callback;

    public ImageProcessorAbstract(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
    }

    public void process() {
    }


    public interface ProcessingCallback {
        void onFinished();
    }
}
