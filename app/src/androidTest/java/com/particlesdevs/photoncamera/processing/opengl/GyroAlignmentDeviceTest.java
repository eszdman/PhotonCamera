package com.particlesdevs.photoncamera.processing.opengl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Half;
import android.util.Size;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.control.Gyro;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.GyroAlignment;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.cpu.HalideAlignment;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GyroAlignmentDeviceTest {
    private static final String TAG = "GyroDeviceTest";
    private static final int W = 512, H = 512;

    private ImageFrame frame(int dx, int dy) {
        ByteBuffer data = ByteBuffer.allocateDirect(W*H*2).order(ByteOrder.nativeOrder());
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double sx = Math.max(0,Math.min(W-1,x-dx));
            double sy = Math.max(0,Math.min(H-1,y-dy));
            float value = (float)(0.45+0.15*Math.sin(sx*0.073+sy*0.037)
                    +0.13*Math.cos(sy*0.11)+0.12*Math.sin(sx*0.18-sy*0.08));
            data.putShort(Half.toHalf(value));
        }
        data.rewind();
        ImageFrame frame = new ImageFrame(data);
        frame.width = W; frame.height = H; frame.fp16 = true;
        frame.pair = new IsoExpoSelector.ExpoPair(10_000_000,1_000_000,100_000_000,100,100,6400,100);
        frame.pair.layerMpy = 1;
        frame.pair.curlayer = IsoExpoSelector.ExpoPair.exposureLayer.Normal;
        return frame;
    }

    @Test public void gpuPrewarpPreservesBayerPlanesAndSubpixels() {
        final int w=130,h=98; // Partial workgroups exercise bounds guards, too.
        ByteBuffer raw=ByteBuffer.allocateDirect(w*h*2).order(ByteOrder.nativeOrder());
        float[] values=new float[w*h];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            short bits=Half.toHalf(0.08f+0.15f*((x&1)+2*(y&1))+0.001f*x+0.0006f*y);
            raw.putShort(bits);
            values[y*w+x]=Half.toFloat(bits);
        }
        raw.rewind();
        float[][] matrices={{1,0,0,0,1,0,0,0,1},
                {1,0,2.5f,0,1,-1.25f,0,0,1},
                {0.9998f,-0.02f,1.3f,0.02f,0.9998f,-1.1f,0.00004f,-0.00003f,1}};
        try(GLContext context=new GLContext(1,1);
            GLTexture input=new GLTexture(new Point(w,h),new GLFormat(GLFormat.DataType.FLOAT_16,1),null);
            GpuGyroPrewarp warp=new GpuGyroPrewarp(context.mProgram,input)) {
            for(float[] m:matrices) {
                try(GpuGyroPrewarp.WarpedFrame warped=warp.warp(raw,m)) {
                    assertTrue(warped.pixels.isDirect());
                    assertEquals(w*h*2,warped.pixels.capacity());
                    for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                        int px=x&1,py=y&1;
                        double z=m[6]*x+m[7]*y+m[8];
                        double u=Math.max(0,Math.min(w/2-1,((m[0]*x+m[1]*y+m[2])/z-px)/2));
                        double v=Math.max(0,Math.min(h/2-1,((m[3]*x+m[4]*y+m[5])/z-py)/2));
                        int x0=(int)u,y0=(int)v,x1=Math.min(x0+1,w/2-1),y1=Math.min(y0+1,h/2-1);
                        double dx=u-x0,dy=v-y0;
                        double top=values[(2*y0+py)*w+2*x0+px]*(1-dx)+values[(2*y0+py)*w+2*x1+px]*dx;
                        double bottom=values[(2*y1+py)*w+2*x0+px]*(1-dx)+values[(2*y1+py)*w+2*x1+px]*dx;
                        assertEquals("Bayer warp at "+x+","+y,top*(1-dy)+bottom*dy,
                                Half.toFloat(warped.pixels.getShort((y*w+x)*2)),0.0005);
                    }
                }
                assertEquals(0,raw.position());
            }
            for(int i=0;i<values.length;i++) assertEquals(values[i],Half.toFloat(raw.getShort(i*2)),0);
            assertEquals("GPU prewarp GL error",android.opengl.GLES30.GL_NO_ERROR,android.opengl.GLES30.glGetError());
        }
    }

    @Test public void halideActuallyMatchesThePrewarpedImage() {
        assertTrue(HalideAlignment.isAvailable());
        try (GLContext context = new GLContext(1,1);
             GLTexture input = new GLTexture(new Point(W,H),new GLFormat(GLFormat.DataType.FLOAT_16,1),null)) {
            ArrayList<ImageFrame> frames = new ArrayList<>();
            frames.add(frame(0,0)); frames.add(frame(24,-16));
            try {
                for (boolean refine : new boolean[]{false,true}) {
                    int tile = refine ? 16 : 32;
                    Parameters p = new Parameters();
                    p.rawSize = new Point(W,H); p.tile = 16; p.tilesX = 1;
                    p.alignmentSize = new Point(W/tile+1,H/tile+1);
                    for (boolean gyro : new boolean[]{false,true}) {
                        try (HalideAlignment align = new HalideAlignment(p.alignmentSize,frames,context.mProgram,input)) {
                            align.parameters = p; align.refineFlow = refine;
                            if (gyro) align.gyroHomographies = new float[][]{null,{1,0,24,0,1,-16,0,0,1}};
                            align.Run();
                            try {
                                align.Result.BufferLoad();
                                FloatBuffer data = align.Result.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),true)
                                        .order(ByteOrder.nativeOrder()).asFloatBuffer();
                                ArrayList<Float> errors = new ArrayList<>();
                                ArrayList<Float> magnitudes = new ArrayList<>();
                                for (int y=3;y<p.alignmentSize.y-3;y++) for (int x=3;x<p.alignmentSize.x-3;x++) {
                                    int o = (y*p.alignmentSize.x+x)*4;
                                    float dx = Math.round(data.get(o)*(W/2))+data.get(o+2);
                                    float dy = Math.round(data.get(o+1)*(H/2))+data.get(o+3);
                                    errors.add((float)Math.hypot(dx-(gyro?0:12),dy-(gyro?0:-8)));
                                    magnitudes.add((float)Math.hypot(dx,dy));
                                }
                                Collections.sort(errors);
                                float median = errors.get(errors.size()/2);
                                android.util.Log.i(TAG,"Halide refine="+refine+" gyro="+gyro+" median vector error="+median);
                                Collections.sort(magnitudes);
                                // Periodic texture can mislock without a prior. What this test
                                // proves is that both kernels consume the GPU-warped pixels:
                                // gyro-on must become an exact identity match, gyro-off must not.
                                if (gyro) assertTrue("Unexpected prewarped residual="+median,median<0.75f);
                                else assertTrue("Unwarped frame should need displacement",magnitudes.get(magnitudes.size()/2)>4);
                            } finally { align.Result.close(); }
                        }
                    }
                }
            } finally { for (ImageFrame f : frames) f.close(); }
        }
    }

    @Test public void mergeAlignGyroOnlyPreservesSubpixelsOnPhoneGpu() {
        final int w=64,h=48;
        try (GLContext context = new GLContext(1,1)) {
            GLProg prog = context.mProgram;
            for (int cfa=0;cfa<4;cfa++) for (int tile : new int[]{16,32}) {
                Point shift = new Point(cfa%2,cfa/2), size = new Point(w+shift.x,h+shift.y);
                float[] data = new float[size.x*size.y*4];
                for (int y=0;y<size.y;y++) for(int x=0;x<size.x;x++) for(int c=0;c<4;c++)
                    data[(y*size.x+x)*4+c]=0.05f+0.1f*c+0.002f*x+0.001f*y;
                Point atlasSize=new Point(w*2/tile+1,h*2/tile+1);
                try (GLTexture source=new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(data));
                     GLTexture base=new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(data));
                     GLTexture out=new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
                     GLTexture atlas=new GLTexture(atlasSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),
                             BufferUtils.getFrom(new float[atlasSize.x*atlasSize.y*4]));
                     GLTexture raw=new GLTexture(new Point(w*2,h*2),new GLFormat(GLFormat.DataType.FLOAT_16,1),null)) {
                    prog.setDefine("TILE_AL",tile); prog.setDefine("GYRO_WARP",1);
                    prog.setLayout(8,8,1); prog.useAssetProgram("merge/mergeAlign",true);
                    prog.setVar("gyroHomography",false,GyroAlignment.columnMajor(new float[]{1,0,0.75f,0,1,-1.25f,0,0,1}));
                    prog.setVar("rawHalf",new Point(w,h)); prog.setVar("cfaShift",shift);
                    prog.setVar("alignmentSize",atlasSize); prog.setVar("shift",new Point(0,0));
                    prog.setVar("exposure",1f); prog.setVar("border",new Point(16,16));
                    prog.setTexture("inTexture",raw); prog.setTexture("alignmentTexture",atlas);
                    prog.setTextureCompute("baseTexture",base,false);
                    prog.setTextureCompute("alterTexture",source,false);
                    prog.setTextureCompute("outTexture",out,true);
                    prog.computeAuto(size,1);
                    out.BufferLoad();
                    FloatBuffer pixels=out.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),true)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    for(int y=2;y<h-2;y++) for(int x=2;x<w-2;x++) for(int c=0;c<4;c++) {
                        float expected=0.05f+0.1f*c+0.002f*(x+0.375f)+0.001f*(y-0.625f);
                        assertEquals("cfa="+cfa+" tile="+tile,expected,pixels.get((y*size.x+x)*4+c),0.0007f);
                    }
                }
            }
        }
    }

    @Test public void realBurstRecordsEverySensorExposure() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        String id=InstrumentationRegistry.getArguments().getString("gyroCameraId","0");
        boolean forceOisData=Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("forceOisData","false"));
        CameraCharacteristics camera=manager.getCameraCharacteristics(id);
        android.util.Log.i(TAG,"Camera "+id+" timestampSource="+camera.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
                +" OIS data modes="+Arrays.toString(camera.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES))
                +" lens OIS modes="+Arrays.toString(camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION))
                +" OIS request key="+camera.getAvailableCaptureRequestKeys().contains(CaptureRequest.STATISTICS_OIS_DATA_MODE)
                +" OIS result key="+camera.getAvailableCaptureResultKeys().contains(CaptureResult.STATISTICS_OIS_SAMPLES)
                +" forceOisData="+forceOisData);
        StreamConfigurationMap map=camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes=map.getOutputSizes(ImageFormat.RAW_SENSOR);
        assertNotNull("RAW camera required",sizes);
        Size size=Collections.min(Arrays.asList(sizes),(a,b)->Long.compare((long)a.getWidth()*a.getHeight(),(long)b.getWidth()*b.getHeight()));
        HandlerThread thread=new HandlerThread("GyroCameraTest"); thread.start();
        Handler handler=new Handler(thread.getLooper());
        Gyro gyro=new Gyro((SensorManager)context.getSystemService(Context.SENSOR_SERVICE));
        AtomicReference<CameraDevice> device=new AtomicReference<>();
        AtomicReference<CameraCaptureSession> session=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        CountDownLatch opened=new CountDownLatch(1), configured=new CountDownLatch(1), finished=new CountDownLatch(1);
        Semaphore imageArrivals=new Semaphore(0);
        Set<Long> timestamps=Collections.synchronizedSet(new HashSet<>());
        try (ImageReader reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.RAW_SENSOR,3)) {
            gyro.register();
            reader.setOnImageAvailableListener(r->{try(Image image=r.acquireNextImage()) {
                if(image!=null) { timestamps.add(image.getTimestamp()); imageArrivals.release(); }
            }},handler);
            manager.openCamera(id,new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice c) { device.set(c); opened.countDown(); }
                public void onDisconnected(CameraDevice c) { error.set("disconnected"); c.close(); opened.countDown(); }
                public void onError(CameraDevice c,int e) { error.set("open error "+e); c.close(); opened.countDown(); }
            },handler);
            assertTrue(opened.await(15,TimeUnit.SECONDS)); assertNull(error.get()); assertNotNull(device.get());
            device.get().createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) { session.set(s); configured.countDown(); }
                public void onConfigureFailed(CameraCaptureSession s) { error.set("configure failed"); configured.countDown(); }
            },handler);
            assertTrue(configured.await(15,TimeUnit.SECONDS)); assertNull(error.get());
            CaptureRequest.Builder request=device.get().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            request.addTarget(reader.getSurface()); Gyro.enableOisReporting(request,camera);
            if (forceOisData) {
                // Diagnostic override: test the HAL even when telemetry is not advertised.
                request.set(CaptureRequest.STATISTICS_OIS_DATA_MODE,CaptureRequest.STATISTICS_OIS_DATA_MODE_ON);
                int[] lensModes=camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
                if (lensModes!=null) for (int mode:lensModes) if (mode==CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) {
                    request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,mode);
                    break;
                }
                assertEquals(Integer.valueOf(CaptureRequest.STATISTICS_OIS_DATA_MODE_ON),request.get(CaptureRequest.STATISTICS_OIS_DATA_MODE));
            }
            android.util.Log.i(TAG,"Capture request: OIS data mode="+request.get(CaptureRequest.STATISTICS_OIS_DATA_MODE)
                    +" lens OIS mode="+request.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE));
            if (forceOisData) {
                CountDownLatch warmedUp=new CountDownLatch(30);
                AtomicInteger warmupFrames=new AtomicInteger(), warmupSamples=new AtomicInteger(), warmupModeOn=new AtomicInteger();
                session.get().setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback() {
                    public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result) {
                        android.hardware.camera2.params.OisSample[] ois=result.get(CaptureResult.STATISTICS_OIS_SAMPLES);
                        warmupFrames.incrementAndGet();
                        if(ois!=null) warmupSamples.addAndGet(ois.length);
                        if(Integer.valueOf(CaptureResult.STATISTICS_OIS_DATA_MODE_ON).equals(result.get(CaptureResult.STATISTICS_OIS_DATA_MODE)))
                            warmupModeOn.incrementAndGet();
                        warmedUp.countDown();
                    }
                },handler);
                assertTrue("OIS probe warm-up timed out",warmedUp.await(10,TimeUnit.SECONDS));
                session.get().stopRepeating();
                android.util.Log.i(TAG,"OIS warm-up: frames="+warmupFrames.get()+" returned mode ON="+warmupModeOn.get()
                        +" samples="+warmupSamples.get());
            }
            ArrayList<GyroBurst> records=new ArrayList<>(); gyro.PrepareGyroBurst(records);
            session.get().captureBurst(Arrays.asList(request.build(),request.build(),request.build(),request.build()),new CameraCaptureSession.CaptureCallback() {
                public void onCaptureStarted(CameraCaptureSession s,CaptureRequest r,long ts,long number) { gyro.CaptureGyroBurst(ts,0); }
                public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result) {
                    gyro.recordCaptureResult(result,camera);
                    android.hardware.camera2.params.OisSample[] ois=result.get(CaptureResult.STATISTICS_OIS_SAMPLES);
                    android.util.Log.i(TAG,"frame="+result.getFrameNumber()+" ts="+result.get(CaptureResult.SENSOR_TIMESTAMP)
                            +" exposure="+result.get(CaptureResult.SENSOR_EXPOSURE_TIME)+" skew="+result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)
                            +" requested OIS data mode="+r.get(CaptureRequest.STATISTICS_OIS_DATA_MODE)
                            +" returned OIS data mode="+result.get(CaptureResult.STATISTICS_OIS_DATA_MODE)
                            +" returned lens OIS mode="+result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)
                            +" OIS samples="+(ois==null?0:ois.length));
                }
                public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f) { error.set("capture failed "+f.getReason()); }
                public void onCaptureSequenceCompleted(CameraCaptureSession s,int sequence,long number) { finished.countDown(); }
            },handler);
            assertTrue(finished.await(30,TimeUnit.SECONDS)); assertNull(error.get());
            gyro.CompleteSequence(); assertEquals(4,records.size());
            long imageDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            for(GyroBurst record:records) {
                // Warm-up results can overlap burst delivery; wait for these exact images.
                while(!timestamps.contains(record.frameTimestamp)) {
                    long remaining=imageDeadline-System.nanoTime();
                    assertTrue("Image/gyro timestamp mismatch for "+record.frameTimestamp,
                            remaining>0 && imageArrivals.tryAcquire(remaining,TimeUnit.NANOSECONDS));
                }
                assertTrue("Gyro coverage missing for "+record.frameTimestamp,record.orientationValid);
                android.util.Log.i(TAG,"Matched gyro ts="+record.frameTimestamp+" samples="+record.samples+" OIS="+record.oisValid);
            }
            if(!forceOisData) assertEquals(4,timestamps.size());
        } finally {
            if(session.get()!=null) session.get().close();
            if(device.get()!=null) device.get().close();
            gyro.unregister(); thread.quitSafely();
        }
    }
}
