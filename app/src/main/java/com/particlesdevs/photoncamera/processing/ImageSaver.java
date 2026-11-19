package com.particlesdevs.photoncamera.processing;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import com.particlesdevs.photoncamera.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.BurstArchiveStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

import com.particlesdevs.photoncamera.settings.TunableInjector;

import static com.particlesdevs.photoncamera.processing.ImageSaverSelector.getImageSaver;
import static com.particlesdevs.photoncamera.processing.ImageSaverSelector.init;

public class ImageSaver {
    /**
     * Image frame buffer
     */
    public static final int JPG_QUALITY = 98;
    private static final String TAG = "ImageSaver";

    public static final ImageSaverSettings SETTINGS = new ImageSaverSettings();

    public SaverImplementation implementation;
    private int imageFormat;
    private int frameCounter = 0;
    private int desiredFrameCount = 0;
    public boolean newBurst = false;

    public void setFrameCount(int desiredFrameCount){
        this.desiredFrameCount = desiredFrameCount;
    }

    public void setImageFormat(int imageFormat) {
        this.imageFormat = imageFormat;
    }

    public void updateFrameCount(int desiredFrameCount){
        this.desiredFrameCount = desiredFrameCount;
        this.implementation.frameCount = desiredFrameCount;
    }

    public int bufferSize(){
        return SaverImplementation.IMAGE_BUFFER.size();
    }

    public ImageSaver(ProcessingEventsListener processingEventsListener) {
        implementation = new DefaultSaver(processingEventsListener);
        init(implementation);
        TunableInjector.inject(SETTINGS);
    }

    public void initProcess(ImageReader mReader) {
        Log.v(TAG, "initProcess()");
        if((frameCounter < desiredFrameCount) || desiredFrameCount == -1) {
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
            imageFormat = mReader.getImageFormat();
            implementation = getImageSaver(format, implementation);
            Log.d(TAG,"Implementation:" + implementation);
            implementation.frameCount = desiredFrameCount;
            implementation.newBurst = newBurst;
            implementation.addImage(mImage);
        } else {
            Image mImage;
            try {
                mImage = mReader.acquireNextImage();
            } catch (Exception ignored) {
                return;
            }
            if (mImage == null)
                return;
            mImage.close();
        }
        frameCounter++;
    }

    public void runRaw(CameraCharacteristics characteristics, CaptureResult captureResult, CaptureRequest captureRequest, ArrayList<GyroBurst> burstShakiness, int cameraRotation, HashMap<Long, Double> exposures) {
        TunableInjector.inject(SETTINGS);
        implementation.runRaw(imageFormat,characteristics,captureResult, captureRequest,burstShakiness,cameraRotation, exposures);
    }

    public void processStart(CameraCharacteristics characteristics, CaptureResult captureResult, CaptureRequest captureRequest, int cameraRotation) {
        TunableInjector.inject(SETTINGS);
        implementation = ImageSaverSelector.getImageSaver(ImageFormat.RAW_SENSOR, implementation);
        implementation.processStart(imageFormat,characteristics,captureResult, captureRequest,cameraRotation);
    }

    public void processEnd() {
        implementation.processEnd();
    }

    /** Feeds per-frame capture results to the RAW video processor (metadata). */
    public void videoCaptureResult(CaptureResult captureResult) {
        if (implementation instanceof DefaultSaver) {
            ((DefaultSaver) implementation).videoCaptureResult(captureResult);
        }
    }

    public static class Util {
        public static boolean saveBitmapAsJPG(Path fileToSave, Bitmap img, int jpgQuality, ParseExif.ExifData exifData) {
            exifData.COMPRESSION = String.valueOf(jpgQuality);
            if (img != null && !img.isRecycled()) {
                exifData.IMAGE_WIDTH = String.valueOf(img.getWidth());
                exifData.IMAGE_LENGTH = String.valueOf(img.getHeight());
            }
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

        /*public static boolean saveBitmapAsAVIF(Path fileToSave, Bitmap img, int jpgQuality, ParseExif.ExifData exifData) {
            exifData.COMPRESSION = String.valueOf(jpgQuality);
            try {
                OutputStream outputStream = Files.newOutputStream(fileToSave);
                //img.compress(Bitmap.CompressFormat.JPEG, jpgQuality, outputStream);
                HeifCoder coder = new HeifCoder();
                var buffer = coder.encodeAvif(img, jpgQuality, PreciseMode.LOSSY, AvifSpeed.EIGHT);
                outputStream.write(buffer);
                outputStream.flush();
                outputStream.close();
                img.recycle();
                //ExifInterface inter = ParseExif.setAllAttributes(fileToSave.toFile(), exifData);
                //inter.saveAttributes();
                return true;
            } catch (IOException e) {
                //e.printStackTrace();
                Log.d(TAG,"AVIF save error:"+Log.getStackTraceString(e));
                return false;
            }
        }*/

        public static boolean saveBitmapAsPNG(Path fileToSave, Bitmap img, int pngQuality, ParseExif.ExifData exifData) {
            try {
                OutputStream outputStream = Files.newOutputStream(fileToSave);
                img.compress(Bitmap.CompressFormat.PNG, pngQuality, outputStream);
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

        public static boolean saveStackedRaw(Path dngFilePath,
                                             ByteBuffer buffer, Parameters parameters) {
            return saveSingleRaw(dngFilePath, buffer, parameters);
        }

        /**
         * One burst input frame for the DNG archive export.
         * {@code buffer} holds the sensor-domain samples: bit-packed (with
         * {@code packedBits} > 0) or plain little-endian uint16. Owned by the
         * caller, which must keep it alive until the synchronous export finishes.
         */
        public static final class BurstFrame {
            public final ByteBuffer buffer;
            public final int packedBits;
            public final int width, height;
            public final int iso;
            public final double exposureTime; // seconds
            public final double layerMpy;
            public final long timestamp; // nanoseconds

            public BurstFrame(ByteBuffer buffer, int packedBits, int width, int height,
                              int iso, double exposureTime, double layerMpy, long timestamp) {
                this.buffer = buffer;
                this.packedBits = packedBits;
                this.width = width;
                this.height = height;
                this.iso = iso;
                this.exposureTime = exposureTime;
                this.layerMpy = layerMpy;
                this.timestamp = timestamp;
            }
        }

        /** Saves the merge inputs in one ZIP; borrowed input buffers remain owned by the caller. */
        public static boolean saveBurstZip(String stem, ArrayList<BurstFrame> frames,
                                            Parameters parameters, short[] sensorBlackLevel,
                                            int sensorWhiteLevel, double[] singleFrameNoise) {
            try {
                BurstArchiveStore.save(PhotonCamera.getAppContext(), stem, frames.size(), (index, output) -> {
                    BurstFrame frame = frames.get(index);
                    ByteBuffer unpacked = null;
                    try {
                        ByteBuffer raw = frame.buffer;
                        if (frame.packedBits > 0) {
                            unpacked = Allocator.allocate(frame.width * frame.height * 2);
                            if (unpacked == null) throw new IOException("Burst frame unpack allocation failed");
                            Allocator.unpack16(unpacked, frame.buffer,
                                    frame.width * frame.height, frame.packedBits);
                            raw = unpacked;
                        }
                        writeBurstFrameDng(output, raw, parameters, frame, sensorBlackLevel,
                                sensorWhiteLevel, singleFrameNoise);
                    } finally {
                        if (unpacked != null) Allocator.free(unpacked);
                    }
                });
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Burst ZIP save failed: " + Log.getStackTraceString(e));
                return false;
            }
        }

        /** Writes shared camera metadata with sensor levels and the frame's own ISO/exposure. */
        private static void writeBurstFrameDng(OutputStream output, ByteBuffer raw,
                                                Parameters parameters, BurstFrame frame,
                                                short[] sensorBlackLevel, int sensorWhiteLevel,
                                                double[] singleFrameNoise) {
            DngCreator dngCreator = new DngCreator();
            try {
                dngCreator.setParameters(parameters);
                dngCreator.setCompression(false);
                dngCreator.setBlackLevel(sensorBlackLevel);
                dngCreator.setWhiteLevel(sensorWhiteLevel);
                dngCreator.setIso((short) frame.iso);
                dngCreator.setExposureTime(frame.exposureTime);
                if (singleFrameNoise != null) {
                    dngCreator.setNoiseProfile(singleFrameNoise);
                }
                dngCreator.setDescription(String.format(java.util.Locale.ROOT,
                        "PhotonCamera burst frame layerMpy=%.4f timestamp=%d ns",
                        frame.layerMpy, frame.timestamp));
                dngCreator.writeBuffer(output, raw, frame.width, frame.height);
            } finally {
                dngCreator.close();
            }
        }
        public static boolean saveSingleRaw(Path dngFilePath,
                                            ImageFrame image,
                                            CameraCharacteristics characteristics,
                                            CaptureResult captureResult,
                                            int cameraRotation) {
            Parameters parameters = new Parameters();
            parameters.binned = image.binned;
            parameters.FillConstParameters(characteristics, new Point(image.width, image.height));
            parameters.setCropDetails(image.cropOriginX, image.cropOriginY);
            if (image.fullWidth > 0 && image.fullHeight > 0) {
                parameters.setFullRawSize(image.fullWidth, image.fullHeight);
            }
            int iso = captureResult.get(CaptureResult.SENSOR_SENSITIVITY);
            parameters.FillDynamicParameters(captureResult, null, iso);
            parameters.cameraRotation = cameraRotation;
            Log.d(TAG, "Camera rotation: " + parameters.cameraRotation);
            Log.d(TAG, "activearr:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
            Log.d(TAG, "precorr:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
            return saveSingleRaw(dngFilePath, image.buffer, parameters);
        }

        public static boolean saveSingleRaw(Path dngFilePath,
                                            ByteBuffer buffer, Parameters parameters) {
            DngCreator dngCreator = new DngCreator();
            dngCreator.setParameters(parameters);
            dngCreator.setCompression(false);
            //dngCreator.setBinning(true);
            try {
                OutputStream outputStream = Files.newOutputStream(dngFilePath);
                dngCreator.writeBuffer(outputStream, buffer, parameters.rawSize.x, parameters.rawSize.y);
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } finally {
                dngCreator.close();
            }
            return true;
        }
    }
}
