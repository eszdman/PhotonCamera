package com.particlesdevs.photoncamera.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.RenderScript;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;

import com.hunter.library.debug.HunterDebug;
import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.Gravity;
import com.particlesdevs.photoncamera.control.Gyro;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.debugclient.Debugger;
import com.particlesdevs.photoncamera.ml.Model;
import com.particlesdevs.photoncamera.pro.SensorSpecifics;
import com.particlesdevs.photoncamera.pro.Specific;
import com.particlesdevs.photoncamera.pro.SpecificSetting;
import com.particlesdevs.photoncamera.pro.SupportedDevice;
import com.particlesdevs.photoncamera.processing.ImagePath;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.render.PreviewParameters;
import com.particlesdevs.photoncamera.settings.MigrationManager;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.ui.SplashActivity;
import com.particlesdevs.photoncamera.util.AssetLoader;
import com.particlesdevs.photoncamera.util.ObjectLoader;
import com.particlesdevs.photoncamera.util.log.ActivityLifecycleMonitor;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.TensorFlowLite;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotonCamera extends Application {
    public static final boolean DEBUG = false;
    private static PhotonCamera sPhotonCamera;
    //    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//    private final ExecutorService executorService = Executors.newWorkStealingPool();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final Handler mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    private Settings mSettings;
    private Gravity mGravity;
    private Gyro mGyro;
    private Vibration mVibration;
    private Parameters mParameters;
    private PreviewParameters mPreviewParameters;
    private CaptureController mCaptureController;
    private SupportedDevice mSupportedDevice;
    private SettingsManager mSettingsManager;
    private AssetLoader mAssetLoader;
    private RenderScript mRS;
    private ObjectLoader objectLoader;
    private Debugger mDebugger;

    @Nullable
    public static PhotonCamera getInstance(Context context) {
        if (context instanceof Activity) {
            Application application = ((Activity) context).getApplication();
            if (application instanceof PhotonCamera) {
                return (PhotonCamera) application;
            }
        }
        return null;
    }

    public static Handler getMainHandler() {
        return sPhotonCamera.mainThreadHandler;
    }

    public static Settings getSettings() {
        return sPhotonCamera.mSettings;
    }

    public static Gravity getGravity() {
        return sPhotonCamera.mGravity;
    }

    public static Gyro getGyro() {
        return sPhotonCamera.mGyro;
    }

    public static Vibration getVibration() {
        return sPhotonCamera.mVibration;
    }

    public static Parameters getParameters() {
        return sPhotonCamera.mParameters;
    }

    public static PreviewParameters getPreviewParameters() {
        return sPhotonCamera.mPreviewParameters;
    }

    public static Debugger getDebugger(){
        return sPhotonCamera.mDebugger;
    }

    public static Specific getSpecific(){
        return sPhotonCamera.mSupportedDevice.specific;
    }
    public static SensorSpecifics getSpecificSensor(){
        return sPhotonCamera.mSupportedDevice.sensorSpecifics;
    }


    public static RenderScript getRenderScript() {
        return sPhotonCamera.mRS;
    }

    public static CaptureController getCaptureController() {
        return sPhotonCamera.mCaptureController;
    }

    public static void setCaptureController(CaptureController captureController) {
        sPhotonCamera.mCaptureController = captureController;
    }

    public static AssetLoader getAssetLoader() {
        return sPhotonCamera.mAssetLoader;
    }

    public static void restartWithDelay(Context context, long delayMs) {
        getMainHandler().postDelayed(() -> restartApp(context), delayMs);
    }

    public static void restartApp(Context context) {
        Intent intent = new Intent(context, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
        System.exit(0);
    }

    public static void showToast(String msg) {
        getMainHandler().post(() -> Toast.makeText(sPhotonCamera, msg, Toast.LENGTH_LONG).show());
    }

    public static void showToast(@StringRes int stringRes) {
        getMainHandler().post(() -> Toast.makeText(sPhotonCamera, stringRes, Toast.LENGTH_LONG).show());
    }

    public static void showToastFast(@StringRes int stringRes) {
        getMainHandler().post(() -> Toast.makeText(sPhotonCamera, stringRes, Toast.LENGTH_SHORT).show());
    }

    public static Resources getResourcesStatic() {
        return sPhotonCamera.getResources();
    }

    public static String getStringStatic(@StringRes int stringRes) {
        return sPhotonCamera.getResources().getString(stringRes);
    }

    public static Drawable getDrawableStatic(int resID) {
        return ContextCompat.getDrawable(sPhotonCamera, resID);
    }

    public static PackageInfo getPackageInfo() throws PackageManager.NameNotFoundException {
        return sPhotonCamera.getPackageManager().getPackageInfo(sPhotonCamera.getPackageName(), 0);
    }

    public static String getVersion() {
        String version = "";
        try {
            PackageInfo pInfo = PhotonCamera.getPackageInfo();
            version = pInfo.versionName + '(' + pInfo.versionCode + ')';
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return version;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public SupportedDevice getSupportedDevice() {
        return mSupportedDevice;
    }

    public SettingsManager getSettingsManager() {
        return mSettingsManager;
    }

    @HunterDebug
    @Override
    public void onCreate() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleMonitor());
        sPhotonCamera = this;
        initModules();
        super.onCreate();
    }
    void test(){
        try {
            org.tensorflow.lite.support.model.Model.Options.Builder builder = new org.tensorflow.lite.support.model.Model.Options.Builder();
            builder.setDevice(org.tensorflow.lite.support.model.Model.Device.GPU);
            Model model = Model.newInstance(this);
            AssetLoader loader = PhotonCamera.getAssetLoader();
            Bitmap bitmap = BitmapFactory.decodeStream(loader.getInputStream("lr-4.jpg"));

            ByteBuffer img = ByteBuffer.allocateDirect(bitmap.getByteCount());
            int width = 256;
            int height = 256;
            int channels = 1;
            int wcount = (bitmap.getByteCount()/height);
            Log.d("PhotonCamera","Wcount:"+wcount);
            bitmap.copyPixelsToBuffer(img);
            byte[] bytesIn = new byte[width*height*4];
            img.limit(width*height*4);
            img.position(0);
            img.get(bytesIn);

            Log.d("PhotonCamera","bb input:"+bytesIn[0]);
            float[] floatsIn = new float[width*height*channels];
            int cnt = 0;
            for(int i = 0; i<bytesIn.length;i+=4){
                for(int ch = 0; ch<channels;ch++) {
                    floatsIn[cnt] = (((int) bytesIn[i + ch] & 0xff) / 256.f);
                    cnt += 1;
                }
            }
            Log.d("PhotonCamera","float input:"+floatsIn[50]+","+floatsIn[51]);
            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, width, height, channels}, DataType.FLOAT32);
            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4 * channels);
            FloatBuffer fb = buffer.asFloatBuffer();

            fb.position(0);
            fb.put(floatsIn);
            buffer.position(0);
            buffer.limit(width * height * 4 * channels);
            inputFeature0.loadBuffer(buffer);

            // Runs model inference and gets result.
            Model.Outputs outputs = model.process(inputFeature0);

            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            fb.position(0);
            /*float[] floats = outputFeature0.getFloatArray();
            ByteBuffer bb2 = ByteBuffer.allocate(width*height*4*channels);
            FloatBuffer fb2 = bb2.asFloatBuffer();
            fb2.position(0);
            fb2.put(floats);
            fb2.position(0);
            bb2.position(0);
            bb2.put(outputFeature0.getBuffer());
            fb2.position(0);
            //fb2.limit(width*height);
            //fb2.get(floats);
            Log.d("PhotonCamera","bb outputsize:"+outputFeature0.getBuffer().capacity());*/
            byte[] bytes = new byte[width*height*4];
            cnt = 0;
            //Log.d("PhotonCamera","bb output2:"+floats[50]+","+floats[51]);
            for(int i = 0; i<width*height*channels;i++){
                if(cnt + 4 >= bytes.length) break;

                bytes[cnt] = (byte)(255);
                bytes[cnt+1] = (byte)(255);
                bytes[cnt+2] = (byte)(255);
                bytes[cnt+3] = (byte)(255);
                Log.d("PhotonCamera","TF runtime:"+TensorFlowLite.runtimeVersion()+",TF native:"+TensorFlowLite.nativeRuntimeVersion());
                for(int ch = 0;ch<channels;ch++){
                    float inp = outputFeature0.getFloatValue(i)*255.f;
                    bytes[cnt+ch] = (byte)(inp);
                    Log.d("PhotonCamera","float input:"+inp);
                }
                /*
                bytes[cnt] = (byte)(inp);
                bytes[cnt+1] = (byte)(inp);
                bytes[cnt+2] = (byte)(inp);
                bytes[cnt+3] = (byte)(255);
                */

                cnt+=4;
            }
            ByteBuffer outBuffer = ByteBuffer.wrap(bytes);
            bitmap.copyPixelsFromBuffer(outBuffer);
            File debug = new File(ImagePath.newJPGFilePath()+"test.jpg");
            FileOutputStream fOut = null;
            try {
                debug.createNewFile();
                fOut = new FileOutputStream(debug);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);


            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            Log.d("PhotonCamera",e.toString());
        }
        Log.d("PhotonCamera","Test Complete!");
    }
    private void initModules() {


        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGravity = new Gravity(sensorManager);

        mGyro = new Gyro(sensorManager);

        mVibration = new Vibration(this);

        mSettingsManager = new SettingsManager(this);

        MigrationManager.migrate(mSettingsManager);

        PreferenceKeys.initialise(mSettingsManager);

        mSettings = new Settings();

        mParameters = new Parameters();
        mPreviewParameters = new PreviewParameters();
        mSupportedDevice = new SupportedDevice(mSettingsManager);
        mAssetLoader = new AssetLoader(this);
        mRS = RenderScript.create(this);
        mDebugger = new Debugger();
        //test();
    }
    //  a MemoryInfo object for the device's current memory status.
    /*public ActivityManager.MemoryInfo AvailableMemory() {
        ActivityManager activityManager = (ActivityManager) mCameraActivity.SystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }*/

    @Override
    public void onTerminate() {
        super.onTerminate();
        executorService.shutdownNow();
        mCaptureController = null;
        sPhotonCamera = null;
    }
}
