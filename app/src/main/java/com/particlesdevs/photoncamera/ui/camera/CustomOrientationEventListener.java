package com.particlesdevs.photoncamera.ui.camera;

import android.content.Context;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.Surface;

public abstract class CustomOrientationEventListener extends OrientationEventListener {


    private static final String TAG = "CustomOrientationEvent";
    private int prevOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private final Context context;
    private int rotation = 0;

    public CustomOrientationEventListener(Context context) {
        super(context);
        this.context = context;
    }

    // For expressing the process of determining the range of angles more clearly
    public boolean orientationPosition(int orientation, int value, int ROTATION) {
        int angleThreshold = 20;
        if (orientation < 20 || orientation >= 340)
            return orientation >= (360 + value - angleThreshold) || orientation < (value + angleThreshold) && rotation != ROTATION;
        else
            return orientation >= (value - angleThreshold) && orientation < (value + angleThreshold) && rotation != ROTATION;
    }

    @Override
    public void onOrientationChanged(int orientation) {

        if (android.provider.Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 0) // 0 = Auto Rotate Disabled
            return;
        int currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

        int ROTATION_0 = 1;
        int ROTATION_90 = 2;
        int ROTATION_180 = 3;
        int ROTATION_270 = 4;

        // Express the process of determining the range of angles more clearly
        if (orientationPosition(orientation, 0, ROTATION_0)) {
            currentOrientation = Surface.ROTATION_0;
            rotation = ROTATION_0;
        } else if (orientationPosition(orientation, 90, ROTATION_90)) {
            currentOrientation = Surface.ROTATION_90;
            rotation = ROTATION_90;
        } else if (orientationPosition(orientation, 180, ROTATION_180)) {
            currentOrientation = Surface.ROTATION_180;
            rotation = ROTATION_180;
        } else if (orientationPosition(orientation, 270, ROTATION_270)) {
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
