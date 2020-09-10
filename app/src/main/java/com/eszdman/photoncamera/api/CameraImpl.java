package com.eszdman.photoncamera.api;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraImpl implements ICamera {

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /**
     * A reference to the opened {@link CameraDevice}.
     */
    public CameraDevice mCameraDevice;

    private ICamera.CameraEvents cameraEventsListner;

    /*An additional thread for running tasks that shouldn't block the UI.*/
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    public CameraImpl()
    {
    }

    public void setCameraEventsListner(CameraEvents cameraEventsListner)
    {
        this.cameraEventsListner = cameraEventsListner;
    }

    private void fireOnCameraOpen()
    {
        if (cameraEventsListner != null)
            cameraEventsListner.onCameraOpen();
    }

    private void fireOnCameraClose()
    {
        if (cameraEventsListner != null)
            cameraEventsListner.onCameraClose();
    }

    @Override
    public void onResume() {
        startBackgroundThread();
    }

    @Override
    public void onPause() {
        stopBackgroundThread();
    }

    @Override
    public String getId() {
        return mCameraDevice.getId();
    }

    @Override
    public CaptureRequest.Builder createCaptureRequest(int template) {
        try {
            return mCameraDevice.createCaptureRequest(template);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void createCaptureSession(List<Surface> var1, CameraCaptureSession.StateCallback var2, Handler var3) {
        try {
            mCameraDevice.createCaptureSession(var1,var2,var3);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void openCamera(String id) {
        CameraManager manager = (CameraManager) Interface.getMainActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraManager2 manager2 = new CameraManager2(manager);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
//                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(id, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    @Override
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            fireOnCameraClose();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        //mBackgroundHandler.post(imageSaver);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if(mBackgroundThread == null) return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            fireOnCameraOpen();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            fireOnCameraClose();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            fireOnCameraClose();
        }

    };
}
