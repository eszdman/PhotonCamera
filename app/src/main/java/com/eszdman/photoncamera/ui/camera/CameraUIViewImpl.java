package com.eszdman.photoncamera.ui.camera;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker;
import de.hdodenhof.circleimageview.CircleImageView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This Class is a dumb 'View' which contains view components visible in the main Camera User Interface
 * <p>
 * It gets instantiated in {@link CameraFragment#onViewCreated(View, Bundle)}
 */
public final class CameraUIViewImpl implements CameraUIView {
    private static final String TAG = "CameraUIView";
    private final View mRootView;
    private ProgressBar mCaptureProgressBar;
    private ImageView mGridView;
    private ImageView mRoundEdgesView;
    private ImageButton mShutterButton;
    private ProgressBar mProcessingProgressBar;
    private CircleImageView mGalleryImageButton;
    private RadioGroup mAuxButtonsGroup;
    private LinearLayout mAuxGroupContainer;
    private CameraUIEventsListener mCameraUIEventsListener;
    private HorizontalPicker mModePicker;
    private ToggleButton mFpsButton;
    private ToggleButton mQuadResolutionButton;
    private ToggleButton mEisPhotoButton;
    private ImageButton mFlipCameraButton;
    private ImageButton mSettingsButton;
    private ToggleButton mHdrXButton;
    private TextView mframeTimer;
    private TextView mframeCount;
    Handler changeFrameTimeCnt = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.obj == null) {
                mframeCount.setText("");
                mframeTimer.setText("");
                return;
            }
            FrameCntTime frameCntTime = (FrameCntTime) msg.obj;
            mframeCount.setText(String.valueOf(Math.abs(frameCntTime.maxframe - frameCntTime.frame)));
            if (frameCntTime.time * frameCntTime.maxframe > 4.0 || frameCntTime.maxframe == 0) {
                frameCntTime.time = Math.abs(frameCntTime.time * frameCntTime.maxframe - frameCntTime.time * frameCntTime.frame);
                mframeTimer.setText(((int) (frameCntTime.time / 60) + ":" + ((int) (frameCntTime.time) % 60)));
            }
        }
    };
    private HashMap<Integer, String> auxButtonsMap;
    private float baseF = 0.f;

    public CameraUIViewImpl(View rootView) {
        Log.d(TAG, "CameraUIView() called with: rootView = [" + rootView + "]");
        mRootView = rootView;
        initViews(rootView);
        initListeners();
        refresh();
    }

    private void initViews(View rview) {
        mGridView = rview.findViewById(R.id.grid_view);
        mRoundEdgesView = rview.findViewById(R.id.round_edges_view);
        mCaptureProgressBar = rview.findViewById(R.id.capture_progress_bar);
        mProcessingProgressBar = rview.findViewById(R.id.processing_progress_bar);
        mShutterButton = rview.findViewById(R.id.shutter_button);
        mGalleryImageButton = rview.findViewById(R.id.gallery_image_button);
        mFpsButton = rview.findViewById(R.id.fps_toggle_button);
        mHdrXButton = rview.findViewById(R.id.hdrx_toggle_button);
        mModePicker = rview.findViewById(R.id.mode_picker_view);
        mQuadResolutionButton = rview.findViewById(R.id.quad_res_toggle_button);
        mEisPhotoButton = rview.findViewById(R.id.eis_toggle_button);
        mFlipCameraButton = rview.findViewById(R.id.flip_camera_button);
        mSettingsButton = rview.findViewById(R.id.settings_button);
        mAuxGroupContainer = rview.findViewById(R.id.aux_buttons_container);
        mframeTimer = rview.findViewById(R.id.frameTimer);
        mframeCount = rview.findViewById(R.id.frameCount);
    }

    private void initListeners() {
        View.OnClickListener commonOnClickListener = v -> mCameraUIEventsListener.onClick(v);

        mShutterButton.setOnClickListener(commonOnClickListener);
        mGalleryImageButton.setOnClickListener(commonOnClickListener);
        mEisPhotoButton.setOnClickListener(commonOnClickListener);
        mFpsButton.setOnClickListener(commonOnClickListener);
        mQuadResolutionButton.setOnClickListener(commonOnClickListener);
        mFlipCameraButton.setOnClickListener(commonOnClickListener);
        mSettingsButton.setOnClickListener(commonOnClickListener);
        mHdrXButton.setOnClickListener(commonOnClickListener);

        setGalleryButtonImage(null);

        String[] modes = CameraMode.names();

        mModePicker.setValues(modes);
        mModePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mModePicker.setOnItemSelectedListener(index -> switchToMode(CameraMode.valueOf(modes[index])));
        mModePicker.setSelectedItem(1);
        PreferenceKeys.setCameraMode(0); //this should not be here, Temporary
        mframeCount.setText("");
        mframeTimer.setText("");

    }

    @Override
    public void activateShutterButton(boolean status) {
        mShutterButton.setActivated(status);
        mShutterButton.setClickable(status);
    }

    private void switchToMode(CameraMode cameraMode) {
        this.mCameraUIEventsListener.onCameraModeChanged(cameraMode);
        reConfigureModeViews(cameraMode);
    }

    private void reConfigureModeViews(CameraMode input) {
        switch (input) {
            case UNLIMITED:
                mEisPhotoButton.setVisibility(View.GONE);
                mFpsButton.setVisibility(View.VISIBLE);
                mHdrXButton.setVisibility(View.GONE);
                mShutterButton.setBackgroundResource(R.drawable.unlimitedbutton);
                break;
            case PHOTO:
            default:
                mEisPhotoButton.setVisibility(View.VISIBLE);
                mFpsButton.setVisibility(View.VISIBLE);
                mHdrXButton.setVisibility(View.VISIBLE);
                mShutterButton.setBackgroundResource(R.drawable.roundbutton);
                break;
            case NIGHT:
                mEisPhotoButton.setVisibility(View.GONE);
                mFpsButton.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void refresh() {
        mFpsButton.setChecked(PreferenceKeys.isFpsPreviewOn());
        mQuadResolutionButton.setChecked(PreferenceKeys.isQuadBayerOn());
        mEisPhotoButton.setChecked(PreferenceKeys.isEisPhotoOn());
        mHdrXButton.setChecked(PreferenceKeys.isHdrXOn());
        if (PreferenceKeys.isShowGridOn())
            mGridView.setVisibility(View.VISIBLE);
        else
            mGridView.setVisibility(View.GONE);
        if (PreferenceKeys.isRoundEdgeOn())
            mRoundEdgesView.setVisibility(View.VISIBLE);
        else
            mRoundEdgesView.setVisibility(View.GONE);
        resetCaptureProgressBar();
        activateShutterButton(true);
        resetProcessingProgressBar();
    }

    @Override
    public void initAuxButtons(Set<String> backCameraIdsList, Map<String, Pair<Float, Float>> Focals, Set<String> frontCameraIdsList) {
        String savedCameraID = PreferenceKeys.getCameraID();
        for (String id : backCameraIdsList) {
            if (baseF == 0.f) {
                baseF = Focals.get(id).first;
            }
        }
        if (mAuxGroupContainer.getChildCount() == 0) {
            if (backCameraIdsList.contains(savedCameraID)) {
                setAuxButtons(backCameraIdsList, Focals, savedCameraID);
            } else if (frontCameraIdsList.contains(savedCameraID)) {
                setAuxButtons(frontCameraIdsList, Focals, savedCameraID);
            }
        }
    }

    @Override
    public void setAuxButtons(Set<String> idsList, Map<String, Pair<Float, Float>> Focals, String active) {
        mAuxGroupContainer.removeAllViews();
        if (idsList.size() > 1) {
            Locale.setDefault(Locale.US);
            auxButtonsMap = new HashMap<>();
            for (String id : idsList) {
                addToAuxGroupButtons(id, String.format("%.1fx", (double) (Focals.get(id).first / baseF)).replace(".0", ""));
            }
            View.OnClickListener auxButtonListener = this::onAuxButtonClick;
            for (int i = 0; i < mAuxGroupContainer.getChildCount(); i++) {
                Button b = (Button) mAuxGroupContainer.getChildAt(i);
                b.setOnClickListener(auxButtonListener);
                if (active.equals(auxButtonsMap.get(b.getId()))) {
                    b.setSelected(true);
                }
            }
            mAuxGroupContainer.setVisibility(View.VISIBLE);
        } else {
            mAuxGroupContainer.setVisibility(View.GONE);
        }
    }

    private void onAuxButtonClick(View view) {
        for (int i = 0; i < mAuxGroupContainer.getChildCount(); i++) {
            mAuxGroupContainer.getChildAt(i).setSelected(false);
        }
        view.setSelected(true);
        mCameraUIEventsListener.onAuxButtonClicked(auxButtonsMap.get(view.getId()));
    }

    public void setGalleryButtonImage(Bitmap bitmap) {
        if (bitmap != null) {
            mGalleryImageButton.setImageBitmap(bitmap);
        }
    }

    private void addToAuxGroupButtons(String cameraId, String name) {
        Button b = new Button(mRootView.getContext());
        int m = (int) mRootView.getResources().getDimension(R.dimen.aux_button_internal_margin);
        int s = (int) mRootView.getResources().getDimension(R.dimen.aux_button_size);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.setMargins(m, m, m, m);
        b.setLayoutParams(lp);
        b.setText(name);
        b.setTextAppearance(R.style.AuxButtonText);
        b.setBackgroundResource(R.drawable.aux_button_background);
        b.setStateListAnimator(null);
        b.setTransformationMethod(null);
        int buttonId = View.generateViewId();
        b.setId(buttonId);
        auxButtonsMap.put(buttonId, cameraId);
        mAuxGroupContainer.addView(b);
    }

    @Override
    public void resetProcessingProgressBar() {
        mProcessingProgressBar.setProgress(0);
        mProcessingProgressBar.setIndeterminate(false);
    }

    @Override
    public void setProcessingProgressBarIndeterminate(boolean indeterminate) {
        mProcessingProgressBar.setIndeterminate(indeterminate);
    }

    @Override
    public void incrementCaptureProgressBar(int step) {
        mCaptureProgressBar.incrementProgressBy(step);
    }

    @Override
    public void resetCaptureProgressBar() {
        mCaptureProgressBar.setProgress(0);
        setCaptureProgressBarOpacity(0);
    }

    @Override
    public void setCaptureProgressBarOpacity(float alpha) {
        try {
            mCaptureProgressBar.setAlpha(alpha);
        } catch (Exception ignore) {
        }
    }

    @SuppressLint("DefaultLocale")
    private String FltFormat(Object in) {
        return String.format("%.0f", in);
    }

    @Override
    public void setFrameTimeCnt(int cnt, int maxcnt, double frametime) {
        FrameCntTime frameCntTime = new FrameCntTime();
        Message msg = new Message();
        switch (PhotonCamera.getSettings().selectedMode) {
            case NIGHT:
            case PHOTO:
                frameCntTime.frame = cnt;
                frameCntTime.maxframe = maxcnt;
                frameCntTime.time = frametime;
                msg.obj = frameCntTime;
                changeFrameTimeCnt.sendMessage(msg);
                return;
            case UNLIMITED:
                frameCntTime.frame = cnt;
                frameCntTime.maxframe = 0;
                frameCntTime.time = frametime;
                msg.obj = frameCntTime;
                changeFrameTimeCnt.sendMessage(msg);

        }
    }

    @Override
    public void clearFrameTimeCnt() {
        //mframeCount.setText("");
        //mframeTimer.setText("");
        Message message = new Message();
        changeFrameTimeCnt.sendMessage(message);
    }

    @Override
    public void setCaptureProgressMax(int max) {
        mCaptureProgressBar.setMax(max);
    }

    @Override
    public void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener) {
        this.mCameraUIEventsListener = cameraUIEventsListener;
    }

    static class FrameCntTime {
        int frame;
        int maxframe;
        double time;
    }
}

