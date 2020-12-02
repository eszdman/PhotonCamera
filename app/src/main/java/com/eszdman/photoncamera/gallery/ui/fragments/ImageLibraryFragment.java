package com.eszdman.photoncamera.gallery.ui.fragments;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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

import java.io.File;
import java.util.Arrays;

public class ImageLibraryFragment extends Fragment {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private final String path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
    private final File[] allFiles = new File(path).listFiles((dir, name) -> name.toUpperCase().endsWith("JPG"));
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
    public void onViewCreated(@NonNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Arrays.sort(allFiles, (f1, f2) -> -Long.compare(f1.lastModified(), f2.lastModified()));
            RecyclerView recyclerView = fragmentGalleryImageLibraryBinding.imageGridRv;
            recyclerView.setAdapter(new ImageGridAdapter(Arrays.asList(allFiles)));
            recyclerView.setHasFixedSize(true);
            recyclerView.setItemViewCacheSize(1000); //trial
            ImageGridAdapter imageGridAdapter = new ImageGridAdapter(Arrays.asList(allFiles));
            recyclerView.setAdapter(imageGridAdapter);
        }, 400);

    }

}
