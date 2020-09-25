package com.eszdman.photoncamera.ui.camera;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker;
import com.eszdman.photoncamera.util.FileManager;
import de.hdodenhof.circleimageview.CircleImageView;
import rapid.decoder.BitmapDecoder;

import java.io.File;
import java.util.Set;

public class CameraUIView {
    private static final String TAG = "CameraUIView";
    private final View mRootView;
    public ProgressBar captureProgressBar;
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

    public CameraUIView(View rootView) {
        Log.d(TAG, "CameraUIView() called with: rootView = [" + rootView + "]");
        mRootView = rootView;
        initViews(rootView);
        initListeners();
    }

    private void initViews(View rootView) {
        mGridView = rootView.findViewById(R.id.grid);
        mRoundEdgesView = rootView.findViewById(R.id.edges);
        captureProgressBar = rootView.findViewById(R.id.lightCycle);
        mProcessingProgressBar = rootView.findViewById(R.id.progressloading);
        mShutterButton = rootView.findViewById(R.id.picture);
        mGalleryImageButton = rootView.findViewById(R.id.ImageOut);
        mFpsButton = rootView.findViewById(R.id.fpsPreview);
        mHdrXButton = rootView.findViewById(R.id.stacking);
        mModePicker = rootView.findViewById(R.id.modePicker);
        mQuadResolutionButton = rootView.findViewById(R.id.quadRes);
        mEisPhotoButton = rootView.findViewById(R.id.eisPhoto);
        mFlipCameraButton = rootView.findViewById(R.id.flip_camera);
        mSettingsButton = rootView.findViewById(R.id.settings);
        mAuxGroupContainer = rootView.findViewById(R.id.aux_buttons_container);

    }

    private void initListeners() {

        captureProgressBar.setMax(PreferenceKeys.getFrameCountValue());
        captureProgressBar.setAlpha(0);
        mProcessingProgressBar.setMax(PreferenceKeys.getFrameCountValue());
        mFpsButton.setChecked(PreferenceKeys.isFpsPreviewOn());
        mQuadResolutionButton.setChecked(PreferenceKeys.isQuadBayerOn());
        mEisPhotoButton.setChecked(PreferenceKeys.isEisPhotoOn());

        View.OnClickListener commonOnClickListener = v -> mCameraUIEventsListener.onClick(v);

        mShutterButton.setOnClickListener(commonOnClickListener);
        mGalleryImageButton.setOnClickListener(commonOnClickListener);
        mEisPhotoButton.setOnClickListener(commonOnClickListener);
        mFpsButton.setOnClickListener(commonOnClickListener);
        mQuadResolutionButton.setOnClickListener(commonOnClickListener);
        mFlipCameraButton.setOnClickListener(commonOnClickListener);
        mSettingsButton.setOnClickListener(commonOnClickListener);
        mHdrXButton.setOnClickListener(commonOnClickListener);

        loadGalleryButtonImage(null);

        String[] modes = CameraMode.names();

        mModePicker.setValues(modes);

        mModePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);

        mModePicker.setOnItemSelectedListener(index -> switchToMode(CameraMode.valueOf(modes[index])));

        mModePicker.setSelectedItem(1);
    }

    public void burstUnlock() {
        mShutterButton.setActivated(true);
        mShutterButton.setClickable(true);
    }

    public void switchToMode(CameraMode cameraMode) {
        this.mCameraUIEventsListener.onCameraModeChanged(cameraMode);
        reConfigureModeViews(cameraMode);
    }

    public void reConfigureModeViews(CameraMode input) {
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

    public void onCameraResume() {
        if (PreferenceKeys.isShowGridOn())
            mGridView.setVisibility(View.VISIBLE);
        else
            mGridView.setVisibility(View.GONE);
        if (PreferenceKeys.isRoundEdgeOn())
            mRoundEdgesView.setVisibility(View.VISIBLE);
        else
            mRoundEdgesView.setVisibility(View.GONE);
        burstUnlock();
        clearProcessingCycle();
    }

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
                    mCameraUIEventsListener.onAuxButtonClick(String.valueOf(i)));
            mAuxButtonsGroup.setVisibility(View.VISIBLE);
            mAuxGroupContainer.addView(mAuxButtonsGroup);
        }
    }

    public void loadGalleryButtonImage(Bitmap bitmap) {
        if (bitmap == null) {
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
                if (lastImage != null) {
                    mGalleryImageButton.setImageBitmap(
                            BitmapDecoder.from(Uri.fromFile(lastImage))
                                    .scaleBy(0.1f)
                                    .decode());
                }
            }
        } else
            mGalleryImageButton.setImageBitmap(bitmap);
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

    public void clearProcessingCycle() {
        mProcessingProgressBar.setProgress(0);
    }

    public void incrementProcessingCycle(int step) {
        int progress = (mProcessingProgressBar.getProgress() + step) % (mProcessingProgressBar.getMax() + step);
        progress = Math.max(step, progress);
        mProcessingProgressBar.setProgress(progress);
    }

    public void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener) {
        this.mCameraUIEventsListener = cameraUIEventsListener;
    }

    public interface CameraUIEventsListener {
        void onClick(View v);

        void onAuxButtonClick(String id);

        void onCameraModeChanged(CameraMode cameraMode);

        void onUnlimitedButtonPressed();

    }
}

