package com.particlesdevs.photoncamera.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.particlesdevs.photoncamera.AiPhoto;

public class SplashActivity extends AppCompatActivity {
    private FirebaseAnalytics mFirebaseAnalytics;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        AiPhoto.initAi(this);
        finish();
    }
}
