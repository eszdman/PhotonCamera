package com.eszdman.photoncamera.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;
import com.eszdman.photoncamera.api.Settings;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.control.Gravity;
import com.eszdman.photoncamera.control.Sensors;
import com.eszdman.photoncamera.pro.SupportedDevice;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.settings.MigrationManager;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.settings.SettingsManager;
import com.eszdman.photoncamera.ui.SplashActivity;
import com.eszdman.photoncamera.util.AssetLoader;
import com.eszdman.photoncamera.util.log.ActivityLifecycleMonitor;
import com.manual.ManualMode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotonCamera extends Application {
    public static final boolean DEBUG = false;
    private static PhotonCamera sPhotonCamera;
//    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//    private final ExecutorService executorService = Executors.newWorkStealingPool();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    private Settings mSettings;
    private Gravity mGravity;
    private Sensors mSensors;
    private Parameters mParameters;
    private CaptureController mCaptureController;
    private SupportedDevice mSupportedDevice;
    private ManualMode mManualMode;
    private SettingsManager mSettingsManager;
    private AssetLoader mAssetLoader;

    public static Handler getMainHandler() {
        return sPhotonCamera.mainThreadHandler;
    }

    public static ExecutorService getExecutorService() {
        return sPhotonCamera.executorService;
    }

    public static Settings getSettings() {
        return sPhotonCamera.mSettings;
    }

    public static Gravity getGravity() {
        return sPhotonCamera.mGravity;
    }

    public static Sensors getSensors() {
        return sPhotonCamera.mSensors;
    }

    public static Parameters getParameters() {
        return sPhotonCamera.mParameters;
    }

    public static CaptureController getCaptureController() {
        return sPhotonCamera.mCaptureController;
    }

    public static void setCaptureController(CaptureController captureController) {
        sPhotonCamera.mCaptureController = captureController;
    }

    public static SupportedDevice getSupportedDevice() {
        return sPhotonCamera.mSupportedDevice;
    }
    public static AssetLoader getAssetLoader() {
        return sPhotonCamera.mAssetLoader;
    }

    public static ManualMode getManualMode() {
        return sPhotonCamera.mManualMode;
    }

    public static void setManualMode(ManualMode manualMode) {
        sPhotonCamera.mManualMode = manualMode;
    }

    public static SettingsManager getSettingsManager() {
        return sPhotonCamera.mSettingsManager;
    }
    
    public static void restartWithDelay(long delayMs) {
        getMainHandler().postDelayed(PhotonCamera::restartApp, delayMs);
    }

    public static void restartApp() {
        Context context = sPhotonCamera;
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

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleMonitor());
        sPhotonCamera = this;
        initModules();
    }

    private void initModules() {

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGravity = new Gravity(sensorManager);
        mSensors = new Sensors(sensorManager);

        mSettingsManager = new SettingsManager(this);

        MigrationManager.migrate(mSettingsManager);

        PreferenceKeys.initialise(mSettingsManager);

        mSettings = new Settings();

        mParameters = new Parameters();
        mSupportedDevice = new SupportedDevice(mSettingsManager);
        mAssetLoader = new AssetLoader(this);
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
        sPhotonCamera = null;
    }
}
