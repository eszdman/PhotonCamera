package com.particlesdevs.photoncamera.gallery.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.base.BaseActivity;
import com.particlesdevs.photoncamera.databinding.ActivityGalleryBinding;

public class GalleryActivity extends BaseActivity {
    private ActivityGalleryBinding activityGalleryBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityGalleryBinding = DataBindingUtil.setContentView(this, R.layout.activity_gallery);
//        DataBindingUtil.setContentView(this, R.layout.activity_gallery);
    }

    /*public void onBackArrowClicked(View view) {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.gallery_navigation_host);
        NavController navController = navHostFragment.getNavController();
        navController.navigateUp();
    }*/

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activityGalleryBinding = null;
    }
}
