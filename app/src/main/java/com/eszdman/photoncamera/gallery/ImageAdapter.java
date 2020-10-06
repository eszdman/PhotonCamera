package com.eszdman.photoncamera.gallery;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import com.bumptech.glide.Glide;


public class ImageAdapter extends PagerAdapter {
    private final Context context;
    private final File[] file;

    ImageAdapter(Context context, File[] file) {
        this.context = context;
        this.file = file;
    }
    @Override
    public int getCount() {
        return file.length;
    }
    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    Comparator c = (Comparator<File>) (file1, file2) -> {
        long tsFile1 = file1.lastModified();
        long tsFile2 = file2.lastModified();
        return Long.compare(tsFile2, tsFile1);
    };

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        Arrays.sort(file, c);
        TouchImageView imageView = new TouchImageView(context);
        Glide
                .with(context)
                .load(file[position])
                .into(imageView);
        container.addView(imageView);
        return imageView;
    }
    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }
}


