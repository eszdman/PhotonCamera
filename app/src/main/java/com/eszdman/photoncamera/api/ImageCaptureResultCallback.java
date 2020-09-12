package com.eszdman.photoncamera.api;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class ImageCaptureResultCallback extends CameraCaptureSession.CaptureCallback
{
    private List<CaptureEvents> eventListners;
    public interface CaptureEvents
    {
        void onCaptureStarted();
        void onCaptureCompleted();
        void onCaptureSequenceCompleted();
        void onCaptureProgressed();
    }
    private CaptureResult result;

    public ImageCaptureResultCallback()
    {
        eventListners = new ArrayList<>();
    }

    public CaptureResult getResult()
    {
        return result;
    }

    public void addEventListner(CaptureEvents events)
    {
        if (!eventListners.contains(events))
            eventListners.add(events);
    }

    public void removeEventListner(CaptureEvents events)
    {
        if (eventListners.contains(events))
            eventListners.remove(events);
    }

    @Override
    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
        for (CaptureEvents events : eventListners)
            events.onCaptureStarted();
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
        this.result = result;
        for (CaptureEvents events : eventListners)
            events.onCaptureCompleted();
    }

    @Override
    public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
        for (CaptureEvents events : eventListners)
            events.onCaptureSequenceCompleted();
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
        for (CaptureEvents events : eventListners)
            events.onCaptureProgressed();
    }
}
