package com.eszdman.photoncamera.gallery.viewmodel;


import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Rational;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.AndroidViewModel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.eszdman.photoncamera.api.ParseExif;
import com.eszdman.photoncamera.gallery.model.ExifDialogModel;
import com.eszdman.photoncamera.gallery.views.Histogram;
import com.eszdman.photoncamera.util.Utilities;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The View Model class which updates the {@link ExifDialogModel}
 */
public class ExifDialogViewModel extends AndroidViewModel {
    private static final String TAG = ExifDialogViewModel.class.getSimpleName();
    private final ExifDialogModel exifDialogModel;

    public ExifDialogViewModel(Application application) {
        super(application);
        this.exifDialogModel = new ExifDialogModel();
    }

    public ExifDialogModel getExifDataModel() {
        return exifDialogModel;
    }
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Updates the ExifDialogModel using exif attributes stored in the Image File
     *
     * @param imageFile the image imageFile whose exif data is to be read
     */
    public void updateModel(File imageFile) {
        ExifInterface exifInterface;
        try {
            exifInterface = new ExifInterface(imageFile);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String attr_make = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
        String attr_model = exifInterface.getAttribute(ExifInterface.TAG_MODEL);
        String attr_exp = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
        String attr_width = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
        String attr_length = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
        String attr_iso = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
        String attr_fnum = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER);
        String attr_focal = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
        String attr_date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
//        String attr_35mmfocal = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
//        Log.d("attr_35mmfocal", "fetched attr_35mmfocal = " + attr_35mmfocal);

        String exposure = (Utilities.formatExposureTime(Double.parseDouble(attr_exp == null ? "NaN" : attr_exp)));
        String resolution_mp = (String.format(Locale.US, "%.1f",
                Double.parseDouble((attr_width == null ? "NaN" : attr_width))
                        * Double.parseDouble((attr_length == null ? "NaN" : attr_length)) / 1E6) + " MP");

        exifDialogModel.setTitle(imageFile.getAbsolutePath());
        exifDialogModel.setRes(attr_length + "x" + attr_width);
        exifDialogModel.setDevice(attr_make + " " + attr_model);
        exifDialogModel.setDate(getDateText(attr_date));
        exifDialogModel.setExposure(exposure + "s");
        exifDialogModel.setIso("ISO" + attr_iso);
        exifDialogModel.setFnum("\u0192/" + attr_fnum);
        exifDialogModel.setFocal(Rational.parseRational(attr_focal == null ? "NaN" : attr_focal).doubleValue() + "mm");
        exifDialogModel.setFile_size((FileUtils.byteCountToDisplaySize((int) imageFile.length())));
        exifDialogModel.setRes_mp(resolution_mp);
        updateHistogramView(imageFile);
        exifDialogModel.notifyChange(); //important
    }

    /**
     * Updates the {@link Histogram.HistogramModel} view which is associated with ExifDialogModel
     * check for more detail {@link com.eszdman.photoncamera.gallery.binding.CustomBinding#updateHistogram(Histogram, Histogram.HistogramModel)}
     */
    private void updateHistogramView(File imageFile) {
        Handler handler = new Handler(Looper.getMainLooper(), msg -> {
            exifDialogModel.setHistogramModel((Histogram.HistogramModel) msg.obj); //setting histogram view to model
            return true;
        });
        executorService.execute(() -> {
            try {
                Bitmap preview = Glide.with(getApplication())
                        .asBitmap()
                        .load(imageFile)
                        .apply(new RequestOptions()
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .signature(new ObjectKey("hist" + imageFile.getName() + imageFile.lastModified()))
                                .override(800) //800*800
                                .fitCenter()
                        )
                        .submit()
                        .get();
                Message msg = new Message();
                msg.obj = Histogram.analyze(preview);
                handler.sendMessage(msg);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private String getDateText(String savedDate) {
        SimpleDateFormat displayedDateFormat = new SimpleDateFormat("EEEE, dd MMM, yyyy \u2022 HH:mm:ss", Locale.US);
        Date photoDate;
        try {
            photoDate = ParseExif.sFormatter.parse(savedDate); //parsing with the same formatter with which it was saved
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return displayedDateFormat.format(photoDate == null ? new Date() : photoDate);
    }
}
