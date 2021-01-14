package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageCompareBinding;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.*;

public class ImageCompareFragment extends Fragment {
    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentGalleryImageCompareBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_compare, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle b = getArguments();
        if (b != null) {
            FragmentTransaction trans = getChildFragmentManager().beginTransaction();
            Bundle b1 = new Bundle();
            b1.putInt(IMAGE_POSITION_KEY, b.getInt(IMAGE1_KEY));
            b1.putString(MODE_KEY, COMPARE);
            trans.add(R.id.image_container1, ImageViewerFragment.class, b1, "image_container1");
            Bundle b2 = new Bundle();
            b2.putInt(IMAGE_POSITION_KEY, b.getInt(IMAGE2_KEY));
            b2.putString(MODE_KEY, COMPARE);
            trans.add(R.id.image_container2, ImageViewerFragment.class, b2, "image_container2");
            trans.commit();
        }
    }
}
