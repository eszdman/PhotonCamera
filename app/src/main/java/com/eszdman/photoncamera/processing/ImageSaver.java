package com.eszdman.photoncamera.processing;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import androidx.exifinterface.media.ExifInterface;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.processing.parameters.FrameNumberSelector;
import com.eszdman.photoncamera.ui.camera.CameraFragment;
import com.eszdman.photoncamera.api.ParseExif;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.ui.camera.CameraActivity;
import rapid.decoder.BitmapDecoder;

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
    static int bcnt = 0;
    private final String TAG = "ImageSaver";
    private final ProcessingEventsListener processingEventsListener;
    private final ImageProcessing mImageProcessing;
    public Handler ProcessCall;

    public ImageSaver(ImageProcessing imageProcessing, ProcessingEventsListener processingEventsListener) {
        this.mImageProcessing = imageProcessing;
        this.processingEventsListener = processingEventsListener;
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
        if (PhotonCamera.getSettings().rawSaver)
            dir = new File(Environment.getExternalStorageDirectory() + "//DCIM//PhotonCamera//Raw//");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir.getAbsolutePath();
    }

    public void SaveImg(File imageToSave) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        PhotonCamera.getSettings().setLastPicture(imageToSave.getAbsolutePath());
        Uri contentUri = Uri.fromFile(imageToSave);
        Bitmap bitmap = BitmapDecoder.from(Uri.fromFile(imageToSave)).scaleBy(0.1f).decode();
        processingEventsListener.onImageSaved(bitmap);
        mediaScanIntent.setData(contentUri);
        CameraActivity.act.sendBroadcast(mediaScanIntent);
    }

    public void done(ImageProcessing proc) {
        //proc.Run();
        proc.imageFramesToProcess = imageBuffer;
        proc.Run();
        imageBuffer = new ArrayList<>();
        Log.d(TAG, "ImageSaver Done!");
        bcnt = 0;
    }

    private void end(ImageReader mReader) {
        mReader.acquireLatestImage();
        try {
            for (int i = 0; i < mReader.getMaxImages(); i++) {
                Image cur = mReader.acquireNextImage();
                if (cur == null) break;
                cur.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        imageBuffer.clear();
        //PhotonCamera.getCameraUI().unlockShutterButton();
        processingEventsListener.onProcessingFinished(null);

    }

    @Override
    public void run() {
        Log.d(TAG, "Thread Created");
        ProcessCall = new Handler(msg -> {
            Process((ImageReader) msg.obj);
            return true;
        });
    }

    public void Process(ImageReader mReader) {
        Image mImage = mReader.acquireNextImage();
        int format = mImage.getFormat();
        FileOutputStream output = null;
        switch (format) {
            case ImageFormat.JPEG: {
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                try {
                    imageBuffer.add(mImage);
                    bcnt++;
                    byte[] bytes = new byte[buffer.remaining()];
                    imageFileToSave = new File(getCurrentDirectory(), generateNewFileName() + ".jpg");
                    if (imageBuffer.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {
                        //unlock();
                        output = new FileOutputStream(imageFileToSave);
                        buffer.duplicate().get(bytes);
                        output.write(bytes);
                        ExifInterface inter = new ExifInterface(imageFileToSave.getAbsolutePath());
                        mImageProcessing.isyuv = false;
                        mImageProcessing.israw = false;
                        mImageProcessing.path = imageFileToSave.getAbsolutePath();
                        done(mImageProcessing);
                        Thread.sleep(25);
                        inter.saveAttributes();
                        SaveImg(imageFileToSave);
                        end(mReader);
                    }
                    if (PhotonCamera.getSettings().frameCount == 1) {
                        imageBuffer = new ArrayList<>();
                        output = new FileOutputStream(imageFileToSave);
                        buffer.get(bytes);
                        output.write(bytes);
                        bcnt = 0;
                        mImage.close();
                        processingEventsListener.onProcessingFinished(null);
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
                        mImageProcessing.isyuv = true;
                        mImageProcessing.israw = false;
                        mImageProcessing.path = imageFileToSave.getAbsolutePath();
                        done(mImageProcessing);
                        ExifInterface inter = ParseExif.Parse(CameraFragment.mCaptureResult, mImageProcessing.path);
                        inter.saveAttributes();
                        SaveImg(imageFileToSave);
                        end(mReader);
                    }
                    if (PhotonCamera.getSettings().frameCount == 1) {
                        imageBuffer = new ArrayList<>();
                        bcnt = 0;
                        //PhotonCamera.getCameraUI().unlockShutterButton();
                        processingEventsListener.onProcessingFinished(null);

                    }
                    bcnt++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            //case ImageFormat.RAW10:
            case ImageFormat.RAW_SENSOR: {
                String ext = ".jpg";
                if (PhotonCamera.getSettings().rawSaver) ext = ".dng";
                imageFileToSave = new File(getCurrentDirectory(), generateNewFileName() + ext);
                String path = getCurrentDirectory() + generateNewFileName() + ext;
                try {
                    Log.d(TAG, "start buffersize:" + imageBuffer.size());
                    if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) {
                        ImageProcessing.UnlimitedCycle(mImage);
                        return;
                    }
                    imageBuffer.add(mImage);
                    if (imageBuffer.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {
                        //unlock();
                        mImageProcessing.isyuv = false;
                        mImageProcessing.israw = true;
                        mImageProcessing.path = path;
                        done(mImageProcessing);
                        ExifInterface inter = ParseExif.Parse(CameraFragment.mCaptureResult, imageFileToSave.getAbsolutePath());
                        if (!PhotonCamera.getSettings().rawSaver) inter.saveAttributes();
                        SaveImg(imageFileToSave);
                        end(mReader);
                    }
                    if (PhotonCamera.getSettings().frameCount == 1) {
                        Log.d(TAG, "activearr:" + CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG, "precorr:" + CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG, "image:" + mImage.getCropRect());
                        DngCreator dngCreator = new DngCreator(CameraFragment.mCameraCharacteristics, CameraFragment.mCaptureResult);
                        output = new FileOutputStream(new File(getCurrentDirectory(), generateNewFileName() + ".dng"));
                        dngCreator.writeImage(output, mImage);
                        imageBuffer = new ArrayList<>();
                        mImage.close();
                        output.close();
                        //PhotonCamera.getCameraUI().unlockShutterButton();
                        processingEventsListener.onProcessingFinished(null);
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
}