package com.particlesdevs.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;

import com.particlesdevs.photoncamera.processing.processor.RawVideoProcessor;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.processor.HdrxProcessor;
import com.particlesdevs.photoncamera.processing.processor.UnlimitedProcessor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.LockSupport;

public class DefaultSaver extends SaverImplementation {
    private static final String TAG = "DefaultSaver";
    final UnlimitedProcessor mUnlimitedProcessor;
    final RawVideoProcessor mRawVideoProcessor;
    final HdrxProcessor hdrxProcessor;

    public DefaultSaver(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
        this.hdrxProcessor = new HdrxProcessor(processingEventsListener);
        this.mUnlimitedProcessor = new UnlimitedProcessor(processingEventsListener);
        this.mRawVideoProcessor = new RawVideoProcessor(processingEventsListener);
    }

    public void runRaw(int imageFormat, CameraCharacteristics characteristics, CaptureResult captureResult, CaptureRequest captureRequest, ArrayList<GyroBurst> burstShakiness, int cameraRotation, HashMap<Long, Double> exposures) {
        super.runRaw(imageFormat, characteristics, captureResult,captureRequest, burstShakiness, cameraRotation, exposures);
        //Wait for one frame at least. Park (not spin): the producers are
        //camera threads that don't all notify, so wait/notify would risk a
        //missed wakeup; parking yields the core with identical semantics.
        Log.d(TAG, "Acquiring:" + IMAGE_BUFFER.size());
        while (bufferLock || IMAGE_BUFFER.isEmpty()) {
            LockSupport.parkNanos(100_000);
        }
        Log.d(TAG, "Acquired:" + IMAGE_BUFFER.size());
        bufferLock = true;
        Log.d(TAG,"Size:"+IMAGE_BUFFER.size());
        /*if (PhotonCamera.getSettings().frameCount == 1) {
            Path dngFile = ImagePath.newDNGFilePath();
            Log.d(TAG, "Size:" + IMAGE_BUFFER.size());
            boolean imageSaved = ImageSaver.Util.saveSingleRaw(dngFile, IMAGE_BUFFER.get(0),
                    characteristics, captureResult, cameraRotation);
            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
            processingEventsListener.onProcessingFinished("Saved Unprocessed RAW");
            IMAGE_BUFFER.clear();
            bufferLock = false;
            return;
        }*/
        Path dngFile = ImagePath.newDNGFilePath();
        Path imageFile = ImagePath.newImageFilePath();
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
        ArrayList<ImageFrame> slicedBuffer = new ArrayList<>();
        ArrayList<ImageFrame> imagebuffer = new ArrayList<>();
        for(int i =0; i<frameCount;i++){
            slicedBuffer.add(IMAGE_BUFFER.get(i));
        }
        for(int i = frameCount; i<IMAGE_BUFFER.size();i++){
            imagebuffer.add(IMAGE_BUFFER.get(i));
        }
        // Frames beyond frameCount are never merged: both capture paths
        // clear() this list before refilling, so anything retained here is
        // orphaned until GC (or heads the next burst with stale metadata).
        // Close deterministically instead (~122 MB per frame at 64 MP).
        // close() is idempotent; the merged set above is untouched.
        long tailBytes = 0;
        for (ImageFrame tail : imagebuffer) {
            if (tail != null && tail.buffer != null) {
                tailBytes += tail.buffer.capacity();
                tail.close();
            }
        }
        if (tailBytes > 0) {
            Log.d(TAG, "Closed tail frames: " + (tailBytes / 1048576) + "MB freed");
        }
        IMAGE_BUFFER.clear();
        IMAGE_BUFFER = imagebuffer;
        bufferLock = false;
        for(int i =0; i<slicedBuffer.size();i++){
            if (slicedBuffer.get(i) == null || slicedBuffer.get(i).buffer == null) {
                slicedBuffer.remove(i);
                i--;
                Log.d(TAG, "IMGBufferSize:" + slicedBuffer.size());
            }
        }

        Log.d(TAG,"moving images");
        //Log.d(TAG,"moved images:"+slicedBuffer.size());
        hdrxProcessor.start(
                dngFile,
                imageFile,
                ParseExif.parse(captureResult, captureRequest),
                burstShakiness,
                slicedBuffer,
                exposures,
                imageFormat,
                cameraRotation,
                characteristics,
                captureResult,
                captureRequest,
                processingCallback
        );
        slicedBuffer.clear();
    }

    public void processStart(int imageFormat, CameraCharacteristics characteristics, CaptureResult captureResult, CaptureRequest captureRequest, int cameraRotation) {
        super.processStart(imageFormat, characteristics, captureResult, captureRequest, cameraRotation);
        Path dngFile = ImagePath.newDNGFilePath();
        Path jpgFile = ImagePath.newImageFilePath();
        switch (PhotonCamera.getSettings().selectedMode) {
            case UNLIMITED:
                mUnlimitedProcessor.configure(PhotonCamera.getSettings().rawSaver);
                mUnlimitedProcessor.unlimitedStart(
                        dngFile,
                        jpgFile,
                        ParseExif.parse(captureResult, captureRequest),
                        characteristics,
                        captureResult,
                        captureRequest,
                        cameraRotation,
                        processingCallback
                );
                break;
            case RAWVIDEO:
                mRawVideoProcessor.videoStart(
                        ImagePath.getNewVideoFolderPath(),
                        ParseExif.parse(captureResult, captureRequest),
                        characteristics,
                        captureResult,
                        captureRequest,
                        cameraRotation,
                        processingCallback
                );
                break;
        }
    }

    public void processEnd() {
        switch (PhotonCamera.getSettings().selectedMode){
            case UNLIMITED:
                mUnlimitedProcessor.unlimitedEnd();
                break;
            case RAWVIDEO:
                mRawVideoProcessor.videoEnd();
                break;
        }
    }
}
