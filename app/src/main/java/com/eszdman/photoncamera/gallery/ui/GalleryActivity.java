package com.eszdman.photoncamera.gallery.ui;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.base.BaseActivity;
import com.eszdman.photoncamera.databinding.ActivityGalleryBinding;

public class GalleryActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityGalleryBinding activityGalleryBinding = DataBindingUtil.setContentView(this, R.layout.activity_gallery);
    }

    /*public void onBackArrowClicked(View view) {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.gallery_navigation_host);
        NavController navController = navHostFragment.getNavController();
        navController.navigateUp();
    }*/
}
