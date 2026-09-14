package com.particlesdevs.photoncamera.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.particlesdevs.photoncamera.ui.camera.CameraActivity;
import com.particlesdevs.photoncamera.util.SecureCameraHelper;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Must be able to show over the keyguard for STILL_IMAGE_CAMERA_SECURE launches.
        SecureCameraHelper.applyLockscreenFlags(this);
        // Ensure splash screen starts in portrait mode
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        forwardToCamera(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        forwardToCamera(intent);
        finish();
    }

    /**
     * Forwards the launch intent (action, data, extras) so secure-camera actions
     * such as STILL_IMAGE_CAMERA_SECURE reach CameraActivity instead of being dropped.
     */
    private void forwardToCamera(Intent source) {
        Intent next = new Intent(SplashActivity.this, CameraActivity.class);
        if (source != null) {
            if (source.getAction() != null) {
                next.setAction(source.getAction());
            }
            if (source.getData() != null) {
                next.setData(source.getData());
            }
            if (source.getExtras() != null) {
                next.putExtras(source.getExtras());
            }
            next.setFlags(source.getFlags()
                    & (Intent.FLAG_ACTIVITY_FORWARD_RESULT
                    | Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        }
        startActivity(next);
    }
}
