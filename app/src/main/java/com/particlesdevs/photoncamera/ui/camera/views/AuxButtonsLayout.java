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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.google.android.material.color.MaterialColors;
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
    private boolean hiddenBySettings;
    private boolean verticalOrder;
    private String activeCameraId;

    /**
     * Selection pill: one highlight for the whole bar that slides between the
     * lens buttons on an M3E spring (slight overshoot) instead of every button
     * toggling its own background. Drawn behind the buttons in {@link #onDraw}.
     */
    private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final FloatValueHolder selectorIndex = new FloatValueHolder(0f);
    private final SpringAnimation selectorSpring;
    /** Target lens index; {@link #selectorAppliedIndex} is what the spring has. */
    private int selectorTarget = -1;
    private int selectorAppliedIndex = Integer.MIN_VALUE;
    private int selectorChildCount = -1;
    private boolean selectorPlaced;
    private float selectorRadius;

public AuxButtonsLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        int margin = (int) context.getResources().getDimension(R.dimen.aux_button_internal_margin);
        int size = (int) context.getResources().getDimension(R.dimen.aux_button_size);
        buttonParams = new LinearLayout.LayoutParams(size, size);
        buttonParams.setMargins(margin, margin, margin, margin);

        selectorPaint.setColor(MaterialColors.getColor(context, R.attr.colorPrimaryContainer,
                Color.TRANSPARENT));
        selectorSpring = new SpringAnimation(selectorIndex)
                .setSpring(MorphShapeDrawable.playfulSpring())
                .addUpdateListener((animation, value, velocity) -> invalidate());
        // The pill is painted by this container, behind its children.
        setWillNotDraw(false);

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
        removeAllViews();
        auxButtonsMap.clear();
        List<CameraLensData> ordered = cameraLensDataList;
        if (verticalOrder) {
            ordered = new ArrayList<>(cameraLensDataList);
            Collections.reverse(ordered);
        }
        boolean mmEquivalent = PreferenceKeys.isLensMmEquivalentOn();
        for (CameraLensData cameraLensData : ordered) {
            addNewButton(cameraLensData.getCameraId(),
                    LensLabelFormatter.format(cameraLensData, mmEquivalent));
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
        int activeIndex = -1;
        for (int i = 0; i < getChildCount(); i++) {
            View button = getChildAt(i);
            button.setOnClickListener(auxButtonListener);
            if (activeId.equals(auxButtonsMap.get(button.getId()))) {
                button.setSelected(true);
                activeIndex = i;
            }
        }
        setSelectorTarget(activeIndex);
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
            int index = indexOfChild(view);
            if (index >= 0) {
                setSelectorTarget(index);
            }
            if (auxButtonListener != null)
                auxButtonListener.onAuxButtonClicked(auxButtonsMap.get(view.getId()));
        }
    }

    /** Points the pill at {@code index}, animating from the previous lens. */
    private void setSelectorTarget(int index) {
        selectorTarget = index;
        // The buttons are rebuilt on every refresh, so a changed set can never
        // be interpolated from the old index: snap in that case.
        if (getChildCount() != selectorChildCount) {
            selectorChildCount = getChildCount();
            selectorPlaced = false;
            selectorAppliedIndex = Integer.MIN_VALUE;
        }
        if (selectorPlaced) {
            applySelectorTarget();
        }
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        applySelectorTarget();
    }

    /**
     * Moves the pill toward the active button once the children have bounds.
     * The first placement (and a rebuilt button set) snaps; every later change
     * glides on the playful spring.
     */
    private void applySelectorTarget() {
        if (selectorTarget < 0 || selectorTarget >= getChildCount()) {
            return;
        }
        View active = getChildAt(selectorTarget);
        if (active.getWidth() <= 0 || active.getHeight() <= 0) {
            return;
        }
        selectorRadius = Math.min(active.getWidth(), active.getHeight()) / 2f;
        if (!selectorPlaced) {
            selectorSpring.cancel();
            selectorIndex.setValue(selectorTarget);
            selectorPlaced = true;
            selectorAppliedIndex = selectorTarget;
        } else if (selectorAppliedIndex != selectorTarget) {
            selectorAppliedIndex = selectorTarget;
            selectorSpring.animateToFinalPosition(selectorTarget);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Behind the buttons: onDraw runs before dispatchDraw. Single-lens
        // devices never show the pill.
        if (selectorPlaced && selectorTarget >= 0 && getChildCount() > 1) {
            boolean vertical = getOrientation() == VERTICAL;
            float along = selectorCenterAlongAxis();
            float cx = vertical ? getWidth() / 2f : along;
            float cy = vertical ? along : getHeight() / 2f;
            canvas.drawCircle(cx, cy, selectorRadius, selectorPaint);
        }
        super.onDraw(canvas);
    }

    /**
     * The pill's centre along the layout axis, interpolated between the real
     * button centres by the spring's animated index, so it tracks both the
     * horizontal and the docked vertical arrangements.
     */
    private float selectorCenterAlongAxis() {
        boolean vertical = getOrientation() == VERTICAL;
        View first = getChildAt(0);
        if (first == null) {
            return 0f;
        }
        float firstCenter = vertical ? first.getTop() + first.getHeight() / 2f
                : first.getLeft() + first.getWidth() / 2f;
        float step = 0f;
        if (getChildCount() > 1) {
            View second = getChildAt(1);
            float secondCenter = vertical ? second.getTop() + second.getHeight() / 2f
                    : second.getLeft() + second.getWidth() / 2f;
            step = secondCenter - firstCenter;
        }
        return firstCenter + step * selectorIndex.getValue();
    }

    private void addNewButton(String cameraId, String buttonText) {
        Button b = new Button(getContext());
        b.setLayoutParams(buttonParams);
        b.setText(buttonText);
        b.setTextAppearance(R.style.AuxButtonText);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 9, 13, 1,
                TypedValue.COMPLEX_UNIT_SP);
        // No per-button highlight: the container's sliding pill is the
        // selection. The shared aux_button_background drawable stays as-is for
        // the settings bar, which still uses its selected state.
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
        int buttonId = View.generateViewId();
        b.setId(buttonId);
        this.auxButtonsMap.put(buttonId, cameraId);
        addView(b);
    }

    public interface AuxButtonListener {
        void onAuxButtonClicked(String cameraId);
    }
}
