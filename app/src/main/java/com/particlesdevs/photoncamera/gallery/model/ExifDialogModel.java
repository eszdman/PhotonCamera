package com.particlesdevs.photoncamera.gallery.model;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import com.particlesdevs.photoncamera.gallery.views.Histogram;

/**
 * A simple data class which stores the exif data to be displayed in the exif dialog in the {@link com.particlesdevs.photoncamera.gallery.ui.GalleryActivity}
 * Currently this data model class is directly bound to {@link com.particlesdevs.photoncamera.R.layout#exif_dialog} through DataBinding library
 */
public class ExifDialogModel extends BaseObservable {
    private String title;
    private String res;
    private String res_mp;
    private String device;
    private String date;
    private String exposure;
    private String iso;
    private String fnum;
    private String focal;
    private String file_size;
    private String miniText;
    private Histogram.HistogramModel histogramModel;

    @Bindable
    public String getMiniText() {
        return miniText;
    }

    public void setMiniText(String miniText) {
        this.miniText = miniText;
    }

    @Bindable
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Bindable
    public String getRes() {
        return res;
    }

    public void setRes(String res) {
        this.res = res;
    }

    @Bindable
    public String getRes_mp() {
        return res_mp;
    }

    public void setRes_mp(String res_mp) {
        this.res_mp = res_mp;
    }

    @Bindable
    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    @Bindable
    public String getFile_size() {
        return file_size;
    }

    public void setFile_size(String file_size) {
        this.file_size = file_size;
    }

    @Bindable
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Bindable
    public String getExposure() {
        return exposure;
    }

    public void setExposure(String exposure) {
        this.exposure = exposure;
    }

    @Bindable
    public String getIso() {
        return iso;
    }

    public void setIso(String iso) {
        this.iso = iso;
    }

    @Bindable
    public String getFnum() {
        return fnum;
    }

    public void setFnum(String fnum) {
        this.fnum = fnum;
    }

    @Bindable
    public String getFocal() {
        return focal;
    }

    public void setFocal(String focal) {
        this.focal = focal;
    }

    @Bindable
    public Histogram.HistogramModel getHistogramModel() {
        return histogramModel;
    }

    public void setHistogramModel(Histogram.HistogramModel histogramModel) {
        this.histogramModel = histogramModel;
        notifyChange();
    }
}
