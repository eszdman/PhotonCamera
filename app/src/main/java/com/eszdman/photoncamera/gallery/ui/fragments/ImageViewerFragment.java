package com.eszdman.photoncamera.gallery.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.eszdman.photoncamera.processing.ImageSaver;
import com.eszdman.photoncamera.util.FileManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;

import static com.eszdman.photoncamera.gallery.helper.Constants.COMPARE;
import static com.eszdman.photoncamera.gallery.helper.Constants.IMAGE_POSITION_KEY;
import static com.eszdman.photoncamera.gallery.helper.Constants.MODE_KEY;

public class ImageViewerFragment extends Fragment {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private List < File > allFiles;
    private File newEditedFile;
    private ExifDialogViewModel exifDialogViewModel;
    private ViewPager viewPager;
    private ImageAdapter adapter;
    private NavController navController;
    private FragmentGalleryImageViewerBinding fragmentGalleryImageViewerBinding;
    private boolean isExifVisible;
    private String mode;

    @Nullable@Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentGalleryImageViewerBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_viewer, container, false);
        initialiseDataMembers();
        setClickListeners();
        return fragmentGalleryImageViewerBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getFragmentManager().beginTransaction().remove((Fragment) ImageViewerFragment.this).commitAllowingStateLoss();
    }

    private void initialiseDataMembers() {
        viewPager = fragmentGalleryImageViewerBinding.viewPager;
        exifDialogViewModel = new ViewModelProvider(this).get(ExifDialogViewModel.class);
        fragmentGalleryImageViewerBinding.exifLayout.setExifmodel(exifDialogViewModel.getExifDataModel());
        fragmentGalleryImageViewerBinding.setExifmodel(exifDialogViewModel.getExifDataModel());
        navController = NavHostFragment.findNavController(this);
        initImageAdapter();
    }

    private void initImageAdapter() {
        allFiles = FileManager.getAllImageFiles();
        adapter = new ImageAdapter(allFiles);
        adapter.setImageViewClickListener(this::onImageViewClicked);
        viewPager.setAdapter(adapter);
    }

    private void setClickListeners() {
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnDelete(this::onDeleteButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnExif(this::onExifButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnEdit(this::onEditButtonClick);
        fragmentGalleryImageViewerBinding.topControlsContainer.setOnGallery(this::onGalleryButtonClick);
        fragmentGalleryImageViewerBinding.topControlsContainer.setOnBack(this::onBack);
        fragmentGalleryImageViewerBinding.exifLayout.histogramView.setHistogramLoadingListener(this::isHistogramLoading);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager.setPageTransformer(true, new DepthPageTransformer());
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {@Override
        public void onPageSelected(int position) {
            updateExif();
        }
        });
        Bundle bundle = getArguments();
        if (bundle != null) {
            mode = bundle.getString(MODE_KEY);
            viewPager.setCurrentItem(bundle.getInt(IMAGE_POSITION_KEY, 0));
        }
        if (isCompareMode()) {
            fragmentGalleryImageViewerBinding.setMiniExifVisible(true);
        }
    }

    private void onBack(View view) {
        getActivity().finish();
    }

    private void onGalleryButtonClick(View view) {
        if (navController.getPreviousBackStackEntry() == null) navController.navigate(R.id.action_imageViewFragment_to_imageLibraryFragment);
        else navController.navigateUp();
    }

    private void onEditButtonClick(View view) {
        int position = viewPager.getCurrentItem();
        if (allFiles != null && getContext() != null) {
            File file = allFiles.get(position);
            String fileName = file.getName();
            String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(fileName));
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
            Intent editIntent = new Intent(Intent.ACTION_EDIT);
            editIntent.setDataAndType(uri, mediaType);
            String outputFilePath = file.getAbsolutePath().replace(file.getName(), ImageSaver.Util.generateNewFileName() + '.' + FileUtils.getExtension(fileName));
            newEditedFile = new File(outputFilePath);
            editIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(newEditedFile));
            editIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(editIntent, null);
            startActivityForResult(chooser, Constants.REQUEST_EDIT_IMAGE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_EDIT_IMAGE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                if (newEditedFile != null) {
                    if (newEditedFile.exists() && newEditedFile.length() == 0) Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + ")->Dummy file deleted : " + newEditedFile.delete());
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
        builder.setMessage(R.string.sure_delete).setTitle(android.R.string.dialog_alert_title).setIcon(R.drawable.ic_delete).setNegativeButton(R.string.cancel, (dialog, which) ->dialog.dismiss())

                .setPositiveButton(R.string.yes, (dialog, which) ->{

                    int position = viewPager.getCurrentItem();
                    File thisFile = new File(String.valueOf(allFiles.get(position)));
                    thisFile.delete();
                    MediaScannerConnection.scanFile(getContext(), new String[] {
                                    String.valueOf(thisFile)
                            },
                            null, null);
                    initImageAdapter();
                    //auto scroll to the next photo
                    viewPager.setCurrentItem(position, true);
                    updateExif();
                    Toast.makeText(getContext(), R.string.image_deleted, Toast.LENGTH_SHORT).show();
                });
        builder.create().show();
    }

    private void onShareButtonClick(View view) {
        int position = viewPager.getCurrentItem();
        File file = allFiles.get(position);
        String fileName = file.getName();
        String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(fileName));
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(mediaType);
        Intent chooser = Intent.createChooser(intent, null);
        startActivity(chooser);
    }

    private void onExifButtonClick(View view) {
        isExifVisible = !isExifVisible;
        fragmentGalleryImageViewerBinding.setExifDialogVisible(isExifVisible);
        updateExif();
    }

    private void onImageViewClicked(View view) {
        if (isCompareMode()) {
            onExifButtonClick(null);
            fragmentGalleryImageViewerBinding.setMiniExifVisible(!isExifVisible);
        } else {
            fragmentGalleryImageViewerBinding.setButtonsVisible(!fragmentGalleryImageViewerBinding.getButtonsVisible());
            if (isExifVisible) {
                fragmentGalleryImageViewerBinding.setExifDialogVisible(fragmentGalleryImageViewerBinding.getButtonsVisible());
                updateExif();
            }
        }
    }

    private void updateExif() {
        int position = viewPager.getCurrentItem();
        File currentFile = allFiles.get(position);
        if (fragmentGalleryImageViewerBinding.getExifDialogVisible()) {
            //update values for exif dialog
            exifDialogViewModel.updateModel(currentFile);
            exifDialogViewModel.updateHistogramView(currentFile);
        } else {
            exifDialogViewModel.updateModel(currentFile);
        }
    }

    private void isHistogramLoading(boolean loading) {
        new Handler(Looper.getMainLooper()).post(() ->{
            if (loading) {
                fragmentGalleryImageViewerBinding.exifLayout.histoLoading.setVisibility(View.VISIBLE);
            } else {
                fragmentGalleryImageViewerBinding.exifLayout.histoLoading.setVisibility(View.INVISIBLE);
            }
        });

    }

    private boolean isCompareMode() {
        return mode != null && mode.equalsIgnoreCase(COMPARE);
    }
}