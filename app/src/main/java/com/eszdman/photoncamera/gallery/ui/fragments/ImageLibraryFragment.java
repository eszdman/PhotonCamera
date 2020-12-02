package com.eszdman.photoncamera.gallery.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.GalleryFragmentImageLibraryBinding;

public class ImageLibraryFragment extends Fragment {
    private NavController navController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        GalleryFragmentImageLibraryBinding galleryFragmentImageLibraryBinding = DataBindingUtil.inflate(inflater, R.layout.gallery_fragment_image_library, container, false);
        navController = NavHostFragment.findNavController(this);
        return galleryFragmentImageLibraryBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() != null) {
            getActivity().findViewById(R.id.temp_gallery_go_back_button).setOnClickListener(this::onGoBackButtonClicked);
        }
    }

    private void onGoBackButtonClicked(View view) {
        navController.popBackStack();
    }
}
