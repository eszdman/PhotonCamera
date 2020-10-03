package com.eszdman.photoncamera.ui.camera;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker;
import de.hdodenhof.circleimageview.CircleImageView;

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
    private FrameLayout mAuxGroupContainer;
    private CameraUIEventsListener mCameraUIEventsListener;
    private HorizontalPicker mModePicker;
    private ToggleButton mFpsButton;
    private ToggleButton mQuadResolutionButton;
    private ToggleButton mEisPhotoButton;
    private ImageButton mFlipCameraButton;
    private ImageButton mSettingsButton;
    private ToggleButton mHdrXButton;

    public CameraUIViewImpl(View rootView) {
        Log.d(TAG, "CameraUIView() called with: rootView = [" + rootView + "]");
        mRootView = rootView;
        initViews(rootView);
        initListeners();
        refresh();
    }

    private void initViews(View rootView) {
        mGridView = rootView.findViewById(R.id.grid_view);
        mRoundEdgesView = rootView.findViewById(R.id.round_edges_view);
        mCaptureProgressBar = rootView.findViewById(R.id.capture_progress_bar);
        mProcessingProgressBar = rootView.findViewById(R.id.processing_progress_bar);
        mShutterButton = rootView.findViewById(R.id.shutter_button);
        mGalleryImageButton = rootView.findViewById(R.id.gallery_image_button);
        mFpsButton = rootView.findViewById(R.id.fps_toggle_button);
        mHdrXButton = rootView.findViewById(R.id.hdrx_toggle_button);
        mModePicker = rootView.findViewById(R.id.mode_picker_view);
        mQuadResolutionButton = rootView.findViewById(R.id.quad_res_toggle_button);
        mEisPhotoButton = rootView.findViewById(R.id.eis_toggle_button);
        mFlipCameraButton = rootView.findViewById(R.id.flip_camera_button);
        mSettingsButton = rootView.findViewById(R.id.settings_button);
        mAuxGroupContainer = rootView.findViewById(R.id.aux_buttons_container);
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
        PreferenceKeys.setCameraMode(0); //this shouldnt be here,Temporary
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
    public void initAuxButtons(Set<String> backCameraIdsList, Set<String> frontCameraIdsList) {
        String savedCameraID = PreferenceKeys.getCameraID();
        if (mAuxGroupContainer.getChildCount() == 0) {
            if (backCameraIdsList.contains(savedCameraID)) {
                setAuxButtons(backCameraIdsList, savedCameraID);
            } else if (frontCameraIdsList.contains(savedCameraID)) {
                setAuxButtons(frontCameraIdsList, savedCameraID);
            }
        }
    }

    @Override
    public void setAuxButtons(Set<String> idsList, String active) {
        mAuxGroupContainer.removeAllViews();
        if (idsList.size() > 1) {
            mAuxButtonsGroup = new RadioGroup(mRootView.getContext());
            mAuxButtonsGroup.setOrientation(LinearLayout.VERTICAL);
            for (String id : idsList) {
                addToAuxGroupButtons(id);
            }
            mAuxButtonsGroup.check(Integer.parseInt(active));
            mAuxButtonsGroup.setOnCheckedChangeListener((radioGroup, i) ->
                    mCameraUIEventsListener.onAuxButtonClicked(String.valueOf(i)));
            mAuxButtonsGroup.setVisibility(View.VISIBLE);
            mAuxGroupContainer.addView(mAuxButtonsGroup);
        }
    }

    public void setGalleryButtonImage(Bitmap bitmap) {
        if (bitmap != null) {
            mGalleryImageButton.setImageBitmap(bitmap);
        }
    }

    private void addToAuxGroupButtons(String id) {
        RadioButton rb = new RadioButton(mRootView.getContext());
        rb.setText(id);
        rb.setButtonDrawable(R.drawable.custom_aux_switch_thumb);
        int padding = (int) rb.getContext().getResources().getDimension(R.dimen.aux_button_padding);
        rb.setPaddingRelative(padding, padding, padding, padding);
        rb.setTextAppearance(R.style.ManualModeKnobText);
        rb.setId(Integer.parseInt(id)); //here actual camera id assigned as RadioButton's resource ID
        mAuxButtonsGroup.addView(rb);
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
        mCaptureProgressBar.setAlpha(alpha);
    }

    @Override
    public void setCaptureProgressMax(int max) {
        mCaptureProgressBar.setMax(max);
    }

    @Override
    public void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener) {
        this.mCameraUIEventsListener = cameraUIEventsListener;
    }
}

