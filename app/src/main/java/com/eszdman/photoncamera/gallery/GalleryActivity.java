package com.eszdman.photoncamera.gallery;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.viewpager.widget.ViewPager;

import com.eszdman.photoncamera.R;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity {

    public static final List<String> EXTENSION_WHITELIST = Collections.singletonList("JPG");

    private final String path = Environment.getExternalStorageDirectory().toString()+"/DCIM/Camera";
    private final File f = new File(path);
    private final File[] file = f.listFiles(new FileFilter() {
        @Override
        public boolean accept(File file) {
            return EXTENSION_WHITELIST.contains(getFileExt(file).toUpperCase(Locale.ROOT));
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        final ViewPager viewPager = findViewById(R.id.view_pager);
        ImageAdapter adapter = new ImageAdapter(this, file);
        viewPager.setAdapter(adapter);
        viewPager.setPageTransformer(true, new DepthPageTransformer());

        //delete image
        Button delete = findViewById(R.id.delete);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);

                builder.setMessage("Are you sure to delete this image?")
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })

                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                int position = viewPager.getCurrentItem();
                                File newFile = new File(String.valueOf(file[position]));
                                String fileName = newFile.getName();
                                File thisfile = new File (path + "/" + fileName);
                                thisfile.delete();

                                MediaScannerConnection.scanFile(GalleryActivity.this, new String[]{String.valueOf(thisfile)}, null, null);

                                //auto scroll to the next photo
                                viewPager.setCurrentItem(position + 1, true);

                                Toast.makeText(GalleryActivity.this, "Image Deleted", Toast.LENGTH_SHORT)
                                        .show();

                                final Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        viewPager.setAdapter(adapter);
                                    }
                                }, 100);
                            }
                        });
                builder.create()
                        .show();

            }
        });

        //share button
        Button share = findViewById(R.id.share);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
            }
        });


        //gallery preview
        /* ImageButton preview = findViewById(R.id.imageButton);
        viewPager.setCurrentItem(0);
        int firstpicture = viewPager.getCurrentItem();
        File firstFile = new File(String.valueOf(file[firstpicture]));
        Bitmap firstfileBitmap = BitmapFactory.decodeFile(firstFile.getAbsolutePath());
        preview.setImageBitmap(firstfileBitmap);
        */

        Button exif = findViewById(R.id.exif);
        exif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int position = viewPager.getCurrentItem();
                File newFile = new File(String.valueOf(file[position]));
                String fileName = newFile.getName();
                Uri uri = FileProvider.getUriForFile(GalleryActivity.this, GalleryActivity.this.getPackageName() + ".provider", new File(path + "/" + fileName));

                try (InputStream inputStream = GalleryActivity.this.getContentResolver().openInputStream(uri)) {
                    assert inputStream != null;
                    ExifInterface exif = new ExifInterface(inputStream);

                    String width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
                    String length = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
                    String make = exif.getAttribute(ExifInterface.TAG_MAKE);
                    String model = exif.getAttribute(ExifInterface.TAG_MODEL);
                    String date = exif.getAttribute(ExifInterface.TAG_DATETIME);
                    String exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
                    String iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
                    String fnum = exif.getAttribute(ExifInterface.TAG_F_NUMBER);

                    final Dialog dialog = new Dialog(GalleryActivity.this);
                    dialog.setContentView(R.layout.exif_dialog);
                    dialog.setTitle("EXIF");

                    TextView res = dialog.findViewById(R.id.value_res);
                    res.setText(width + "x" + length);

                    TextView device = dialog.findViewById(R.id.value_device);
                    device.setText(make + " " + model);

                    TextView datetime = dialog.findViewById(R.id.value_date);
                    datetime.setText(date);

                    TextView exp = dialog.findViewById(R.id.value_exposure);
                    String exposureTime = formatExposureTime(Double.valueOf(exposure));
                    exp.setText(exposureTime);

                    TextView isospeed = dialog.findViewById(R.id.value_iso);
                    isospeed.setText(iso);

                    TextView fnumber = dialog.findViewById(R.id.value_fnumber);
                    fnumber.setText(fnum);

                    Button dialogButton = dialog.findViewById(R.id.close);
                    // if button is clicked, close the custom dialog
                    dialogButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                        }
                    });
                    dialog.show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public static String getFileExt(File fileName) {
        return fileName.getAbsolutePath().substring(fileName.getAbsolutePath().lastIndexOf(".") + 1);
    }

    public static String formatExposureTime(final double value)
    {
        String output;

        if (value < 1.0f)
        {
            output = String.format(Locale.getDefault(), "%d/%d", 1, (int)(0.5f + 1 / value));
        }
        else
        {
            final int    integer = (int)value;
            final double time    = value - integer;
            output = String.format(Locale.getDefault(), "%d''", integer);

            if (time > 0.0001f)
            {
                output += String.format(Locale.getDefault(), " %d/%d", 1, (int)(0.5f + 1 / time));
            }
        }

        return output;
    }
}
