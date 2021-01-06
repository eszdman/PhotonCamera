package com.eszdman.photoncamera.control;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.View.OnTouchListener;
import androidx.annotation.Nullable;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.settings.PreferenceKeys;

import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_REGIONS;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_REGIONS;

public class TouchFocus {
    private static final String TAG = "TouchFocus";
    private static final int AUTO_HIDE_DELAY_MS = 3000;
    private final CaptureController captureController;
    private final Point previewMaxSize;
    private final View focusEl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideFocusCircleRunnable = this::hideFocusCircleView;
    private final OnTouchListener focusListener = (v, event) -> {
        v.performClick();
        resetFocusCircle();
        setInitialAFAE();
        return true;
    };


    private TouchFocus(CaptureController captureController, View focusCircle, Point previewMaxSize) {
        this.captureController = captureController;
        this.focusEl = focusCircle;
        this.previewMaxSize = previewMaxSize;
        focusEl.setOnTouchListener(focusListener);
        resetFocusCircle();
    }

    public static TouchFocus initialise(CaptureController captureController, View focusCircle, Point previewSize) {
        return new TouchFocus(captureController, focusCircle, previewSize);
    }

    public void processTouchToFocus(float fx, float fy) {
        mainHandler.removeCallbacks(hideFocusCircleRunnable);
        showFocusCircle(fx, fy);
        try {
            setFocus((int) fy, (int) fx);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mainHandler.postDelayed(hideFocusCircleRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void showFocusCircle(float fx, float fy) {
        focusEl.setX(fx - focusEl.getMeasuredWidth() / 2.0f);
        focusEl.setY(fy - focusEl.getMeasuredHeight() / 2.0f);
        focusEl.setVisibility(View.VISIBLE);
        focusEl.animate().scaleY(1.2f).scaleX(1.2f).setDuration(250)
                .withEndAction(() -> focusEl.animate().scaleY(1f).scaleX(1f).setDuration(250).start())
                .start();
    }

    /**
     * Sets state of focus circle view based on AF State
     */
    public void setState(@Nullable Integer afstate) {
        if (afstate != null) {
            focusEl.setActivated(afstate == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED);
            focusEl.setSelected(afstate == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
        }
    }

    private void setInitialAFAE() {
        CaptureRequest.Builder builder = captureController.mPreviewRequestBuilder;
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, builder.get(CONTROL_AF_REGIONS));
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, builder.get(CONTROL_AE_REGIONS));
        builder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        captureController.rebuildPreviewBuilder();
    }

    private void setFocus(int x, int y) {
        Point size = new Point(captureController.mImageReaderPreview.getWidth(), captureController.mImageReaderPreview.getHeight());
        Point CurUi = previewMaxSize;
        Rect sizee = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (sizee == null) {
            sizee = new Rect(0, 0, size.x, size.y);
        }
        if (x < 0)
            x = 0;
        if (y < 0)
            y = 0;
        /*if (y > CurUi.y)
            y = CurUi.y;
        if (x > CurUi.x)
            x  =CurUi.x;*/
        //use 1/8 from the the sensor size for the focus rect
        int width_to_set = sizee.width() / 6;
        float kProp = (float) CurUi.x / (float) (CurUi.y);
        int height_to_set = (int) (width_to_set * kProp);
        float x_scale = (float) sizee.width() / (float) CurUi.y;
        float y_scale = (float) sizee.height() / (float) CurUi.x;
        int x_to_set = (int) (x * x_scale) - width_to_set / 2;
        int y_to_set = (int) (y * y_scale) - height_to_set / 2;
        if (x_to_set < 0)
            x_to_set = 0;
        if (y_to_set < 0)
            y_to_set = 0;
        if (y_to_set - height_to_set > sizee.height())
            y_to_set = sizee.height() - height_to_set;
        if (x_to_set - width_to_set > sizee.width())
            y_to_set = sizee.width() - width_to_set;
        MeteringRectangle rect_to_set = new MeteringRectangle(x_to_set, y_to_set, width_to_set, height_to_set, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        MeteringRectangle[] rectaf = new MeteringRectangle[1];
        Log.v(TAG, "\nInput x/y:" + x + "/" + y + "\n" +
                "sensor size width/height to set:" + width_to_set + "/" + height_to_set + "\n" +
                "preview/sensorsize: " + CurUi.toString() + " / " + sizee.toString() + "\n" +
                "scale x/y:" + x_scale + "/" + y_scale + "\n" +
                "final rect :" + rect_to_set.toString());
        rectaf[0] = rect_to_set;
        triggerAutoFocus(rectaf);
    }

    private void triggerAutoFocus(MeteringRectangle[] rectaf) {
        CaptureRequest.Builder build = captureController.mPreviewRequestBuilder;
        build.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        build.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        captureController.rebuildPreviewBuilderOneShot();

        build.set(CaptureRequest.CONTROL_AF_REGIONS, rectaf);
        build.set(CaptureRequest.CONTROL_AE_REGIONS, rectaf);
        build.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        build.set(CaptureRequest.CONTROL_AF_MODE, PreferenceKeys.getAfMode());
        build.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        //set focus area repeating,else cam forget after one frame where it should focus
        //Interface.getCameraFragment().rebuildPreviewBuilder();
        //trigger af start only once. cam starts focusing till its focused or failed
//        if (onConfigured) {
        build.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        build.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        captureController.rebuildPreviewBuilderOneShot();
        //set focus trigger back to idle to signal cam after focusing is done to do nothing
        build.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
        build.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        captureController.rebuildPreviewBuilderOneShot();
        captureController.rebuildPreviewBuilder();
//        }
    }

    //Thread safe
    //call when focus circle needs to be hidden immediately
    public void resetFocusCircle() {
        mainHandler.removeCallbacks(hideFocusCircleRunnable);
        mainHandler.post(hideFocusCircleRunnable);
    }

    //Must be run on UI Thread
    private void hideFocusCircleView() {
        if (focusEl.getVisibility() == View.VISIBLE) {
            focusEl.animate().alpha(0f).scaleY(1.8f).scaleX(1.8f).setDuration(100)
                    .withEndAction(() -> {
                        focusEl.setVisibility(View.GONE);
                        focusEl.setX((float) previewMaxSize.x / 2.f);
                        focusEl.setY((float) previewMaxSize.y / 2.f);
                        focusEl.setScaleY(1f);
                        focusEl.setScaleX(1f);
                        focusEl.setAlpha(1f);
                    })
                    .start();
        }
    }
}
