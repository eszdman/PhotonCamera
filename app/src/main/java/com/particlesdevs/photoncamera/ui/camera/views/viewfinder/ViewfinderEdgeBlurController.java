package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import android.graphics.Rect;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Owns the preview surface's geometry. By default the surface matches the
 * viewfinder frame exactly (the black/gradient letterbox stays visible); with
 * the edge-blur option on, the surface fills the whole camera layout and the
 * renderer letterboxes the sharp image back into the frame, painting a blurred
 * backdrop in the surrounding black areas.
 */
public class ViewfinderEdgeBlurController {
    private final ConstraintLayout root;
    private final GLPreview preview;
    private final View frame;
    private boolean enabled;
    private int lastLeft = Integer.MIN_VALUE;
    private int lastTop = Integer.MIN_VALUE;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int lastSharpLeft = Integer.MIN_VALUE;
    private int lastSharpTop = Integer.MIN_VALUE;
    private int lastSharpWidth = -1;
    private int lastSharpHeight = -1;
    private boolean lastEnabled;

    public ViewfinderEdgeBlurController(ConstraintLayout root, GLPreview preview, View frame) {
        this.root = root;
        this.preview = preview;
        this.frame = frame;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        update();
    }

    /** Recomputes the preview bounds and sharp rect (frame moved/resized). */
    public void update() {
        if (root == null || preview == null || frame == null) {
            return;
        }
        int rootW = root.getWidth();
        int rootH = root.getHeight();
        int frameW = frame.getWidth();
        int frameH = frame.getHeight();
        if (rootW <= 0 || rootH <= 0 || frameW <= 0 || frameH <= 0) {
            return;
        }

        int[] rootLocation = new int[2];
        int[] frameLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        frame.getLocationOnScreen(frameLocation);
        int frameLeft = frameLocation[0] - rootLocation[0];
        int frameTop = frameLocation[1] - rootLocation[1];

        int targetLeft;
        int targetTop;
        int targetW;
        int targetH;
        int sharpLeft;
        int sharpTop;
        int sharpW;
        int sharpH;
        if (enabled) {
            targetLeft = 0;
            targetTop = 0;
            targetW = rootW;
            targetH = rootH;
            sharpLeft = frameLeft;
            sharpTop = frameTop;
            sharpW = frameW;
            sharpH = frameH;
        } else {
            targetLeft = frameLeft;
            targetTop = frameTop;
            targetW = frameW;
            targetH = frameH;
            sharpLeft = 0;
            sharpTop = 0;
            sharpW = frameW;
            sharpH = frameH;
        }
        if (targetLeft == lastLeft && targetTop == lastTop && targetW == lastWidth
                && targetH == lastHeight && sharpLeft == lastSharpLeft
                && sharpTop == lastSharpTop && sharpW == lastSharpWidth
                && sharpH == lastSharpHeight && enabled == lastEnabled) {
            return;
        }
        lastLeft = targetLeft;
        lastTop = targetTop;
        lastWidth = targetW;
        lastHeight = targetH;
        lastSharpLeft = sharpLeft;
        lastSharpTop = sharpTop;
        lastSharpWidth = sharpW;
        lastSharpHeight = sharpH;
        lastEnabled = enabled;

        ConstraintLayout.LayoutParams params =
                (ConstraintLayout.LayoutParams) preview.getLayoutParams();
        params.width = targetW;
        params.height = targetH;
        params.leftMargin = targetLeft;
        params.topMargin = targetTop;
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        preview.setLayoutParams(params);

        preview.setEdgeBlurEnabled(enabled);
        preview.setSharpRect(new Rect(sharpLeft, sharpTop, sharpLeft + sharpW, sharpTop + sharpH));
    }
}
