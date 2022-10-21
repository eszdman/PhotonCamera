package com.particlesdevs.photoncamera.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.particlesdevs.photoncamera.AiPhoto;
import com.particlesdevs.photoncamera.util.FileManager;

import java.io.File;
import java.io.IOException;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            final File path = new File(
                    FileManager.sPHOTON_DIR, "PhotonLog");
            if (!path.exists()) {
                boolean result = path.mkdirs();
            }
            Runtime.getRuntime().exec("adb logcat --clear");
            Runtime.getRuntime().exec(
                    "logcat  -d -f " + path + File.separator
                            + "dbo_logcat"
                            + ".txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
        AiPhoto.initAi(this);
        finish();
    }
}
