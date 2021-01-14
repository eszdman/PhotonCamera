package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageLibraryBinding;
import com.particlesdevs.photoncamera.gallery.adapters.ImageGridAdapter;
import com.particlesdevs.photoncamera.util.FileManager;
import com.google.android.material.snackbar.Snackbar;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.IMAGE1_KEY;
import static com.particlesdevs.photoncamera.gallery.helper.Constants.IMAGE2_KEY;

public class ImageLibraryFragment extends Fragment implements ImageGridAdapter.ImageSelectionListener {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private FragmentGalleryImageLibraryBinding fragmentGalleryImageLibraryBinding;
    private NavController navController;
    private ImageGridAdapter imageGridAdapter;
    private RecyclerView recyclerView;
    private boolean isFABOpen;
    private List<File> allFiles;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentGalleryImageLibraryBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_library, container, false);
        navController = NavHostFragment.findNavController(this);
        return fragmentGalleryImageLibraryBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = fragmentGalleryImageLibraryBinding.imageGridRv;
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10); //trial
        initImageAdapter();
        initListeners();
    }

    private void initImageAdapter() {
        allFiles = FileManager.getAllImageFiles();
        imageGridAdapter = new ImageGridAdapter(allFiles);
        imageGridAdapter.setHasStableIds(true);
        imageGridAdapter.setImageSelectionListener(this);
        recyclerView.setAdapter(imageGridAdapter);
    }

    private void initListeners() {
        fragmentGalleryImageLibraryBinding.fabGroup.numberFab.setOnLongClickListener(v -> {
            onImageSelectionStopped();
            return true;
        });
        fragmentGalleryImageLibraryBinding.fabGroup.setOnNumFabClicked(this::onNumFabClicked);
        fragmentGalleryImageLibraryBinding.fabGroup.setOnShareFabClicked(this::onShareFabClicked);
        fragmentGalleryImageLibraryBinding.fabGroup.setOnDeleteFabClicked(this::onDeleteFabClicked);
        fragmentGalleryImageLibraryBinding.fabGroup.setOnCompareFabClicked(this::onCompareFabClicked);
    }

    private void onCompareFabClicked(View view) {
        List<File> list = imageGridAdapter.getSelectedFiles();
        if (list.size() == 2) {
            NavController navController = Navigation.findNavController(view);
            Bundle b = new Bundle(2);
            int image1pos = allFiles.indexOf(list.get(0));
            int image2pos = allFiles.indexOf(list.get(1));
            b.putInt(IMAGE1_KEY, image1pos);
            b.putInt(IMAGE2_KEY, image2pos);
            navController.navigate(R.id.action_imageLibraryFragment_to_imageCompareFragment, b);
        }
    }

    private void onDeleteFabClicked(View view) {
        List<File> filesToDelete = imageGridAdapter.getSelectedFiles();
        String numOfFiles = String.valueOf(filesToDelete.size());
        String totalFileSize = FileUtils.byteCountToDisplaySize((int) filesToDelete.stream().mapToLong(File::length).sum());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder
                .setMessage(getContext().getString(R.string.sure_delete_multiple, numOfFiles, totalFileSize))
                .setTitle(android.R.string.dialog_alert_title)
                .setIcon(R.drawable.ic_delete)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    filesToDelete.forEach(file -> {
                        file.delete();
                        MediaScannerConnection.scanFile(getContext(), new String[]{String.valueOf(file)}, null, null);
                    });
                    onImageSelectionStopped();
                    allFiles = FileManager.getAllImageFiles();
                    imageGridAdapter.setImageList(allFiles);
                    recyclerView.requestLayout();
                    Snackbar.make(view,
                            getString(R.string.multiple_deleted_success, numOfFiles, totalFileSize),
                            Snackbar.LENGTH_SHORT).show();
                })
                .create()
                .show();
    }

    private void onShareFabClicked(View view) {
        ArrayList<Uri> imageUris = new ArrayList<>();
        imageGridAdapter.getSelectedFiles()
                .forEach(file -> imageUris.add(FileProvider.getUriForFile(getContext(),
                        getContext().getPackageName() + ".provider", file)));
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris);
        shareIntent.setType("image/*");
        startActivity(Intent.createChooser(shareIntent, null));
    }

    private void onNumFabClicked(View view) {
        if (!isFABOpen) {
            showFABMenu();
        } else {
            closeFABMenu();
        }
    }

    private void showFABMenu() {
        isFABOpen = true;
        fragmentGalleryImageLibraryBinding.fabGroup.deleteFab.animate().translationY(-getResources().getDimension(R.dimen.standard_65));
        fragmentGalleryImageLibraryBinding.fabGroup.shareFab.animate().translationY(-getResources().getDimension(R.dimen.standard_125));
    }

    private void closeFABMenu() {
        isFABOpen = false;
        fragmentGalleryImageLibraryBinding.fabGroup.deleteFab.animate().translationY(0);
        fragmentGalleryImageLibraryBinding.fabGroup.shareFab.animate().translationY(0);
    }

    @Override
    public void onImageSelectionChanged(int numOfSelectedFiles) {
        fragmentGalleryImageLibraryBinding.setButtonsVisible(true);
        fragmentGalleryImageLibraryBinding.fabGroup.setSelectedCount(String.valueOf(numOfSelectedFiles));
        fragmentGalleryImageLibraryBinding.fabGroup.setCompareVisible(numOfSelectedFiles == 2);
    }

    @Override
    public void onImageSelectionStopped() {
        imageGridAdapter.deselectAll();
        if (isFABOpen) {
            closeFABMenu();
        }
        fragmentGalleryImageLibraryBinding.setButtonsVisible(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isFABOpen) {
            closeFABMenu();
        }
    }
}
