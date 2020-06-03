package com.eszdman.photoncamera.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Permissions;

import org.opencv.android.OpenCVLoader;

import de.hdodenhof.circleimageview.CircleImageView;


public class MainActivity extends AppCompatActivity {
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
        //Interface.i.swipedetection.RunDetection();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        act = this;
        Interface inter = new Interface();
        Wrapper.Test();
        inter.mainActivity = this;
        Permissions.RequestPermissions(this, 2, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        CameraFragment.context = CameraFragment.newInstance();
        inter.camera = CameraFragment.context;
        setContentView(R.layout.activity_camera);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraFragment.context)
                    .commit();
        }
    }
}