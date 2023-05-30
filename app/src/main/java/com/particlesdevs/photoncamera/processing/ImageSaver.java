package com.particlesdevs.photoncamera.processing;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.hunter.library.debug.HunterDebug;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;
import com.particlesdevs.photoncamera.util.FileManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public class ImageSaver {
    /**
     * Image frame buffer
     */
    public static final int JPG_QUALITY = 97;
    private static final String TAG = "ImageSaver";

    public SaverImplementation implementation;
    private ImageReader imageReader;
    public ImageSaver(ProcessingEventsListener processingEventsListener) {
        implementation = new DefaultSaver(processingEventsListener);
    }

    public void initProcess(ImageReader mReader) {
        Log.v(TAG, "initProcess() : called from \"" + Thread.currentThread().getName() + "\" Thread");
        Image mImage;
        try {
            mImage = mReader.acquireNextImage();
        } catch (Exception ignored) {
            return;
        }
        if (mImage == null)
            return;
        int format = mImage.getFormat();
        imageReader = mReader;
        ImageSaverSelector imageSaverSelector =  new ImageSaverSelector(format, implementation);
        implementation = imageSaverSelector.SelectedImageSaver();
        implementation.addImage(mImage);
    }

    public void runRaw(CameraCharacteristics characteristics, CaptureResult captureResult, ArrayList<GyroBurst> burstShakiness, int cameraRotation) {
        implementation.runRaw(imageReader,characteristics,captureResult,burstShakiness,cameraRotation);
    }

    public void unlimitedStart(CameraCharacteristics characteristics, CaptureResult captureResult, int cameraRotation) {
        implementation.unlimitedStart(imageReader,characteristics,captureResult,cameraRotation);
    }

    public void unlimitedEnd() {
        implementation.unlimitedEnd();
    }

    public static class Util {
        public static boolean saveBitmapAsJPG(Path fileToSave, Bitmap img, int jpgQuality, ParseExif.ExifData exifData) {
            exifData.COMPRESSION = String.valueOf(jpgQuality);
            try {
                OutputStream outputStream = Files.newOutputStream(fileToSave);
                img.compress(Bitmap.CompressFormat.JPEG, jpgQuality, outputStream);
                outputStream.flush();
                outputStream.close();
                img.recycle();
                ExifInterface inter = ParseExif.setAllAttributes(fileToSave.toFile(), exifData);
                inter.saveAttributes();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        //Different method name just for clarity of usage
        public static boolean saveStackedRaw(Path dngFilePath,
                                             Image image,
                                             CameraCharacteristics characteristics,
                                             CaptureResult captureResult,
                                             int cameraRotation) {
            return saveSingleRaw(dngFilePath, image, characteristics, captureResult, cameraRotation);
        }

        @HunterDebug
        public static boolean saveSingleRaw(Path dngFilePath,
                                            Image image,
                                            CameraCharacteristics characteristics,
                                            CaptureResult captureResult,
                                            int cameraRotation) {
            Log.d(TAG, "activearr:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
            Log.d(TAG, "precorr:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
            Log.d(TAG, "image:" + image.getCropRect());
            DngCreator dngCreator =
                    new DngCreator(characteristics, captureResult)
                            .setDescription(PhotonCamera.getParameters().toString())
                            .setOrientation(ParseExif.getOrientation(cameraRotation));
            try {
                OutputStream outputStream = Files.newOutputStream(dngFilePath);
                dngCreator.writeImage(outputStream, image);
//                image.close();
                outputStream.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}