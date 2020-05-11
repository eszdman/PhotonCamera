package com.eszdman.photoncamera;

import android.content.pm.PackageManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Permissions {
    public static void RequestPermission(AppCompatActivity Activity, int requestID, String permission){
        if (ContextCompat.checkSelfPermission(Activity,
                permission)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(Activity,
                    permission)) {
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(Activity,
                        new String[]{permission},
                        requestID);
            }
            // Permission has already been granted
        } else {
        }
    }
    public static void RequestPermissions(AppCompatActivity Activity, int requestID, String[] permissions){
        boolean permGranted = true;
        for(int k = 0; k<permissions.length;k++) {
            if (ContextCompat.checkSelfPermission(Activity,
                    permissions[k])
                    != PackageManager.PERMISSION_GRANTED) permGranted = false; //Permission not granted
        }
        if(!permGranted)
                ActivityCompat.requestPermissions(Activity,
                        permissions,
                        requestID);


    }
}
