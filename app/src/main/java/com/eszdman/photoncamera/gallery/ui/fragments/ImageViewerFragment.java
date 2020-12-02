package com.eszdman.photoncamera.gallery.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager.widget.ViewPager;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.GalleryFragmentImageViewerBinding;
import com.eszdman.photoncamera.gallery.DepthPageTransformer;
import com.eszdman.photoncamera.gallery.Histogram;
import com.eszdman.photoncamera.gallery.ImageAdapter;
import com.eszdman.photoncamera.gallery.viewmodel.ExifDialogViewModel;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ImageViewerFragment extends Fragment {
    public static final List<String> EXTENSION_WHITELIST = Collections.singletonList("JPG");
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private final String path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
    private final File f = new File(path);
    private final File[] file = f.listFiles(file -> EXTENSION_WHITELIST.contains(getFileExt(file).toUpperCase(Locale.ROOT)));
    private ExifDialogViewModel exifDialogViewModel;
    private ViewPager viewPager;
    private ImageAdapter adapter;
    private ConstraintLayout exifLayout;
    private Histogram histogram;
    private NavController navController;

    private static String getFileExt(File fileName) {
        return fileName.getAbsolutePath().substring(fileName.getAbsolutePath().lastIndexOf(".") + 1);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        GalleryFragmentImageViewerBinding galleryFragmentImageViewBinding = DataBindingUtil.inflate(inflater, R.layout.gallery_fragment_image_viewer, container, false);
        exifDialogViewModel = new ViewModelProvider(this).get(ExifDialogViewModel.class);
        galleryFragmentImageViewBinding.exifLayout.setExifmodel(exifDialogViewModel.getExifDataModel());
        navController = NavHostFragment.findNavController(this);
        return galleryFragmentImageViewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initialiseDataMembers();
        setClickListeners();
        viewPager.setAdapter(adapter);
        viewPager.setPageTransformer(true, new DepthPageTransformer());
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (exifLayout.getVisibility() == View.VISIBLE) {
                    updateExif();
                }
            }
        });
    }

    private void setClickListeners() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.findViewById(R.id.share).setOnClickListener(this::onShareButtonClick);
            activity.findViewById(R.id.delete).setOnClickListener(this::onDeleteButtonClick);
            activity.findViewById(R.id.exif).setOnClickListener(this::onExifButtonClick);
            activity.findViewById(R.id.gallery_grid_button).setOnClickListener(this::onGalleryButtonClick);
        }
    }

    private void onGalleryButtonClick(View view) {
        navController.navigate(R.id.imageLibraryFragment);
    }

    private void initialiseDataMembers() {
        if (getActivity() != null) {
            viewPager = getActivity().findViewById(R.id.view_pager);
            adapter = new ImageAdapter(getContext(), file);
            exifLayout = getActivity().findViewById(R.id.exif_layout);
            histogram = new Histogram(getContext());
        }
    }

    private void onDeleteButtonClick(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(R.string.sure_delete)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())

                .setPositiveButton(R.string.yes, (dialog, which) -> {

                    int position = viewPager.getCurrentItem();
                    File newFile = new File(String.valueOf(file[position]));
                    String fileName = newFile.getName();
                    File thisFile = new File(path + "/" + fileName);
                    thisFile.delete();

                    MediaScannerConnection.scanFile(getContext(), new String[]{String.valueOf(thisFile)}, null, null);

                    //auto scroll to the next photo
                    viewPager.setCurrentItem(position + 1, true);

                    Toast.makeText(getContext(), R.string.image_deleted, Toast.LENGTH_SHORT)
                            .show();

                    final Handler handler = new Handler();
                    handler.postDelayed(() -> viewPager.setAdapter(adapter), 100);
                });
        builder.create()
                .show();
    }

    private void onShareButtonClick(View view) {
        int position = viewPager.getCurrentItem();
        File newFile = new File(String.valueOf(file[position]));
        String fileName = newFile.getName();
        String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(fileName));
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", new File(path + "/" + fileName));
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(mediaType);
        startActivity(intent);
    }

    private void onExifButtonClick(View view) {
        if (exifLayout.getVisibility() == View.VISIBLE) {
            exifLayout.setVisibility(View.INVISIBLE);
            return;
        }
        updateExif();
        exifLayout.setVisibility(View.VISIBLE);
    }

    private void updateExif() {
        int position = viewPager.getCurrentItem();
        File currentFile = file[position];
        //update values for exif dialog
        exifDialogViewModel.updateModel(currentFile, histogram);
    }
}
