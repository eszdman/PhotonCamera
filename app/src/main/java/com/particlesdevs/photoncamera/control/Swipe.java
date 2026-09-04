package com.particlesdevs.photoncamera.control;

import android.graphics.RectF;
import com.particlesdevs.photoncamera.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.ui.camera.CameraFragment;
import com.particlesdevs.photoncamera.ui.camera.viewmodel.CameraFragmentViewModel;

public class Swipe {
    private static final String TAG = "Swipe";
    private final CameraFragment cameraFragment;
    private final CaptureController captureController;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;
    private ManualModeConsole manualModeConsole;
    private CameraFragmentViewModel cameraFragmentViewModel;
    private ImageView ocManual;
    private ZoomGestureListener zoomGestureListener;

    /** Notified on every handled pinch-to-zoom movement (used to reveal the zoom slider). */
    public interface ZoomGestureListener {
        void onZoomGesture();
    }

    public void setZoomGestureListener(ZoomGestureListener listener) {
        this.zoomGestureListener = listener;
    }

    public Swipe(CameraFragment cameraFragment) {
        this.cameraFragment = cameraFragment;
        this.captureController = cameraFragment.getCaptureController();
    }

    public void init() {
        Log.d(TAG, "SwipeDetection - ON");
        manualModeConsole = cameraFragment.getManualModeConsole();
        cameraFragmentViewModel = cameraFragment.getCameraFragmentViewModel();
        ocManual = cameraFragment.findViewById(R.id.open_close_manual);
        manualModeConsole.setPanelVisibility(false);
        ocManual.animate().rotation(0).setDuration(250).start();
        ocManual.setOnClickListener((v) -> {
            if (!manualModeConsole.isPanelVisible()) {
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
            public boolean onSingleTapConfirmed(MotionEvent e) {
                cameraFragmentViewModel.setSettingsBarVisible(false);
                startTouchToFocus(e);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                startSpotWb(e);
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
        scaleDetector = new ScaleGestureDetector(cameraFragment.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                // Ignore pinch while a burst/processing is active or the manual
                // panel is open, to avoid fighting the manual controls.
                if (manualModeConsole.isPanelVisible() || CaptureController.isProcessing) {
                    return true;
                }
                float current = captureController.getZoomRatio();
                float newZoom = current * detector.getScaleFactor();
                // The crop is always centered (the HAL's CONTROL_ZOOM_RATIO, and
                // therefore the preview, zooms to sensor center), so pass the
                // centered focal point to keep the saved JPEG/RAW matching the
                // viewfinder exactly.
                captureController.setZoom(newZoom, 0.5f, 0.5f);
                cameraFragmentViewModel.setZoomRatio(captureController.getZoomRatio());
                if (zoomGestureListener != null) zoomGestureListener.onZoomGesture();
                return true;
            }
        });
        View.OnTouchListener touchListener = (view, motionEvent) -> {
            boolean handled = gestureDetector.onTouchEvent(motionEvent);
            // Feed the same events to the scale detector so pinch-to-zoom works
            // alongside tap-to-focus / swipe.
            if (scaleDetector != null) {
                scaleDetector.onTouchEvent(motionEvent);
                handled |= scaleDetector.isInProgress();
            }
            return handled;
        };
        View holder = cameraFragment.findViewById(R.id.textureHolder);
        Log.d(TAG, "input:" + holder);
        if (holder != null) holder.setOnTouchListener(touchListener);
    }

    private RectF getViewfinderRect() {
        //takes into consideration the top and bottom translation of camera_container(if it has been moved due to different display ratios)
        // for calculation of size of viewfinder RectF.(for touch focus detection)
        ConstraintLayout camera_container = cameraFragment.findViewById(R.id.camera_container);
        FrameLayout layout_viewfinder = cameraFragment.findViewById(R.id.layout_viewfinder);
        return new RectF(
                layout_viewfinder.getLeft(),//left edge of viewfinder
                camera_container.getY(), //y position of camera_container
                layout_viewfinder.getRight(), //right edge of viewfinder
                layout_viewfinder.getBottom() + camera_container.getY() //bottom edge of viewfinder + y position of camera_container
        );
    }

    private void startTouchToFocus(MotionEvent event) {
        ConstraintLayout camera_container = cameraFragment.findViewById(R.id.camera_container);
        RectF viewfinderRect = getViewfinderRect();
        // Interface.getCameraFragment().showToast(previewRect.toString()+"\nCurX"+event.getX()+"CurY"+event.getY());
        if (viewfinderRect.contains(event.getX(), event.getY())) {
            float translateX = event.getX() - camera_container.getLeft();
            float translateY = event.getY() - camera_container.getTop();
            if (manualModeConsole.getManualParamModel().getCurrentFocusValue() == ManualParamModel.FOCUS_AUTO)
                cameraFragment.getTouchFocus().processTouchToFocus(translateX, translateY);
        }
    }

    private void startSpotWb(MotionEvent event) {
        // Dispatches long-press coordinates to TouchFocus for Spot WB measurement
        ConstraintLayout camera_container = cameraFragment.findViewById(R.id.camera_container);
        FrameLayout layout_viewfinder = cameraFragment.findViewById(R.id.layout_viewfinder);
        if (camera_container == null || layout_viewfinder == null || cameraFragment.getTouchFocus() == null) {
            return;
        }
        RectF viewfinderRect = new RectF(
                layout_viewfinder.getLeft(),
                camera_container.getY(),
                layout_viewfinder.getRight(),
                layout_viewfinder.getBottom() + camera_container.getY()
        );
        if (viewfinderRect.contains(event.getX(), event.getY())) {
            float translateX = event.getX() - camera_container.getLeft();
            float translateY = event.getY() - camera_container.getTop();
            cameraFragment.getTouchFocus().processSpotWb(translateX, translateY);
        }
    }

    public void SwipeUp() {
        if (cameraFragmentViewModel.isSettingsBarVisible()) {
            cameraFragmentViewModel.setSettingsBarVisible(false);
        } else {
            ocManual.animate().rotation(180).setDuration(250).start();
            manualModeConsole.setPanelVisibility(true);
//        cameraFragment.getCaptureController().rebuildPreview();
            cameraFragment.getTouchFocus().resetFocusCircle();
        }

    }

    public void SwipeDown() {
        if (manualModeConsole.isPanelVisible()) {
            ocManual.animate().rotation(0).setDuration(250).start();
            cameraFragment.getTouchFocus().resetFocusCircle();
            // Capture before the resets below clear the values: if no knob was
            // touched, there is nothing to re-apply and the preview session can
            // be left alone, making close as cheap as open.
            boolean hadManualChanges = manualModeConsole.getManualParamModel().isManualMode();
            manualModeConsole.retractAllKnobs();
            manualModeConsole.setPanelVisibility(false);
            if (hadManualChanges) {
                captureController.reset3Aparams();
            }
        } else {
            cameraFragmentViewModel.setSettingsBarVisible(true);
        }
    }

    public void SwipeRight() {

    }

    public void SwipeLeft() {

    }

}
