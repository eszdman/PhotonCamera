package com.particlesdevs.photoncamera.app.base;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;

public class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        PreferenceKeys.setActivityTheme(BaseActivity.this);
        super.onCreate(savedInstanceState);
    }
    public interface BackPressedListener{
        boolean onBackPressed();
    }
}
