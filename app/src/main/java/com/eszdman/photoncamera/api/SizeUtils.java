package com.eszdman.photoncamera.api;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SizeUtils {

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_HEIGHT = 1080;

    private static final String TAG = SizeUtils.class.getSimpleName();

    public static Size findPreviewSize(StreamConfigurationMap map, Size target,Size mPreviewSize, boolean swappedDimensions) {
        Point displaySize = new Point();
        Interface.getMainActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = mPreviewSize.getWidth();
        int rotatedPreviewHeight = mPreviewSize.getHeight();
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;

        if (swappedDimensions) {
            rotatedPreviewWidth =  mPreviewSize.getHeight();
            rotatedPreviewHeight = mPreviewSize.getWidth();;
            //noinspection SuspiciousNameCombination
            maxPreviewWidth = displaySize.y;
            //noinspection SuspiciousNameCombination
            maxPreviewHeight = displaySize.x;
        }

        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        return chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                maxPreviewHeight, target);
    }

    public static Size getCameraOutputSize(Size[] in) {
        Arrays.sort(in, new CameraController.CompareSizesByArea());
        Size target = null;
        List<Size> sizes = new ArrayList<>(Arrays.asList(in));
        int s = sizes.size() - 1;
        if (sizes.get(s).getWidth() * sizes.get(s).getHeight() <= 40 * 1000000) {
            target = sizes.get(s);
            return target;
        }
        else {
            if(sizes.size()>1) {
                target = sizes.get(s - 1);
                return target;
            }
        }
        return target;
    }

    private static void mul(Rect in, double k) {
        in.bottom *= k;
        in.left *= k;
        in.right *= k;
        in.top *= k;
    }

    public static Size getCameraOutputSize(Size[] in, Size mPreviewSize, CameraCharacteristics mCameraCharacteristics) {
        Size target = null;
        if(in == null) return mPreviewSize;
        Arrays.sort(in, new CameraController.CompareSizesByArea());
        List<Size> sizes = new ArrayList<>(Arrays.asList(in));
        int s = sizes.size() - 1;
        if (sizes.get(s).getWidth() * sizes.get(s).getHeight() <= 40 * 1000000 || Interface.getSettings().QuadBayer){
            target = sizes.get(s);
            if(Interface.getSettings().QuadBayer) {
                Rect pre = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                if(pre == null) return target;
                Rect act = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if(act == null) return target;
                double k = (double) (target.getHeight()) / act.bottom;
                mul(pre, k);
                mul(act, k);
                CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE, act);
                CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE, pre);
            }
            return target;
        }
        else {
            if(sizes.size()> 1 ) {
                target = sizes.get(s - 1);
                return target;
            }
        }
        return mPreviewSize;
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                         int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CameraController.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CameraController.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
}
