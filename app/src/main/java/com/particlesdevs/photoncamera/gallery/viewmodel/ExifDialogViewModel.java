package com.particlesdevs.photoncamera.gallery.viewmodel;


import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentResolver;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.AndroidViewModel;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.files.MediaFile;
import com.particlesdevs.photoncamera.gallery.helper.ExifDescriptionDecoder;
import com.particlesdevs.photoncamera.gallery.helper.GalleryExecutors;
import com.particlesdevs.photoncamera.gallery.model.ExifDialogModel;
import com.particlesdevs.photoncamera.gallery.views.Histogram;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.util.Utilities;

import org.apache.commons.io.FileUtils;

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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /**
     * Guards model updates against swipe races: each request bumps the
     * generation and only the latest generation may publish its data, so a
     * slow parse for a previous image can never overwrite the current one.
     */
    private int exifGeneration;
    /** Guards histogram loads against swipe races, same as above. */
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
     * Parses the file's EXIF on the shared gallery IO thread and applies it to
     * the model on the main thread. Never touches the disk on the UI thread.
     *
     * @param knownDimensions dimensions already decoded for this file (e.g. the
     *                        viewer's preview cache), {@code null} to decode
     *                        bounds when the file has no EXIF dimensions
     * @param onApplied       optional main-thread callback once the model holds
     *                        this file's data (skipped when the request was
     *                        superseded or the parse failed)
     */
    public void updateModel(ContentResolver contentResolver, MediaFile imageFile,
                            @Nullable Point knownDimensions, @Nullable Runnable onApplied) {
        final int generation = ++exifGeneration;
        GalleryExecutors.io().execute(() -> {
            final ExifData data = parseExif(contentResolver, imageFile, knownDimensions);
            if (data == null) return;
            mainHandler.post(() -> {
                if (generation != exifGeneration) return;
                applyExifData(imageFile, data);
                if (onApplied != null) onApplied.run();
            });
        });
    }

    /** Runs on the {@link GalleryExecutors#io()} thread. */
    @Nullable
    private ExifData parseExif(ContentResolver contentResolver, MediaFile imageFile,
                               @Nullable Point knownDimensions) {
        try (InputStream inputStream = contentResolver.openInputStream(imageFile.getFileUri())) {
            if (inputStream == null) return null;
            ExifInterface exifInterface = new ExifInterface(inputStream);
            ExifData data = new ExifData();
            data.make = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
            data.model = exifInterface.getAttribute(ExifInterface.TAG_MODEL);
            data.exposure = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
            data.width = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
            data.length = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
            data.iso = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
            data.fnum = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER);
            data.focal = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
            data.date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
            // getAttribute() sanitizes ASCII control characters (newlines -> '?'),
            // so decode the raw bytes to keep the parameter dump's line breaks.
            data.description = ExifDescriptionDecoder.decode(
                    exifInterface.getAttributeBytes(ExifInterface.TAG_IMAGE_DESCRIPTION));

            // Fallback for files without EXIF dimensions (HEIC with stripped
            // EXIF, foreign files): use dimensions the viewer already decoded,
            // otherwise decode bounds only, no pixel allocation.
            if (data.width == null || data.length == null) {
                if (knownDimensions != null && knownDimensions.x > 0 && knownDimensions.y > 0) {
                    data.width = String.valueOf(knownDimensions.x);
                    data.length = String.valueOf(knownDimensions.y);
                } else {
                    try (InputStream boundsStream = contentResolver.openInputStream(imageFile.getFileUri())) {
                        if (boundsStream != null) {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            BitmapFactory.decodeStream(boundsStream, null, opts);
                            if (opts.outWidth > 0 && opts.outHeight > 0) {
                                data.width = String.valueOf(opts.outWidth);
                                data.length = String.valueOf(opts.outHeight);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return data;
        } catch (Exception e) {
            Log.d(TAG, "EXIF parse failed " + Log.getStackTraceString(e));
            return null;
        }
    }

    /** Runs on the main thread. */
    private void applyExifData(MediaFile imageFile, ExifData data) {
        String exposure = (Utilities.formatExposureTime(Double.parseDouble(data.exposure == null ? "NaN" : data.exposure)));
        String resolution_mp = (String.format(Locale.US, "%.1f",
                Double.parseDouble((data.width == null ? "NaN" : data.width))
                        * Double.parseDouble((data.length == null ? "NaN" : data.length)) / 1E6) + " MP");
        String disp_exp = exposure + "s";
        String disp_fnum = "\u0192/" + data.fnum;
        String disp_focal = Rational.parseRational(data.focal == null ? "NaN" : data.focal).doubleValue() + "mm";
        String disp_iso = "ISO" + data.iso;

        exifDialogModel.setTitle(imageFile.getAbsolutePath());
        exifDialogModel.setRes(data.length + "x" + data.width);
        exifDialogModel.setDevice(data.make + " " + data.model);
        exifDialogModel.setDate(getDateText(data.date));
        exifDialogModel.setExposure(disp_exp);
        exifDialogModel.setIso(disp_iso);
        exifDialogModel.setFnum(disp_fnum);
        exifDialogModel.setFocal(disp_focal);
        exifDialogModel.setFile_size((FileUtils.byteCountToDisplaySize((int) imageFile.getSize())));
        exifDialogModel.setRes_mp(resolution_mp);
        exifDialogModel.setDescription(data.description);
        exifDialogModel.setMiniText(
                imageFile.getDisplayName() + "\n" +
                        disp_exp + " | " +
                        disp_iso + " | " +
                        disp_fnum + " | " +
                        disp_focal + " | " +
                        resolution_mp);
        exifDialogModel.notifyChange(); //important
    }

    /**
     * EXIF panel content for videos (no EXIF tags to read): file identity,
     * size and duration instead of exposure metadata. The retriever runs on
     * the gallery IO thread; the model is filled on the main thread.
     *
     * @param onApplied optional main-thread callback once the model holds this
     *                  video's data (skipped when the request was superseded)
     */
    public void updateVideoModel(MediaFile videoFile, @Nullable Runnable onApplied) {
        final int generation = ++exifGeneration;
        GalleryExecutors.io().execute(() -> {
            String duration = "";
            String res = "";
            String resMp = "";
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(getApplication(), videoFile.getFileUri());
                    String ms = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (ms != null) {
                        long totalSeconds = Long.parseLong(ms) / 1000;
                        duration = String.format(Locale.US, "%02d:%02d",
                                totalSeconds / 60, totalSeconds % 60);
                    }
                    String w = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    String h = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    if (w != null && h != null) {
                        res = h + "x" + w;
                        try {
                            double mp = Double.parseDouble(w) * Double.parseDouble(h) / 1E6;
                            resMp = String.format(Locale.US, "%.1f MP", mp);
                        } catch (Exception ignored) {
                        }
                    }
                } finally {
                    try {
                        retriever.release();
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Video metadata read failed " + Log.getStackTraceString(e));
            }
            final String durationText = duration;
            final String resText = res;
            final String resMpText = resMp;
            mainHandler.post(() -> {
                if (generation != exifGeneration) return;
                exifDialogModel.setTitle(videoFile.getAbsolutePath());
                exifDialogModel.setDevice("");
                exifDialogModel.setDate("");
                exifDialogModel.setExposure(durationText);
                exifDialogModel.setIso("");
                exifDialogModel.setFnum("");
                exifDialogModel.setFocal("");
                exifDialogModel.setRes(resText);
                exifDialogModel.setRes_mp(resMpText);
                try {
                    exifDialogModel.setFile_size(FileUtils.byteCountToDisplaySize((int) videoFile.getSize()));
                } catch (Exception ignored) {
                    exifDialogModel.setFile_size("");
                }
                exifDialogModel.setDescription("");
                exifDialogModel.setMiniText(videoFile.getDisplayName() + "\nVideo"
                        + (durationText.isEmpty() ? "" : " | " + durationText));
                exifDialogModel.notifyChange();
                if (onApplied != null) onApplied.run();
            });
        });
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
        exifGeneration++;
        histogramGeneration++;
        if (histogram != null) {
            histogram.close();
            histogram = null;
        }
    }

    /** Raw attribute bag parsed off the main thread, applied on the main thread. */
    private static final class ExifData {
        String make;
        String model;
        String exposure;
        String width;
        String length;
        String iso;
        String fnum;
        String focal;
        String date;
        String description;
    }
}
