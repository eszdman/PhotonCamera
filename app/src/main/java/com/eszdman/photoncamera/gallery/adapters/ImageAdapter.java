package com.eszdman.photoncamera.gallery.adapters;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.eszdman.photoncamera.gallery.views.TouchImageView;

import java.io.File;
import java.util.List;


public class ImageAdapter extends PagerAdapter {
    private final List<File> imageFiles;

    public ImageAdapter(List<File> imageFiles) {
        this.imageFiles = imageFiles;
    }

    @Override
    public int getCount() {
        return imageFiles.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }


    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        TouchImageView imageView = new TouchImageView(container.getContext());
        File file = imageFiles.get(position);
        Glide
                .with(container.getContext())
                .load(file)
                .apply(RequestOptions.signatureOf(new ObjectKey(file.getName() + file.lastModified())))
                .into(imageView);
        container.addView(imageView);
        return imageView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }
}


