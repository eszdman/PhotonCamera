package com.particlesdevs.photoncamera.control;

import android.graphics.RectF;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.manual.model.ManualModel;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.CameraFragment;

public class Swipe {
    private static final String TAG = "Swipe";
    private static boolean panelShowing;
    private final CameraFragment cameraFragment;
    private GestureDetector gestureDetector;
    private RelativeLayout manualMode;
    private ImageView ocManual;

    public Swipe(CameraFragment cameraFragment) {
        this.cameraFragment = cameraFragment;
    }

    public void init() {
        Log.d(TAG, "SwipeDetection - ON");
        manualMode = cameraFragment.findViewById(R.id.manual_mode);
        ocManual = cameraFragment.findViewById(R.id.open_close_manual);
        manualMode.post(this::hidePanel);
        ocManual.setOnClickListener((v) -> {
            if (!panelShowing) {
                SwipeUp();
                Log.d(TAG, "Arrow Clicked:SwipeUp");
            } else {
                SwipeDown();
                Log.d(TAG, "Arrow Clicked:SwipeDown");
            }
        });
        gestureDetector = new GestureDetector(cameraFragment.getContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                startTouchToFocus(e);
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            Log.d(TAG, "Right");
                            SwipeRight();
                        } else {
                            Log.d(TAG, "Left");
                            SwipeLeft();
                        }
                        return true;
                    }
                } else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        Log.d(TAG, "Bottom");//it swipes from top to bottom
                        SwipeDown();
                    } else {
                        Log.d(TAG, "Top");//it swipes from bottom to top
                        SwipeUp();
                    }
                    return true;
                }
                return false;
            }
        });
        View.OnTouchListener touchListener = (view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent);
        View holder = cameraFragment.findViewById(R.id.textureHolder);
        Log.d(TAG, "input:" + holder);
        if (holder != null) holder.setOnTouchListener(touchListener);
    }

    private void startTouchToFocus(MotionEvent event) {
        //takes into consideration the top and bottom translation of camera_container(if it has been moved due to different display ratios)
        // for calculation of size of viewfinder RectF.(for touch focus detection)
        ConstraintLayout camera_container = cameraFragment.findViewById(R.id.camera_container);
        ConstraintLayout layout_viewfinder = cameraFragment.findViewById(R.id.layout_viewfinder);
        RectF viewfinderRect = new RectF(
                layout_viewfinder.getLeft(),//left edge of viewfinder
                camera_container.getY(), //y position of camera_container
                layout_viewfinder.getRight(), //right edge of viewfinder
                layout_viewfinder.getBottom() + camera_container.getY() //bottom edge of viewfinder + y position of camera_container
        );
        // Interface.getCameraFragment().showToast(previewRect.toString()+"\nCurX"+event.getX()+"CurY"+event.getY());
        if (viewfinderRect.contains(event.getX(), event.getY())) {
            float translateX = event.getX() - camera_container.getLeft();
            float translateY = event.getY() - camera_container.getTop();
            if (PhotonCamera.getManualMode().getCurrentFocusValue() == ManualModel.FOCUS_AUTO)
                cameraFragment.getTouchFocus().processTouchToFocus(translateX, translateY);
        }
    }

    public void SwipeUp() {
        manualMode.post(this::showPanel);
//        cameraFragment.getCaptureController().rebuildPreview();
        manualMode.setVisibility(View.VISIBLE);
        cameraFragment.getTouchFocus().resetFocusCircle();
    }

    public void SwipeDown() {
        manualMode.post(this::hidePanel);
        cameraFragment.getTouchFocus().resetFocusCircle();
        cameraFragment.getCaptureController().setPreviewAEMode();
        cameraFragment.getCaptureController().mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, PreferenceKeys.getAfMode());
        PhotonCamera.getCaptureController().rebuildPreviewBuilder();
        PhotonCamera.getManualMode().retractAllKnobs();
    }

    public void SwipeRight() {

    }

    public void SwipeLeft() {

    }

    private void hidePanel() {
        if (panelShowing) {
            manualMode.animate()
                    .translationY(cameraFragment.getResources().getDimension(R.dimen.standard_20))
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction(() -> manualMode.setVisibility(View.GONE))
                    .start();
            ocManual.animate().rotation(0).setDuration(250).start();
            panelShowing = false;
        }
    }

    private void showPanel() {
        if (!panelShowing) {
            manualMode.animate().translationY(0).setDuration(100).alpha(1f).start();
            ocManual.animate().rotation(180).setDuration(250).start();
            panelShowing = true;
        }
    }
}
