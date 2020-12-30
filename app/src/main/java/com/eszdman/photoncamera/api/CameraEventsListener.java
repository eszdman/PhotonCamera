package com.eszdman.photoncamera.api;

import android.util.Log;
import com.eszdman.photoncamera.capture.CaptureEventsListener;
import com.eszdman.photoncamera.processing.ProcessingEventsListener;

public abstract class CameraEventsListener implements CaptureEventsListener, ProcessingEventsListener {
    protected String TAG = "CameraEventsListener";

    protected void logD(String msg) {
        Log.d(TAG, msg);
    }

    protected void logE(String msg) {
        Log.e(TAG, msg);
    }
}
