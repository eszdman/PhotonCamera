package com.particlesdevs.photoncamera.pro;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.particlesdevs.photoncamera.util.ListWrapper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

//Device current sensor specific
public class SpecificSettingSensor {
    public int id = 0;
    public boolean isRawColorCorrection = false;

    public float[] blackLevel = new float[4];

    public float captureSharpeningS = 1.05f;
    public float captureSharpeningIntense = 0.5f;

    public float[] aberrationCorrection = new float[8];

    public float[][] cct = new float[3][3];
    public boolean cctExists = false;

    public double[][] noiseModelerArr = new double[4][4];
    public boolean modelerExists = false;


    public static String serializeList(ArrayList<SpecificSettingSensor> list) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(new ListWrapper<>(list));
    }

    @Nullable
    public static ArrayList<SpecificSettingSensor> deserializeList(@Nullable String json) {
        try {
            ListWrapper<SpecificSettingSensor> wrapper = new GsonBuilder().setPrettyPrinting().create().fromJson(json, getListType());
            if (wrapper != null) {
                return wrapper.getList();
            }
            return null;
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Type getListType() {
        return new TypeToken<ListWrapper<SpecificSettingSensor>>() {
        }.getType();
    }

    @NonNull
    @Override
    public String toString() {
        return "\n" +
                "SpecificSettingSensor{" +
                "\n" +
                "id=" + id +
                "\n" +
                ", isRawColorCorrection=" + isRawColorCorrection +
                "\n" +
                ", blackLevel=" + Arrays.toString(blackLevel) +
                "\n" +
                ", captureSharpeningS=" + captureSharpeningS +
                "\n" +
                ", captureSharpeningIntense=" + captureSharpeningIntense +
                "\n" +
                ", aberrationCorrection=" + Arrays.toString(aberrationCorrection) +
                "\n" +
                ", cct=" + Arrays.deepToString(cct) +
                "\n" +
                ", cctExists=" + cctExists +
                "\n" +
                ", noiseModelerArr=" + Arrays.deepToString(noiseModelerArr) +
                "\n" +
                ", modelerExists=" + modelerExists +
                '}' +
                "\n";
    }
}
