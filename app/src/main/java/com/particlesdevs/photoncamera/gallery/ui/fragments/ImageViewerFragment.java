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
import androidx.viewpager2.widget.ViewPager2;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageViewerBinding;
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

public class ImageViewerFragment extends Fragment implements ImageAdapter.HdrStateListener {
    private List<GalleryItem> galleryItems=new ArrayList<>(0);
    private ExifDialogViewModel exifDialogViewModel;
    private ViewPager2 viewPager;
    private RecyclerView linearRecyclerView;
    private ImageAdapter adapter;
    private ImageGridAdapter linearGridAdapter;
    private NavController navController;
    private FragmentGalleryImageViewerBinding fragmentGalleryImageViewerBinding;
    private boolean isExifVisible;
    private String mode;
    private int seek_position = 0;
    private int lastHdrPosition = -1;
    private int deferredReleasePos = -1;
    private SSIVListener ssivListener = new SSIVListener() {
        @Override public void onScaleChanged(float newScale, int origin) {
            updateScaleText();
            if (viewPager != null) {
                CustomSSIV cur = getCurrentSSIV();
                if (cur != null && cur.isReady()) {
                    boolean zoomed = cur.getScale() > cur.getMinScale() + 0.02f;
                    viewPager.setUserInputEnabled(!zoomed);
                } else if (viewPager != null) {
                    viewPager.setUserInputEnabled(true);
                }
            }
        }
        @Override public void onCenterChanged(PointF newCenter, int origin) {}
        @Override public void onTouched(int id) {}
    };
    private int indexToDelete = -1;
    private GalleryViewModel viewModel;
    private ViewPager2.OnPageChangeCallback pageCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mode = args.getString(Constants.MODE_KEY);
            seek_position = args.getInt(Constants.IMAGE_POSITION_KEY, 0);
        }
        if (savedInstanceState != null) {
            seek_position = savedInstanceState.getInt(Constants.IMAGE_POSITION_KEY, seek_position);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewPager != null) outState.putInt(Constants.IMAGE_POSITION_KEY, viewPager.getCurrentItem());
        else outState.putInt(Constants.IMAGE_POSITION_KEY, seek_position);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentGalleryImageViewerBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_viewer, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(GalleryViewModel.class);
        initialiseDataMembers();
        if (fragmentGalleryImageViewerBinding.hdrToggleText != null) {
            fragmentGalleryImageViewerBinding.hdrToggleText.setOnClickListener(this::onHdrToggleClicked);
        }
        setClickListeners();
        return fragmentGalleryImageViewerBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (viewPager != null && pageCallback != null) {
            viewPager.unregisterOnPageChangeCallback(pageCallback);
        }
        if (adapter != null) adapter.clearPreviewCaches();
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
            if (seek_position >= galleryItems.size()) seek_position = Math.max(0, galleryItems.size()-1);
            if (seek_position < 0) seek_position = 0;
            adapter = new ImageAdapter(this.galleryItems);
            adapter.setAppContext(requireContext().getApplicationContext());
            adapter.setImageViewClickListener(ImageViewerFragment.this::onImageViewClicked);
            adapter.setHdrStateListener(this);
            if (ssivListener != null) adapter.setSsivListener(ssivListener);
            adapter.setImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
                @Override public void onReady() { updateScaleText(); }
            });
            viewPager.setAdapter(adapter);
            initLinearRecyclerAdapter(galleryItems);
            viewPager.setCurrentItem(seek_position, false);
            // Eagerly preload previews for the window so neighbor pages show a placeholder (no black).
            adapter.preloadPreviews(seek_position);
            linearRecyclerView.post(() -> linearRecyclerView.scrollToPosition(seek_position));
            viewPager.post(() -> onPageHdrSelected(seek_position));
        }
    }

    private void initLinearRecyclerAdapter(List<GalleryItem> galleryItems) {
        if (galleryItems != null) {
            linearGridAdapter = new ImageGridAdapter(galleryItems, Constants.GALLERY_ITEM_TYPE_LINEAR);
            fragmentGalleryImageViewerBinding.bottomControlsContainer.scrollingGalleryView.setAdapter(linearGridAdapter);
            linearGridAdapter.setGridAdapterCallback(new ImageGridAdapter.GridAdapterCallback() {
                @Override public void onItemClicked(int position, View view, GalleryItem galleryItem) {
                    seek_position = position;
                    viewPager.setCurrentItem(position, true);
                    LinearLayoutManager lm = (LinearLayoutManager) linearRecyclerView.getLayoutManager();
                    if (lm != null) {
                        int avg = (lm.findFirstCompletelyVisibleItemPosition() + (lm.findFirstCompletelyVisibleItemPosition() + 1) +
                                lm.findLastCompletelyVisibleItemPosition()) / 3;
                        if (position > avg) linearRecyclerView.smoothScrollToPosition(position + 1);
                        else if (position != 0) linearRecyclerView.smoothScrollToPosition(position - 1);
                        else linearRecyclerView.smoothScrollToPosition(0);
                    }
                }
                @Override public void onImageSelectionChanged(int num) {}
                @Override public void onImageSelectionStopped() {}
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
        viewPager.setPageTransformer(null);
        // D: O(viewport) ~10-12 MB per page at 160dpi; keep baseline low on 4GB: offscreen=1 and cache=1 (was 2/2).
        // Still pre-warms ±1 tile via preview placeholder, without extra native tile retention.
        viewPager.setOffscreenPageLimit(1);
        viewPager.setUserInputEnabled(true);
        try {
            RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
            if (rv != null) {
                rv.setHasFixedSize(true);
                rv.setItemViewCacheSize(1);
            }
        } catch (Exception ignored) {}
        pageCallback = new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                seek_position = position;
                updateScaleText();
                linearRecyclerView.smoothScrollToPosition(position);
                onPageHdrSelected(position);
                viewPager.setUserInputEnabled(true);
                // Re-arm preview preload for the new window so neighbors remain instant.
                if (adapter != null) adapter.preloadPreviews(position);
                // Defer heavy Exif/Histogram off critical swipe jank (saves ~48ms)
                viewPager.postDelayed(ImageViewerFragment.this::updateExif, 120);
            }
            @Override public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE && deferredReleasePos != -1) {
                    CustomSSIV prev = adapter != null ? adapter.getActiveSsiv(deferredReleasePos) : null;
                    if (prev == null) prev = getSsivAt(deferredReleasePos);
                    if (adapter != null) adapter.releaseHdrForPosition(prev, deferredReleasePos);
                    deferredReleasePos = -1;
                    updateWindowHdrForVisible();
                }
            }
        };
        viewPager.registerOnPageChangeCallback(pageCallback);
        updateExif();
    }

    @Override public void onResume() {
        super.onResume();
        if (fragmentGalleryImageViewerBinding != null)
            fragmentGalleryImageViewerBinding.setMiniExifVisible(!fragmentGalleryImageViewerBinding.getButtonsVisible());
        if (adapter != null && viewPager != null) {
            int position = viewPager.getCurrentItem();
            seek_position = position;
            if (adapter.isHdrActive(position)) {
                updateWindowHdrForVisible();
                updateHdrToggleUi(adapter.isHdrAvailable(position), true);
            } else {
                CustomSSIV ssiv = getSsivAt(position);
                if (ssiv != null) adapter.loadHdrForPosition(ssiv, position);
                else viewPager.post(() -> adapter.loadHdrForPosition(getSsivAt(position), position));
                updateHdrToggleUi(adapter.isHdrAvailable(position), false);
                updateWindowHdrForVisible();
            }
        }
    }

    @Override public void onPause() {
        super.onPause();
        if (viewPager != null) seek_position = viewPager.getCurrentItem();
        UltraHdrGalleryUtil.setWindowHdr(getActivity(), false);
        if (adapter != null && viewPager != null) {
            int position = viewPager.getCurrentItem();
            updateHdrToggleUi(adapter.isHdrAvailable(position), false);
        }
        if (viewPager != null) viewPager.setUserInputEnabled(true);
        if (deferredReleasePos != -1 && adapter != null) {
            CustomSSIV prev = adapter.getActiveSsiv(deferredReleasePos);
            if (prev == null) prev = getSsivAt(deferredReleasePos);
            adapter.releaseHdrForPosition(prev, deferredReleasePos);
            deferredReleasePos = -1;
        }
        // C: trim caches when backgrounded to drop baseline quickly on 4GB
        if (adapter != null) adapter.trimCaches(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    }

    private void onPageHdrSelected(int position) {
        if (adapter == null) return;
        if (lastHdrPosition >= 0 && lastHdrPosition != position) {
            deferredReleasePos = lastHdrPosition;
        }
        lastHdrPosition = position;
        CustomSSIV cur = adapter.getActiveSsiv(position);
        if (cur == null) cur = getSsivAt(position);
        if (cur != null) adapter.loadHdrForPosition(cur, position);
        else viewPager.post(() -> adapter.loadHdrForPosition(getSsivAt(position), position));
        updateWindowHdrForVisible();
    }

    private void updateWindowHdrForVisible() {
        if (getActivity() == null || adapter == null || viewPager == null) return;
        if (isCompareMode()) {
            UltraHdrGalleryUtil.setWindowHdr(getActivity(), adapter.isHdrActive(viewPager.getCurrentItem()));
            return;
        }
        boolean needHdr = adapter.isHdrActive(viewPager.getCurrentItem());
        if (!needHdr && deferredReleasePos != -1) needHdr = adapter.isHdrActive(deferredReleasePos);
        UltraHdrGalleryUtil.setWindowHdr(getActivity(), needHdr);
    }

    @Override public void onHdrStateChanged(int position, boolean isHdr) {
        if (getActivity() != null && viewPager != null && position == viewPager.getCurrentItem()) {
            updateWindowHdrForVisible();
            updateHdrToggleUi(adapter != null && adapter.isHdrAvailable(position), isHdr);
        }
    }

    private void onHdrToggleClicked(View view) {
        if (adapter == null || viewPager == null) return;
        int position = viewPager.getCurrentItem();
        if (!adapter.isHdrAvailable(position)) return;
        CustomSSIV ssiv = getSsivAt(position);
        if (adapter.isHdrActive(position)) {
            adapter.releaseHdrForPosition(ssiv, position);
            UltraHdrGalleryUtil.setWindowHdr(getActivity(), false);
            updateHdrToggleUi(true, false);
        } else {
            adapter.loadHdrForPosition(ssiv, position);
        }
    }

    @Override public void onHdrAvailabilityChanged(int position, boolean isUltraHdr) {
        if (viewPager != null && position == viewPager.getCurrentItem()) {
            boolean isHdr = adapter != null && adapter.isHdrActive(position);
            updateHdrToggleUi(isUltraHdr, isHdr);
        }
    }

    private void updateHdrToggleUi(boolean isUltraHdr, boolean isHdr) {
        if (fragmentGalleryImageViewerBinding == null || fragmentGalleryImageViewerBinding.hdrToggleText == null) return;
        if (!isUltraHdr || !fragmentGalleryImageViewerBinding.getButtonsVisible()) {
            fragmentGalleryImageViewerBinding.hdrToggleText.setVisibility(View.GONE);
            return;
        }
        fragmentGalleryImageViewerBinding.hdrToggleText.setVisibility(View.VISIBLE);
        fragmentGalleryImageViewerBinding.hdrToggleText.setImageResource(isHdr ? R.drawable.ic_ultra_hdr : R.drawable.ic_ultra_hdr_off);
    }

    public void setSsivListener(SSIVListener ssivListener) { this.ssivListener = ssivListener; }
    public CustomSSIV getCurrentSSIV() { return getSsivAt(viewPager != null ? viewPager.getCurrentItem() : seek_position); }

    private CustomSSIV getSsivAt(int position) {
        if (adapter == null || viewPager == null) return null;
        CustomSSIV fromMap = adapter.getActiveSsiv(position);
        if (fromMap != null) return fromMap;
        try {
            RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
            if (rv != null) {
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
                if (vh instanceof ImageAdapter.Holder) return ((ImageAdapter.Holder) vh).getSsiv();
                for (int i = 0; i < rv.getChildCount(); i++) {
                    View child = rv.getChildAt(i);
                    RecyclerView.ViewHolder ch = rv.getChildViewHolder(child);
                    if (ch != null && ch.getBindingAdapterPosition() == position && ch instanceof ImageAdapter.Holder) {
                        return ((ImageAdapter.Holder) ch).getSsiv();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void onBack(View view) { if (getActivity()!=null) getActivity().finish(); }

    private void onQuickCompare(View view) {
        if (galleryItems.size() >= 2) {
            NavController navController = Navigation.findNavController(view);
            Bundle b = new Bundle(2);
            int image1pos = viewPager.getCurrentItem();
            int image2pos = image1pos + 1;
            if (image1pos == galleryItems.size() - 1) { image2pos = image1pos; image1pos -= 1; }
            b.putInt(Constants.IMAGE1_KEY, image1pos);
            b.putInt(Constants.IMAGE2_KEY, image2pos);
            navController.navigate(R.id.action_imageViewerFragment_to_imageCompareFragment, b);
        } else Toast.makeText(getContext(), "No images to compare!", Toast.LENGTH_SHORT).show();
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

    @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_EDIT_IMAGE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                String savedFilePath = data.getData().getPath();
                Toast.makeText(getContext(), "Saved : " + savedFilePath, Toast.LENGTH_LONG).show();
                viewModel.fetchAllMedia();
                initImageAdapter(viewModel.getCurrentFolderImages().getValue());
                refreshLinearGridAdapter(viewModel.getCurrentFolderImages().getValue());
                updateExif();
            }
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
        startActivity(Intent.createChooser(intent, null));
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
            int position = viewPager.getCurrentItem();
            updateHdrToggleUi(adapter != null && adapter.isHdrAvailable(position), adapter != null && adapter.isHdrActive(position));
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
        if (view != null && fragmentGalleryImageViewerBinding != null) {
            fragmentGalleryImageViewerBinding.setScale(String.format(Locale.ROOT, "%.0f%%", (view.getScale() * 100)));
        }
    }

    public void resetScaleText() { if (fragmentGalleryImageViewerBinding!=null) fragmentGalleryImageViewerBinding.setScale(""); }

    private void updateExif() {
        if (viewPager == null) return;
        int position = viewPager.getCurrentItem();
        if (galleryItems != null && !galleryItems.isEmpty() && position < galleryItems.size()) {
            GalleryItem galleryItem = galleryItems.get(position);
            exifDialogViewModel.updateModel(requireContext().getContentResolver(), galleryItem.getFile());
            if (fragmentGalleryImageViewerBinding.getExifDialogVisible()) {
                exifDialogViewModel.updateHistogramView((ImageFile) galleryItem.getFile());
            }
        }
    }

    private void isHistogramLoading(boolean loading) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (fragmentGalleryImageViewerBinding == null || fragmentGalleryImageViewerBinding.exifLayout == null) return;
            if (loading) fragmentGalleryImageViewerBinding.exifLayout.histoLoading.setVisibility(View.VISIBLE);
            else fragmentGalleryImageViewerBinding.exifLayout.histoLoading.setVisibility(View.INVISIBLE);
        });
    }

    private boolean isCompareMode() { return mode != null && mode.equalsIgnoreCase(Constants.COMPARE); }

    public void handleImagesDeletedCallback(boolean isDeleted) {
        if (isDeleted && indexToDelete >= 0) {
            galleryItems.remove(indexToDelete);
            seek_position=indexToDelete;
            if (!galleryItems.isEmpty()) initImageAdapter(galleryItems);
            updateExif();
            Toast.makeText(getContext(), R.string.image_deleted, Toast.LENGTH_SHORT).show();
            indexToDelete = -1;
            if (galleryItems.isEmpty()) { viewModel.setUpdatePending(true); navController.navigateUp(); }
        } else Toast.makeText(getContext(), "Deletion Failed!", Toast.LENGTH_SHORT).show();
    }
}
