package com.particlesdevs.photoncamera.gallery.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.base.BaseActivity;
import com.particlesdevs.photoncamera.databinding.ActivityGalleryBinding;
import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.helper.Constants;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.ui.fragments.ImageLibraryFragment;
import com.particlesdevs.photoncamera.gallery.ui.fragments.ImageViewerFragment;
import com.particlesdevs.photoncamera.gallery.viewmodel.GalleryViewModel;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.SecureCameraHelper;

import java.util.ArrayList;
import java.util.List;

public class GalleryActivity extends BaseActivity {
    private ActivityGalleryBinding activityGalleryBinding;
    private GalleryViewModel viewModel;

    private boolean externalUsage = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Defense-in-depth: never expose the library over the keyguard. The secure
        // camera flow launches us only after credential confirmation (EXTRA_UNLOCKED_JUST_NOW).
        if (SecureCameraHelper.isDeviceLocked(this)
                && !getIntent().getBooleanExtra(SecureCameraHelper.EXTRA_UNLOCKED_JUST_NOW, false)) {
            Toast.makeText(this, getString(R.string.secure_camera_gallery_locked),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Hide system UI immediately to prevent flickering
        hideSystemUI();
        
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            Object fragment = getIntent().getExtras().get("CameraFragment");
            externalUsage = fragment == null;
        } else {
            externalUsage = true;
        }

        getDelegate().setLocalNightMode(PreferenceKeys.getThemeValue());
        activityGalleryBinding = DataBindingUtil.setContentView(this, R.layout.activity_gallery);
        viewModel = new ViewModelProvider(this).get(GalleryViewModel.class);
        
        // Check if this is a VIEW or REVIEW intent from another app (like HedgeCam2)
        String action = intent.getAction();
        if ((Intent.ACTION_VIEW.equals(action) || Constants.ACTION_REVIEW.equals(action)) 
                && intent.getData() != null) {
            handleExternalViewIntent(intent.getData());
        } else {
            // Normal gallery mode - show all media
            viewModel.fetchAllMedia();
            viewModel.setCurrentFolderImages(viewModel.getAllSelectedImageFolder().getValue());
        }
    }
    
    /**
     * Handle VIEW intent from external apps (like HedgeCam2)
     * Loads all gallery images but starts viewing at the selected image
     */
    private void handleExternalViewIntent(Uri uri) {
        try {
            // Load all media from the gallery first
            viewModel.fetchAllMedia();
            
            // Create an ImageFile from the external URI
            ImageFile externalImageFile = createImageFileFromUri(uri);
            
            // Get all images from the gallery
            GalleryItem allImagesFolder = viewModel.getAllSelectedImageFolder().getValue();
            
            int initialPosition = 0;
            
            if (allImagesFolder != null && !allImagesFolder.getFiles().isEmpty()) {
                List<GalleryItem> allImages = allImagesFolder.getFiles();
                
                // Try to find the external image in the existing gallery
                // Match by URI, absolute path, or display name
                int foundPosition = -1;
                String externalPath = uri.getPath();
                String externalDisplayName = externalImageFile.getDisplayName();
                
                for (int i = 0; i < allImages.size(); i++) {
                    GalleryItem item = allImages.get(i);
                    if (item.getFile() != null) {
                        // Try to match by URI (most reliable for content:// URIs)
                        if (uri.equals(item.getFile().getFileUri())) {
                            foundPosition = i;
                            break;
                        }
                        // Try to match by absolute path (for file:// URIs)
                        if (externalPath != null && item.getFile().getAbsolutePath() != null 
                                && externalPath.equals(item.getFile().getAbsolutePath())) {
                            foundPosition = i;
                            break;
                        }
                        // Try to match by display name and size as last resort
                        if (externalDisplayName != null && externalDisplayName.equals(item.getFile().getDisplayName())
                                && externalImageFile.getSize() == item.getFile().getSize()) {
                            foundPosition = i;
                            break;
                        }
                    }
                }
                
                if (foundPosition >= 0) {
                    // Image found in gallery, use its position
                    initialPosition = foundPosition;
                } else {
                    // Image not in gallery, add it at the beginning
                    GalleryItem externalGalleryItem = new GalleryItem(externalImageFile);
                    allImages.add(0, externalGalleryItem);
                    initialPosition = 0;
                }
                
                // Set the current folder images
                viewModel.setCurrentFolderImages(allImagesFolder);
            } else {
                // No gallery images found, create a single-item list with just the external image
                GalleryItem externalGalleryItem = new GalleryItem(externalImageFile);
                List<GalleryItem> singleItemList = new ArrayList<>();
                singleItemList.add(externalGalleryItem);
                viewModel.getCurrentFolderImages().setValue(singleItemList);
            }
            
            // Set up the navigation graph with initial arguments for the start destination
            // This avoids creating a duplicate back stack entry
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.gallery_navigation_host);
            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                Bundle bundle = new Bundle();
                bundle.putInt(Constants.IMAGE_POSITION_KEY, initialPosition);
                bundle.putString(Constants.EXTERNAL_URI_KEY, uri.toString());
                
                // Set the graph with default arguments instead of navigating
                // Since imageViewerFragment is the start destination, this prevents duplicate back stack entries
                navController.setGraph(R.navigation.gallery_nav_graph, bundle);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to normal gallery mode if something goes wrong
            viewModel.fetchAllMedia();
            viewModel.setCurrentFolderImages(viewModel.getAllSelectedImageFolder().getValue());
        }
    }
    
    /**
     * Creates an ImageFile object from a URI
     * Extracts file information like name, size, etc. from the URI
     */
    private ImageFile createImageFileFromUri(Uri uri) {
        String displayName = "Unknown";
        long size = 0;
        long lastModified = System.currentTimeMillis();
        
        // Try to get file information from the ContentResolver
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex);
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Create and return the ImageFile
        // Use a negative ID to indicate this is an external file
        return new ImageFile(-1, uri, displayName, lastModified, size, uri.toString());
    }

    /*public void onBackArrowClicked(View view) {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.gallery_navigation_host);
        NavController navController = navHostFragment.getNavController();
        navController.navigateUp();
    }*/

    @Override
    protected void onResume() {
        super.onResume();
        // Apply hideSystemUI in onResume to prevent flickering when returning to the gallery
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activityGalleryBinding = null;
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float displayAspectRatio = (float) Math.max(dm.heightPixels, dm.widthPixels) / Math.min(dm.heightPixels, dm.widthPixels);
            if (displayAspectRatio <= (16f / 9) || dm.densityDpi > 440) {
                hideSystemUI();
            }
        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GalleryFileOperations.REQUEST_PERM_DELETE) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.gallery_navigation_host);
            Fragment fragment = null;
            if (navHostFragment != null) {
                fragment = navHostFragment.getChildFragmentManager().getFragments().get(0);
            }
            if (fragment instanceof ImageViewerFragment) {
                ((ImageViewerFragment) fragment).handleImagesDeletedCallback(resultCode == Activity.RESULT_OK);
            } else if (fragment instanceof ImageLibraryFragment) {
                ((ImageLibraryFragment) fragment).handleImagesDeletedCallback(resultCode == Activity.RESULT_OK);
            }
        }
    }
}
