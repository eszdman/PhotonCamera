package com.eszdman.photoncamera.Control;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;

import java.util.Objects;

public class Swipe {
    private static String TAG = "Swipe";
    private GestureDetector gestureDetector;
    private View.OnTouchListener touchListener;
    private TextureView.SurfaceTextureListener textureListener;
    public Surface previewSurface;
    @SuppressLint("ClickableViewAccessibility")
    public void RunDetection(){
        Log.d(TAG,"SwipeDetection - ON");
        gestureDetector = new GestureDetector(Interface.i.mainActivity, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            Log.d(TAG, "Right");
                        } else {
                            Log.d(TAG, "Left");
                        }
                        return true;
                    }
                } else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        Log.d(TAG, "Bottom");
                    } else {
                        Log.d(TAG, "Top");
                    }
                    return true;
                }
                return false;
            }
        });
        touchListener = (view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent);
        Log.d(TAG,"input:"+Interface.i.mainActivity.findViewById(R.id.textureHolder));
        Objects.requireNonNull((View)Interface.i.mainActivity.findViewById(R.id.textureHolder)).setOnTouchListener(touchListener);
    }
}
