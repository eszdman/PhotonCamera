package com.particlesdevs.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

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

public class DefaultSaver extends SaverImplementation{
    private static final String TAG = "DefaultSaver";
    final UnlimitedProcessor mUnlimitedProcessor;
    final HdrxProcessor hdrxProcessor;

    public DefaultSaver(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
        this.hdrxProcessor = new HdrxProcessor(processingEventsListener);
        this.mUnlimitedProcessor = new UnlimitedProcessor(processingEventsListener);
    }
    public void addRAW16(Image image) {
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) {
            mUnlimitedProcessor.unlimitedCycle(image);
        } else {
            Log.d(TAG, "start buffer size:" + IMAGE_BUFFER.size());
            image.getFormat();
            IMAGE_BUFFER.add(image);
        }
    }

    public void addJPEG(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        try {
            IMAGE_BUFFER.add(image);
            byte[] bytes = new byte[buffer.remaining()];
            if (IMAGE_BUFFER.size() == PhotonCamera.getCaptureController().mMeasuredFrameCnt && PhotonCamera.getSettings().frameCount != 1) {
                Path jpgPath = ImageSaver.Util.newJPGFilePath();
                buffer.duplicate().get(bytes);
                Files.write(jpgPath, bytes);

//                hdrxProcessor.start(dngFile, jpgFile, IMAGE_BUFFER, mImage.getFormat(),
//                        CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult,
//                        () -> clearImageReader(mReader));

                IMAGE_BUFFER.clear();
            }
            if (PhotonCamera.getSettings().frameCount == 1) {
                Path jpgPath = ImageSaver.Util.newJPGFilePath();
                IMAGE_BUFFER.clear();
                buffer.get(bytes);
                Files.write(jpgPath, bytes);
                image.close();
                processingEventsListener.onProcessingFinished("JPEG: Single Frame, Not Processed!");
                processingEventsListener.notifyImageSavedStatus(true, jpgPath);
            }
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void addYUV(Image image) {
        Log.d(TAG, "start buffersize:" + IMAGE_BUFFER.size());
        IMAGE_BUFFER.add(image);
        if (IMAGE_BUFFER.size() == PhotonCamera.getCaptureController().mMeasuredFrameCnt && PhotonCamera.getSettings().frameCount != 1) {

//            hdrxProcessor.start(dngFile, jpgFile, IMAGE_BUFFER, mImage.getFormat(),
//                        CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult,
//                        () -> clearImageReader(mReader));

            IMAGE_BUFFER.clear();
        }
        if (PhotonCamera.getSettings().frameCount == 1) {
            IMAGE_BUFFER.clear();
            processingEventsListener.onProcessingFinished("YUV: Single Frame, Not Processed!");

        }
    }
    public void runRaw(ImageReader imageReader, CameraCharacteristics characteristics, CaptureResult captureResult, ArrayList<GyroBurst> burstShakiness, int cameraRotation) {
        super.runRaw(imageReader,characteristics,captureResult,burstShakiness,cameraRotation);
        if (PhotonCamera.getSettings().frameCount == 1) {
            Path dngFile = ImageSaver.Util.newDNGFilePath();
            boolean imageSaved = ImageSaver.Util.saveSingleRaw(dngFile, IMAGE_BUFFER.get(0),
                    characteristics, captureResult, cameraRotation);
            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
            processingEventsListener.onProcessingFinished("Saved Unprocessed RAW");
            IMAGE_BUFFER.clear();
            clearImageReader(imageReader);
            return;
        }
        Path dngFile = ImageSaver.Util.newDNGFilePath();
        Path jpgFile = ImageSaver.Util.newJPGFilePath();
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
    public void unlimitedStart(ImageReader imageReader,CameraCharacteristics characteristics, CaptureResult captureResult, int cameraRotation) {
        super.unlimitedStart(imageReader,characteristics,captureResult,cameraRotation);
        Path dngFile = ImageSaver.Util.newDNGFilePath();
        Path jpgFile = ImageSaver.Util.newJPGFilePath();

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
    public void unlimitedEnd(){
        mUnlimitedProcessor.unlimitedEnd();
    }
}
