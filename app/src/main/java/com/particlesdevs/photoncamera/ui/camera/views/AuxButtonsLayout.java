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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.ui.camera.binding.CustomBinding;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.ui.camera.model.AuxButtonsModel;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Container for multi-camera buttons.
 * <p>
 * This layout's functionality is dependent on {@link AuxButtonsModel} which is provided
 * through DataBinding {@link CustomBinding#setAuxButtonModel(AuxButtonsLayout, AuxButtonsModel)}.
 */
public class AuxButtonsLayout extends LinearLayout {

    /**
     * this map stores dynamically generated view-ids and corresponding camera-ids attached to that view(or button)
     * for functional purpose
     */
    private final HashMap<Integer, String> auxButtonsMap = new HashMap<>();

    private final LinearLayout.LayoutParams buttonParams;
    private AuxButtonListener auxButtonListener;
    private AuxButtonsModel auxButtonsModel;

    public AuxButtonsLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        int margin = (int) context.getResources().getDimension(R.dimen.aux_button_internal_margin);
        int size = (int) context.getResources().getDimension(R.dimen.aux_button_size);
        buttonParams = new LinearLayout.LayoutParams(size, size);
        buttonParams.setMargins(margin, margin, margin, margin);
    }

    private static String getAuxButtonName(float zoomFactor) {
        return String.format(Locale.US, "%.1fx", zoomFactor).replace(".0", "");
    }

    public void setAuxButtonsModel(AuxButtonsModel auxButtonsModel) {
        this.auxButtonsModel = auxButtonsModel;
        auxButtonListener = auxButtonsModel.getAuxButtonListener();
    }

    public void setActiveId(String activeId) {
        refresh(activeId);
    }

    private void refresh(String cameraId) {
        if (!isFront(cameraId)) {
            this.setAuxButtons(auxButtonsModel.getBackCameras(), cameraId);
        } else {
            this.setAuxButtons(auxButtonsModel.getFrontCameras(), cameraId);
        }
    }

    private boolean isFront(String cameraId) {
        return auxButtonsModel.getFrontCameras().stream().anyMatch(cameraLensData -> cameraLensData.getCameraId().equals(cameraId));
    }

    private void setAuxButtons(List<CameraLensData> cameraLensDataList, String activeId) {
        removeAllViews();
        auxButtonsMap.clear();
        cameraLensDataList.forEach(cameraLensData -> addNewButton(cameraLensData.getCameraId(), getAuxButtonName(cameraLensData.getZoomFactor())));
        setListenerAndSelected(activeId);
        updateVisibility();
    }

    private void setListenerAndSelected(String activeId) {
        View.OnClickListener auxButtonListener = this::onAuxButtonClick;
        for (int i = 0; i < getChildCount(); i++) {
            View button = getChildAt(i);
            button.setOnClickListener(auxButtonListener);
            if (activeId.equals(auxButtonsMap.get(button.getId()))) {
                button.setSelected(true);
            }
        }
    }

    private void updateVisibility() {
        setVisibility(getChildCount() > 1 ? View.VISIBLE : View.INVISIBLE);
    }

    private void onAuxButtonClick(View view) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.setSelected(view.equals(child));
        }
        if (auxButtonListener != null) {
            auxButtonListener.onAuxButtonClicked(auxButtonsMap.get(view.getId()));
        }
    }

    private void addNewButton(String cameraId, String buttonText) {
        Button b = new Button(getContext());
        b.setLayoutParams(buttonParams);
        b.setText(buttonText);
        b.setTextAppearance(R.style.AuxButtonText);
        b.setBackgroundResource(R.drawable.aux_button_background);
        b.setStateListAnimator(null);
        b.setTransformationMethod(null);
        int buttonId = View.generateViewId();
        b.setId(buttonId);
        this.auxButtonsMap.put(buttonId, cameraId);
        addView(b);
    }

    public interface AuxButtonListener {
        void onAuxButtonClicked(String cameraId);
    }
}
