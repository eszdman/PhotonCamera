package com.eszdman.photoncamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;

import java.util.concurrent.CancellationException;

public class MainActivity extends AppCompatActivity {
CheckBox cs;
    static Camera2Api inst;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        inst = Camera2Api.newInstance();
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, inst)
                    .commit();
        }

        Permissions.RequestPermissions(MainActivity.this, 2,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
    }

   View.OnClickListener k = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cs.setChecked(true);
        }
    };
}
