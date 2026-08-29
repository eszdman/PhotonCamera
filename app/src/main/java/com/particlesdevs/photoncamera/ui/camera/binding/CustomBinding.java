package com.particlesdevs.photoncamera.ui.camera.binding;

import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.BindingAdapter;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.ui.camera.model.AuxButtonsModel;
import com.particlesdevs.photoncamera.ui.camera.model.CameraFragmentModel;
import com.particlesdevs.photoncamera.ui.camera.views.AuxButtonsLayout;

/**
 * Class to handle custom bindings that should get applied when a model change
 * <p>
 * Created by KillerInk on 02/Oct/2020
 * Modified by Vibhor
 */
public class CustomBinding {

    /**
     * Handle the rotation that should get applied when the CameraFragmentModels rotation change
     * the view item must add attribute 'bindRotate="@{uimodel}"'
     *
     * @param view  any view that needs to be rotated
     * @param model the cameraFragmentModel
     */
    @BindingAdapter("bindRotate")
    public static void rotateView(View view, CameraFragmentModel model) {
        if (model != null)
            view.animate().rotation(model.getOrientation()).setDuration(model.getDuration()).start();
    }

    /**
     * Handle the rotation that should get applied to any ViewGroup when the CameraFragmentModels rotation change
     * Only the children views within the ViewGroup will rotate.
     * the ui item must add bindViewGroupChildrenRotate="@{uimodel}"
     *
     * @param viewGroup the container ViewGroup
     * @param model     the cameraFragmentModel
     */
    @BindingAdapter("bindViewGroupChildrenRotate")
    public static void rotateAuxButtons(ViewGroup viewGroup, CameraFragmentModel model) {
        if (model != null) {
            int orientation = model.getOrientation();
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                viewGroup.getChildAt(i).animate().rotation(orientation).setDuration(model.getDuration()).start();
            }
        }
    }

    /**
     * Change the selected state of any view
     *
     * @param view     the target view
     * @param selected whether selected
     */
    @BindingAdapter("android:selected")
    public static void setSelected(View view, Boolean selected) {
        if (selected != null && view != null) {
            view.setSelected(selected);
        }
    }

    /**
     * Selects/unselects the children of the target {@link ViewGroup} here {@link R.id#buttons_container}.
     * Only the child with given view id will be selected and rest of children will get unselected.
     *
     * @param viewGroup the target ViewGroup
     * @param viewID    id of the {@link CheckedTextView} to be checked
     */
    @BindingAdapter("selectViewIdInViewGroup")
    public static void selectViewIdInViewGroup(ViewGroup viewGroup, int viewID) {
        if (viewGroup != null) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                viewGroup.getChildAt(i).setSelected(viewGroup.getChildAt(i).getId() == viewID);
            }
        }
    }

    @BindingAdapter("settingsBarVisibility")
    public static void toggleSettingsBarVisibility(ViewGroup viewGroup, boolean visible) {
        if (viewGroup != null)
            if (visible)
                viewGroup.post(() -> {
                    viewGroup.animate().setDuration(200).alpha(1).translationY(0).scaleX(1).scaleY(1).start();
                    viewGroup.setVisibility(View.VISIBLE);
                });
            else
                viewGroup.post(() -> viewGroup.animate().setDuration(200).alpha(0).translationY(-viewGroup.getResources().getDimension(R.dimen.standard_125))
                        .scaleX(0).scaleY(0).withEndAction(() -> viewGroup.setVisibility(View.INVISIBLE))
                        .start());
    }

    @BindingAdapter("setAuxButtonModel")
    public static void setAuxButtonModel(AuxButtonsLayout layout, AuxButtonsModel auxButtonsModel) {
        if (auxButtonsModel != null)
            layout.setAuxButtonsModel(auxButtonsModel);
    }

    @BindingAdapter("hideAuxButtons")
    public static void setAuxButtonsHidden(AuxButtonsLayout layout, boolean hidden) {
        if (layout != null)
            layout.setAuxButtonsHidden(hidden);
    }

    @BindingAdapter("setActiveId")
    public static void setActiveCameraId(AuxButtonsLayout layout, String cameraId) {
        if (cameraId != null)
            layout.setActiveId(cameraId);
    }

    @BindingAdapter("layoutMarginTop")
    public static void setLayoutMarginTop(View view, float margin) {
        ViewGroup.MarginLayoutParams layoutParams = ((ViewGroup.MarginLayoutParams) view.getLayoutParams());
        layoutParams.topMargin = (int) margin;
        view.setLayoutParams(layoutParams);
    }

    @BindingAdapter("adjustCameraContainer")
    public static void adjustCameraContainer(ConstraintLayout cameraContainer, float displayAspectRatio) {
        if (displayAspectRatio <= 16f / 9) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) cameraContainer.getLayoutParams();
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            params.topToBottom = ConstraintLayout.LayoutParams.UNSET;
        }
    }

    @BindingAdapter("adjustTopBar")
    public static void adjustTopBar(View topbar, float displayAspectRatio) {
        if (displayAspectRatio > 16f / 9) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) topbar.getLayoutParams();
            DisplayMetrics displayMetrics = topbar.getResources().getDisplayMetrics();
            float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
            float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
            float dpmargin = (dpHeight - (dpWidth / 9f * 16f));
            params.topMargin = (int) dpmargin;
        }
    }
    
    @BindingAdapter("setAspectRatio")
    public static void setAspectRatio(View view, String ratio) {
        if (view != null && ratio != null && !ratio.isEmpty()) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) layoutParams;
                params.dimensionRatio = ratio;
                view.setLayoutParams(params);
            }
        }
    }

    /**
     * Updates the on-screen zoom indicator (a {@link android.widget.TextView})
     * from the live zoom ratio, and hides it entirely at 1.0x.
     */
    @BindingAdapter("zoomIndicator")
    public static void setZoomIndicator(android.widget.TextView view, float zoomRatio) {
        if (view == null) return;
        view.setTag(R.id.zoom_ratio_tag, zoomRatio);
        if (Math.abs(zoomRatio - 1.0f) > 0.0001f && !isHiddenBySettings(view)) {
            view.setText(String.format(java.util.Locale.US, "%.1fx", zoomRatio));
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Hides the zoom indicator along with the lens switcher when the floating
     * settings bar is opened.
     */
    @BindingAdapter("hideZoomIndicator")
    public static void setZoomIndicatorHidden(android.widget.TextView view, boolean hidden) {
        if (view == null) return;
        view.setTag(R.id.zoom_hidden_tag, hidden);
        hideOrShowZoom(view);
    }

    private static boolean isHiddenBySettings(android.widget.TextView view) {
        Object tag = view.getTag(R.id.zoom_hidden_tag);
        return tag instanceof Boolean && (Boolean) tag;
    }

    private static void hideOrShowZoom(android.widget.TextView view) {
        Object ratioTag = view.getTag(R.id.zoom_ratio_tag);
        float ratio = ratioTag instanceof Float ? (Float) ratioTag : 1.0f;
        boolean hidden = isHiddenBySettings(view);
        if (Math.abs(ratio - 1.0f) > 0.0001f && !hidden) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }
}
