package com.particlesdevs.photoncamera.manual.model;

import com.particlesdevs.photoncamera.manual.KnobInfo;
import com.particlesdevs.photoncamera.manual.KnobItemInfo;

import java.util.List;

public interface IModel {
    List<KnobItemInfo> getKnobInfoList();

    KnobItemInfo getCurrentInfo();

    KnobInfo getKnobInfo();
}
