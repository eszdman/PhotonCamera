package com.particlesdevs.photoncamera.processing;

import android.media.Image;
import android.util.Log;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;

public class RAW16Saver extends DefaultSaver{
    private static final String TAG = "RAW16Saver";
    public RAW16Saver(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void addImage(Image image) {
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) {
            mUnlimitedProcessor.unlimitedCycle(image);
        } else {
            Log.d(TAG, "start buffer size:" + IMAGE_BUFFER.size());
            image.getFormat();
            IMAGE_BUFFER.add(image);
        }
    }

}
