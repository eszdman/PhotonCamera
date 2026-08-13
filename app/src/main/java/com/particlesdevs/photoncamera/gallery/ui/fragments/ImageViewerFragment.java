package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageViewerBinding;
import com.particlesdevs.photoncamera.gallery.adapters.DepthPageTransformer;
import com.particlesdevs.photoncamera.gallery.adapters.ImageAdapter;
import com.particlesdevs.photoncamera.gallery.adapters.ImageGridAdapter;
import com.particlesdevs.photoncamera.gallery.compare.SSIVListener;
import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.helper.Constants;
import com.particlesdevs.photoncamera.gallery.helper.UltraHdrGalleryUtil;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.viewmodel.ExifDialogViewModel;
import com.particlesdevs.photoncamera.gallery.viewmodel.GalleryViewModel;
import com.particlesdevs.photoncamera.gallery.views.CustomSSIV;
import com.particlesdevs.photoncamera.processing.ImagePath;

import org.apache.commons.io.FileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


/**
 * Created by Vibhor Srivastava on 02-Dec-2020
 */
public class ImageViewerFragment extends Fragment implements ImageAdapter.HdrStateListener {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private List<GalleryItem> galleryItems=new ArrayList<>(0);
    private ExifDialogViewModel exifDialogViewModel;
    private ViewPager viewPager;
    private RecyclerView linearRecyclerView;
    private ImageAdapter adapter;
    private ImageGridAdapter linearGridAdapter;
    private NavController navController;
    private FragmentGalleryImageViewerBinding fragmentGalleryImageViewerBinding;
    private boolean isExifVisible;
    private String mode;
    private int seek_position = 0;
    private int lastHdrPosition = -1;
    private SSIVListener ssivListener = new SSIVListener() {
        @Override
        public void onScaleChanged(float newScale, int origin) {
            updateScaleText();
        }

        @Override
        public void onCenterChanged(PointF newCenter, int origin) {

        }

        @Override
        public void onTouched(int id) {

        }
    };
    private int indexToDelete = -1;
    private GalleryViewModel viewModel;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentGalleryImageViewerBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_viewer, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(GalleryViewModel.class);
        initialiseDataMembers();
        setClickListeners();
        return fragmentGalleryImageViewerBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getParentFragmentManager().beginTransaction().remove((Fragment) ImageViewerFragment.this).commitAllowingStateLoss();
        fragmentGalleryImageViewerBinding = null;
    }

    private void initialiseDataMembers() {
        viewPager = fragmentGalleryImageViewerBinding.viewPager;
        linearRecyclerView = fragmentGalleryImageViewerBinding.bottomControlsContainer.scrollingGalleryView;
        exifDialogViewModel = new ViewModelProvider(this).get(ExifDialogViewModel.class);
        fragmentGalleryImageViewerBinding.exifLayout.setExifmodel(exifDialogViewModel.getExifDataModel());
        fragmentGalleryImageViewerBinding.setExifmodel(exifDialogViewModel.getExifDataModel());
        navController = NavHostFragment.findNavController(this);
        viewModel.getCurrentFolderImages().observe(getViewLifecycleOwner(),this::initImageAdapter);
    }

    private void initImageAdapter(List<GalleryItem> galleryItems) {
        if (galleryItems != null) {
            this.galleryItems = galleryItems;
            adapter = new ImageAdapter(this.galleryItems);
            adapter.setImageViewClickListener(ImageViewerFragment.this::onImageViewClicked);
            adapter.setHdrStateListener(this);
            if (ssivListener != null) {
                adapter.setSsivListener(ssivListener);
            }
            adapter.setImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
                @Override
                public void onReady() {
                    updateScaleText();
                }
            });
            viewPager.setAdapter(adapter);
            initLinearRecyclerAdapter(galleryItems);
            viewPager.setCurrentItem(seek_position);
            linearRecyclerView.postDelayed(() -> linearRecyclerView.scrollToPosition(seek_position),500);
            viewPager.post(() -> onPageHdrSelected(seek_position));
        }
    }

    private void initLinearRecyclerAdapter(List<GalleryItem> galleryItems) {
        if (galleryItems != null) {
            linearGridAdapter = new ImageGridAdapter(galleryItems, Constants.GALLERY_ITEM_TYPE_LINEAR);
            fragmentGalleryImageViewerBinding.bottomControlsContainer.scrollingGalleryView.setAdapter(linearGridAdapter);
            linearGridAdapter.setGridAdapterCallback(new ImageGridAdapter.GridAdapterCallback() {
                @Override
                public void onItemClicked(int position, View view, GalleryItem galleryItem) {
                    viewPager.setCurrentItem(position, true);
                    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) linearRecyclerView.getLayoutManager();
                    if (linearLayoutManager != null) {
                        int avg = (linearLayoutManager.findFirstCompletelyVisibleItemPosition() + (linearLayoutManager.findFirstCompletelyVisibleItemPosition() + 1) +
                                linearLayoutManager.findLastCompletelyVisibleItemPosition()) / 3;
                        if (position > avg)
                            linearRecyclerView.smoothScrollToPosition(position + 1);
                        else if (position != 0)
                            linearRecyclerView.smoothScrollToPosition(position - 1);
                        else
                            linearRecyclerView.smoothScrollToPosition(0);

                    }
                }

                @Override
                public void onImageSelectionChanged(int numOfSelectedFiles) {
                    //Not implemented
                }

                @Override
                public void onImageSelectionStopped() {
                    //Not implemented
                }
            });
        }
    }

    private void setClickListeners() {
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnDelete(this::onDeleteButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnExif(this::onExifButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnShare(this::onShareButtonClick);
        fragmentGalleryImageViewerBinding.bottomControlsContainer.setOnEdit(this::onEditButtonClick);
        fragmentGalleryImageViewerBinding.topControlsContainer.setOnGallery(this::onGalleryButtonClick);
        fragmentGalleryImageViewerBinding.topControlsContainer.setOnBack(this::onBack);
        fragmentGalleryImageViewerBinding.topControlsContainer.setOnQuickCompare(this::onQuickCompare);
        fragmentGalleryImageViewerBinding.exifLayout.histogramView.setHistogramLoadingListener(this::isHistogramLoading);
        fragmentGalleryImageViewerBinding.setOnclickempty(this::onEmptyViewClicked);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager.setPageTransformer(true, new DepthPageTransformer());
        viewPager.setOffscreenPageLimit(3);
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updateExif();
                updateScaleText();
                linearRecyclerView.smoothScrollToPosition(position);
                onPageHdrSelected(position);
            }
        });
        updateExif();
        Bundle bundle = getArguments();
        if (bundle != null) {
            mode = bundle.getString(Constants.MODE_KEY);
            seek_position = bundle.getInt(Constants.IMAGE_POSITION_KEY, 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
//        if (isCompareMode()) {
            fragmentGalleryImageViewerBinding.setMiniExifVisible(!fragmentGalleryImageViewerBinding.getButtonsVisible());
//        }
        if (adapter != null) {
            int position = viewPager.getCurrentItem();
            if (adapter.isHdrActive(position)) {
                UltraHdrGalleryUtil.setWindowHdr(getActivity(), true);
            } else {
                adapter.loadHdrForPosition(getSsivAt(position), position);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        UltraHdrGalleryUtil.setWindowHdr(getActivity(), false);
    }

    /**
     * Loads the Ultra HDR rendition for the newly selected page and releases
     * the previous page's HDR bitmap so at most one full-resolution HDR
     * bitmap stays in memory. The window color mode is re-synchronised with
     * the visible page so an HDR window can never linger while a plain SDR
     * image is displayed; the HDR state listener re-enables it as soon as
     * the Ultra HDR decode for the current page completes.
     */
    private void onPageHdrSelected(int position) {
        if (adapter == null) {
            return;
        }
        if (lastHdrPosition >= 0 && lastHdrPosition != position) {
            adapter.releaseHdrForPosition(getSsivAt(lastHdrPosition), lastHdrPosition);
        }
        lastHdrPosition = position;
        adapter.loadHdrForPosition(getSsivAt(position), position);
        // In compare mode the shared window is driven by either pane's HDR
        // readiness (via the listener) and cleared on pause, so no pane can
        // stomp the other's window state.
        if (!isCompareMode() && getActivity() != null) {
            UltraHdrGalleryUtil.setWindowHdr(getActivity(), adapter.isHdrActive(position));
        }
    }

    @Override
    public void onHdrStateChanged(int position, boolean isHdr) {
        if (getActivity() != null && viewPager != null && position == viewPager.getCurrentItem()) {
            UltraHdrGalleryUtil.setWindowHdr(getActivity(), isHdr);
        }
    }

    public void setSsivListener(SSIVListener ssivListener) {
        this.ssivListener = ssivListener;
    }

    public CustomSSIV getCurrentSSIV() {
        return getSsivAt(viewPager.getCurrentItem());
    }

    private CustomSSIV getSsivAt(int position) {
        if (adapter == null || viewPager == null) {
            return null;
        }
        return viewPager.findViewById(adapter.getSsivId(position));
    }

    private void onBack(View view) {
        getActivity().finish();
    }

    private void onQuickCompare(View view) {
        if (galleryItems.size() >= 2) {
            NavController navController = Navigation.findNavController(view);
            Bundle b = new Bundle(2);
            int image1pos = viewPager.getCurrentItem();
            int image2pos = image1pos + 1;
            if (image1pos == galleryItems.size() - 1) {
                image2pos = image1pos;
                image1pos -= 1;
            }
            b.putInt(Constants.IMAGE1_KEY, image1pos);
            b.putInt(Constants.IMAGE2_KEY, image2pos);
            navController.navigate(R.id.action_imageViewerFragment_to_imageCompareFragment, b);
        } else {
            Toast.makeText(getContext(), "No images to compare!", Toast.LENGTH_SHORT).show();
        }
    }

    private void onGalleryButtonClick(View view) {
        if (navController.getPreviousBackStackEntry() == null)
            navController.navigate(R.id.action_imageViewFragment_to_imageLibraryFragment);
        else navController.navigateUp();
    }

    private void onEditButtonClick(View view) {
        int position = viewPager.getCurrentItem();
        if (galleryItems != null && getContext() != null) {
            GalleryItem galleryItem = galleryItems.get(position);
            String fileName = galleryItem.getFile().getDisplayName();
            String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(fileName));
            Uri uri = galleryItem.getFile().getFileUri();
            Intent editIntent = new Intent(Intent.ACTION_EDIT);
            editIntent.setDataAndType(uri, mediaType);
            String outPutFileUri = galleryItem.getFile().getFileUri().toString().replace(galleryItem.getFile().getDisplayName(), ImagePath.generateNewFileName("IMG") + '.' + FileUtils.getExtension(fileName));
            editIntent.putExtra(MediaStore.EXTRA_OUTPUT, outPutFileUri);
            editIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(editIntent, null);
            startActivityForResult(chooser, Constants.REQUEST_EDIT_IMAGE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_EDIT_IMAGE) {
            if (resultCode == Activity.RESULT_CANCELED) {
//                if (newEditedFile != null) {
//                    if (newEditedFile.exists() && newEditedFile.length() == 0)
//                        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + ")->Dummy file deleted : " + newEditedFile.delete());
//                }
            }
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getData() != null) {
                    String savedFilePath = data.getData().getPath();
                    Toast.makeText(getContext(), "Saved : " + savedFilePath, Toast.LENGTH_LONG).show();
                    viewModel.fetchAllMedia();
                    initImageAdapter(viewModel.getCurrentFolderImages().getValue());
                    refreshLinearGridAdapter(viewModel.getCurrentFolderImages().getValue());
                    updateExif();
                }
            }
            //            Log.d(TAG, "onActivityResult(): requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");
        }
    }

    private void refreshLinearGridAdapter(List<GalleryItem> galleryItems) {
        linearGridAdapter.setGalleryItemList(galleryItems);
        linearGridAdapter.notifyDataSetChanged();
    }

    private void onDeleteButtonClick(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(R.string.sure_delete).setTitle(android.R.string.dialog_alert_title).setIcon(R.drawable.ic_delete).setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    indexToDelete = viewPager.getCurrentItem();
                    GalleryFileOperations.deleteImageFiles(getActivity(), Collections.singletonList((ImageFile) galleryItems.get(indexToDelete).getFile()), this::handleImagesDeletedCallback);
                });
        builder.create().show();
    }

    private void onShareButtonClick(View view) {
        int position = viewPager.getCurrentItem();
        GalleryItem galleryItem = galleryItems.get(position);
        String fileName = galleryItem.getFile().getDisplayName();
        String mediaType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileUtils.getExtension(fileName));
        Uri uri = galleryItem.getFile().getFileUri();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(mediaType);
        Intent chooser = Intent.createChooser(intent, null);
        startActivity(chooser);
    }

    private void onExifButtonClick(View view) {
        isExifVisible = !isExifVisible;
        fragmentGalleryImageViewerBinding.setExifDialogVisible(isExifVisible);
        updateExif();
    }

    private void onImageViewClicked(View view) {
        if (isCompareMode()) {
            onExifButtonClick(null);
            fragmentGalleryImageViewerBinding.setMiniExifVisible(!isExifVisible);
        } else {
            fragmentGalleryImageViewerBinding.setButtonsVisible(!fragmentGalleryImageViewerBinding.getButtonsVisible());
            fragmentGalleryImageViewerBinding.setMiniExifVisible(!fragmentGalleryImageViewerBinding.getButtonsVisible());
            if (isExifVisible) {
                fragmentGalleryImageViewerBinding.setExifDialogVisible(fragmentGalleryImageViewerBinding.getButtonsVisible());
                updateExif();
            }
        }
    }

    private void onEmptyViewClicked(View view) {
        NavController navController = Navigation.findNavController(view);
        navController.navigate(R.id.action_imageViewerFragment_to_gallerySettingsFragment);
    }

    public void updateScaleText() {
        SubsamplingScaleImageView view = getCurrentSSIV();
        if (view != null) {
            fragmentGalleryImageViewerBinding.setScale(String.format(Locale.ROOT, "%.0f%%", (view.getScale() * 100)));
        }
    }

    public void resetScaleText() {
        fragmentGalleryImageViewerBinding.setScale("");
    }

    private void updateExif() {
        int position = viewPager.getCurrentItem();
        if (!galleryItems.isEmpty()) {
            GalleryItem galleryItem = galleryItems.get(position);
            exifDialogViewModel.updateModel(requireContext().getContentResolver(), galleryItem.getFile());
            if (fragmentGalleryImageViewerBinding.getExifDialogVisible()) {
                exifDialogViewModel.updateHistogramView((ImageFile) galleryItem.getFile());
            }
        }
    }

    private void isHistogramLoading(boolean loading) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (loading) {
                fragmentGalleryImageViewerBinding.exifLayout.histoLoading.setVisibility(View.VISIBLE);
            } else {
                fragmentGalleryImageViewerBinding.exifLayout.histoLoading.setVisibility(View.INVISIBLE);
            }
        });

    }

    private boolean isCompareMode() {
        return mode != null && mode.equalsIgnoreCase(Constants.COMPARE);
    }

    public void handleImagesDeletedCallback(boolean isDeleted) {
        if (isDeleted && indexToDelete >= 0) {
            galleryItems.remove(indexToDelete);
            seek_position=indexToDelete;
            if (!galleryItems.isEmpty()) {
                initImageAdapter(galleryItems);
            }
            updateExif();
            Toast.makeText(getContext(), R.string.image_deleted, Toast.LENGTH_SHORT).show();
            indexToDelete = -1;
            if (galleryItems.isEmpty()) {
                viewModel.setUpdatePending(true);
                navController.navigateUp();
            }
        } else {
            Toast.makeText(getContext(), "Deletion Failed!", Toast.LENGTH_SHORT).show();
        }
    }
}