package com.particlesdevs.photoncamera.circularbarlib.control.models;


import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.SliderItem;

import java.util.List;

public interface IModel {
    List<SliderItem> getSliderItems();

    SliderItem getCurrentInfo();
}
