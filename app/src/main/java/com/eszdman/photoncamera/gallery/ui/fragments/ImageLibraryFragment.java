package com.eszdman.photoncamera.gallery.ui.fragments;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.FragmentGalleryImageLibraryBinding;
import com.eszdman.photoncamera.gallery.adapters.ImageGridAdapter;
import com.eszdman.photoncamera.util.FileManager;

import java.io.File;
import java.util.Arrays;

public class ImageLibraryFragment extends Fragment {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private FragmentGalleryImageLibraryBinding fragmentGalleryImageLibraryBinding;
    private NavController navController;


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
        RecyclerView recyclerView = fragmentGalleryImageLibraryBinding.imageGridRv;
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10); //trial
        ImageGridAdapter imageGridAdapter = new ImageGridAdapter(FileManager.getAllImageFiles());
        imageGridAdapter.setHasStableIds(true);
        recyclerView.setAdapter(imageGridAdapter);
    }

}
