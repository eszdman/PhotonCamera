package com.particlesdevs.photoncamera.processing.processor;

import android.graphics.Bitmap;
import com.particlesdevs.photoncamera.BuildConfig;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.os.SystemClock;
import android.util.Pair;

import com.particlesdevs.photoncamera.processing.opengl.scripts.ESD4D;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.api.Camera2ApiAutoFix;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.ImageFrameDeblur;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.encoder.HeicSupport;
import com.particlesdevs.photoncamera.processing.encoder.ImageFormatConfig;
import com.particlesdevs.photoncamera.processing.encoder.StillEncoder;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapComputer;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.Allocator;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class HdrxProcessor extends ProcessorBase {
    private static final String TAG = "HdrxProcessor";
    private ArrayList<ImageFrame> mImageFramesToProcess;
    private HashMap<Long, Double> exposures;
    private int imageFormat;
    /* config */
    private int alignAlgorithm;
    private int saveRAW;
    private CameraMode cameraMode;
    private ArrayList<GyroBurst> BurstShakiness;


    public HdrxProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void configure(int alignAlgorithm, int saveRAW, CameraMode cameraMode) {
        this.alignAlgorithm = alignAlgorithm;
        this.saveRAW = saveRAW;
        this.cameraMode = cameraMode;
    }

    public void start(Path dngFile, Path imageFile,
                      ParseExif.ExifData exifData,
                      ArrayList<GyroBurst> BurstShakiness,
                      ArrayList<ImageFrame> imageBuffer,
                      HashMap<Long, Double> exposures,
                      int imageFormat,
                      int cameraRotation,
                      CameraCharacteristics characteristics,
                      CaptureResult captureResult,
                      CaptureRequest captureRequest,
                      ProcessingCallback callback) {
        this.imageFile = imageFile;
        this.dngFile = dngFile;
        this.exifData = exifData;
        this.BurstShakiness = new ArrayList<>(BurstShakiness);
        this.imageFormat = imageFormat;
        this.cameraRotation = cameraRotation;
        this.mImageFramesToProcess = imageBuffer;
        this.exposures = exposures;
        this.callback = callback;
        this.characteristics = characteristics;
        this.captureResult = captureResult;
        this.captureRequest = captureRequest;
        Log.d(TAG, "HdrxProcessor called start()");
        Run();
    }

    public void Run() {
        try {
            Camera2ApiAutoFix.ApplyRes(captureResult);
            if (imageFormat == CaptureController.RAW_FORMAT) {
                ApplyHdrX();
            } else {
                Log.d(TAG, "HdrX processing skipped due to unsupported image format: " + imageFormat);
                callback.onFinished();
                return;
            }
//            if (isYuv) {
//                ApplyStabilization();
//            }
        } catch (Exception e) {
            Log.e(TAG, ProcessingEventsListener.FAILED_MSG);
            Log.e(TAG, "Error in HdrX Processing:"+Log.getStackTraceString(e));
            callback.onFailed();
            processingEventsListener.onProcessingError("HdrX Processing Failed");
        }
    }

    private void ApplyHdrX() {
        callback.onStarted();
        processingEventsListener.onProcessingStarted("HDRX");
        long processingStartMs = SystemClock.elapsedRealtime();
        Allocator.resetPeakMemory();
        GLTexture.resetPeakVram();

        Log.d(TAG, "ApplyHdrX() called from" + Thread.currentThread().getName());

        Log.d(TAG, "ApplyHdrX() mImageFramesToProcess.size():" + mImageFramesToProcess.size());
        int width = mImageFramesToProcess.get(0).width;
        int height = mImageFramesToProcess.get(0).height;
        Log.d(TAG, "APPLY HDRX: buffer:" + mImageFramesToProcess.get(0).buffer.asShortBuffer().remaining());
        Log.d(TAG, "Api WhiteLevel:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG, "Api BlackLevel:" + characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        Parameters processingParameters = new Parameters();
        processingParameters.FillConstParameters(characteristics, new Point(width, height));
        // Reflect any digital-zoom crop origin so sensor-relative metadata
        // (active array, principal point) matches the cropped buffer.
        ImageFrame first = mImageFramesToProcess.get(0);
        processingParameters.binned = first.binned;
        processingParameters.setCropDetails(first.cropOriginX, first.cropOriginY);
        if (first.fullWidth > 0 && first.fullHeight > 0) {
            processingParameters.setFullRawSize(first.fullWidth, first.fullHeight);
        }
        // Packed burst staging: sensor samples never exceed whiteLevel, so
        // each burst frame is repacked at ceil(log2(whiteLevel+1)) bits
        // (2 B/px -> 1.25 B/px at the common 10-bit setting). Frames already
        // packed at arrival (RAW16Saver) are skipped below and act as the
        // fast path; this loop only handles leftovers. Uploads unpack
        // on demand (ImageFrame.upload()); the merged output and the
        // single-frame path stay 16-bit. Only tightly packed buffers are
        // repacked; padded layouts keep their current form.
        // Dynamic parameters scale the binned levels later; packing already
        // sees the four-tap sums and must reserve their extra two bits now.
        int packingWhiteLevel = first.binned
                ? (int) Math.min(processingParameters.whiteLevel * 4L, 65535L)
                : processingParameters.whiteLevel;
        int packBits = packingWhiteLevel > 0
                ? 32 - Integer.numberOfLeadingZeros(packingWhiteLevel) : 0;
        if (mImageFramesToProcess.size() > 1 && packBits > 0 && packBits < 16) {
            int packedCount = 0;
            for (ImageFrame frame : mImageFramesToProcess) {
                if (frame.buffer == null || frame.fp16 || frame.packedBits != 0
                        || frame.buffer.capacity() != frame.width * frame.height * 2) {
                    continue;
                }
                ByteBuffer packed = Allocator.packBits(frame.buffer,
                        frame.width * frame.height, packBits, PhotonCamera.DEBUG);
                if (packed != null) {
                    Allocator.free(frame.buffer);
                    frame.buffer = packed;
                    frame.packedBits = packBits;
                    packedCount++;
                }
            }
            Log.d(TAG, "Packed burst frames: " + packedCount + "/"
                    + mImageFramesToProcess.size() + " at " + packBits + " bits");
        }
        Log.d(TAG, "Stage[params+pack] elapsed:" + (SystemClock.elapsedRealtime() - processingStartMs) + " ms");
        long frameSelectT = System.currentTimeMillis();
        // sort by timestamp first
        mImageFramesToProcess.sort(Comparator.comparingLong(ImageFrame::getTimestamp));
        double minExpo = exposures.get(mImageFramesToProcess.get(0).getTimestamp());
        for (int i = 1; i < mImageFramesToProcess.size(); i++) {
            minExpo = Math.min(minExpo, exposures.get(mImageFramesToProcess.get(i).getTimestamp()));
        }
        Log.d(TAG, "Wrapper.init");
        ArrayList<ImageFrame> images = new ArrayList<>();
        int ISO = 0;
        int normalFrames = 0;
        if(BurstShakiness.size() < mImageFramesToProcess.size()){
            Log.d(TAG,"Warning: Gyro data size:"+BurstShakiness.size()+" is less than image size:"+mImageFramesToProcess.size());
        }
        java.util.HashMap<Long, GyroBurst> gyroByTimestamp = new java.util.HashMap<>();
        for (GyroBurst gyro : BurstShakiness) gyroByTimestamp.put(gyro.frameTimestamp, gyro);
        for (int i = 0; i < mImageFramesToProcess.size(); i++) {
            ImageFrame frame = mImageFramesToProcess.get(i);
            frame.frameGyro = gyroByTimestamp.get(frame.timestamp);
            if (frame.frameGyro == null) {
                frame.frameGyro = new GyroBurst(0);
                frame.frameGyro.frameTimestamp = frame.timestamp;
                Log.w(TAG, "No gyro record for image timestamp " + frame.timestamp);
            }
            //frame.image = mImageFramesToProcess.get(i);
            //Log.d(TAG,"Timestamp:"+frame.image.getTimestamp());
            //frame.pair = IsoExpoSelector.pairs.get(i % IsoExpoSelector.patternSize);
            frame.pair = IsoExpoSelector.fullpairs.get(i);
            frame.number = i;
            frame.pair.layerMpy = (float) (exposures.get(mImageFramesToProcess.get(i).getTimestamp()) / minExpo);
            if (frame.pair.layerMpy > 1.0) {
                frame.pair.curlayer = IsoExpoSelector.ExpoPair.exposureLayer.High;
            } else {
                frame.pair.curlayer = IsoExpoSelector.ExpoPair.exposureLayer.Normal;
                normalFrames++;
            }
            /*if(i == mImageFramesToProcess.size()-1){
                int ind = Math.max(0,mImageFramesToProcess.size()-2);
                frame.frameGyro = BurstShakiness.get(ind);
            }*/
            if (PhotonCamera.DEBUG)
                Log.d(TAG, "Mpy:" + frame.pair.layerMpy);
            images.add(frame);
            ISO += frame.pair.iso;
        }
        ISO /= mImageFramesToProcess.size();
        long inputBytes = 0;
        int inputBits = images.isEmpty() ? 16 : images.get(0).packedBits;
        for (ImageFrame frame : images) {
            if (frame.buffer != null) inputBytes += frame.buffer.capacity();
        }
        Allocator.logStage(TAG, "burst-start frames=" + images.size() + " " + width + "x" + height
                + " packed=" + (inputBits > 0 ? inputBits : 16)
                + " actual=" + (inputBytes / 1048576) + "MB");
        Allocator.logProc(TAG, "burst-start");

        processingParameters.FillDynamicParameters(captureResult, captureRequest,ISO);
        processingParameters.cameraRotation = cameraRotation;

        ParseExif.syncWithParameters(exifData, processingParameters);
        // Missing gyro coverage must not rank a frame as perfectly still.
        float measuredShake = 0;
        int measuredFrames = 0;
        for (ImageFrame image : images) if (image.frameGyro.orientationValid) {
            measuredShake += image.frameGyro.shakiness;
            measuredFrames++;
        }
        if (measuredFrames > 0) {
            float unknownShake = measuredShake / measuredFrames;
            for (ImageFrame image : images) if (!image.frameGyro.orientationValid)
                image.frameGyro.shakiness = unknownShake;
        }
        ImageFrameDeblur imageFrameDeblur = new ImageFrameDeblur(processingParameters);
        imageFrameDeblur.firstFrameGyro = images.get(0).frameGyro.clone();
        for (int i = 0; i < images.size(); i++)
            imageFrameDeblur.processDeblurPosition(images.get(i));
        if (mImageFramesToProcess.size() >= 3)
            images.sort((img1, img2) -> Float.compare(img1.frameGyro.shakiness, img2.frameGyro.shakiness));
        double unluckypickiness = 1.05;
        float unluckyavr = 0;
        for (ImageFrame image : images) {
            unluckyavr += image.frameGyro.shakiness;
            Log.d(TAG, "unlucky map:" + image.frameGyro.shakiness + "n:" + image.number);
        }
        unluckyavr /= images.size();
        // search for high exposure close frame by time
        int highind = -1;
        int timeDiff = Integer.MAX_VALUE;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).pair.curlayer == IsoExpoSelector.ExpoPair.exposureLayer.High) {
                int diff = (int) Math.abs(images.get(i).timestamp - images.get(0).timestamp);
                if (diff < timeDiff) {
                    timeDiff = diff;
                    highind = i;
                }
            }
        }
        // swap to second
        if (highind != -1) {
            ImageFrame frame = images.get(0);
            images.set(0, images.get(highind));
            images.set(highind, frame);
        }

        if (images.size() > 10) {
            int size = (int) (images.size() - FrameNumberSelector.throwCount);
            Log.d(TAG, "Throw Count:" + size);
            Log.d(TAG, "Image Count:" + images.size());
            //if (size == images.size())
                size = (int) (images.size() * 0.75);
            for (int i = images.size(); i > size; i--) {
                ImageFrame cur = images.get(images.size() - 1);
                float curunlucky = cur.frameGyro.shakiness;
                if (curunlucky > unluckyavr * unluckypickiness) {
                    if(normalFrames == 1 && cur.pair.curlayer == IsoExpoSelector.ExpoPair.exposureLayer.Normal) {
                        continue;
                    }
                    if(cur.pair.curlayer == IsoExpoSelector.ExpoPair.exposureLayer.Normal){
                        normalFrames--;
                    }
                    Log.d(TAG, "Removing unlucky:" + curunlucky + " number:" + images.get(images.size() - 1).number);
                    images.get(images.size() - 1).close();
                    images.remove(images.size() - 1);
                }
            }
            Log.d(TAG, "Size after removal:" + images.size());
        }

        float minMpy = 1000.f;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).pair.layerMpy < minMpy) {
                minMpy = images.get(i).pair.layerMpy;
            }
        }
        /*
        if (images.get(0).pair.layerMpy != minMpy) {
            Log.d(TAG,"Replace 0 with minMpy");
            for (int i = 1; i < images.size(); i++) {
                if (images.get(i).pair.layerMpy == minMpy) {
                    ImageFrame frame = images.get(0);
                    images.set(0, images.get(i));
                    images.set(i, frame);
                    break;
                }
            }
        }*/
        int selected = 0;
        for (int i = 0; i < images.size(); i++) {
            if(images.get(i).pair.layerMpy == minMpy){
                selected = i;
                break;
            }
        }

        // move selected image to 0 index
        if(selected != 0){
            ImageFrame frame = images.get(0);
            images.set(0, images.get(selected));
            images.set(selected, frame);
        }
        selected = 0;



        // Export the exact merge inputs before ESD4D replaces their buffers
        // with fp16. Stream each DNG into the ZIP while the original buffers
        // are still alive, including when the shot uses RAW-only save mode.
        if (PreferenceKeys.isSaveBurstDngsOn() && images.size() > 1) {
            short[] sensorBlackLevel = new short[4];
            for (int i = 0; i < 4; i++) {
                sensorBlackLevel[i] = (short) processingParameters.blackLevel[i];
            }
            ArrayList<ImageSaver.Util.BurstFrame> burstFrames = new ArrayList<>();
            for (ImageFrame frame : images) {
                if (frame.buffer == null || frame.fp16) {
                    Log.w(TAG, "Burst ZIP: frame " + frame.number + " has no raw buffer");
                    break;
                }
                burstFrames.add(new ImageSaver.Util.BurstFrame(frame.buffer, frame.packedBits,
                        frame.width, frame.height, frame.pair.iso,
                        frame.pair.exposure / 1e9, frame.pair.layerMpy, frame.getTimestamp()));
            }
            String name = dngFile.getFileName().toString();
            String stem = name.substring(0, name.lastIndexOf('.'));
            boolean archiveSaved = burstFrames.size() == images.size()
                    && ImageSaver.Util.saveBurstZip(stem, burstFrames, processingParameters,
                    sensorBlackLevel, processingParameters.whiteLevel,
                    singleFrameNoiseModel(processingParameters));
            // The archive is already published by BurstArchiveStore. A ZIP is
            // not an image thumbnail and does not need the image media scanner.
            if (!archiveSaved) processingEventsListener.notifyImageSavedStatus(false, null);
        }

        Log.d(TAG, "White Level:" + processingParameters.whiteLevel);
        Log.d(TAG, "Wrapper.loadFrame");
        //float noiseLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) *
        //        IsoExpoSelector.getMPY() - 40.)*6400.f / (6.2f*IsoExpoSelector.getISOAnalog());

        ByteBuffer output = null;
        // True when output already holds white/black-level normalized fp16
        // (ESD4D's merge2o renders straight into an R16F buffer).
        boolean outputF16 = false;
        Log.d(TAG, "Packing");
        //WrapperAl.packImages();
        Log.d(TAG, "Packed");
        Log.d(TAG, "Stage[frame-select] elapsed:" + (System.currentTimeMillis() - frameSelectT) + " ms");
        ESD4D esd4d = null;
        KomputeBurst nativeBurst = null;
        if (images.size() > 1) {
            long mergeStartNs = SystemClock.elapsedRealtimeNanos();
            if (BuildConfig.BUILD_ESD4D) nativeBurst = KomputeBurst.tryMerge(images, processingParameters);
            if (nativeBurst != null) {
                output = nativeBurst.bayer;
            } else {
                esd4d = new ESD4D(new Point(width, height), images);
                esd4d.parameters = processingParameters;
                try {
                    esd4d.Run();
                    output = esd4d.Output;
                } finally {
                    esd4d.close();
                    for (ImageFrame frame : images) if (frame.buffer != null) frame.close();
                }
            }
            processingParameters.mergeTimeNs = SystemClock.elapsedRealtimeNanos() - mergeStartNs;
            Log.d(TAG, (nativeBurst != null ? "Kompute" : "GLES") + " ESD4D merged " + images.size()
                    + " frames in " + processingParameters.mergeTimeNs / 1_000_000L + " ms");
            long postMergeT = System.currentTimeMillis();
            Allocator.logStage(TAG, "post-merge");
            Allocator.logProc(TAG, "post-merge");
            Log.d(TAG, "Stage[post-merge-logging] elapsed:" + (System.currentTimeMillis() - postMergeT) + " ms");
            IncreaseWLBL(processingParameters);
            outputF16 = true;
        } else if (images.get(0).packedBits > 0) {
            // Rare: burst setting packed frames at arrival but only one frame
            // survived filtering. Restore the single-frame path's 16-bit
            // contract via the exact inverse (unpacks losslessly, no staging
            // pool involvement).
            ImageFrame single = images.get(0);
            int pixels = single.width * single.height;
            ByteBuffer restored = Allocator.allocate(pixels * 2);
            if (restored == null) {
                throw new IllegalStateException("Single-frame packed restore failed");
            }
            Allocator.unpack16(restored, single.buffer, pixels, single.packedBits);
            single.close();
            single.buffer = restored;
            single.packedBits = 0;
            output = single.buffer;
            single.buffer = null;
        } else {
            output = images.get(0).buffer;
            images.get(0).buffer = null;
        }
        Log.d(TAG, "HDRX Alignment elapsed:" + (SystemClock.elapsedRealtime() - processingStartMs) + " ms");
        int saveMode = ImageFormatConfig.resolve(saveRAW, PhotonCamera.getSettings().isHeicSave());
        boolean useHeic = ImageFormatConfig.usesHeic(saveMode);
        if (useHeic && !HeicSupport.isHeicEncodeSupported()) {
            Log.e(TAG, "HEIC save mode on unsupported device; JPEG fallback");
            useHeic = false;
        }
        Bitmap img;
        PostPipeline pipeline = null;
        try {
            if (ImageFormatConfig.savesRaw(saveMode) && alignAlgorithm != 2) {
                // DNG carries uint16 raw counts: re-encode the fp16 merge back to
                // the IncreasedWL scale (FAKE_WL, black 0) just for the save.
                ByteBuffer dngOut = output;
                if (outputF16) {
                    dngOut = Allocator.createU16FromF16(output, width, height,
                            processingParameters.whiteLevel, processingParameters.blackLevel);
                }
                boolean imageSaved;
                try {
                    imageSaved = ImageSaver.Util.saveStackedRaw(dngFile, dngOut, processingParameters);
                } finally {
                    if (dngOut != output && dngOut != null) Allocator.free(dngOut);
                }
                processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
                if (ImageFormatConfig.isRawOnly(saveMode)) {
                    processingEventsListener.onProcessingFinished("HdrX RAW Processing Finished");
                    callback.onFinished();
                    Allocator.logStage(TAG, "raw-only-exit");
                    return;
                }
            }
            // PostPipeline consumes normalized fp16 raw (Bayer2Float uploads a
            // FLOAT_16 texture). The single-frame path skips ESD4D, so its raw
            // uint16 buffer is normalized here with the sensor levels.
            long renderStartNs = SystemClock.elapsedRealtimeNanos();
            if (!outputF16) {
                ByteBuffer f16 = Allocator.createF16(output, width, height,
                        processingParameters.whiteLevel, processingParameters.blackLevel);
                Allocator.free(output);
                output = f16;
            }

            processingParameters.noiseModeler.computeStackingNoiseModel(images.size());

            pipeline = new PostPipeline();
            if (nativeBurst != null) {
                nativeBurst.handOffKernels(pipeline);
            } else if (esd4d != null) {
                pipeline.kernelParams = esd4d.kernelsMapCPU;
                pipeline.kernelParamsSize = esd4d.kernelsMapCPUSize;
                pipeline.kernelParamsBase = esd4d.kernelsMapBase;
                // CPU copy handed off; UpscaleCrop nulls the pipeline side after
                // its GPU upload, freeing the result buffer for the render.
                esd4d.kernelsMapCPU = null;
                esd4d.kernelsMapCPUSize = null;
                esd4d.kernelsMapBase = null;
            }

            img = pipeline.Run(output, processingParameters);
            processingParameters.postProcessingTimeNs = SystemClock.elapsedRealtimeNanos() - renderStartNs;
            Allocator.logStage(TAG, "post-render");
            Allocator.logProc(TAG, "post-render");
        } finally {
            if (output != null && (pipeline == null || !pipeline.isStackFrameReleased())) Allocator.free(output);
            if (nativeBurst != null) nativeBurst.releaseKernels();
            if (esd4d != null && esd4d.kernelsMapBase != null) Allocator.free(esd4d.kernelsMapBase);
            if (pipeline != null && pipeline.kernelParamsBase != null) {
                Allocator.free(pipeline.kernelParamsBase);
                pipeline.kernelParamsBase = null;
                pipeline.kernelParams = null;
            }
        }
        Allocator.logStage(TAG, "post-raw-free");
        Allocator.logProc(TAG, "post-raw-free");
        // Deterministic collection of pipeline garbage (e.g. ~90 MB transient
        // Java inside Amaze at 50 MP): survival was GC-timing-dependent before
        // this (collected-an-encode-survived vs retained-and-LMK). A hint only;
        // ART still decides, but it removes the flakiness in practice.
        System.gc();

        PostPipeline.GainMapRaw gm = null;
        if (PhotonCamera.getSettings().ultraHdr) {
            long gainMapStartNs = SystemClock.elapsedRealtimeNanos();
            try {
                gm = pipeline.RunHDRGainMap(processingParameters, img, GainMapComputer.SCALE);
            } catch (Exception e) {
                Log.e(TAG, "Ultra HDR gain-map pass failed, falling back to SDR JPEG", e);
            } finally {
                processingParameters.postProcessingTimeNs += SystemClock.elapsedRealtimeNanos() - gainMapStartNs;
            }
        }
        Allocator.logStage(TAG, "post-gainmap");

        if (!pipeline.debugData.isEmpty()) {
            long overlayStartNs = SystemClock.elapsedRealtimeNanos();
            img = overlay(img, pipeline.debugData.toArray(new Bitmap[0]));
            processingParameters.postProcessingTimeNs += SystemClock.elapsedRealtimeNanos() - overlayStartNs;
        }
        if (PhotonCamera.DEBUG) {
            processingParameters.peakVramMB = GLTexture.getPeakVramMB();
            processingParameters.peakMemoryMB = Allocator.getPeakMemoryMB();
        }
        exifData.IMAGE_DESCRIPTION = processingParameters.toString();
        if (PhotonCamera.DEBUG) {
            Log.d(TAG, processingParameters.processingTimeDescription()
                    + " PeakVram=" + processingParameters.peakVramMB
                    + "MB PeakMemory=" + processingParameters.peakMemoryMB + "MB");
        }
        try {
            processingEventsListener.onProcessingFinished("HdrX JPG Processing Finished");
        }
        catch (Exception e){
            Log.d(TAG,"Error in processingEventsListener.onProcessingFinished:"+Log.getStackTraceString(e));
        }
        imageFile = Paths.get(imageFile.toAbsolutePath()
                + (useHeic ? ".heic" : ".jpg"));
        StillEncoder.Result still = StillEncoder.encodeStill(
                imageFile, img, gm, exifData, useHeic);
        boolean imageSaved = still.saved;
        imageFile = still.file;

        try {
            processingEventsListener.notifyImageSavedStatus(imageSaved, imageFile);
        }
        catch (Exception e){
            Log.d(TAG,"Error in processingEventsListener.notifyImageSavedStatus:"+Log.getStackTraceString(e));
        }

        try {
            pipeline.close();
        } catch (Exception e) {
            Log.e(TAG, "PostPipeline close failed (non-fatal): " + Log.getStackTraceString(e));
        }


        Allocator.logStage(TAG, "hdrx-end");
        callback.onFinished();
    }

    /**
     * Single-frame noise model for the burst input frame DNGs: the
     * ESD4D-fitted noiseS/noiseO live in NoiseModeler.baseModel after the
     * merge. The merged DNG keeps the stacked computeModel through
     * DngCreator.setParameters.
     */
    private static double[] singleFrameNoiseModel(Parameters parameters) {
        if (parameters.noiseModeler == null || parameters.noiseModeler.baseModel == null) {
            return null;
        }
        double[] noise = new double[6];
        for (int c = 0; c < 3; c++) {
            Pair<Double, Double> model = parameters.noiseModeler.baseModel[c];
            noise[2 * c] = model.first;
            noise[2 * c + 1] = model.second;
        }
        return noise;
    }

}
