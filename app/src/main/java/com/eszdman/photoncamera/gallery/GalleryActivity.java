package com.eszdman.photoncamera.gallery;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.viewpager.widget.ViewPager;

import com.eszdman.photoncamera.R;

import com.eszdman.photoncamera.util.Utilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity {

    public static final List<String> EXTENSION_WHITELIST = Collections.singletonList("JPG");

    private final String path = Environment.getExternalStorageDirectory().toString()+"/DCIM/Camera";
    private final File f = new File(path);
    private final File[] file = f.listFiles(file -> EXTENSION_WHITELIST.contains(getFileExt(file).toUpperCase(Locale.ROOT)));
    public static GalleryActivity activity;
    public boolean startUpdate = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = this;
        setContentView(R.layout.activity_gallery);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final ViewPager viewPager = findViewById(R.id.view_pager);
        ImageAdapter adapter = new ImageAdapter(this, file);
        viewPager.setAdapter(adapter);
        viewPager.setPageTransformer(true, new DepthPageTransformer());
        //delete image
        Button delete = findViewById(R.id.delete);
        delete.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);

            builder.setMessage(R.string.sure_delete)
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())

                    .setPositiveButton(R.string.yes, (dialog, which) -> {

                        int position = viewPager.getCurrentItem();
                        File newFile = new File(String.valueOf(file[position]));
                        String fileName = newFile.getName();
                        File thisfile = new File (path + "/" + fileName);
                        thisfile.delete();

                        MediaScannerConnection.scanFile(GalleryActivity.this, new String[]{String.valueOf(thisfile)}, null, null);

                        //auto scroll to the next photo
                        viewPager.setCurrentItem(position + 1, true);

                        Toast.makeText(GalleryActivity.this, R.string.image_deleted, Toast.LENGTH_SHORT)
                                .show();

                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                viewPager.setAdapter(adapter);
                            }
                        }, 100);
                    });
            builder.create()
                    .show();

        });

        //share button
        Button share = findViewById(R.id.share);
        share.setOnClickListener(view -> {
            int position = viewPager.getCurrentItem();
            File newFile = new File(String.valueOf(file[position]));
            String fileName = newFile.getName();

            String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FilenameUtils.getExtension(fileName));

            Uri uri = FileProvider.getUriForFile(GalleryActivity.this, GalleryActivity.this.getPackageName() + ".provider", new File(path + "/" + fileName));

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType(mediaType);
            startActivity(intent);
        });
        ConstraintLayout exifLayout = findViewById(R.id.exif_layout);
        Button exif = findViewById(R.id.exif);
        Histogram histogram = new Histogram(this);
        LinearLayout histogramview = findViewById(R.id.exif_histogram);
        Log.d("GalleryActivity","Offset:"+histogram.offset);
        histogramview.addView(histogram);
        //exifLayout.setAlpha(0.5f);
        exif.setOnClickListener(view -> {
            if(exifLayout.getVisibility() == View.VISIBLE) {exifLayout.setVisibility(View.INVISIBLE); return;}
            int position = viewPager.getCurrentItem();

            File currentFile = file[position];
            String fileName = currentFile.getName();
            Uri uri = FileProvider.getUriForFile(GalleryActivity.this, GalleryActivity.this.getPackageName() + ".provider", new File(path + "/" + fileName));

            try (InputStream inputStream = GalleryActivity.this.getContentResolver().openInputStream(uri)) {
                assert inputStream != null;
                ExifInterface exif1 = new ExifInterface(inputStream);

                String width = exif1.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
                String length = exif1.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
                String make = exif1.getAttribute(ExifInterface.TAG_MAKE);
                String model = exif1.getAttribute(ExifInterface.TAG_MODEL);
                String date = exif1.getAttribute(ExifInterface.TAG_DATETIME);
                String exposure = exif1.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
                String iso = exif1.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
                String fnum = exif1.getAttribute(ExifInterface.TAG_F_NUMBER);
                String focal = exif1.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);



                TextView title = findViewById(R.id.value_filename);
                TextView res = findViewById(R.id.value_res);
                TextView device = findViewById(R.id.value_device);
                TextView datetime = findViewById(R.id.value_date);
                TextView exp = findViewById(R.id.value_exposure);
                TextView isospeed = findViewById(R.id.value_iso);
                TextView fnumber = findViewById(R.id.value_fnumber);
                TextView fileSize = findViewById(R.id.value_filesize);
                TextView focallength = findViewById(R.id.value_flength);

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFile(currentFile.getAbsolutePath(), options);
                histogram.Analyze(bitmap);

                title.setText(fileName.toUpperCase(Locale.ROOT));
                res.setText(width + "x" + length);
                device.setText(make + " " + model);
                datetime.setText(date);
                if(exposure == null) exposure = "0";
                String exposureTime = Utilities.formatExposureTime(Double.parseDouble(exposure));
                exp.setText(exposureTime);
                isospeed.setText("ISO"+iso);
                fnumber.setText("f/" + fnum);
                fileSize.setText(FileUtils.byteCountToDisplaySize(currentFile.length()));
                if(focal != null) {
                //Removed uwu code
                int numerator = Integer.parseInt(focal.substring(0, focal.indexOf("/")));
                int denumerator = Integer.parseInt(focal.substring(focal.indexOf("/")+1));
                focallength.setText(((double)(numerator)/denumerator) + "mm");
                } else {
                    focallength.setText("");
                }
                exifLayout.setVisibility(View.VISIBLE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }
    public static String getFileExt(File fileName) {
        return fileName.getAbsolutePath().substring(fileName.getAbsolutePath().lastIndexOf(".") + 1);
    }
}
