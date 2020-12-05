package com.eszdman.photoncamera.gallery.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import com.eszdman.photoncamera.gallery.helper.Constants;
import com.eszdman.photoncamera.gallery.viewmodel.ExifDialogViewModel;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageViewerFragment extends Fragment {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private final String path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
    private final File[] allFiles = new File(path).listFiles((dir, name) -> name.toUpperCase().endsWith("JPG"));
    private File newEditedFile;
    private ExifDialogViewModel exifDialogViewModel;
    private ViewPager viewPager;
    private ImageAdapter adapter;
    private NavController navController;
    private FragmentGalleryImageViewerBinding fragmentGalleryImageViewerBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentGalleryImageViewerBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_viewer, container, false);
        initialiseDataMembers();
        setClickListeners();
        return fragmentGalleryImageViewerBinding.getRoot();
    }

    private void initialiseDataMembers() {
        viewPager = fragmentGalleryImageViewerBinding.viewPager;
        exifDialogViewModel = new ViewModelProvider(this).get(ExifDialogViewModel.class);
        fragmentGalleryImageViewerBinding.exifLayout.setExifmodel(exifDialogViewModel.getExifDataModel());
        adapter = new ImageAdapter(allFiles);
        navController = NavHostFragment.findNavController(this);
    }

    private void setClickListeners() {
        fragmentGalleryImageViewerBinding.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.setOnDelete(this::onDeleteButtonClick);
        fragmentGalleryImageViewerBinding.setOnExif(this::onExifButtonClick);
        fragmentGalleryImageViewerBinding.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.setOnGallery(this::onGalleryButtonClick);
        fragmentGalleryImageViewerBinding.setOnEdit(this::onEditButtonClick);
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

    private void onEditButtonClick(View view) {
        int position = viewPager.getCurrentItem();
        if (allFiles != null && getContext() != null) {
            File file = allFiles[position];
            String fileName = file.getName();
            String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(fileName));
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", new File(path + "/" + fileName));
            Intent editIntent = new Intent(Intent.ACTION_EDIT);
            editIntent.setDataAndType(uri, mediaType);
            String outputFilePath = file.getAbsolutePath().replace(file.getName(), generateNewFileName() + '.' + FileUtils.getExtension(fileName));
            newEditedFile = new File(outputFilePath);
            editIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(newEditedFile));
            editIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(editIntent, null);
            startActivityForResult(chooser, Constants.REQUEST_EDIT_IMAGE);
        }
    }

    private String generateNewFileName() {
        return "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_EDIT_IMAGE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                if (newEditedFile != null) {
                    if (newEditedFile.exists() && newEditedFile.length() == 0)
                        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + ")->Dummy file deleted : " + newEditedFile.delete());
                }
            }
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getData() != null) {
                    String savedFilePath = data.getData().getPath();
                    Toast.makeText(getContext(), "Saved : " + savedFilePath, Toast.LENGTH_LONG).show();
                }
            }
//            Log.d(TAG, "onActivityResult(): requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");
        }
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
        Intent chooser = Intent.createChooser(intent, null);
        startActivity(chooser);
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
            exifDialogViewModel.updateModel(currentFile);
        }
    }
}
