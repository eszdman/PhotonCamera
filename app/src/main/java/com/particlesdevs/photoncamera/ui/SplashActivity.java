package com.particlesdevs.photoncamera.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.particlesdevs.photoncamera.AiPhoto;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AiPhoto.initAi(this);
        finish();
    }
}
