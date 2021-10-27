package com.particlesdevs.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;

import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;

import java.util.ArrayList;

public class SaverImplementation {
    private static final String TAG = "SaverImplementation";
    public static final ArrayList<Image> IMAGE_BUFFER = new ArrayList<>();
    private ImageReader imageReader;
    public final ProcessingEventsListener processingEventsListener;

    final ProcessorBase.ProcessingCallback processingCallback = new ProcessorBase.ProcessingCallback() {
        @Override
        public void onStarted() {
            CaptureController.isProcessing = true;
        }

        @Override
        public void onFailed() {
            onFinished();
        }

        @Override
        public void onFinished() {
            clearImageReader(imageReader);
            CaptureController.isProcessing = false;
        }
    };
    public SaverImplementation(ProcessingEventsListener processingEventsListener){
        this.processingEventsListener = processingEventsListener;
    }
    void addJPEG(Image image){
        image.close();
    }
    void addYUV(Image image){
        image.close();
    }
    void addRAW16(Image image){
        image.close();
    }
    void addRAW10(Image image){
        image.close();
    }
    protected void clearImageReader(ImageReader reader) {
        reader.close();
    }

    public void runRaw(ImageReader imageReader, CameraCharacteristics characteristics, CaptureResult captureResult, ArrayList<GyroBurst> burstShakiness, int cameraRotation) {
        this.imageReader = imageReader;
    }
    public void unlimitedStart(ImageReader imageReader, CameraCharacteristics characteristics, CaptureResult captureResult, int cameraRotation) {
        this.imageReader = imageReader;
    }
    public void unlimitedEnd(){
    }
}
