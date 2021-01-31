package com.particlesdevs.photoncamera.control;

import android.graphics.RectF;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.manual.ManualParamModel;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.CameraFragment;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.ManualModeViewModel;

public class Swipe {
    private static final String TAG = "Swipe";
    private static boolean panelShowing;
    private final CameraFragment cameraFragment;
    private final CaptureController captureController;
    private GestureDetector gestureDetector;
    private ManualModeViewModel manualModeViewModel;
    private ImageView ocManual;

    public Swipe(CameraFragment cameraFragment) {
        this.cameraFragment = cameraFragment;
        this.captureController = cameraFragment.getCaptureController();
    }

    public void init() {
        Log.d(TAG, "SwipeDetection - ON");
        manualModeViewModel = cameraFragment.getManualModeViewModel();
        ocManual = cameraFragment.findViewById(R.id.open_close_manual);
        panelShowing = manualModeViewModel.togglePanelVisibility(false);
        ocManual.animate().rotation(0).setDuration(250).start();
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
            if (captureController.getManualParamModel().getCurrentFocusValue() == ManualParamModel.FOCUS_AUTO)
                cameraFragment.getTouchFocus().processTouchToFocus(translateX, translateY);
        }
    }

    public void SwipeUp() {
        ocManual.animate().rotation(180).setDuration(250).start();
        panelShowing = manualModeViewModel.togglePanelVisibility(true);
//        cameraFragment.getCaptureController().rebuildPreview();
        cameraFragment.getTouchFocus().resetFocusCircle();
    }

    public void SwipeDown() {
        if (panelShowing) {
            ocManual.animate().rotation(0).setDuration(250).start();
            cameraFragment.getTouchFocus().resetFocusCircle();
            captureController.reset3Aparams();
            panelShowing = manualModeViewModel.togglePanelVisibility(false);
            manualModeViewModel.retractAllKnobs();
        }
    }

    public void SwipeRight() {

    }

    public void SwipeLeft() {

    }

}
