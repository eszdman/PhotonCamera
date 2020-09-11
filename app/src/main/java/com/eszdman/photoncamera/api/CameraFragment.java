/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eszdman.photoncamera.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.SurfaceViewOverViewfinder;
import com.eszdman.photoncamera.gallery.GalleryActivity;
import com.eszdman.photoncamera.ui.MainActivity;
import com.eszdman.photoncamera.ui.SettingsActivity;
import com.eszdman.photoncamera.util.CustomLogger;
import com.eszdman.photoncamera.util.FileManager;

import rapid.decoder.BitmapDecoder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import static androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT;

@SuppressWarnings("rawtypes")
public class CameraFragment extends Fragment
        implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback, CameraController.ControllerEvents {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    private Size target;
    private final Field[] metadataFields = CameraReflectionApi.getAllMetadataFields();


    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2Api";

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
            Log.v(TAG, "onSurfaceTextureAvailable");
            CameraController.GET().setTextureView(mTextureView);
            CameraController.GET().openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
            configureTransform(width, height, CameraController.GET().getPreviewWidth(),CameraController.GET().getPreviewHeight());
        }

        @Override

        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {
        }

    };

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    public AutoFitTextureView mTextureView;
    public SurfaceViewOverViewfinder surfaceView;
    private static CameraFragment cameraFragment;

    public static CameraFragment GET() {
        if (cameraFragment == null)
            cameraFragment = new CameraFragment();
        return cameraFragment;
    }

    private CameraFragment() {
        super();
    }

    @Override
    public void updateScreenLog(CaptureResult result, int width, int height) {
        CustomLogger cl = new CustomLogger(getActivity(), R.id.screen_log_focus);
        if (Interface.getSettings().afdata) {
            IsoExpoSelector.ExpoPair expoPair = IsoExpoSelector.GenerateExpoPair(0);
            LinkedHashMap<String, String> dataset = new LinkedHashMap<>();
            dataset.put("AF_MODE", getResultFieldName("CONTROL_AF_MODE_", result.get(CaptureResult.CONTROL_AF_MODE)));
            dataset.put("AF_TRIGGER", getResultFieldName("CONTROL_AF_TRIGGER_", result.get(CaptureResult.CONTROL_AF_TRIGGER)));
            dataset.put("AF_STATE", getResultFieldName("CONTROL_AF_STATE_", result.get(CaptureResult.CONTROL_AF_STATE)));
            dataset.put("FOCUS_DISTANCE", String.valueOf(result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
            dataset.put("EXPOSURE_TIME", expoPair.ExposureString() + "s");
//            dataset.put("EXPOSURE_TIME_CR", String.format(Locale.ROOT,"%.5f",result.get(CaptureResult.SENSOR_EXPOSURE_TIME).doubleValue()/1E9)+ "s");
            dataset.put("ISO", String.valueOf(expoPair.iso));
//            dataset.put("ISO_CR", String.valueOf(result.get(CaptureResult.SENSOR_SENSITIVITY)));
            dataset.put("Shakeness", String.valueOf(Interface.getSensors().getShakeness()));
            dataset.put("FOCUS_RECT", Arrays.deepToString(result.get(CaptureResult.CONTROL_AF_REGIONS)));
            MeteringRectangle[] rectobj = result.get(CaptureResult.CONTROL_AF_REGIONS);
            if(rectobj != null && rectobj.length > 0) {
                RectF rect = getScreenRectFromMeteringRect(rectobj[0],width,height);
                dataset.put("F_RECT(px)", rect.toString());
                surfaceView.update(rect);
            }
            cl.setVisibility(View.VISIBLE);
            cl.updateText(cl.createTextFrom(dataset));
        } else {
            if (surfaceView.rectToDraw != null) {
                surfaceView.rectToDraw = null;
                surfaceView.invalidate();
                cl.setVisibility(View.GONE);
            }
        }
    }

    private RectF getScreenRectFromMeteringRect(MeteringRectangle meteringRectangle,int width2, int height2) {
            float left = (((float) meteringRectangle.getY() / height2) * (surfaceView.getWidth()));
            float top = (((float) meteringRectangle.getX() / width2)* (surfaceView.getHeight()));
            float width = (((float) meteringRectangle.getHeight() / height2) * (surfaceView.getWidth()));
            float height = (((float) meteringRectangle.getWidth() / width2) * (surfaceView.getHeight()));
            return new RectF(
                          left        , //Left
                          top         ,  //Top
                    left + width,//Right
                    top+height //Bottom
            );
    }

    private String getResultFieldName(String prefix, Integer value) {
        for (Field f : this.metadataFields)
            if (f.getName().startsWith(prefix)) {
                try {
                    if (f.getInt(f) == value)
                        return f.getName().replace(prefix, "").concat("(" + value + ")");
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        return "";
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    public void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void updateTextureViewOrientation(int width, int height) {
        // We fit the aspect ratio of TextureView to the size of preview we picked.
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTextureView.setAspectRatio(
                    width, height);
        } else {
            mTextureView.setAspectRatio(
                    height, width);
        }
    }

    @Override
    public void updateTouchtoFocus() {
        if(getActivity()!=null){
            getActivity().runOnUiThread(() -> Interface.getTouchFocus().resetFocusCircle());
        }
    }



    /**
     * Returns the ConstraintLayout object after adjusting the LayoutParams of Views contained in it.
     * Adjusts the relative position of layout_topbar and camera_container (= viewfinder + rest of the buttons excluding layout_topbar)
     * depending on the aspect ratio of device.
     * This is done in order to re-organise the camera layout for long displays (having aspect ratio > 16:9)
     *
     * @param aspectRatio     the aspect ratio of device display given by (height in pixels / width in pixels)
     * @param activity_layout here, the layout of activity_main
     * @return Object of {@param activity_layout} after adjustments.
     */
    private ConstraintLayout getAdjustedLayout(float aspectRatio, ConstraintLayout activity_layout) {
        ConstraintLayout camera_container = activity_layout.findViewById(R.id.camera_container);
        ConstraintLayout.LayoutParams camera_containerLP = (ConstraintLayout.LayoutParams) camera_container.getLayoutParams();
        if (aspectRatio > 16f / 9f) {
            camera_containerLP.height = WRAP_CONTENT;
            showToast(String.valueOf(aspectRatio));
            ConstraintLayout.LayoutParams layout_topbarLP = ((ConstraintLayout.LayoutParams) activity_layout.findViewById(R.id.layout_topbar).getLayoutParams());
            layout_topbarLP.bottomToTop = R.id.camera_container;    //sets the bottom constraint of layout_topbar to top of camera_container
            if (aspectRatio > 2) {                  //for ratios even greater than 18:9
                layout_topbarLP.topToTop = -1;      //resets/removes the top constraint of topbar
            } else if (aspectRatio == 2) {          //for ratio 18:9
                camera_containerLP.topToTop = -1;   //resets/removes the top constraint of camera_container
                camera_containerLP.topToBottom = R.id.layout_topbar;    //constraints the top of cameracontainer to bottom of layout_topbar
            }
            if (((ConstraintLayout.LayoutParams) activity_layout.findViewById(R.id.texture).getLayoutParams()).dimensionRatio.equals("H,3:4")) {  //if viewfinder ratio is 3:4
                ConstraintLayout.LayoutParams layout_viewfinderLP = (ConstraintLayout.LayoutParams) camera_container.findViewById(R.id.layout_viewfinder).getLayoutParams();
                layout_viewfinderLP.bottomToTop = R.id.layout_bottombar;    //set the bottom of layout_viewfinder to top of layout_bottombar
            }
        }
        return activity_layout;
    }

    /**
     * Logs the device display properties
     *
     * @param dm Object of {@link DisplayMetrics} obtained from Fragment
     */
    private void logDisplayProperties(DisplayMetrics dm) {
        String TAG = "DisplayProps";
        Log.i(TAG, "ScreenResolution = " + dm.heightPixels + "x" + dm.widthPixels);
        Log.i(TAG, "AspectRatio = " + (float) dm.heightPixels / dm.widthPixels);
        Log.i(TAG, "SmallestWidth = " + (int) (dm.widthPixels / (dm.densityDpi / 160f)) + "dp");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        logDisplayProperties(dm);
        float aspectRatio = (float) dm.heightPixels / dm.widthPixels;
        ConstraintLayout activity_main = (ConstraintLayout) inflater.inflate(R.layout.activity_main, container, false);
        CameraController.GET().setEventsListner(this);
        return getAdjustedLayout(aspectRatio, activity_main);
    }

    boolean onUnlimited = false;
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                if(Interface.getSettings().selectedMode != Settings.CameraMode.UNLIMITED) {
                    Interface.getCameraUI().shot.setActivated(false);
                    Interface.getCameraUI().shot.setClickable(false);
                    CameraController.GET().takePicture();
                }
                else {
                    if(!onUnlimited) {
                        onUnlimited = true;
                        Interface.getCameraUI().shot.setActivated(false);
                        Interface.getCameraUI().shot.setClickable(true);
                        CameraController.GET().takePicture();
                    } else {
                        Interface.getCameraUI().shot.setActivated(true);
                        Interface.getCameraUI().shot.setClickable(true);
                        onUnlimited = false;
                        CameraController.GET().iCaptureSession.abortCaptures();
                        ImageProcessing.UnlimitedEnd();
                        CameraController.GET().createCameraPreviewSession();
                    }
                }
                break;
            }
            case R.id.settings: {
//                closeCamera();
//                Interface.getSettings().openSettingsActivity();
                Intent intent = new Intent(MainActivity.act, SettingsActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.stacking: {
                ToggleButton sw = (ToggleButton) view;
                if (sw.isChecked()) {
                    CameraController.mTargetFormat = CameraController.rawFormat;
                    Interface.getSettings().hdrx = true;
                } else {
                    CameraController.mTargetFormat = CameraController.yuvFormat;
                    Interface.getSettings().hdrx = false;
                }
                Interface.getSettings().save();
                CameraController.GET().restartCamera();
                break;
            }
            case R.id.ImageOut: {
                Intent intent = new Intent(MainActivity.act, GalleryActivity.class);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.texture);
        surfaceView = view.findViewById(R.id.surfaceView);
        Interface.getCameraUI().onCameraViewCreated();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    public void loadGalleryButtonImage() {
        File[] files = FileManager.DCIM_CAMERA.listFiles((dir, name) -> name.toUpperCase().endsWith(".JPG"));
        if (files != null) {
            long lastModifiedTime = -1;
            File lastImage = null;
            for (File f : files) {      //finds the last modified file from the list
                if (f.lastModified() > lastModifiedTime) {
                    lastImage = f;
                    lastModifiedTime = f.lastModified();
                }
            }
            //Used fastest decoder on the wide west
            if(lastImage != null) {
                    Interface.getCameraUI().galleryImageButton.setImageBitmap(
                            BitmapDecoder.from(Uri.fromFile(lastImage))
                                    .scaleBy(0.1f)
                                    .decode());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        CameraController.GET().onResume();
        Interface.getCameraUI().onCameraResume();
        if (mTextureView == null) mTextureView = new AutoFitTextureView(MainActivity.act);
        if (mTextureView.isAvailable()) {
            CameraController.GET().setTextureView(mTextureView);
            CameraController.GET().openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        loadGalleryButtonImage();
    }

    @Override
    public void onPause() {
        Interface.getCameraUI().onCameraPause();
        CameraController.GET().onPause();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    @Override
    public void configureTransform(int viewWidth, int viewHeight, int pWidth, int pHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == activity) {
            return;
        }
        int rotation = Interface.getGravity().getRotation();//activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, pHeight, pWidth);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / pHeight,
                    (float) viewWidth / pWidth);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }




    @Override
    public void onCreateOutPutError(int msg) {
        CameraFragment.ErrorDialog.newInstance(getString(msg))
                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }


    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            assert getArguments() != null;
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        if (activity != null) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

}