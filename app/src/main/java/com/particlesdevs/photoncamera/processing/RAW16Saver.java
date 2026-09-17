package com.particlesdevs.photoncamera.processing;

import android.hardware.camera2.CameraCharacteristics;
import android.media.Image;
import android.os.AsyncTask;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;

public class RAW16Saver extends DefaultSaver{
    private static final String TAG = "RAW16Saver";
    public RAW16Saver(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    /**
     * Packs a burst frame at arrival so the pack phase moves off the
     * processing critical path into sensor-readout gaps. Fail-safe:
     * HdrxProcessor's own pack loop still handles any unpacked leftovers,
     * and a failed pack keeps the 16-bit buffer untouched.
     */
    private static void packBurstAtArrival(ImageFrame frame) {
        if (frame == null || PhotonCamera.getSettings().frameCount <= 1) return;
        int whiteLevel = 0;
        try {
            CameraCharacteristics chars = CaptureController.mCameraCharacteristics;
            if (chars != null) {
                Integer wl = chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
                if (wl != null) whiteLevel = wl;
            }
        } catch (Exception ignored) {
        }
        ImageFrame.packBurstAtArrival(frame, whiteLevel, PhotonCamera.DEBUG);
        if (PhotonCamera.DEBUG) Log.d(TAG, "arrival pack: bits=" + frame.packedBits);
    }

    public void addImage(Image image) {
        switch (PhotonCamera.getSettings().selectedMode) {
            case RAWVIDEO:
                Log.d(TAG, "rawvideoaddImage: " + this + " " + mRawVideoProcessor);
                mRawVideoProcessor.videoCycle(image);
                //image.close();
                bufferLock = false;
                break;
            case UNLIMITED:
                Log.d(TAG, "unlimitedaddImage: " + this + " " + mUnlimitedProcessor);
                mUnlimitedProcessor.unlimitedCycle(image);
                image.close();
                bufferLock = false;
                break;
            default:
                Log.d(TAG, "start buffer size:" + IMAGE_BUFFER.size());
                image.getFormat();
                /*while (bufferLock){
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {}
                }*/
                ImageFrame arrived = getFrame(image);
                packBurstAtArrival(arrived);
                IMAGE_BUFFER.add(arrived);
                image.close();
                bufferLock = false;
        }
    }
}
