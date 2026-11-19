package com.particlesdevs.photoncamera.processing.opengl;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.Gyro;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.cpu.HalideAlignment;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.processor.HdrxProcessor;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.ui.camera.CameraActivity;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.Assert.*;

/** Opt-in phone benchmark: one real burst, identical replay inputs, restored preferences. */
@RunWith(AndroidJUnit4.class)
public class GyroProcessingBenchmark {
    private static final String TAG="GyroBench";
    private static final String PREFIX="pref_tunable_esd4d_";
    private final ArrayList<ImageFrame> originals=new ArrayList<>();
    private final ArrayList<GyroBurst> motion=new ArrayList<>();
    private final Map<Long,TotalCaptureResult> results=new ConcurrentHashMap<>();
    private CameraCharacteristics camera;
    private CaptureRequest request;
    private String cameraId;

    @Test public void benchmarkCapturedBurst() throws Exception {
        Bundle args=InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Opt in with -e gyroBenchmark true",Boolean.parseBoolean(args.getString("gyroBenchmark","false")));
        assertTrue("Benchmark requires the native Halide aligner",HalideAlignment.isAvailable());
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        PhotonCamera.getSettings().loadCache();
        cameraId=args.getString("gyroCameraId",PhotonCamera.getSettings().mCameraID);
        int frames=Integer.parseInt(args.getString("frames","22"));
        int rounds=Integer.parseInt(args.getString("rounds","3"));
        SettingsManager settings=PhotonCamera.getSettingsManagerStatic();
        SharedPreferences prefs=settings.getDefaultPreferences();
        String burstKey=context.getString(R.string.pref_save_burst_dngs_key);
        String[] keys={PREFIX+"gyroprealignment",PREFIX+"gyroonly",PREFIX+"alignerselect",
                PREFIX+"enablenoisestore",PREFIX+"profilegpusync",PREFIX+"aligndebugcompare",burstKey};
        Map<String,?> saved=prefs.getAll();
        String savedCamera=PhotonCamera.getSettings().mCameraID;
        CameraCharacteristics savedCharacteristics=CaptureController.mCameraCharacteristics;
        CaptureResult savedResult=CaptureController.mCaptureResult;
        CaptureResult savedPreview=CaptureController.mPreviewCaptureResult;
        CaptureRequest savedRequest=CaptureController.mCaptureRequest;
        CaptureController savedController=PhotonCamera.getCaptureController();
        try {
            settings.set(SettingsManager.SCOPE_GLOBAL,PreferenceKeys.Key.KEY_SAVE_BURST_DNGS,false);
            prefs.edit().putInt(PREFIX+"enablenoisestore",0).putInt(PREFIX+"profilegpusync",0)
                    .putInt(PREFIX+"aligndebugcompare",0).putString(PREFIX+"alignerselect","halide").commit();
            PhotonCamera.getSettings().mCameraID=cameraId;
            CaptureController controller=initializeController(context);
            PhotonCamera.setCaptureController(controller);
            capture(context,frames);
            CaptureController.mCameraCharacteristics=camera;
            CaptureController.mCaptureResult=results.get(originals.get(0).timestamp);
            CaptureController.mPreviewCaptureResult=CaptureController.mCaptureResult;
            CaptureController.mCaptureRequest=request;
            // RAW-only sessions on this HAL return NaN neutral-point rationals.
            // Use the same fixed white balance for every replay, without a live preview.
            controller.mPreviewTemp=new android.util.Rational[]{new android.util.Rational(1,1),
                    new android.util.Rational(1,1),new android.util.Rational(1,1)};
            controller.mColorSpaceTransform=CaptureController.mCaptureResult.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
            controller.mPreviewIso=CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY);
            controller.mPreviewExposureTime=CaptureController.mCaptureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            controller.mSensorOrientation=camera.get(CameraCharacteristics.SENSOR_ORIENTATION);
            android.util.Log.i(TAG,"FIXTURE camera="+cameraId+" size="+originals.get(0).width+"x"+originals.get(0).height
                    +" frames="+frames+" gyroValid="+motion.stream().filter(g->g.orientationValid).count()
                    +" halideRefine="+saved.get(PREFIX+"haliderefineflow")
                    +" alignmentStartLevel="+saved.get(PREFIX+"alignmentstartlevel")+" fixedWhiteBalance=true");
            for(int mode=0;mode<3;mode++) run(context,prefs,mode,-1);
            int[][] order={{0,1,2},{2,0,1},{1,2,0}};
            for(int round=0;round<rounds;round++) for(int mode:order[round%order.length]) run(context,prefs,mode,round);
        } finally {
            SharedPreferences.Editor restore=prefs.edit();
            for(String key:keys) {
                Object value=saved.get(key);
                if(value==null) restore.remove(key);
                else if(value instanceof Integer) restore.putInt(key,(Integer)value);
                else if(value instanceof Float) restore.putFloat(key,(Float)value);
                else if(value instanceof Boolean) restore.putBoolean(key,(Boolean)value);
                else if(value instanceof Long) restore.putLong(key,(Long)value);
                else if(value instanceof String) restore.putString(key,(String)value);
                else throw new IllegalStateException("Unexpected preference type for "+key);
            }
            restore.commit();
            PhotonCamera.getSettings().mCameraID=savedCamera;
            CaptureController.mCameraCharacteristics=savedCharacteristics;
            CaptureController.mCaptureResult=savedResult;
            CaptureController.mPreviewCaptureResult=savedPreview;
            CaptureController.mCaptureRequest=savedRequest;
            PhotonCamera.setCaptureController(savedController);
            for(ImageFrame frame:originals) frame.close();
            originals.clear();
            android.util.Log.i(TAG,"RESTORED preferences and released fixture");
        }
    }

    // Processing reads the controller's preview white balance and sensor orientation.
    // Initialize the ordinary camera screen, then close it so replay has no live preview load.
    private CaptureController initializeController(Context context) throws Exception {
        Application app=(Application)context.getApplicationContext();
        AtomicReference<CaptureController> controller=new AtomicReference<>();
        CountDownLatch closed=new CountDownLatch(1);
        Handler main=new Handler(Looper.getMainLooper());
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a,Bundle state) { }
            public void onActivityStarted(Activity a) { }
            public void onActivityResumed(Activity a) {
                if(a instanceof CameraActivity) main.post(() -> {
                    controller.set(PhotonCamera.getCaptureController());
                    a.finish();
                });
            }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a,Bundle state) { }
            public void onActivityDestroyed(Activity a) { if(a instanceof CameraActivity) main.post(closed::countDown); }
        };
        app.registerActivityLifecycleCallbacks(callbacks);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.startActivity(new Intent(app,CameraActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)));
            assertTrue("Camera initialization timed out",closed.await(20,TimeUnit.SECONDS));
            assertNotNull(controller.get());
            return controller.get();
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks); }
    }

    private void run(Context context,SharedPreferences prefs,int mode,int round) throws Exception {
        prefs.edit().putInt(PREFIX+"gyroprealignment",mode==0?0:1).putInt(PREFIX+"gyroonly",mode==2?1:0).commit();
        String name=new String[]{"halide","gyro_halide","gyro_only"}[mode];
        ArrayList<ImageFrame> copies=new ArrayList<>();
        HashMap<Long,Double> exposures=new HashMap<>();
        FrameNumberSelector.frameCount=originals.size();
        FrameNumberSelector.throwCount=0;
        IsoExpoSelector.fullpairs.clear();
        for(ImageFrame source:originals) {
            ImageFrame frame=new ImageFrame(source.buffer);
            frame.width=source.width; frame.height=source.height; frame.timestamp=source.timestamp;
            frame.fullWidth=source.fullWidth; frame.fullHeight=source.fullHeight;
            copies.add(frame);
            TotalCaptureResult metadata=results.get(source.timestamp);
            int iso=metadata.get(CaptureResult.SENSOR_SENSITIVITY);
            long exposure=metadata.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            exposures.put(source.timestamp,iso*exposure/1e9);
            IsoExpoSelector.ExpoPair pair=new IsoExpoSelector.ExpoPair(exposure,1000,1_000_000_000L,iso,50,12800,100);
            pair.layerMpy=1; pair.curlayer=IsoExpoSelector.ExpoPair.exposureLayer.Normal;
            IsoExpoSelector.fullpairs.add(pair);
        }
        Path output=context.getCacheDir().toPath().resolve("gyro-benchmark-output");
        AtomicReference<String> error=new AtomicReference<>();
        AtomicLong started=new AtomicLong(), processingNs=new AtomicLong();
        AtomicBoolean completed=new AtomicBoolean(), encoded=new AtomicBoolean();
        ProcessingEventsListener listener=new ProcessingEventsListener() {
            public void onProcessingStarted(String process) { started.set(SystemClock.elapsedRealtimeNanos()); }
            public void onProcessingChanged(Object value) { }
            public void onProcessingFinished(Object value) { processingNs.set(SystemClock.elapsedRealtimeNanos()-started.get()); }
            public void onProcessingError(Object value) { error.set(String.valueOf(value)); }
            public void notifyImageSavedStatus(boolean success,Path path) { encoded.set(success); }
        };
        HdrxProcessor processor=new HdrxProcessor(listener);
        processor.configure(0,0,CameraMode.MOTION);
        android.util.Log.i(TAG,"BEGIN mode="+name+" round="+round);
        long start=SystemClock.elapsedRealtimeNanos();
        try {
            processor.start(output.resolveSibling("gyro-benchmark-output.dng"),output,new ParseExif.ExifData(),motion,
                    copies,exposures,ImageFormat.RAW_SENSOR,0,camera,results.get(originals.get(0).timestamp),request,
                    new ProcessorBase.ProcessingCallback() {
                        public void onStarted() { }
                        public void onFailed() { error.set("Processor failed"); }
                        public void onFinished() { completed.set(true); }
                    });
            assertNull(error.get()); assertTrue(completed.get()); assertTrue(processingNs.get()>0); assertTrue(encoded.get());
            android.util.Log.i(TAG,"RESULT mode="+name+" round="+round+" processing_ms="+(processingNs.get()/1e6)
                    +" with_encode_ms="+((SystemClock.elapsedRealtimeNanos()-start)/1e6));
        } finally {
            for(ImageFrame frame:copies) frame.close();
            Files.deleteIfExists(output.resolveSibling("gyro-benchmark-output.jpg"));
            Files.deleteIfExists(output.resolveSibling("gyro-benchmark-output.heic"));
            System.gc();
        }
    }

    private void capture(Context context,int count) throws Exception {
        CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        camera=manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map=camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size size=Collections.min(Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                (a,b)->Long.compare((long)a.getWidth()*a.getHeight(),(long)b.getWidth()*b.getHeight()));
        HandlerThread thread=new HandlerThread("GyroBenchmarkCapture"); thread.start();
        Handler handler=new Handler(thread.getLooper());
        Gyro gyro=new Gyro((SensorManager)context.getSystemService(Context.SENSOR_SERVICE));
        AtomicReference<CameraDevice> device=new AtomicReference<>();
        AtomicReference<CameraCaptureSession> session=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        AtomicBoolean retain=new AtomicBoolean();
        Map<Long,ImageFrame> frames=new ConcurrentHashMap<>();
        Semaphore arrivals=new Semaphore(0);
        CountDownLatch opened=new CountDownLatch(1), configured=new CountDownLatch(1), finished=new CountDownLatch(1);
        int cfa=camera.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        try(ImageReader reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.RAW_SENSOR,3)) {
            gyro.register();
            reader.setOnImageAvailableListener(r->{try(Image image=r.acquireNextImage()) {
                if(image!=null && retain.get()) {
                    Image.Plane plane=image.getPlanes()[0];
                    ImageFrame frame=ImageFrame.fromCrop(plane.getBuffer(),image.getFormat(),image.getWidth(),image.getHeight(),
                            plane.getRowStride(),plane.getPixelStride(),new Rect(0,0,image.getWidth(),image.getHeight()),false,cfa);
                    frame.timestamp=image.getTimestamp();
                    frames.put(frame.timestamp,frame); arrivals.release();
                }
            }},handler);
            manager.openCamera(cameraId,new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice c) { device.set(c); opened.countDown(); }
                public void onDisconnected(CameraDevice c) { error.set("Camera disconnected"); c.close(); opened.countDown(); }
                public void onError(CameraDevice c,int e) { error.set("Camera error "+e); c.close(); opened.countDown(); }
            },handler);
            assertTrue(opened.await(15,TimeUnit.SECONDS)); assertNull(error.get()); assertNotNull(device.get());
            device.get().createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) { session.set(s); configured.countDown(); }
                public void onConfigureFailed(CameraCaptureSession s) { error.set("Configure failed"); configured.countDown(); }
            },handler);
            assertTrue(configured.await(15,TimeUnit.SECONDS)); assertNull(error.get());
            CaptureRequest.Builder builder=device.get().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(reader.getSurface());
            builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,20_000_000L);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY,400);
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            Gyro.enableOisReporting(builder,camera);
            request=builder.build();
            CountDownLatch warmup=new CountDownLatch(15);
            session.get().setRepeatingRequest(request,new CameraCaptureSession.CaptureCallback() {
                public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result) { warmup.countDown(); }
            },handler);
            assertTrue(warmup.await(10,TimeUnit.SECONDS)); session.get().stopRepeating();
            gyro.PrepareGyroBurst(motion); retain.set(true);
            session.get().captureBurst(Collections.nCopies(count,request),new CameraCaptureSession.CaptureCallback() {
                public void onCaptureStarted(CameraCaptureSession s,CaptureRequest r,long ts,long number) { gyro.CaptureGyroBurst(ts,20_000_000L); }
                public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result) {
                    gyro.recordCaptureResult(result,camera); results.put(result.get(CaptureResult.SENSOR_TIMESTAMP),result);
                }
                public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure failure) { error.set("Capture failed"); }
                public void onCaptureSequenceCompleted(CameraCaptureSession s,int sequence,long number) { finished.countDown(); }
            },handler);
            assertTrue(finished.await(30,TimeUnit.SECONDS)); assertNull(error.get());
            gyro.CompleteSequence(); assertEquals(count,motion.size());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            for(GyroBurst record:motion) {
                while(!frames.containsKey(record.frameTimestamp)) {
                    long remaining=deadline-System.nanoTime();
                    assertTrue(remaining>0 && arrivals.tryAcquire(remaining,TimeUnit.NANOSECONDS));
                }
                assertTrue("Missing gyro coverage",record.orientationValid);
                originals.add(frames.remove(record.frameTimestamp));
            }
        } finally {
            if(session.get()!=null) session.get().close();
            if(device.get()!=null) device.get().close();
            gyro.unregister(); thread.quitSafely(); thread.join(3000);
            for(ImageFrame frame:frames.values()) frame.close();
        }
    }
}
