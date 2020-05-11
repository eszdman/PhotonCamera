package com.eszdman.photoncamera;

import android.Manifest;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.OpenCVLoader;


public class MainActivity extends AppCompatActivity {
    static Camera2Api inst;
    static MainActivity act;
    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }
    @Override
    public void onBackPressed()
    {
        if(inst.mState != 5)  super.onBackPressed(); // optional depending on your needs
        else {Settings.instance.getSettings();}
        // code here to show dialog
        setContentView(R.layout.activity_camera);
        inst = Camera2Api.newInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, inst)
                .commit();
        //inst.onResume();
        //inst.restartCamera();

    }
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        act = this;
        Settings settings = new Settings();
        setContentView(R.layout.activity_camera);
        inst = Camera2Api.newInstance();
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, inst)
                    .commit();
        }

        Permissions.RequestPermissions(MainActivity.this, 2,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
    }
}
