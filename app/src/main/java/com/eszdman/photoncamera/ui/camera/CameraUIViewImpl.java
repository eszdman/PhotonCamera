package com.eszdman.photoncamera.ui.camera;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.core.util.Pair;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.databinding.CameraFragmentBinding;
import com.eszdman.photoncamera.databinding.LayoutBottombuttonsBinding;
import com.eszdman.photoncamera.databinding.LayoutMainTopbarBinding;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.ui.camera.views.modeswitcher.wefika.horizontalpicker.HorizontalPicker;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This Class is a dumb 'View' which contains view components visible in the main Camera User Interface
 * <p>
 * It gets instantiated in {@link CameraFragment#onViewCreated(View, Bundle)}
 */
public final class CameraUIViewImpl implements CameraUIView {
    private static final String TAG = "CameraUIView";
    private final CameraFragmentBinding cameraFragmentBinding;
    private final LayoutMainTopbarBinding topbar;
    private final LayoutBottombuttonsBinding bottombuttons;
    private ProgressBar mCaptureProgressBar;
    private ImageButton mShutterButton;
    private ProgressBar mProcessingProgressBar;
    private LinearLayout mAuxGroupContainer;
    private CameraUIEventsListener mCameraUIEventsListener;
    private HorizontalPicker mModePicker;
    private HashMap<Integer, String> auxButtonsMap;
    private float baseF = 0.f;

    public CameraUIViewImpl(CameraFragmentBinding cameraFragmentBinding) {
        this.cameraFragmentBinding = cameraFragmentBinding;
        this.topbar = cameraFragmentBinding.layoutTopbar;
        this.bottombuttons = cameraFragmentBinding.layoutBottombar.bottomButtons;
        initViews();
        initListeners();
        initModeSwitcher();
        refresh();
    }

    private void initViews() {
        mCaptureProgressBar = cameraFragmentBinding.layoutViewfinder.captureProgressBar;
        mProcessingProgressBar = bottombuttons.processingProgressBar;
        mShutterButton = bottombuttons.shutterButton;
        mModePicker = cameraFragmentBinding.layoutBottombar.modeSwitcher.modePickerView;
        mAuxGroupContainer = cameraFragmentBinding.auxButtonsContainer;
    }

    private void initListeners() {
        topbar.setTopBarClickListener(v -> mCameraUIEventsListener.onClick(v));
        bottombuttons.setBottomBarClickListener(v -> mCameraUIEventsListener.onClick(v));
    }

    private void initModeSwitcher() {
        String[] modes = Stream.of(CameraMode.names()).map(s -> s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT)).toArray(String[]::new);
        mModePicker.setValues(modes);
        mModePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mModePicker.setOnItemSelectedListener(index -> switchToMode(CameraMode.valueOf(index)));
        mModePicker.setSelectedItem(PreferenceKeys.getCameraModeOrdinal());
        reConfigureModeViews(CameraMode.valueOf(PreferenceKeys.getCameraModeOrdinal()));
    }

    @Override
    public void activateShutterButton(boolean status) {
        mShutterButton.setActivated(status);
        mShutterButton.setClickable(status);
    }

    private void switchToMode(CameraMode cameraMode) {
        reConfigureModeViews(cameraMode);
        this.mCameraUIEventsListener.onCameraModeChanged(cameraMode);
    }

    private void reConfigureModeViews(CameraMode input) {
        Log.d(TAG, "Current Mode:" + input.name());
        switch (input) {
            case VIDEO:
                topbar.setEisVisible(true);
            case UNLIMITED:
                topbar.setFpsVisible(true);
                mShutterButton.setBackgroundResource(R.drawable.unlimitedbutton);
                break;
            case PHOTO:
            default:
                topbar.setEisVisible(true);
                topbar.setFpsVisible(true);
                mShutterButton.setBackgroundResource(R.drawable.roundbutton);
                break;
            case NIGHT:
                topbar.setEisVisible(false);
                topbar.setFpsVisible(false);
                mShutterButton.setBackgroundResource(R.drawable.roundbutton);
                break;
        }
    }

    @Override
    public void refresh() {
        cameraFragmentBinding.invalidateAll();
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

    @SuppressLint("DefaultLocale")
    @Override
    public void setAuxButtons(Set<String> idsList, Map<String, Pair<Float, Float>> Focals, String active) {
        mAuxGroupContainer.removeAllViews();
        if (idsList.size() > 1) {
            Locale.setDefault(Locale.US);
            auxButtonsMap = new HashMap<>();
            for (String id : idsList) {
                addToAuxGroupButtons(id, String.format("%.1fx", ((Focals.get(id).first / baseF) - 0.049)).replace(".0", ""));
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

    private void addToAuxGroupButtons(String cameraId, String name) {
        Button b = new Button(mAuxGroupContainer.getContext());
        int m = (int) mAuxGroupContainer.getContext().getResources().getDimension(R.dimen.aux_button_internal_margin);
        int s = (int) mAuxGroupContainer.getContext().getResources().getDimension(R.dimen.aux_button_size);
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
        new Handler(Looper.getMainLooper()).post(() -> mCaptureProgressBar.setAlpha(alpha));
    }

    /*@SuppressLint("DefaultLocale")
    private String FltFormat(Object in) {
        return String.format("%.0f", in);
    }*/

    @Override
    public void setCaptureProgressMax(int max) {
        mCaptureProgressBar.setMax(max);
    }

    @Override
    public void showFlashButton(boolean flashAvailable) {
        topbar.setFlashVisible(flashAvailable);
    }

    @Override
    public void setCameraUIEventsListener(CameraUIEventsListener cameraUIEventsListener) {
        this.mCameraUIEventsListener = cameraUIEventsListener;
    }
}

