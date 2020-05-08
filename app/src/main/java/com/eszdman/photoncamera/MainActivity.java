package com.eszdman.photoncamera;

import android.Manifest;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import java.util.Objects;
import java.util.concurrent.CancellationException;

public class MainActivity extends AppCompatActivity {
    static Camera2Api inst;
    static MainActivity act;
    public static void showToast(final String text) {
        if (act != null) {
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(act, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    @Override
    public void onBackPressed()
    {
        if(inst.mState != 5)  super.onBackPressed(); // optional depending on your needs
        else Settings.instance.getSettings();
        // code here to show dialog
        setContentView(R.layout.activity_camera);
        inst = Camera2Api.newInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, inst)
                .commit();

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
