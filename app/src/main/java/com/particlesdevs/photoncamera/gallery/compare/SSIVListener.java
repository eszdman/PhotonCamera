package com.particlesdevs.photoncamera.gallery.compare;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.particlesdevs.photoncamera.gallery.views.CustomSSIV;

public abstract class SSIVListener implements SubsamplingScaleImageView.OnStateChangedListener, CustomSSIV.TouchCallBack {
    /**
     * Called in compare mode after the current page's image finished loading
     * (also after an HDR decoder swap): the pane's state may have jumped, so
     * delta syncing must re-baseline instead of mirroring the jump.
     */
    public void onImageReady() {
    }

    /**
     * Called in compare mode when the viewer's selected page changed: the
     * freshly bound page starts at fit, so delta syncing must re-baseline
     * instead of mirroring the reset.
     */
    public void onPageSwitched() {
    }
}
