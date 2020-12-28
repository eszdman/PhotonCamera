package com.eszdman.photoncamera.gallery.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.FragmentGalleryImageLibraryBinding;
import com.eszdman.photoncamera.gallery.adapters.ImageGridAdapter;
import com.eszdman.photoncamera.util.FileManager;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    }

    private void onDeleteFabClicked(View view) {
        List<File> filesToDelete = imageGridAdapter.getSelectedFiles();
        int numOfFiles = filesToDelete.size();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder
                .setMessage(getContext().getString(R.string.sure_delete_multiple, String.valueOf(numOfFiles)))
                .setTitle(android.R.string.dialog_alert_title)
                .setIcon(R.drawable.ic_delete)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    for (File f : filesToDelete) {
                        f.delete();
                    }
                    onImageSelectionStopped();
                    allFiles = FileManager.getAllImageFiles();
                    imageGridAdapter.setImageList(allFiles);
                    recyclerView.requestLayout();
                    Snackbar.make(view,numOfFiles + " File/s Deleted!", Snackbar.LENGTH_SHORT).show();
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
