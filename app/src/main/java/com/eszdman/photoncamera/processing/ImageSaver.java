package com.eszdman.photoncamera.processing;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.api.ParseExif;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.parameters.FrameNumberSelector;
import com.eszdman.photoncamera.capture.CaptureController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ImageSaver implements Runnable {
    public static File imageFileToSave;
    /**
     * Image frame buffer
     */
    public static ArrayList<Image> imageBuffer = new ArrayList<>();
    static int bCnt = 0;
    private final String TAG = "ImageSaver";
    private final ProcessingEventsListener processingEventsListener;
    private final ImageProcessing mImageProcessing;
    public Handler processingHandler;
    private HandlerThread processingThread;

    public ImageSaver(ImageProcessing imageProcessing, ProcessingEventsListener processingEventsListener) {
        this.mImageProcessing = imageProcessing;
        this.processingEventsListener = processingEventsListener;
    }

    @Override
    public void run() {
        startProcessingThread();
        processingHandler = new Handler(processingThread.getLooper(), msg -> {
            try {
                initProcess((ImageReader) msg.obj);
            } catch (Exception e) {
                Log.e(TAG, ProcessingEventsListener.FAILED_MSG);
                processingEventsListener.onErrorOccurred(ProcessingEventsListener.FAILED_MSG);
                e.printStackTrace();
            }
            return true;
        });
    }

    /**
     * Starts the image processing thread and its {@link Handler}.
     */
    public void startProcessingThread() {
        if (processingThread == null) {
            processingThread = new HandlerThread("ImageProcessing");
            processingThread.start();
            processingHandler = new Handler(processingThread.getLooper());
            Log.d(TAG, "startProcessingThread() called from \"" + Thread.currentThread().getName() + "\" Thread");
        }
    }

    /**
     * Stops the image processing thread and its {@link Handler}.
     */
    public void stopProcessingThread() {
        if (processingThread == null)
            return;
        processingThread.quitSafely();
        try {
            processingThread.join();
            processingThread = null;
            processingHandler = null;
            Log.d(TAG, "stopProcessingThread() called from \"" + Thread.currentThread().getName() + "\" Thread");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void initProcess(ImageReader mReader) {
        Log.v(TAG, "initProcess() : called from \"" + Thread.currentThread().getName() + "\" Thread");
        Image mImage = null;
        try {
        mImage = mReader.acquireNextImage();
        } catch (Exception e){
            mReader.close();
            //mImage = mReader.acquireLatestImage();
        }
        if(mImage == null) return;
        int format = mImage.getFormat();
        FileOutputStream output = null;
        switch (format) {
            case ImageFormat.JPEG: {
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                try {
                    imageBuffer.add(mImage);
                    bCnt++;
                    byte[] bytes = new byte[buffer.remaining()];
                    imageFileToSave = new File(getCurrentDirectory(), generateNewFileName() + ".jpg");
                    if (imageBuffer.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {
                        //unlock();
                        output = new FileOutputStream(imageFileToSave);
                        buffer.duplicate().get(bytes);
                        output.write(bytes);
                        ExifInterface inter = new ExifInterface(imageFileToSave.getAbsolutePath());

                        mImageProcessing.setYuv(false);
                        mImageProcessing.setRaw(false);
                        begin(mImageProcessing);

                        Thread.sleep(25);
                        inter.saveAttributes();
                        processingEventsListener.onImageSaved(imageFileToSave);
//                        triggerMediaScanner(imageFileToSave);
                        end(mReader);
                    }
                    if (PhotonCamera.getSettings().frameCount == 1) {
                        imageBuffer = new ArrayList<>();
                        output = new FileOutputStream(imageFileToSave);
                        buffer.get(bytes);
                        output.write(bytes);
                        bCnt = 0;
                        mImage.close();
                        processingEventsListener.onProcessingFinished("JPEG: Single Frame, Not Processed!");
                        processingEventsListener.onImageSaved(imageFileToSave);
//                        PhotonCamera.getCameraUI().unlockShutterButton();
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    //mImage.close();
                    if (null != output) {
                        try {
                            output.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }
            case ImageFormat.YUV_420_888: {
                imageFileToSave = new File(getCurrentDirectory(), generateNewFileName() + ".jpg");
                try {
                    Log.d(TAG, "start buffersize:" + imageBuffer.size());
                    imageBuffer.add(mImage);
                    if (imageBuffer.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {
                        //unlock();

                        mImageProcessing.setYuv(true);
                        mImageProcessing.setRaw(false);
                        begin(mImageProcessing);

                        ExifInterface inter = ParseExif.Parse(CaptureController.mCaptureResult, imageFileToSave.getAbsolutePath());
                        inter.saveAttributes();
                        processingEventsListener.onImageSaved(imageFileToSave);
//                        triggerMediaScanner(imageFileToSave);
                        end(mReader);
                    }
                    if (PhotonCamera.getSettings().frameCount == 1) {
                        imageBuffer = new ArrayList<>();
                        bCnt = 0;
                        //PhotonCamera.getCameraUI().unlockShutterButton();
                        processingEventsListener.onProcessingFinished("YUV: Single Frame, Not Processed!");

                    }
                    bCnt++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            //case ImageFormat.RAW10:
            case ImageFormat.RAW_SENSOR: {
                String ext = ".jpg";
                if (PhotonCamera.getSettings().rawSaver) {
                    ext = ".dng";
                }
                imageFileToSave = new File(getCurrentDirectory(), generateNewFileName() + ext);
                String path = getCurrentDirectory() + generateNewFileName() + ext;
                try {
                    Log.d(TAG, "start buffer size:" + imageBuffer.size());
                    if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) {
                        mImageProcessing.unlimitedCycle(mImage);
                        return;
                    }
                    imageBuffer.add(mImage);
                    if (imageBuffer.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {
                        //unlock();

                        mImageProcessing.setYuv(false);
                        mImageProcessing.setRaw(true);
                        begin(mImageProcessing);

                        ExifInterface inter = ParseExif.Parse(CaptureController.mCaptureResult, mImageProcessing.getFilePath());
                        if (!PhotonCamera.getSettings().rawSaver) {
                            inter.saveAttributes();
                        }
                        processingEventsListener.onImageSaved(imageFileToSave);
//                        triggerMediaScanner(imageFileToSave);
                        end(mReader);
                    }
                    if (PhotonCamera.getSettings().frameCount == 1) {
                        Log.d(TAG, "activearr:" + CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG, "precorr:" + CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG, "image:" + mImage.getCropRect());
                        DngCreator dngCreator = new DngCreator(CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult);
                        File dngFileToSave = new File(getCurrentDirectory(), generateNewFileName() + ".dng");
                        output = new FileOutputStream(dngFileToSave);
                        dngCreator.writeImage(output, mImage);
                        imageBuffer = new ArrayList<>();
                        mImage.close();
                        output.close();
                        //PhotonCamera.getCameraUI().unlockShutterButton();
                        processingEventsListener.onProcessingFinished("RAW_SENSOR: Single Frame, Not Processed!");
                        processingEventsListener.onImageSaved(dngFileToSave);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            default: {
                Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                break;
            }
        }
    }

    private void begin(ImageProcessing processing) {
        processing.setFilePath(imageFileToSave.getAbsolutePath());
        processing.setImageFramesToProcess(imageBuffer);
        processing.Run();
        imageBuffer = new ArrayList<>();
        Log.d(TAG, "ImageSaver Done!");
        bCnt = 0;
    }

    private void end(ImageReader mReader) {
        try {
            for (int i = 0; i < mReader.getMaxImages(); i++) {
                Image cur = mReader.acquireNextImage();
                if (cur == null) {
                    continue;
                }
                cur.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        PhotonCamera.getCameraFragment().getCaptureController().BurstShakiness.clear();
        imageBuffer.clear();
        //PhotonCamera.getCameraUI().unlockShutterButton();
        processingEventsListener.onProcessingFinished("Processing Cycle Ended!");
    }

    private String generateNewFileName() {
        Date currentDate = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String dateText = dateFormat.format(currentDate);
        return "IMG_" + dateText;
    }

    private String getCurrentDirectory() {
        File dir;
        dir = new File(Environment.getExternalStorageDirectory() + "//DCIM//Camera//");
        if (PhotonCamera.getSettings().rawSaver) {
            dir = new File(Environment.getExternalStorageDirectory() + "//DCIM//PhotonCamera//Raw//");
        }
        dir.mkdirs();
        return dir.getAbsolutePath();
    }
}