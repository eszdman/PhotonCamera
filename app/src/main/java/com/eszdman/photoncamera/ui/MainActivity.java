package com.eszdman.photoncamera.ui;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ToggleButton;
import androidx.appcompat.app.AppCompatActivity;
import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraFragment;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Permissions;
import com.eszdman.photoncamera.util.FileManager;
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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        act = this;
        new Interface(this);
        //Wrapper.Test();
        Permissions.RequestPermissions(this, 2, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        FileManager.CreateFolders();
        CameraFragment.context = CameraFragment.newInstance();
        Interface.setCameraFragment(CameraFragment.context);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraFragment.context)
                    .commit();
        }

        customOrientationEventListener = new
                CustomOrientationEventListener(getBaseContext()) {
                    @Override
                    public void onSimpleOrientationChanged(int orientation) {
                        ToggleButton stacking = findViewById(R.id.stacking);
                        Button settings = findViewById(R.id.settings);
                        CircleImageView gallery = findViewById(R.id.ImageOut);
//                        ImageView expText = findViewById(R.id.expText);
//                        ImageView isoText = findViewById(R.id.isoText);
//                        ImageView focusText = findViewById(R.id.focusText);
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
//                        expText.animate().rotation(rot).setDuration(RotationDur).start();
//                        isoText.animate().rotation(rot).setDuration(RotationDur).start();
//                        focusText.animate().rotation(rot).setDuration(RotationDur).start();
                        eis.animate().rotation(rot).setDuration(RotationDur).start();
                        fpsPreview.animate().rotation(rot).setDuration(RotationDur).start();
                        quadres.animate().rotation(rot).setDuration(RotationDur).start();
                        if (findViewById(R.id.manual_mode).getVisibility() == View.VISIBLE)
                            Interface.getManualMode().rotate(rot);
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

