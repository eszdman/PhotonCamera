package com.eszdman.photoncamera;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.eszdman.photoncamera.Photos.Photo;

import org.opencv.android.OpenCVLoader;


public class MainActivity extends AppCompatActivity {
    public static MainActivity act;
    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }
    @Override
    public void onBackPressed()
    {
        if(Camera2Api.context.mState != 5)  {super.onBackPressed(); return;} // optional depending on your needs
        else {//inst.closeCamera();
        //inst.onPause();
        //Camera2Api.context.onPause();
        try {
            Settings.instance.getSettings();
        }catch (Exception e){}
        }
        setContentView(R.layout.activity_camera);
        Intent intent = this.getIntent();
        this.finish();
        this.startActivity(intent);
        Animatoo.animateShrink(this);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Wrapper wp = new Wrapper();
        Photo photo = new Photo();
        act = this;
        Permissions.RequestPermissions(MainActivity.this, 2,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        Settings settings = new Settings();
        setContentView(R.layout.activity_camera);
        Camera2Api.context = Camera2Api.newInstance();
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, Camera2Api.context)
                    .commit();
        }
    }
}