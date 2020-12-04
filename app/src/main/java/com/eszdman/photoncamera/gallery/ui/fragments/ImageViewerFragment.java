package com.eszdman.photoncamera.gallery.ui.fragments;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import com.eszdman.photoncamera.databinding.FragmentGalleryImageViewerBinding;
import com.eszdman.photoncamera.gallery.adapters.DepthPageTransformer;
import com.eszdman.photoncamera.gallery.adapters.ImageAdapter;
import com.eszdman.photoncamera.gallery.viewmodel.ExifDialogViewModel;
import com.eszdman.photoncamera.gallery.views.Histogram;
import org.apache.commons.io.FileUtils;

import java.io.File;

public class ImageViewerFragment extends Fragment {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private final String path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
    private final File[] allFiles = new File(path).listFiles((dir, name) -> name.toUpperCase().endsWith("JPG"));
    private ExifDialogViewModel exifDialogViewModel;
    private ViewPager viewPager;
    private ImageAdapter adapter;
    private Histogram histogram;
    private NavController navController;
    private FragmentGalleryImageViewerBinding fragmentGalleryImageViewerBinding;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentGalleryImageViewerBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_viewer, container, false);
        initialiseDataMembers();
        setClickListeners();
        exifDialogViewModel = new ViewModelProvider(this).get(ExifDialogViewModel.class);
        fragmentGalleryImageViewerBinding.exifLayout.setExifmodel(exifDialogViewModel.getExifDataModel());
        navController = NavHostFragment.findNavController(this);
        return fragmentGalleryImageViewerBinding.getRoot();
    }

    private void initialiseDataMembers() {
        viewPager = fragmentGalleryImageViewerBinding.viewPager;
        histogram = new Histogram(getContext());
        adapter = new ImageAdapter(allFiles);
    }

    private void setClickListeners() {
        fragmentGalleryImageViewerBinding.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.setOnDelete(this::onDeleteButtonClick);
        fragmentGalleryImageViewerBinding.setOnExif(this::onExifButtonClick);
        fragmentGalleryImageViewerBinding.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.setOnGallery(this::onGalleryButtonClick);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager.setAdapter(adapter);
        viewPager.setPageTransformer(true, new DepthPageTransformer());
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updateExif();
            }
        });
        Bundle bundle = getArguments();
        if (bundle != null)
            viewPager.setCurrentItem(bundle.getInt("imagePosition", 0));
    }


    private void onGalleryButtonClick(View view) {
        if (navController.getPreviousBackStackEntry() == null)
            navController.navigate(R.id.action_imageViewFragment_to_imageLibraryFragment);
        else
            navController.navigateUp();
    }


    private void onDeleteButtonClick(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(R.string.sure_delete)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())

                .setPositiveButton(R.string.yes, (dialog, which) -> {

                    int position = viewPager.getCurrentItem();
                    File newFile = new File(String.valueOf(allFiles[position]));
                    String fileName = newFile.getName();
                    File thisFile = new File(path + "/" + fileName);
                    thisFile.delete();

                    MediaScannerConnection.scanFile(getContext(), new String[]{String.valueOf(thisFile)}, null, null);
                    adapter.notifyDataSetChanged();
                    viewPager.setAdapter(adapter);
                    //auto scroll to the next photo
                    viewPager.setCurrentItem(position);
                    Toast.makeText(getContext(), R.string.image_deleted, Toast.LENGTH_SHORT)
                            .show();
                });
        builder.create()
                .show();
    }

    private void onShareButtonClick(View view) {
        int position = viewPager.getCurrentItem();
        File newFile = new File(String.valueOf(allFiles[position]));
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
        fragmentGalleryImageViewerBinding.setExifDialogVisible(!fragmentGalleryImageViewerBinding.getExifDialogVisible());
        updateExif();
    }

    private void updateExif() {
        if (fragmentGalleryImageViewerBinding.getExifDialogVisible()) {
            int position = viewPager.getCurrentItem();
            File currentFile = allFiles[position];
            //update values for exif dialog
            exifDialogViewModel.updateModel(currentFile, histogram);
        }
    }
}
