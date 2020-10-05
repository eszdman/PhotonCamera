package com.eszdman.photoncamera.ui.camera;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Permissions;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.util.FileManager;
import com.eszdman.photoncamera.util.log.FragmentLifeCycleMonitor;
import com.manual.ManualMode;
import org.opencv.android.OpenCVLoader;


public class CameraActivity extends AppCompatActivity {

    public static CameraActivity act;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PreferenceKeys.setActivityTheme(CameraActivity.this);
        super.onCreate(savedInstanceState);
        act = this;
        PhotonCamera.setCameraActivity(this);
        PhotonCamera.setManualMode(ManualMode.getInstance(this));
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        PreferenceKeys.setDefaults();
        PhotonCamera.getSettings().loadCache();
        //Wrapper.Test();
        Permissions.RequestPermissions(this, 2, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        FileManager.CreateFolders();
        CameraFragment.context = CameraFragment.newInstance();
        PhotonCamera.setCameraFragment(CameraFragment.context);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //   if (null == savedInstanceState)
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, CameraFragment.context)
                .commit();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentLifeCycleMonitor(), true);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Animatoo.animateShrink(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }


}

