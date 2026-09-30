package com.particlesdevs.photoncamera.gallery.viewmodel;


import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentResolver;
import android.util.Rational;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.AndroidViewModel;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.files.MediaFile;
import com.particlesdevs.photoncamera.gallery.helper.ExifDescriptionDecoder;
import com.particlesdevs.photoncamera.gallery.model.ExifDialogModel;
import com.particlesdevs.photoncamera.gallery.views.Histogram;
import com.particlesdevs.photoncamera.util.Utilities;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The View Model class which updates the {@link ExifDialogModel}
 */
public class ExifDialogViewModel extends AndroidViewModel {
    private static final String TAG = ExifDialogViewModel.class.getSimpleName();
    private final ExifDialogModel exifDialogModel;
    /**
     * Guards histogram loads against swipe races: each request bumps the
     * generation and only the latest generation may publish its model, so a
     * slow load for a previous image can never overwrite the current one.
     */
    private int histogramGeneration;
    /** Single histogram computer for this ViewModel; owns its GL context. */
    private Histogram histogram;

    public ExifDialogViewModel(Application application) {
        super(application);
        this.exifDialogModel = new ExifDialogModel();
    }

    public ExifDialogModel getExifDataModel() {
        return exifDialogModel;
    }

    /**
     * Updates the ExifDialogModel using exif attributes stored in the Image File
     *
     * @param imageFile the image imageFile whose exif data is to be read
     */
    public void updateModel(ContentResolver contentResolver, MediaFile imageFile) {
        ExifInterface exifInterface;
        InputStream inputStream;
        try {
            inputStream = contentResolver.openInputStream(imageFile.getFileUri());
            exifInterface = new ExifInterface(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String attr_make = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
        String attr_model = exifInterface.getAttribute(ExifInterface.TAG_MODEL);
        String attr_exp = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
        String attr_width = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
        String attr_length = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);        String attr_iso = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
        String attr_fnum = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER);
        String attr_focal = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
        String attr_date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
        // getAttribute() sanitizes ASCII control characters (newlines -> '?'),
        // so decode the raw bytes to keep the parameter dump's line breaks.
        String disp_description = ExifDescriptionDecoder.decode(
                exifInterface.getAttributeBytes(ExifInterface.TAG_IMAGE_DESCRIPTION));
//        String attr_35mmfocal = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
//        Log.d("attr_35mmfocal", "fetched attr_35mmfocal = " + attr_35mmfocal);

        // Fallback for files without EXIF dimensions (HEIC with stripped
        // EXIF, foreign files): decode bounds only, no pixel allocation.
        if (attr_width == null || attr_length == null) {
            try (InputStream boundsStream = contentResolver.openInputStream(imageFile.getFileUri())) {
                if (boundsStream != null) {
                    android.graphics.BitmapFactory.Options opts =
                            new android.graphics.BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeStream(boundsStream, null, opts);
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        attr_width = String.valueOf(opts.outWidth);
                        attr_length = String.valueOf(opts.outHeight);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String exposure = (Utilities.formatExposureTime(Double.parseDouble(attr_exp == null ? "NaN" : attr_exp)));
        String resolution_mp = (String.format(Locale.US, "%.1f",
                Double.parseDouble((attr_width == null ? "NaN" : attr_width))
                        * Double.parseDouble((attr_length == null ? "NaN" : attr_length)) / 1E6) + " MP");
        String disp_exp = exposure + "s";
        String disp_fnum = "\u0192/" + attr_fnum;
        String disp_focal = Rational.parseRational(attr_focal == null ? "NaN" : attr_focal).doubleValue() + "mm";
        String disp_iso = "ISO" + attr_iso;

        exifDialogModel.setTitle(imageFile.getAbsolutePath());
        exifDialogModel.setRes(attr_length + "x" + attr_width);
        exifDialogModel.setDevice(attr_make + " " + attr_model);
        exifDialogModel.setDate(getDateText(attr_date));
        exifDialogModel.setExposure(disp_exp);
        exifDialogModel.setIso(disp_iso);
        exifDialogModel.setFnum(disp_fnum);
        exifDialogModel.setFocal(disp_focal);
        exifDialogModel.setFile_size((FileUtils.byteCountToDisplaySize((int) imageFile.getSize())));
        exifDialogModel.setRes_mp(resolution_mp);
        exifDialogModel.setDescription(disp_description);
        exifDialogModel.setMiniText(
                imageFile.getDisplayName() + "\n" +
                        disp_exp + " | " +
                        disp_iso + " | " +
                        disp_fnum + " | " +
                        disp_focal + " | " +
                        resolution_mp);
        exifDialogModel.notifyChange(); //important
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the {@link Histogram.HistogramModel} view which is associated with ExifDialogModel
     * check for more detail {@link com.particlesdevs.photoncamera.gallery.binding.CustomBinding#updateHistogram(Histogram, Histogram.HistogramModel)}
     */
    public void updateHistogramView(ImageFile imageFile) {
        // Clear the bars immediately so stale data is never shown while the new
        // analysis runs, and bump the generation so only the latest request may
        // publish its model.
        exifDialogModel.setHistogramModel(null);
        final int generation = ++histogramGeneration;
        histogram().analyzeAsync(getApplication().getContentResolver(), imageFile.getFileUri(), model -> {
            if (generation != histogramGeneration) {
                return;
            }
            exifDialogModel.setHistogramModel(model);
        });
    }

    /**
     * The histogram computer is reused for the ViewModel's lifetime so its GL
     * context is created (and later destroyed) exactly once, instead of once
     * per image.
     */
    private Histogram histogram() {
        if (histogram == null) {
            histogram = new Histogram(getApplication(), null);
        }
        return histogram;
    }

    private String getDateText(String savedDate) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat displayedDateFormat = new SimpleDateFormat("EEEE, dd MMM, yyyy \u2022 HH:mm:ss");
        Date photoDate;
        try {
            photoDate = ParseExif.sFormatter.parse(savedDate); //parsing with the same formatter with which it was saved
        } catch (Exception ignored) {
            return "";
        }
        return displayedDateFormat.format(photoDate == null ? new Date() : photoDate);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        histogramGeneration++;
        if (histogram != null) {
            histogram.close();
            histogram = null;
        }
    }
}
