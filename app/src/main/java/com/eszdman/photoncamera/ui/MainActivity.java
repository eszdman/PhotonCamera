package com.eszdman.photoncamera.ui;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ToggleButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraFragment;
import com.eszdman.photoncamera.api.Permissions;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.log.FragmentLifeCycleMonitor;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.util.FileManager;
import com.manual.ManualMode;
import de.hdodenhof.circleimageview.CircleImageView;
import org.opencv.android.OpenCVLoader;


public class MainActivity extends AppCompatActivity {

    private CustomOrientationEventListener customOrientationEventListener;
    final int Rotation90  = 2;
    final int Rotation180 = 3;
    final int Rotation270 = 4;
    public final int RotationDur = 350;
    public static MainActivity act;
    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Animatoo.animateShrink(this);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        act = this;
        PhotonCamera.setMainActivity(this);
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
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentLifeCycleMonitor(),true);

        customOrientationEventListener = new
                CustomOrientationEventListener(getBaseContext()) {
                    @Override
                    public void onSimpleOrientationChanged(int orientation) {
                        ToggleButton stacking = findViewById(R.id.stacking);
                        ImageButton settings = findViewById(R.id.settings);
                        CircleImageView gallery = findViewById(R.id.ImageOut);
                        ToggleButton eis = findViewById(R.id.eisPhoto);
                        ToggleButton fpsPreview = findViewById(R.id.fpsPreview);
                        ToggleButton quadres = findViewById(R.id.quadRes);
                        int rot = 0;
                        switch (orientation) {
                            case Rotation90:
                                rot = -90;
                                //rotate as left on top
                                break;
                            case Rotation270:
                                //rotate as right on top
                                rot = 90;
                                break;
                            case Rotation180:
                                //rotate as upside down
                                rot = 180;
                                break;
                        }
                        stacking.animate().rotation(rot).setDuration(RotationDur).start();
                        settings.animate().rotation(rot).setDuration(RotationDur).start();
                        gallery.animate().rotation(rot).setDuration(RotationDur).start();
                        eis.animate().rotation(rot).setDuration(RotationDur).start();
                        fpsPreview.animate().rotation(rot).setDuration(RotationDur).start();
                        quadres.animate().rotation(rot).setDuration(RotationDur).start();
                        if (findViewById(R.id.manual_mode).getVisibility() == View.VISIBLE)
                            PhotonCamera.getManualMode().rotate(rot);
                    }
                };
    }
    @Override
    protected void onResume() {
        super.onResume();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        customOrientationEventListener.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        customOrientationEventListener.disable();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        customOrientationEventListener.disable();
    }
}

