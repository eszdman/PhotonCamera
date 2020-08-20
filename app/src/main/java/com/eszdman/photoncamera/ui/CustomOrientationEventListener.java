package com.eszdman.photoncamera.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.Surface;

public abstract class CustomOrientationEventListener  extends OrientationEventListener {


    private static final String TAG = "CustomOrientationEvent";
    private int prevOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private final Context context;
    private int rotation = 0;

    public CustomOrientationEventListener(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public void onOrientationChanged(int orientation) {

        if (android.provider.Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 0) // 0 = Auto Rotate Disabled
            return;
        int currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
        int ROTATION_O = 1;
        int ROTATION_90 = 2;
        int ROTATION_180 = 3;
        int ROTATION_270 = 4;
        if (orientation >= 340 || orientation < 20 && rotation != ROTATION_O) {
            currentOrientation = Surface.ROTATION_0;
            rotation = ROTATION_O;

        } else if (orientation >= 70 && orientation < 110 && rotation != ROTATION_90) {
            currentOrientation = Surface.ROTATION_90;
            rotation = ROTATION_90;

        } else if (orientation >= 160 && orientation < 200 && rotation != ROTATION_180) {
            currentOrientation = Surface.ROTATION_180;
            rotation = ROTATION_180;

        } else if (orientation >= 250 && orientation < 290 && rotation != ROTATION_270) {
            currentOrientation = Surface.ROTATION_270;
            rotation = ROTATION_270;
        }

        if (prevOrientation != currentOrientation && orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            prevOrientation = currentOrientation;
            if (currentOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                onSimpleOrientationChanged(rotation);
            }
        }
    }

    public abstract void onSimpleOrientationChanged(int orientation);


}
