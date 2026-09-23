/*
 *
 *  PhotonCamera
 *  AuxButtonsLayout.java
 *  Copyright (C) 2020 - 2021  Vibhor
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

package com.particlesdevs.photoncamera.ui.camera.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.dynamicanimation.animation.SpringForce;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.ui.camera.binding.CustomBinding;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.ui.camera.data.LensLabelFormatter;
import com.particlesdevs.photoncamera.ui.camera.model.AuxButtonsModel;
import com.particlesdevs.photoncamera.ui.widget.MorphShapeDrawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Container for multi-camera buttons.
 * <p>
 * This layout's functionality is dependent on {@link AuxButtonsModel} which is provided
 * through DataBinding {@link CustomBinding#setAuxButtonModel(AuxButtonsLayout, AuxButtonsModel)}.
 * <p>
 * The selection highlight is the sliding pill inherited from
 * {@link SelectorPillLayout}: the buttons carry no background of their own and
 * only their label tint follows the selected state.
 */
public class AuxButtonsLayout extends SelectorPillLayout {

    /**
     * this map stores dynamically generated view-ids and corresponding camera-ids attached to that view(or button)
     * for functional purpose
     */
    private final HashMap<Integer, String> auxButtonsMap = new HashMap<>();

    private final LinearLayout.LayoutParams buttonParams;
    private AuxButtonListener auxButtonListener;
    private AuxButtonsModel auxButtonsModel;
    private boolean hiddenBySettings;
    private boolean verticalOrder;
    private String activeCameraId;

    public AuxButtonsLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        int margin = (int) context.getResources().getDimension(R.dimen.aux_button_internal_margin);
        int size = (int) context.getResources().getDimension(R.dimen.aux_button_size);
        buttonParams = new LinearLayout.LayoutParams(size, size);
        buttonParams.setMargins(margin, margin, margin, margin);

        // The layout editor runs this constructor but not the data-binding adapters,
        // so populate a few sample buttons so the host preview shows the aux palette.
        if (isInEditMode()) {
            addNewButton("0", "1x");
            addNewButton("1", "2x");
            addNewButton("2", "5x");
            setListenerAndSelected("0");
            updateVisibility();
        }
    }

    public void setAuxButtonsModel(AuxButtonsModel auxButtonsModel) {
        this.auxButtonsModel = auxButtonsModel;
        auxButtonListener = auxButtonsModel.getAuxButtonListener();
    }

    /**
     * The lens slide uses the slower playful spring so it takes about as long
     * as the viewfinder's aspect stretch (~350ms) and the two read as one
     * motion; the quick-settings rows keep the snappy default.
     */
    @NonNull
    @Override
    protected SpringForce createPillSpring() {
        return MorphShapeDrawable.slowPlayfulSpring();
    }

    public void setActiveId(String activeId) {
        activeCameraId = activeId;
        refresh(activeId);
    }

    /**
     * Sets the pill's reading order. Horizontal reads ascending zoom from left
     * to right (ultra-wide to tele); vertical reads tele at the top through
     * ultra-wide at the bottom, matching the pre-216133f2 vertical pill.
     */
    public void setVerticalOrder(boolean vertical) {
        if (verticalOrder == vertical) return;
        verticalOrder = vertical;
        if (activeCameraId != null) refresh(activeCameraId);
    }

    private void refresh(String cameraId) {
        if (auxButtonsModel == null) return;
        List<CameraLensData> front = auxButtonsModel.getFrontCameras();
        List<CameraLensData> back = auxButtonsModel.getBackCameras();
        if (front == null || back == null) return;
        if (!isFront(cameraId, front))
            this.setAuxButtons(back, cameraId);
        else
            this.setAuxButtons(front, cameraId);
    }

    private boolean isFront(String cameraId, List<CameraLensData> frontCameras) {
        return frontCameras.stream().anyMatch(cameraLensData -> cameraLensData.getCameraId().equals(cameraId));
    }

    private void setAuxButtons(List<CameraLensData> cameraLensDataList, String activeId) {
        List<CameraLensData> ordered = cameraLensDataList;
        if (verticalOrder) {
            ordered = new ArrayList<>(cameraLensDataList);
            Collections.reverse(ordered);
        }
        boolean mmEquivalent = PreferenceKeys.isLensMmEquivalentOn();
        // Reuse the existing buttons instead of recreating them: inflating a
        // styled Button per lens mid-animation (the lens set changes between a
        // logical video id and the physical photo lenses) drops frames for
        // every running animation. Trim/append only the difference, then
        // reassign the ids and labels by position.
        int count = ordered.size();
        while (getChildCount() > count) {
            View last = getChildAt(getChildCount() - 1);
            auxButtonsMap.remove(last.getId());
            removeViewAt(getChildCount() - 1);
        }
        while (getChildCount() < count) {
            addNewButton();
        }
        for (int i = 0; i < count; i++) {
            CameraLensData cameraLensData = ordered.get(i);
            Button button = (Button) getChildAt(i);
            auxButtonsMap.put(button.getId(), cameraLensData.getCameraId());
            String label = LensLabelFormatter.format(cameraLensData, mmEquivalent);
            if (!label.contentEquals(button.getText())) {
                button.setText(label);
            }
        }
        setListenerAndSelected(activeId);
        updateVisibility();
    }

    /**
     * Rebuilds the buttons with the current label mode. Called when the camera
     * resumes so a Lens Labels settings change applies without reopening the camera.
     */
    public void refresh() {
        if (activeCameraId != null) {
            refresh(activeCameraId);
        }
    }

    private void setListenerAndSelected(String activeId) {
        View.OnClickListener auxButtonListener = this::onAuxButtonClick;
        for (int i = 0; i < getChildCount(); i++) {
            View button = getChildAt(i);
            button.setOnClickListener(auxButtonListener);
            // Reused buttons keep their previous state, so the selection must
            // be written both ways: leaving a stale selected button behind
            // would park the pill on a lens that is no longer active.
            button.setSelected(activeId.equals(auxButtonsMap.get(button.getId())));
        }
        refreshSelection();
    }

    private void updateVisibility() {
        setVisibility(hiddenBySettings || getChildCount() <= 1 ? View.INVISIBLE : View.VISIBLE);
    }

    public void setAuxButtonsHidden(boolean hidden) {
        hiddenBySettings = hidden;
        if (hidden) {
            animate().setDuration(Motion.durationShort4(getContext()))
                    .setInterpolator(Motion.emphasizedDecelerate(getContext()))
                    .alpha(0).scaleX(0).scaleY(0)
                    .withEndAction(() -> setVisibility(View.INVISIBLE)).start();
        } else {
            updateVisibility();
            animate().setDuration(Motion.durationShort4(getContext()))
                    .setInterpolator(Motion.emphasized(getContext()))
                    .alpha(1).scaleX(1).scaleY(1).start();
        }
    }

    private void onAuxButtonClick(View view) {
        if (auxButtonsModel.isEnabled()) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.setSelected(view.equals(child));
            }
            // Slide the pill right away; the model's active id follows once the
            // new lens is open, and by then the target is already there.
            refreshSelection();
            if (auxButtonListener != null)
                auxButtonListener.onAuxButtonClicked(auxButtonsMap.get(view.getId()));
        }
    }

    /** Creates and adds one aux button; the caller assigns its id/label. */
    private Button addNewButton() {
        Button b = new Button(getContext());
        b.setLayoutParams(buttonParams);
        b.setTextAppearance(R.style.AuxButtonText);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 9, 13, 1,
                TypedValue.COMPLEX_UNIT_SP);
        // No per-button highlight: the container's sliding pill is the
        // selection, and the label tint follows the selected state.
        b.setBackground(null);
        // The Material button style's 24dp content padding would leave a 35dp
        // button no room for its label (it wrapped to a clipped second line).
        // The label owns the whole button and autosizes onto a single line.
        b.setPadding(0, 0, 0, 0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setMaxLines(1);
        b.setStateListAnimator(null);
        b.setTransformationMethod(null);
        b.setId(View.generateViewId());
        addView(b);
        return b;
    }

    /** Layout-editor helper: creates a button with its id and label set. */
    private void addNewButton(String cameraId, String buttonText) {
        Button b = addNewButton();
        b.setText(buttonText);
        auxButtonsMap.put(b.getId(), cameraId);
    }

    public interface AuxButtonListener {
        void onAuxButtonClicked(String cameraId);
    }
}
