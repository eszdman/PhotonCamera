package com.particlesdevs.photoncamera.ui.camera;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.app.base.BaseActivity;
import com.particlesdevs.photoncamera.manual.ManualMode;
import com.particlesdevs.photoncamera.settings.MigrationManager;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.FileManager;
import com.particlesdevs.photoncamera.util.log.FragmentLifeCycleMonitor;

import java.util.Arrays;


public class CameraActivity extends BaseActivity {

    private static final int CODE_REQUEST_PERMISSIONS = 1;
    private static final String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET};
    private static int requestCount;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PhotonCamera.setManualMode(ManualMode.getInstance(this));

        //Preferences Init
        PreferenceManager.setDefaultValues(this, R.xml.preferences, MigrationManager.readAgain);
        PreferenceKeys.setDefaults(this);
        PhotonCamera.getSettings().loadCache();

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentLifeCycleMonitor(), true);

        if (hasAllPermissions()) {
//            if (null == savedInstanceState)
                tryLoad();
        } else {
            requestPermissions(PERMISSIONS, CODE_REQUEST_PERMISSIONS); //First Permission request
        }
    }

    private boolean hasAllPermissions() { //checks if permissions have already been granted
        return Arrays.stream(PERMISSIONS).allMatch(permission -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
    }

    private void tryLoad() {
        FileManager.CreateFolders();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, CameraFragment.newInstance())
                .commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d("CameraActivity", "onRequestPermissionsResult() called with: " + "requestCode = [" + requestCode + "], " + "permissions = [" + Arrays.toString(permissions) + "], " + "grantResults = [" + Arrays.toString(grantResults) + "]");
        if (requestCode == CODE_REQUEST_PERMISSIONS) {
            if (Arrays.stream(grantResults).asLongStream().anyMatch(value -> value == PackageManager.PERMISSION_DENIED)) {
                requestPermissions(PERMISSIONS, CODE_REQUEST_PERMISSIONS); //Recursive Permission check
                requestCount++;
            } else {
                tryLoad();
            }
            if (requestCount > 15) {
                System.exit(0);
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    protected void onDestroy() {
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
                    if (view.isClickable()) {
                        view.performClick();
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }
}

