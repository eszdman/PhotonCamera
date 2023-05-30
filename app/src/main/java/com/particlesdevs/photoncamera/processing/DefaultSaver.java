package com.particlesdevs.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import com.hunter.library.debug.HunterDebug;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.processor.HdrxProcessor;
import com.particlesdevs.photoncamera.processing.processor.UnlimitedProcessor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class DefaultSaver extends SaverImplementation {
    private static final String TAG = "DefaultSaver";
    final UnlimitedProcessor mUnlimitedProcessor;
    final HdrxProcessor hdrxProcessor;

    public DefaultSaver(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
        this.hdrxProcessor = new HdrxProcessor(processingEventsListener);
        this.mUnlimitedProcessor = new UnlimitedProcessor(processingEventsListener);
    }

    @HunterDebug
    public void runRaw(ImageReader imageReader, CameraCharacteristics characteristics, CaptureResult captureResult, ArrayList<GyroBurst> burstShakiness, int cameraRotation) {
        super.runRaw(imageReader, characteristics, captureResult, burstShakiness, cameraRotation);
        //Wait for one frame at least.
        Log.d(TAG,"Size:"+IMAGE_BUFFER.size());
        while (IMAGE_BUFFER.size() < 1){
            try {

                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (PhotonCamera.getSettings().frameCount == 1) {
            Path dngFile = ImagePath.newDNGFilePath();
            Log.d(TAG, "Size:" + IMAGE_BUFFER.size());
            boolean imageSaved = ImageSaver.Util.saveSingleRaw(dngFile, IMAGE_BUFFER.get(0),
                    characteristics, captureResult, cameraRotation);
            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
            processingEventsListener.onProcessingFinished("Saved Unprocessed RAW");
            IMAGE_BUFFER.clear();
            return;
        }
        Path dngFile = ImagePath.newDNGFilePath();
        Path jpgFile = ImagePath.newJPGFilePath();
        //Remove broken images
            /*for(int i =0; i<IMAGE_BUFFER.size();i++){
                try{
                    IMAGE_BUFFER.get(i).getFormat();
                } catch (IllegalStateException e){
                    IMAGE_BUFFER.remove(i);
                    i--;
                    Log.d(TAG,"IMGBufferSize:"+IMAGE_BUFFER.size());
                    e.printStackTrace();
                }
            }*/
        hdrxProcessor.configure(
                PhotonCamera.getSettings().alignAlgorithm,
                PhotonCamera.getSettings().rawSaver,
                PhotonCamera.getSettings().selectedMode
        );
        hdrxProcessor.start(
                dngFile,
                jpgFile,
                ParseExif.parse(captureResult),
                burstShakiness,
                IMAGE_BUFFER,
                imageReader.getImageFormat(),
                cameraRotation,
                characteristics,
                captureResult,
                processingCallback
        );
        IMAGE_BUFFER.clear();
    }

    public void unlimitedStart(ImageReader imageReader, CameraCharacteristics characteristics, CaptureResult captureResult, int cameraRotation) {
        super.unlimitedStart(imageReader, characteristics, captureResult, cameraRotation);
        Path dngFile = ImagePath.newDNGFilePath();
        Path jpgFile = ImagePath.newJPGFilePath();

        mUnlimitedProcessor.configure(PhotonCamera.getSettings().rawSaver);
        mUnlimitedProcessor.unlimitedStart(
                dngFile,
                jpgFile,
                ParseExif.parse(captureResult),
                characteristics,
                captureResult,
                cameraRotation,
                processingCallback
        );
    }

    public void unlimitedEnd() {
        mUnlimitedProcessor.unlimitedEnd();
    }
}
