package com.eszdman.photoncamera.gallery.ui;

import android.os.Bundle;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.ActivityGalleryBinding;

public class GalleryActivity extends AppCompatActivity {
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
