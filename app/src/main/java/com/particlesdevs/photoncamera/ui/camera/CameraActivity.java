package com.particlesdevs.photoncamera.ui.camera;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import com.particlesdevs.photoncamera.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.app.base.BaseActivity;
import com.particlesdevs.photoncamera.settings.MigrationManager;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.anggrayudi.storage.contract.RequestStorageAccessContract;
import com.anggrayudi.storage.contract.RequestStorageAccessResult;
import com.anggrayudi.storage.file.StorageType;
import com.particlesdevs.photoncamera.util.FileManager;
import com.particlesdevs.photoncamera.util.SecureCameraHelper;
import com.particlesdevs.photoncamera.util.SimpleStorageHelper;
import com.particlesdevs.photoncamera.util.SystemBarsHelper;
import com.particlesdevs.photoncamera.util.log.FragmentLifeCycleMonitor;

import java.util.Arrays;

import static android.os.Build.VERSION.SDK_INT;


public class CameraActivity extends BaseActivity {

    private static final int CODE_REQUEST_PERMISSIONS = 1;
    private static final int CODE_REQUEST_MEDIA = 2;
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
    };
    /** Legacy storage for Android 10 and below */
    private static final String[] PERMISSIONS2 = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    /** Media read for gallery (system camera images) on Android 13+ */
    private static final String[] PERMISSIONS_MEDIA_33 = {
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
    };
    /** Media read for gallery on Android 11-12 */
    private static final String[] PERMISSIONS_MEDIA_30 = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    private static int requestCount;
    private ActivityResultLauncher<RequestStorageAccessContract.Options> storageAccessLauncher;

    private boolean rationaleShownCamera = false;
    private boolean rationaleShownStorage = false;
    private boolean rationaleShownMedia = false;
    private boolean rationaleShownDcim = false;

    /**
     * True while running as a lockscreen ("secure camera") session: launched via
     * STILL_IMAGE_CAMERA_SECURE / IMAGE_CAPTURE_SECURE, or while the keyguard is
     * locked. While true the gallery shows no pre-lock content and settings are blocked.
     * Cleared when the device is unlocked (ACTION_USER_PRESENT).
     */
    private boolean secureSession = false;
    private BroadcastReceiver unlockReceiver;

    /** Whether this activity is currently in a lockscreen secure-camera session. */
    public boolean isSecureSession() {
        return secureSession;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("CameraActivity", "Called onCreate()");
        // Must be visible over the keyguard for lockscreen launches.
        SecureCameraHelper.applyLockscreenFlags(this);
        secureSession = SecureCameraHelper.isSecureSession(this);
        Log.d("CameraActivity", "secureSession=" + secureSession
                + " action=" + (getIntent() != null ? getIntent().getAction() : null));
        // Hide system UI immediately to prevent flickering (like gallery view)
        hideSystemUI();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, MigrationManager.readAgain);
        PreferenceKeys.setDefaults(this);
        PhotonCamera.getSettings().loadCache();

        storageAccessLauncher = registerForActivityResult(
                new RequestStorageAccessContract(this, StorageType.EXTERNAL, SimpleStorageHelper.DCIM_BASE_PATH),
                result -> {
                    if (result instanceof RequestStorageAccessResult.RootPathPermissionGranted) {
                        Log.d("CameraActivity", "Storage access granted (SimpleStorage)");
                        SimpleStorageHelper.updateFileManagerPaths(CameraActivity.this);
                        tryLoad();
                    } else {
                        Log.e("CameraActivity", "Storage access denied or wrong folder");
                        requestPermission();
                    }
                });

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentLifeCycleMonitor(), true);

        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction()) && secureSession) {
                    Log.d("CameraActivity", "Device unlocked, leaving secure session");
                    setSecureSession(false);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        }

        if (hasAllPermissions()) {
            tryLoad();
        } else {
            requestPermission();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean nowSecure = SecureCameraHelper.isSecureSession(this);
        if (nowSecure != secureSession) {
            setSecureSession(nowSecure);
        }
    }

    private void setSecureSession(boolean secure) {
        secureSession = secure;
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
        if (fragment instanceof CameraFragment) {
            ((CameraFragment) fragment).onSecureSessionChanged(secure);
        }
    }

    private void requestPermission() {
        // Step 1: Camera, microphone, internet
        if (Arrays.stream(PERMISSIONS).anyMatch(p -> checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)) {
            if (!rationaleShownCamera) {
                rationaleShownCamera = true;
                showRationale(
                        getString(R.string.perm_rationale_camera_title),
                        getString(R.string.perm_rationale_camera_message),
                        () -> requestPermissions(PERMISSIONS, CODE_REQUEST_PERMISSIONS)
                );
            } else {
                requestPermissions(PERMISSIONS, CODE_REQUEST_PERMISSIONS);
            }
            return;
        }
        if (secureSession) {
            // Lockscreen secure session is capture-only: the SAF folder picker and the
            // media-library permission UI cannot be completed over the keyguard, and
            // pre-lock library content must stay hidden. Capture via MediaStore works
            // without them; full access is requested after unlock (see onResume).
            tryLoad();
            return;
        }
        if (SDK_INT < Build.VERSION_CODES.R) {
            // Step 2: Legacy storage (Android 10 and below)
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (!rationaleShownStorage) {
                    rationaleShownStorage = true;
                    showRationale(
                            getString(R.string.perm_rationale_storage_title),
                            getString(R.string.perm_rationale_storage_message),
                            () -> requestPermissions(PERMISSIONS2, CODE_REQUEST_PERMISSIONS + 1)
                    );
                } else {
                    requestPermissions(PERMISSIONS2, CODE_REQUEST_PERMISSIONS + 1);
                }
            } else {
                tryLoad();
            }
            return;
        }
        // Step 3: Media read for gallery (Android 11+)
        if (!hasMediaReadPermission()) {
            if (!rationaleShownMedia) {
                rationaleShownMedia = true;
                showRationale(
                        getString(R.string.perm_rationale_media_title),
                        getString(R.string.perm_rationale_media_message),
                        this::doRequestMediaPermission
                );
            } else {
                doRequestMediaPermission();
            }
            return;
        }
        // Step 4: SAF DCIM folder access for RAW video and device configs
        if (!SimpleStorageHelper.hasStorageAccess(this)) {
            if (!rationaleShownDcim) {
                rationaleShownDcim = true;
                showRationale(
                        getString(R.string.perm_rationale_dcim_title),
                        getString(R.string.perm_rationale_dcim_message),
                        () -> storageAccessLauncher.launch(new RequestStorageAccessContract.Options(SimpleStorageHelper.createDcimInitialPath(this)))
                );
            } else {
                storageAccessLauncher.launch(new RequestStorageAccessContract.Options(SimpleStorageHelper.createDcimInitialPath(this)));
            }
        } else {
            tryLoad();
        }
    }

    private void doRequestMediaPermission() {
        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(PERMISSIONS_MEDIA_33, CODE_REQUEST_MEDIA);
        } else {
            requestPermissions(PERMISSIONS_MEDIA_30, CODE_REQUEST_MEDIA);
        }
    }

    private void showRationale(String title, String message, Runnable onProceed) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, which) -> onProceed.run())
                .setCancelable(false)
                .show();
    }

    private void showSettingsRedirectDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.perm_open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> System.exit(0))
                .setCancelable(false)
                .show();
    }
    
    /** Gallery needs this to show system camera images via MediaStore */
    private boolean hasMediaReadPermission() {
        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        if (SDK_INT >= Build.VERSION_CODES.R) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean hasAllPermissions() {
        boolean basicPermissions = Arrays.stream(PERMISSIONS).allMatch(permission -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);

        if (secureSession) {
            // Capture-only over the keyguard: don't gate on gallery/SAF access.
            return basicPermissions;
        }
        if (SDK_INT >= Build.VERSION_CODES.R) {
            return basicPermissions
                    && hasMediaReadPermission()
                    && SimpleStorageHelper.hasStorageAccess(this);
        } else {
            // Android 10 and below - legacy storage
            return basicPermissions
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void tryLoad() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            SimpleStorageHelper.updateFileManagerPaths(this);
        }
        FileManager.CreateFolders();
        Log.setLogFolder(getApplicationContext());
        PhotonCamera photonCamera = PhotonCamera.getInstance(this);
        if (photonCamera != null) {
            photonCamera.getSupportedDevice().loadCheck();
        }
        if (getSupportFragmentManager().findFragmentById(R.id.container) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraFragment.newInstance())
                    .commit();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d("CameraActivity", "onRequestPermissionsResult() requestCode=" + requestCode + " grantResults=" + Arrays.toString(grantResults));

        boolean anyDenied = Arrays.stream(grantResults).asLongStream().anyMatch(v -> v == PackageManager.PERMISSION_DENIED);

        if (requestCode == CODE_REQUEST_PERMISSIONS || requestCode == CODE_REQUEST_PERMISSIONS + 1) {
            if (anyDenied) {
                requestCount++;
                if (requestCount > 15) System.exit(0);
            }
            requestPermission();
        } else if (requestCode == CODE_REQUEST_MEDIA) {
            if (anyDenied) {
                requestCount++;
                if (requestCount > 15) System.exit(0);
                // If user denied or selected limited access, check if we can still ask
                boolean canAskAgain = SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? Arrays.stream(PERMISSIONS_MEDIA_33).anyMatch(this::shouldShowRequestPermissionRationale)
                        : shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE);
                if (!canAskAgain) {
                    // Permanently denied or limited selection chosen — redirect to settings
                    showSettingsRedirectDialog(
                            getString(R.string.perm_rationale_media_title),
                            getString(R.string.perm_rationale_media_settings)
                    );
                    return;
                }
            }
            requestPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Apply hideSystemUI in onResume to prevent flickering when returning to the camera
        hideSystemUI();
        // Ensure portrait orientation is enforced every time activity resumes
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // If the user unlocked while we were paused, leave the secure session so the
        // gallery thumbnail and settings become available without a restart.
        if (secureSession && !SecureCameraHelper.isDeviceLocked(this)) {
            Log.d("CameraActivity", "Device unlocked, leaving secure session (onResume)");
            setSecureSession(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (unlockReceiver != null) {
            try {
                unregisterReceiver(unlockReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
            unlockReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    View view = findViewById(R.id.shutter_button);
                    if (view.isClickable())
                        view.performClick();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    /**
     * Traditional camera behavior: status bar hidden, navigation bar visible and
     * transparent so a single swipe goes home. Delegates to {@link SystemBarsHelper}.
     */
    private void hideSystemUI() {
        SystemBarsHelper.applyCameraBars(this);
    }

}

