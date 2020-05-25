package com.eszdman.photoncamera;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.eszdman.photoncamera.Photos.Photo;
import com.eszdman.photoncamera.api.Interface;

import org.opencv.android.OpenCVLoader;


public class MainActivity extends AppCompatActivity {
    public static MainActivity act;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }
    @Override
    public void onBackPressed() {
        if (Camera2Api.context.mState != 5) {
            super.onBackPressed();
            return;
        }
        setContentView(R.layout.activity_camera);
        Intent intent = this.getIntent();
        this.finish();
        this.startActivity(intent);
        Animatoo.animateShrink(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Wrapper wp = new Wrapper();
        Photo photo = new Photo();
        act = this;
        Interface inter = new Interface();
        inter.m_activity = this;
        Permissions.RequestPermissions(MainActivity.this, 2, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        setContentView(R.layout.activity_camera);
        Camera2Api.context = Camera2Api.newInstance();
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, Camera2Api.context)
                    .commit();
        }
    }
}