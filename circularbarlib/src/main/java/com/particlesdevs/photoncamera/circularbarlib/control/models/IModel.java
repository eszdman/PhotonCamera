package com.particlesdevs.photoncamera.circularbarlib.control.models;


import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobInfo;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobItemInfo;

import java.util.List;

public interface IModel {
    List<KnobItemInfo> getKnobInfoList();

    KnobItemInfo getCurrentInfo();

    KnobInfo getKnobInfo();
}
