package com.particlesdevs.photoncamera.processing.processor;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;

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
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapComputer;
import com.particlesdevs.photoncamera.processing.ultrahdr.UltraHdrEncoder;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.NightReferenceSelector;
import com.particlesdevs.photoncamera.processing.parameters.NightRawMetrics;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.Allocator;

import java.nio.ByteBuffer;
import java.nio.file.Files;
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

    private NightReferenceSelector.Quality[] nightQuality(ArrayList<ImageFrame> images, Parameters parameters,
                                                         double[] energy, double minimum) {
        NightReferenceSelector.Quality[] quality = new NightReferenceSelector.Quality[images.size()];
        NightRawMetrics[] metrics = new NightRawMetrics[images.size()];
        ArrayList<Integer> shorts = new ArrayList<>();
        long intent = 0;
        Integer clock = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        if (clock != null && clock == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
                && captureRequest != null && captureRequest.getTag() instanceof NightReferenceSelector.CaptureIntent)
            intent = ((NightReferenceSelector.CaptureIntent) captureRequest.getTag()).elapsedRealtimeNs;
        double shot = 0, readout = 0;
        for (android.util.Pair<Double, Double> channel : parameters.noiseModeler.baseModel) {
            shot += channel.first / 3.0; readout += channel.second / 3.0;
        }
        for (int i = 0; i < images.size(); i++) {
            if (energy[i] != minimum) continue;
            shorts.add(i);
            ImageFrame frame = images.get(i);
            NightReferenceSelector.Quality q = new NightReferenceSelector.Quality();
            quality[i] = q;
            metrics[i] = NightRawMetrics.measure(frame.buffer, parameters.rawSize.x, parameters.rawSize.y,
                    parameters.whiteLevel, parameters.blackLevel, shot, readout);
            if (metrics[i] != null) {
                q.sharpness = metrics[i].sharpness; q.clipping = metrics[i].clippedFraction;
            }
            q.gyroBlur = gyroBlurExtent(frame.frameGyro);
            double seconds = (frame.timestamp - (double) intent) * 1e-9 + frame.pair.exposure * .5e-9;
            if (intent > 0 && seconds >= 0 && seconds < 60) q.timeDistanceSeconds = seconds;
        }
        // Three distributed short-frame anchors, excluding self and duplicates.
        for (int i : shorts) {
            if (metrics[i] == null) continue;
            double[] changes = new double[3], alignment = new double[3];
            int nc = 0, na = 0, last = -1;
            for (int a = 0; a < 3; a++) {
                int j = shorts.get(a * (shorts.size() - 1) / 2);
                if (j == i || j == last || metrics[j] == null) continue;
                last = j;
                double[] comparison = metrics[i].compare(metrics[j]);
                if (Double.isFinite(comparison[0])) changes[nc++] = comparison[0];
                if (Double.isFinite(comparison[1])) alignment[na++] = comparison[1];
            }
            java.util.Arrays.sort(changes, 0, nc);
            java.util.Arrays.sort(alignment, 0, na);
            if (nc > 0) quality[i].subjectChange = changes[nc / 2];
            if (na > 0) quality[i].alignment = alignment[na / 2];
        }
        return quality;
    }

    private static double gyroBlurExtent(GyroBurst burst) {
        if (burst == null || burst.samples <= 0 || burst.movementss == null || burst.movementss.length < 3)
            return Double.NaN;
        double squaredExtent = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (burst.movementss[axis] == null || burst.movementss[axis].length < burst.samples) return Double.NaN;
            double position = 0, low = 0, high = 0;
            for (int sample = 0; sample < burst.samples; sample++) {
                float increment = burst.movementss[axis][sample];
                if (!Float.isFinite(increment)) return Double.NaN;
                position += increment; low = Math.min(low, position); high = Math.max(high, position);
            }
            squaredExtent += (high - low) * (high - low);
        }
        return Math.sqrt(squaredExtent);
    }

    private void ApplyHdrX() {
        callback.onStarted();
        processingEventsListener.onProcessingStarted("HDRX");

        Log.d(TAG, "ApplyHdrX() called from" + Thread.currentThread().getName());

        long startTime = System.currentTimeMillis();
        Log.d(TAG, "ApplyHdrX() mImageFramesToProcess.size():" + mImageFramesToProcess.size());
        int width = mImageFramesToProcess.get(0).width;
        int height = mImageFramesToProcess.get(0).height;
        Log.d(TAG, "APPLY HDRX: buffer:" + mImageFramesToProcess.get(0).buffer.asShortBuffer().remaining());
        Log.d(TAG, "Api WhiteLevel:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG, "Api BlackLevel:" + characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        Parameters processingParameters = new Parameters();
        processingParameters.FillConstParameters(characteristics, new Point(width, height));
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
        for (int i = 0; i < mImageFramesToProcess.size(); i++) {
            ImageFrame frame = mImageFramesToProcess.get(i);
            // Incomplete sequences cannot safely be associated by index. Do
            // not recycle another frame's measurements or divide by zero.
            frame.frameGyro = BurstShakiness.size() == mImageFramesToProcess.size()
                    && BurstShakiness.get(i) != null ? BurstShakiness.get(i) : new GyroBurst(0);
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
            Log.d(TAG, "Mpy:" + frame.pair.layerMpy);
            images.add(frame);
            ISO += frame.pair.iso;
        }
        ISO /= mImageFramesToProcess.size();

        processingParameters.FillDynamicParameters(captureResult, captureRequest,ISO);
        processingParameters.cameraRotation = cameraRotation;

        ParseExif.syncWithParameters(exifData, processingParameters);
        ImageFrameDeblur imageFrameDeblur = new ImageFrameDeblur(processingParameters);
        imageFrameDeblur.firstFrameGyro = images.get(0).frameGyro.clone();
        for (int i = 0; i < images.size(); i++)
            imageFrameDeblur.processDeblurPosition(images.get(i));
        if (cameraMode == CameraMode.NIGHT) {
            double[] frameExposures = new double[images.size()];
            float[] shake = new float[images.size()];
            for (int i = 0; i < images.size(); i++) {
                ImageFrame frame = images.get(i);
                frameExposures[i] = exposures.get(frame.timestamp);
                shake[i] = frame.frameGyro.samples > 0
                        ? frame.frameGyro.shakiness : Float.NaN;
            }
            NightReferenceSelector.Quality[] quality = nightQuality(images, processingParameters, frameExposures, minExpo);
            int reference = NightReferenceSelector.select(frameExposures, shake, quality);
            ImageFrame selectedFrame = images.remove(reference);
            images.add(0, selectedFrame);
            // Keep alternates in capture order. Their useful regions still
            // contribute; the existing local merge gates reject disagreement.
            // Whole-frame shake rejection loses valid static regions and SNR.
            Log.d(TAG, "Night reference=" + selectedFrame.number
                    + " shake=" + shake[reference] + " retained=" + images.size());
            NightReferenceSelector.Quality q = quality[reference];
            if (q != null) Log.d(TAG, "Night quality sharp=" + q.sharpness + " clipping=" + q.clipping
                    + " gyroExtent=" + q.gyroBlur + " subjectChange=" + q.subjectChange
                    + " alignment=" + q.alignment + " shutterDistance=" + q.timeDistanceSeconds);
        } else {
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
        }



        Log.d(TAG, "White Level:" + processingParameters.whiteLevel);
        Log.d(TAG, "Wrapper.loadFrame");
        //float noiseLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) *
        //        IsoExpoSelector.getMPY() - 40.)*6400.f / (6.2f*IsoExpoSelector.getISOAnalog());

        ByteBuffer output = null;
        Log.d(TAG, "Packing");
        //WrapperAl.packImages();
        Log.d(TAG, "Packed");
        ESD4D esd4d = null;
        if(images.size() > 1) {
            esd4d = new ESD4D(new Point(width, height), images);
            esd4d.parameters = processingParameters;
            esd4d.Run();
            esd4d.close();
            output = esd4d.Output;
            for (int i = 0; i < images.size(); i++) {
                images.get(i).close();
            }
            IncreaseWLBL(processingParameters);
        } else {
            output = images.get(0).buffer;
            images.get(0).buffer = null;
        }
        Log.d(TAG, "HDRX Alignment elapsed:" + (System.currentTimeMillis() - startTime) + " ms");
        if ((saveRAW >= 1) && alignAlgorithm != 2) {
            boolean imageSaved = ImageSaver.Util.saveStackedRaw(dngFile, output,
                    processingParameters);
            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
            if (saveRAW == 2) {
                processingEventsListener.onProcessingFinished("HdrX RAW Processing Finished");
                callback.onFinished();
                Allocator.free(output);
                Allocator.getMemoryCount();
                return;
            }
        }

        processingParameters.noiseModeler.computeStackingNoiseModel(images.size());

        PostPipeline pipeline = new PostPipeline();
        pipeline.kernelParams = esd4d != null ? esd4d.kernelsMapCPU : null;
        pipeline.kernelParamsSize = esd4d != null ? esd4d.kernelsMapCPUSize : null;
        pipeline.nightUncertainty = esd4d != null ? esd4d.nightUncertaintyCPU : null;
        pipeline.nightUncertaintySize = esd4d != null ? esd4d.nightUncertaintySize : null;
        pipeline.nightNominalReduction = Math.max(1.0f, images.size() * .9f);

        Bitmap img = pipeline.Run(output, processingParameters);

        PostPipeline.GainMapRaw gm = null;
        if (PhotonCamera.getSettings().ultraHdr) {
            // Must run before the raw frame buffer is freed.
            try {
                gm = pipeline.RunHDRGainMap(output, processingParameters, img,
                        GainMapComputer.SCALE_DOWN, GainMapComputer.SCALE);
            } catch (Exception e) {
                Log.e(TAG, "Ultra HDR gain-map pass failed, falling back to SDR JPEG", e);
            }
        }

        Allocator.free(output);

        img = overlay(img, pipeline.debugData.toArray(new Bitmap[0]));
        try {
            processingEventsListener.onProcessingFinished("HdrX JPG Processing Finished");
        }
        catch (Exception e){
            Log.d(TAG,"Error in processingEventsListener.onProcessingFinished:"+Log.getStackTraceString(e));
        }
        imageFile = Paths.get(imageFile.toAbsolutePath() + ".jpg");
        boolean imageSaved;
        if (PhotonCamera.getSettings().ultraHdr && gm != null) {
            try {
                GainMapComputer.Result res = GainMapComputer.compute(gm.bitmap, gm.down, gm.scale);
                byte[] uhdr = UltraHdrEncoder.encode(img, res, exifData);
                Files.write(imageFile, uhdr);
                img.recycle();
                imageSaved = true;
            } catch (Exception e) {
                Log.e(TAG, "Ultra HDR encode failed, falling back to SDR JPEG", e);
                imageSaved = ImageSaver.Util.saveBitmapAsJPG(imageFile, img,
                        ImageSaver.JPG_QUALITY, exifData);
            }
        } else {
            //Saves the final bitmap
            imageSaved = ImageSaver.Util.saveBitmapAsJPG(imageFile, img,
                    ImageSaver.JPG_QUALITY, exifData);
        }

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


        Allocator.getMemoryCount();
        callback.onFinished();
    }

}
