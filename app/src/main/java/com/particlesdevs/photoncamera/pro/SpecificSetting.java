package com.particlesdevs.photoncamera.pro;


import androidx.annotation.Nullable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

//Device API specifics
public class SpecificSetting {
    public boolean isDualSessionSupported = true;
    public boolean isRawColorCorrection = false;
    public int[] cameraIDS = new int[4];

    public static String serialize(SpecificSetting specificSetting) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(specificSetting);
    }

    @Nullable
    public static SpecificSetting deserialize(String json) {
        try {
            return new GsonBuilder().setPrettyPrinting().create().fromJson(json, SpecificSetting.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }


}
