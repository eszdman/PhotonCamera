package com.eszdman.photoncamera.gallery.adapters;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import com.bumptech.glide.Glide;
import com.eszdman.photoncamera.gallery.views.TouchImageView;

import java.io.File;
import java.util.Arrays;


public class ImageAdapter extends PagerAdapter {
    private final File[] imageFiles;

    public ImageAdapter(File[] imageFiles) {
        this.imageFiles = imageFiles;
    }

    @Override
    public int getCount() {
        return imageFiles.length;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }


    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        Arrays.sort(imageFiles, (file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
        TouchImageView imageView = new TouchImageView(container.getContext());
        Glide
                .with(container.getContext())
                .load(imageFiles[position])
                .into(imageView);
        container.addView(imageView);
        return imageView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }
}


