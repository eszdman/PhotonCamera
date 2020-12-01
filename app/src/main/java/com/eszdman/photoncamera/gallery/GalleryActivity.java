package com.eszdman.photoncamera.gallery;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.ActivityGalleryBinding;
import com.eszdman.photoncamera.gallery.viewmodel.ExifDialogViewModel;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity implements View.OnClickListener {

    public static final List<String> EXTENSION_WHITELIST = Collections.singletonList("JPG");

    private final String path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
    private final File f = new File(path);
    private final File[] file = f.listFiles(file -> EXTENSION_WHITELIST.contains(getFileExt(file).toUpperCase(Locale.ROOT)));
    public static GalleryActivity activity;
    private ExifDialogViewModel exifDialogViewModel;
    private ViewPager viewPager;
    private ImageAdapter adapter;
    private ConstraintLayout exifLayout;
    private Histogram histogram;

    public static String getFileExt(File fileName) {
        return fileName.getAbsolutePath().substring(fileName.getAbsolutePath().lastIndexOf(".") + 1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PreferenceKeys.setActivityTheme(GalleryActivity.this);
        super.onCreate(savedInstanceState);
        activity = this;
        ActivityGalleryBinding activityGalleryBinding = DataBindingUtil.setContentView(this, R.layout.activity_gallery);
        exifDialogViewModel = new ViewModelProvider(this).get(ExifDialogViewModel.class);
        activityGalleryBinding.exifLayout.setExifmodel(exifDialogViewModel.getExifDataModel());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        initialiseDataMembers();
        viewPager.setAdapter(adapter);
        viewPager.setPageTransformer(true, new DepthPageTransformer());

        Button delete = findViewById(R.id.delete);
        delete.setOnClickListener(this);

        Button share = findViewById(R.id.share);
        share.setOnClickListener(this);

        Button exif = findViewById(R.id.exif);
        exif.setOnClickListener(this);

        Log.d("GalleryActivity", "Offset:" + histogram.offset);
    }

    void initialiseDataMembers() {
        viewPager = findViewById(R.id.view_pager);
        adapter = new ImageAdapter(this, file);
        exifLayout = findViewById(R.id.exif_layout);
        histogram = new Histogram(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                builder.setMessage(R.string.sure_delete)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())

                        .setPositiveButton(R.string.yes, (dialog, which) -> {

                            int position = viewPager.getCurrentItem();
                            File newFile = new File(String.valueOf(file[position]));
                            String fileName = newFile.getName();
                            File thisFile = new File(path + "/" + fileName);
                            thisFile.delete();

                            MediaScannerConnection.scanFile(activity, new String[]{String.valueOf(thisFile)}, null, null);

                            //auto scroll to the next photo
                            viewPager.setCurrentItem(position + 1, true);

                            Toast.makeText(activity, R.string.image_deleted, Toast.LENGTH_SHORT)
                                    .show();

                            final Handler handler = new Handler();
                            handler.postDelayed(() -> viewPager.setAdapter(adapter), 100);
                        });
                builder.create()
                        .show();
                break;

            case R.id.share:
                int position = viewPager.getCurrentItem();
                File newFile = new File(String.valueOf(file[position]));
                String fileName = newFile.getName();
                String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(fileName));
                Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", new File(path + "/" + fileName));
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setType(mediaType);
                startActivity(intent);
                break;

            case R.id.exif:
                if (exifLayout.getVisibility() == View.VISIBLE) {
                    exifLayout.setVisibility(View.INVISIBLE);
                    return;
                }
                updateExif();
                exifLayout.setVisibility(View.VISIBLE);
                break;
        }
    }


    public void updateExif() {
        int position = viewPager.getCurrentItem();
        File currentFile = file[position];
        //update values for exif dialog
        exifDialogViewModel.updateModel(currentFile, histogram);
    }
}
