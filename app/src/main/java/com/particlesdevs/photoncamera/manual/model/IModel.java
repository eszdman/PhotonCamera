package com.particlesdevs.photoncamera.manual.model;

import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.KnobInfo;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.KnobItemInfo;

import java.util.List;

public interface IModel {
    List<KnobItemInfo> getKnobInfoList();

    KnobItemInfo getCurrentInfo();

    KnobInfo getKnobInfo();
}
