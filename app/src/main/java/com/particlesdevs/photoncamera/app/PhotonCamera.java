package com.particlesdevs.photoncamera.app;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;

import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.Gravity;
import com.particlesdevs.photoncamera.control.Gyro;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.debugclient.Debugger;
import com.particlesdevs.photoncamera.pro.SensorSpecifics;
import com.particlesdevs.photoncamera.pro.Specific;
import com.particlesdevs.photoncamera.pro.SupportedDevice;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.render.PreviewParameters;
import com.particlesdevs.photoncamera.settings.MigrationManager;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.ui.SplashActivity;
import com.particlesdevs.photoncamera.util.AssetLoader;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.util.SimpleStorageHelper;
import com.particlesdevs.photoncamera.util.ObjectLoader;
import com.particlesdevs.photoncamera.util.log.ActivityLifecycleMonitor;
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
    //private Parameters mParameters;
    private PreviewParameters mPreviewParameters;
    private CaptureController mCaptureController;
    private SupportedDevice mSupportedDevice;
    private SettingsManager mSettingsManager;
    private AssetLoader mAssetLoader;
    private ObjectLoader objectLoader;
    private Debugger mDebugger;
    private AudioManager audioManager;

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

    @Nullable
    public static Context getAppContext() {
        return sPhotonCamera;
    }

    public static AudioManager getAudioManager() {
        return sPhotonCamera.audioManager;
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

    /*public static Parameters getParameters() {
        return sPhotonCamera.mParameters;
    }*/

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
    public static String getLibsDirectory(){
        return sPhotonCamera.getApplicationInfo().nativeLibraryDir;
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
    
    public static SettingsManager getSettingsManagerStatic() {
        return sPhotonCamera != null ? sPhotonCamera.mSettingsManager : null;
    }

    @Override
    public void onCreate() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleMonitor());
        sPhotonCamera = this;
        Log.d("PhotonCamera", "Initializing PhotonCamera Modules");
        initModules();
        super.onCreate();
    }
    private void initModules() {

        SimpleStorageHelper.init(this);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mGravity = new Gravity(sensorManager);

        mGyro = new Gyro(sensorManager);

        mVibration = new Vibration(this);

        mSettingsManager = new SettingsManager(this);
        mSupportedDevice = new SupportedDevice(mSettingsManager, this);

        MigrationManager.migrate(mSettingsManager);

        PreferenceKeys.initialise(mSettingsManager);

        mSettings = new Settings();

        //mParameters = new Parameters();
        mPreviewParameters = new PreviewParameters();
        mAssetLoader = new AssetLoader(this);
        mDebugger = new Debugger();
        
        // Initialize gallery icon visibility based on preference
        applyGalleryIconVisibility();
        //test();
    }
    
    /**
     * Applies the gallery icon visibility setting based on the user preference.
     * This should be called on app startup to ensure the launcher icon state matches the saved preference.
     */
    private void applyGalleryIconVisibility() {
        try {
            // Get the hide gallery icon preference
            boolean hideGalleryIcon = mSettingsManager.getBoolean(
                    SettingsManager.SCOPE_GLOBAL,
                    PreferenceKeys.Key.KEY_HIDE_GALLERY_ICON
            );
            
            Log.d("PhotonCamera", "Applying gallery icon visibility: hideGalleryIcon=" + hideGalleryIcon);
            
            // Get the ComponentName for the activity-alias using explicit package name
            String packageName = getPackageName();
            ComponentName galleryLauncher = new ComponentName(
                    packageName,
                    packageName + ".gallery.ui.GalleryActivityLauncher"
            );
            
            // Set the component enabled state based on the preference
            int newState = hideGalleryIcon ? 
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED : 
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            
            Log.d("PhotonCamera", "Setting component " + galleryLauncher + " to state: " + newState);
            
            getPackageManager().setComponentEnabledSetting(
                    galleryLauncher,
                    newState,
                    PackageManager.DONT_KILL_APP
            );
            
            Log.d("PhotonCamera", "Gallery icon visibility applied successfully");
        } catch (Exception e) {
            Log.e("PhotonCamera", "Error applying gallery icon visibility: " + e.getMessage());
            e.printStackTrace();
        }
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
