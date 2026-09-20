/*
 *
 *  PhotonCamera
 *  SettingsBarLayout.java
 *  Copyright (C) 2020 - 2021  Vibhor Srivastava
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * /
 */

package com.particlesdevs.photoncamera.ui.camera.views.settingsbar;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.ui.camera.model.SettingsBarButtonModel;
import com.particlesdevs.photoncamera.ui.camera.model.SettingsBarEntryModel;
import com.particlesdevs.photoncamera.ui.settings.SettingsActivity;
import com.particlesdevs.photoncamera.util.SecureCameraHelper;

public class SettingsBarLayout extends RelativeLayout implements SettingsBarListener {
    private final LinearLayout optionsContainer;
    private final Vibration vibration;

    public SettingsBarLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // In the layout editor (isInEditMode) the PhotonCamera Application instance
        // is never created, so the static sPhotonCamera is null.
        vibration = isInEditMode() ? null : PhotonCamera.getVibration();
        setBackgroundResource(R.drawable.exif_background);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setId(R.id.settings_bar_scroll_view);
        scrollView.setPadding(dp(10), dp(10), dp(10), dp(5));

        optionsContainer = new LinearLayout(context);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        RelativeLayout.LayoutParams optionsContainerParam = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        scrollView.addView(optionsContainer, optionsContainerParam);

        LinearLayout settingsButtonContainer = new LinearLayout(context);
        settingsButtonContainer.setId(R.id.settings_bar_settings_button_container);
        settingsButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        settingsButtonContainer.setGravity(Gravity.END);
        RelativeLayout.LayoutParams settingsButtonContainerParam = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        settingsButtonContainerParam.setMargins(dp(5), dp(0), dp(5), dp(5));
        settingsButtonContainerParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        ImageButton settingsButton = new ImageButton(context);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setBackgroundResource(getResolvedAttr(context, android.R.attr.selectableItemBackgroundBorderless));
        settingsButton.setPadding(dp(10), dp(5), dp(10), dp(5));
        settingsButton.setOnClickListener(v -> {
            if (SecureCameraHelper.isDeviceLocked(context)) {
                android.widget.Toast.makeText(context,
                        context.getString(R.string.secure_camera_settings_locked),
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(context, SettingsActivity.class);
            try {
                intent.putExtra("camera_mode",
                        com.particlesdevs.photoncamera.settings.PreferenceKeys.getCameraModeOrdinal());
            } catch (Exception ignored) {
            }
            context.startActivity(intent);
        });
        LayoutParams buttonParam = new LayoutParams(dp(35), dp(35));
        buttonParam.setMargins(dp(10), dp(2.5f), dp(20), dp(2.5f));
        settingsButtonContainer.addView(settingsButton, buttonParam);

        RelativeLayout.LayoutParams scrollViewParam = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scrollViewParam.addRule(ABOVE, R.id.settings_bar_settings_button_container);

        addView(scrollView, scrollViewParam);
        addView(settingsButtonContainer, settingsButtonContainerParam);
    }

    public void addEntry(SettingsBarEntryModel entryModel) {
        entryModel.setSettingsBarListener(this);
        SettingsBarEntryView entryView = new SettingsBarEntryView(getContext());
        entryView.setId(entryModel.getId());
        entryView.setSettingsBarEntryModel(entryModel);
        optionsContainer.addView(entryView);
    }

    private int dp(float f) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, f, getContext().getResources().getDisplayMetrics());
    }

    @Override
    public void onEntryUpdated(SettingsBarEntryModel entryModel, SettingsBarButtonModel buttonModel) {
        vibration.confirm();
        for (SettingsBarButtonModel model : entryModel.getSettingsBarButtonModels()) {
            findViewById(entryModel.getId()).findViewById(model.getId()).setSelected(model.isSelected());
        }
        if (entryModel.getStateTextStringId() != 0) {
            ((TextView) findViewById(entryModel.getId()).findViewById(android.R.id.summary)).setText(entryModel.getStateTextStringId());
        }
    }

    public void removeEntries() {
        if (optionsContainer != null) {
            optionsContainer.removeAllViews();
        }
    }

    /**
     * Predictive-back progress: maps 0 (open) to 1 (fully dismissed) onto the
     * same transform as the hide animation, without committing any state.
     * Only invoked on API 34+ while a back gesture is in flight.
     */
    public void setBackProgress(float progress) {
        float p = Math.min(1f, Math.max(0f, progress));
        float maxDy = getResources().getDimension(R.dimen.standard_125);
        setTranslationY(-maxDy * p);
        setScaleX(1f - p);
        setScaleY(1f - p);
        setAlpha(1f - p);
    }

    /** Restores the fully-open transform after a cancelled predictive back gesture. */
    public void cancelBackProgress() {
        animate().setDuration(Motion.durationMedium2(getContext()))
                .setInterpolator(Motion.emphasized(getContext()))
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).start();
    }

    private int getResolvedAttr(Context context, int attrId) {
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(attrId, outValue, true);
        return outValue.resourceId;
    }

    /**
     * Called by the camera fragment when the preview renderer enables its live
     * frosted-glass backdrop behind this panel. While active the panel keeps a
     * 40% dark scrim so the blurred content stays legible; otherwise the fully
     * opaque fallback is used.
     */
    public void setBlurActive(boolean active) {
        setBackgroundResource(active ? R.drawable.cam_bar_blur_overlay : R.drawable.exif_background);
    }

    public void setChildVisibility(@IdRes int id, int visibility) {
        View view = findViewById(id);
        if (view != null) {
            view.setVisibility(visibility);
        }
    }
}
