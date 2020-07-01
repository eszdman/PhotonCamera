package com.eszdman.photoncamera.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.eszdman.photoncamera.Control.Manual;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Permissions;

import org.opencv.android.OpenCVLoader;

import de.hdodenhof.circleimageview.CircleImageView;


public class MainActivity extends AppCompatActivity {

    private CustomOrientationEventListener customOrientationEventListener;
    final int ROTATION_O    = 1;
    final int ROTATION_90   = 2;
    final int ROTATION_180  = 3;
    final int ROTATION_270  = 4;

    public static MainActivity act;
    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }
    @Override
    public void onBackPressed() {
        if (CameraFragment.context.mState != 5) {
            super.onBackPressed();
            return;
        }
        Intent intent = this.getIntent();
        this.finish();
        this.startActivity(intent);
        Animatoo.animateShrink(this);
    }

    ImageView grid;
    public void onCameraResume(){
        Interface.i.swipedetection.RunDetection();
    }
    public static void onCameraViewCreated(){
        Interface.i.manual = new Manual();

        //Interface.i.swipedetection.RunDetection();
    }
    public static void onCameraInitialization(){
        Camera2ApiAutoFix.Init();
        Interface.i.manual.Init();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        act = this;
        Interface inter = new Interface(this);
//        Wrapper.Test();
        Permissions.RequestPermissions(this, 2, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        CameraFragment.context = CameraFragment.newInstance();
        inter.camera = CameraFragment.context;
        setContentView(R.layout.activity_camera);
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
                        ImageButton flipcam = findViewById(R.id.flip_camera);
                        CircleImageView gallery = findViewById(R.id.ImageOut);
                        ImageView expText = findViewById(R.id.expText);
                        TextView expValue = findViewById(R.id.expValue);
                        TextView isoText = findViewById(R.id.isoText);
                        TextView isoValue = findViewById(R.id.isoValue);
                        ImageView focusText = findViewById(R.id.focusText);
                        TextView focusValue = findViewById(R.id.focusValue);
                        switch(orientation){
                            case ROTATION_O:
                                //rotate as on portrait
                                stacking.animate().rotation(0).setDuration(350).start();
                                settings.animate().rotation(0).setDuration(350).start();
                                flipcam.animate().rotation(0).setDuration(350).start();
                                gallery.animate().rotation(0).setDuration(350).start();
                                expText.animate().rotation(0).setDuration(350).start();
                                expValue.animate().rotation(0).setDuration(350).start();
                                isoText.animate().rotation(0).setDuration(350).start();
                                isoValue.animate().rotation(0).setDuration(350).start();
                                focusText.animate().rotation(0).setDuration(350).start();
                                focusValue.animate().rotation(0).setDuration(350).start();
                                break;
                            case ROTATION_90:
                                //rotate as left on top
                                stacking.animate().rotation(-90).setDuration(350).start();
                                settings.animate().rotation(-90).setDuration(350).start();
                                flipcam.animate().rotation(-90).setDuration(350).start();
                                gallery.animate().rotation(-90).setDuration(350).start();
                                expText.animate().rotation(-90).setDuration(350).start();
                                expValue.animate().rotation(-90).setDuration(350).start();
                                isoText.animate().rotation(-90).setDuration(350).start();
                                isoValue.animate().rotation(-90).setDuration(350).start();
                                focusText.animate().rotation(-90).setDuration(350).start();
                                focusValue.animate().rotation(-90).setDuration(350).start();
                                break;
                            case ROTATION_270:
                                //rotate as right on top
                                stacking.animate().rotation(90).setDuration(350).start();
                                settings.animate().rotation(90).setDuration(350).start();
                                flipcam.animate().rotation(90).setDuration(350).start();
                                gallery.animate().rotation(90).setDuration(350).start();
                                expText.animate().rotation(90).setDuration(350).start();
                                expValue.animate().rotation(90).setDuration(350).start();
                                isoText.animate().rotation(90).setDuration(350).start();
                                isoValue.animate().rotation(90).setDuration(350).start();
                                focusText.animate().rotation(90).setDuration(350).start();
                                focusValue.animate().rotation(90).setDuration(350).start();
                                break;
                            case ROTATION_180:
                                //rotate as upside down
                                stacking.animate().rotation(180).setDuration(350).start();
                                settings.animate().rotation(180).setDuration(350).start();
                                flipcam.animate().rotation(180).setDuration(350).start();
                                gallery.animate().rotation(180).setDuration(350).start();
                                expText.animate().rotation(180).setDuration(350).start();
                                expValue.animate().rotation(180).setDuration(350).start();
                                isoText.animate().rotation(180).setDuration(350).start();
                                isoValue.animate().rotation(180).setDuration(350).start();
                                focusText.animate().rotation(180).setDuration(350).start();
                                focusValue.animate().rotation(180).setDuration(350).start();
                                break;

                        }
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