package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.particlesdevs.photoncamera.util.BlurSupport;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.viewpager2.widget.ViewPager2;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.control.Vibration;
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
import com.particlesdevs.photoncamera.gallery.views.ExifBackdropView;
import com.particlesdevs.photoncamera.processing.ImagePath;
import com.particlesdevs.photoncamera.util.SystemBarsHelper;

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
    /** Whether the scrollable EXIF description inside the panel is expanded. */
    private boolean isDescriptionExpanded;
    /**
     * Clock animating alongside the panel's bounds toggle so the backdrop
     * snapshot can be recaptured while the panel grows or shrinks.
     */
    private ValueAnimator descriptionBlurClock;
    /** Blur radius applied to the EXIF panel backdrop, in dp. */
    private static final float EXIF_BLUR_RADIUS_DP = 32f;
    /** 40% dark scrim blended into the backdrop for text legibility. */
    private static final int EXIF_BLUR_SCRIM = 0x66121417;
    /** Retry cadence while the page's preview bitmap is still decoding. */
    private static final long EXIF_BLUR_RETRY_MS = 120L;
    private static final int EXIF_BLUR_MAX_RETRIES = 10;
    private static final long EXIF_BLUR_REANCHOR_MS = 800L;
    /**
     * Rounded-corner mask applied *after* the GPU blur. Only RenderEffect chains
     * can mask after a blur without smearing the content back into the corners.
     */
    private static final String EXIF_MASK_AGSL =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float radius;\n" +
            "uniform half4 scrim;\n" +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 d = abs(coord - halfSize) - max(halfSize - radius, float2(0.0));\n" +
            "    float dist = length(max(d, float2(0.0))) + min(max(d.x, d.y), 0.0) - radius;\n" +
            "    float mask = 1.0 - smoothstep(-1.0, 1.0, dist);\n" +
            "    half4 c = content.eval(coord);\n" +
            "    half3 frosted = mix(c.rgb, scrim.rgb, scrim.a);\n" +
            "    return half4(frosted * mask, mask);\n" +
            "}\n";

    private RuntimeShader exifMaskShader;
    private RenderEffect exifBackdropEffect;
    private int exifEffectWidth;
    private int exifEffectHeight;
    /** True once the transparent panel background has replaced the opening scrim. */
    private boolean exifBackdropShown;
    /** Retries while the page's preview bitmap is still decoding. */
    private int exifBlurRetries;
    private final Handler exifBlurHandler = new Handler(Looper.getMainLooper());
    private final Runnable exifBlurShowRunnable = this::showExifBackdrop;
    private final Runnable exifBlurSettleRunnable = this::reanchorExifBackdrop;
    /** Tracking state: the SSIV whose pan/zoom the backdrop follows. */
    private CustomSSIV exifTrackSsiv;
    private final PointF exifTrackAnchorSrc = new PointF();
    private final PointF exifTrackAnchorView = new PointF();
    private final PointF exifTrackPivot = new PointF();
    private float exifTrackBaseScale = 1f;
    /** Base placement matrix (panel-local). */
    private final Matrix exifBaseMatrix = new Matrix();
    private final Matrix exifWorkMatrix = new Matrix();
    private float exifBackdropBmpW;
    private float exifBackdropBmpH;
    private int exifPanelW;
    private int exifPanelH;
    private final int[] exifPanelLocation = new int[2];
    private final int[] exifSsivLocation = new int[2];
    private String mode;
    private int seek_position = 0;
    private int lastHdrPosition = -1;
    private int deferredReleasePos = -1;
    private SSIVListener ssivListener = new SSIVListener() {
        @Override public void onScaleChanged(float newScale, int origin) {
            updateScaleText();
            if (origin == SubsamplingScaleImageView.ORIGIN_DOUBLE_TAP_ZOOM && vibration != null) {
                vibration.zoomDetent();
            }
            if (viewPager != null) {
                CustomSSIV cur = getCurrentSSIV();
                if (cur != null && cur.isReady()) {
                    boolean zoomed = cur.getScale() > cur.getMinScale() + 0.02f;
                    viewPager.setUserInputEnabled(!zoomed);
                } else if (viewPager != null) {
                    viewPager.setUserInputEnabled(true);
                }
            }
            updateExifBackdropTransform();
        }
        @Override public void onCenterChanged(PointF newCenter, int origin) {
            updateExifBackdropTransform();
        }
        @Override public void onTouched(int id) {}
    };
    private int indexToDelete = -1;
    private GalleryViewModel viewModel;
    private Vibration vibration;
    private ViewPager2.OnPageChangeCallback pageCallback;
    // Deferred EXIF refresh after swipes: must be cancellable so it never
    // fires on a detached fragment (requireContext() would throw).
    private final Runnable exifUpdateRunnable = this::updateExif;

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
    public void onDestroyView() {
        super.onDestroyView();
        // The deferred EXIF runnable must not outlive the view: it touches
        // requireContext() and would crash on a detached fragment.
        if (viewPager != null) {
            viewPager.removeCallbacks(exifUpdateRunnable);
        }
        if (descriptionBlurClock != null) {
            ValueAnimator clock = descriptionBlurClock;
            descriptionBlurClock = null;
            clock.cancel();
        }
        exifBlurHandler.removeCallbacks(exifBlurShowRunnable);
        exifBlurHandler.removeCallbacks(exifBlurSettleRunnable);
        clearExifBlur();
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
        // The panel is wrap_content: the EXIF text and histogram bind
        // asynchronously and resize it after the panel is shown. Re-fit the
        // backdrop whenever the panel settles at a new size so the rounded
        // mask matches the final geometry.
        fragmentGalleryImageViewerBinding.exifLayout.getRoot().addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        scheduleExifBlurShow();
                    }
                });
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
                @Override public void onReady() {
                    updateScaleText();
                    // The image just became ready: (re)anchor the backdrop tracking
                    // if it was set up before the SSIV could map coordinates.
                    scheduleExifBlurShow();
                }
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
        fragmentGalleryImageViewerBinding.exifLayout.exifDescriptionToggle.setOnClickListener(this::onDescriptionToggleClicked);
        fragmentGalleryImageViewerBinding.setOnclickempty(this::onEmptyViewClicked);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vibration = PhotonCamera.getVibration();
        // Keep bottom controls above the transparent navigation bar.
        // The photo pager itself stays full-bleed behind it.
        View bottomControls = view.findViewById(R.id.bottom_controls_container);
        if (bottomControls != null) {
            SystemBarsHelper.padBottomForNavBar(bottomControls);
        }
        View miniExif = view.findViewById(R.id.mini_exif_container);
        if (miniExif != null) {
            SystemBarsHelper.padBottomForNavBar(miniExif);
        }
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
                if (vibration != null) vibration.pageSnap();
                updateScaleText();
                linearRecyclerView.smoothScrollToPosition(position);
                onPageHdrSelected(position);
                viewPager.setUserInputEnabled(true);
                // Re-arm preview preload for the new window so neighbors remain instant.
                if (adapter != null) adapter.preloadPreviews(position);
                // Defer heavy Exif/Histogram off critical swipe jank (saves ~48ms).
                // Re-post (not pile up) so only the settled page refreshes.
                viewPager.removeCallbacks(exifUpdateRunnable);
                viewPager.postDelayed(exifUpdateRunnable, 120);
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
        if (vibration != null) vibration.toggle(!adapter.isHdrActive(position));
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

    private void onBack(View view) {
        if (vibration != null) vibration.confirm();
        // Match system back: pop the nav graph; only finish when launched externally.
        if (navController != null && navController.navigateUp()) return;
        if (getActivity() != null) getActivity().finish();
    }

    private void onQuickCompare(View view) {
        if (galleryItems.size() >= 2) {
            if (vibration != null) vibration.confirm();
            NavController navController = Navigation.findNavController(view);
            Bundle b = new Bundle(2);
            int image1pos = viewPager.getCurrentItem();
            int image2pos = image1pos + 1;
            if (image1pos == galleryItems.size() - 1) { image2pos = image1pos; image1pos -= 1; }
            b.putInt(Constants.IMAGE1_KEY, image1pos);
            b.putInt(Constants.IMAGE2_KEY, image2pos);
            navController.navigate(R.id.action_imageViewerFragment_to_imageCompareFragment, b);
        } else {
            if (vibration != null) vibration.reject();
            Toast.makeText(getContext(), "No images to compare!", Toast.LENGTH_SHORT).show();
        }
    }

    private void onGalleryButtonClick(View view) {
        if (vibration != null) vibration.confirm();
        if (navController.getPreviousBackStackEntry() == null)
            navController.navigate(R.id.action_imageViewFragment_to_imageLibraryFragment);
        else navController.navigateUp();
    }

    private void onEditButtonClick(View view) {
        if (vibration != null) vibration.confirm();
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
                if (vibration != null) vibration.confirm();
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
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setMessage(R.string.sure_delete).setTitle(android.R.string.dialog_alert_title).setIcon(R.drawable.ic_delete).setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    if (vibration != null) vibration.confirm();
                    indexToDelete = viewPager.getCurrentItem();
                    GalleryFileOperations.deleteImageFiles(getActivity(), Collections.singletonList((ImageFile) galleryItems.get(indexToDelete).getFile()), this::handleImagesDeletedCallback);
                });
        BlurSupport.show(builder.create());
    }

    private void onShareButtonClick(View view) {
        if (vibration != null) vibration.confirm();
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
        if (vibration != null) vibration.toggle(isExifVisible);
        fragmentGalleryImageViewerBinding.setExifDialogVisible(isExifVisible);
        updateExif();
    }

    private void onDescriptionToggleClicked(View view) {
        if (fragmentGalleryImageViewerBinding == null || fragmentGalleryImageViewerBinding.exifLayout == null) return;
        View panel = fragmentGalleryImageViewerBinding.exifLayout.getRoot();
        View scroll = fragmentGalleryImageViewerBinding.exifLayout.exifDescriptionScroll;
        if (panel == null || scroll == null) return;
        Context context = view.getContext();
        isDescriptionExpanded = !isDescriptionExpanded;
        if (vibration != null) vibration.toggle(isDescriptionExpanded);
        int duration = Motion.durationMedium1(context);
        TimeInterpolator interpolator = Motion.emphasized(context);
        // Animate the panel's bounds so the description is revealed downwards;
        // ChangeBounds interpolates the panel and every child below the toggle.
        ViewGroup parent = panel.getParent() instanceof ViewGroup ? (ViewGroup) panel.getParent() : null;
        TransitionManager.beginDelayedTransition(parent != null ? parent : (ViewGroup) panel,
                new ChangeBounds().setDuration(duration).setInterpolator(interpolator));
        scroll.setVisibility(isDescriptionExpanded ? View.VISIBLE : View.GONE);
        startDescriptionBlurClock(panel, duration, interpolator);
        updateDescriptionToggleUi();
    }

    /**
     * The backdrop is a snapshot of the image behind the panel, so it has to be
     * recaptured at the panel's current, animated size every frame -- otherwise
     * the stale capture only stretches over the new area and the blur visibly
     * lands after the text. A clock with the transition's duration/interpolator
     * keeps the captures aligned with the bounds animation, then posts one last
     * capture a frame after the panel settles.
     */
    private void startDescriptionBlurClock(View panel, int duration, TimeInterpolator interpolator) {
        if (descriptionBlurClock != null) {
            descriptionBlurClock.cancel();
        }
        ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f).setDuration(duration);
        clock.setInterpolator(interpolator);
        clock.addUpdateListener(animation -> showExifBackdrop());
        clock.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                boolean finished = descriptionBlurClock == clock;
                if (finished) descriptionBlurClock = null;
                if (finished && getView() != null) {
                    panel.post(ImageViewerFragment.this::showExifBackdrop);
                }
            }
        });
        descriptionBlurClock = clock;
        clock.start();
    }

    /** Mirrors the model's description availability and the expanded state onto the panel. */
    private void syncDescriptionToggle() {
        if (fragmentGalleryImageViewerBinding == null || fragmentGalleryImageViewerBinding.exifLayout == null) return;
        View scroll = fragmentGalleryImageViewerBinding.exifLayout.exifDescriptionScroll;
        ImageView toggle = fragmentGalleryImageViewerBinding.exifLayout.exifDescriptionToggle;
        if (scroll == null || toggle == null) return;
        String description = exifDialogViewModel.getExifDataModel().getDescription();
        boolean available = description != null && !description.isEmpty();
        if (!available) isDescriptionExpanded = false;
        scroll.setVisibility(available && isDescriptionExpanded ? View.VISIBLE : View.GONE);
        toggle.setVisibility(available ? View.VISIBLE : View.GONE);
        updateDescriptionToggleUi();
    }

    private void updateDescriptionToggleUi() {
        if (fragmentGalleryImageViewerBinding == null || fragmentGalleryImageViewerBinding.exifLayout == null) return;
        ImageView toggle = fragmentGalleryImageViewerBinding.exifLayout.exifDescriptionToggle;
        if (toggle == null) return;
        Context context = toggle.getContext();
        toggle.animate()
                .rotation(isDescriptionExpanded ? 0f : 180f)
                .setDuration(Motion.durationShort4(context))
                .setInterpolator(Motion.emphasized(context))
                .start();
        toggle.setContentDescription(context.getString(isDescriptionExpanded
                ? R.string.exif_hide_description : R.string.exif_show_description));
    }

    private void onImageViewClicked(View view) {
        if (vibration != null) vibration.chromeToggle();
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
        // May run from a delayed post after navigation; never touch
        // requireContext() when detached.
        if (!isAdded() || viewPager == null) return;
        int position = viewPager.getCurrentItem();
        if (galleryItems != null && !galleryItems.isEmpty() && position < galleryItems.size()) {
            GalleryItem galleryItem = galleryItems.get(position);
            exifDialogViewModel.updateModel(requireContext().getContentResolver(), galleryItem.getFile());
            if (fragmentGalleryImageViewerBinding.getExifDialogVisible()) {
                exifDialogViewModel.updateHistogramView((ImageFile) galleryItem.getFile());
            }
        }
        syncDescriptionToggle();
        syncExifBlur();
    }

    /**
     * Shows, refits or clears the frosted-glass backdrop behind the EXIF
     * panel. The current page's preview bitmap is shown blurred (GPU
     * RenderEffect), clipped to the panel's rounded corners.
     */
    private void syncExifBlur() {
        if (!isAdded() || fragmentGalleryImageViewerBinding == null) {
            return;
        }
        boolean panelVisible = Boolean.TRUE.equals(fragmentGalleryImageViewerBinding.getExifDialogVisible());
        if (!panelVisible || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Below API 33 there is no mask-after-blur; keep the opaque rounded panel.
            clearExifBlur();
            return;
        }
        // Show the scrim right away: the first blurred frame lands a few ms later
        // and the opaque panel must not flash in between.
        if (fragmentGalleryImageViewerBinding.exifLayout != null) {
            ExifBackdropView backdrop = fragmentGalleryImageViewerBinding.exifLayout.exifBlurBackdrop;
            if (backdrop == null || backdrop.getVisibility() != View.VISIBLE) {
                View panel = fragmentGalleryImageViewerBinding.exifLayout.getRoot();
                if (panel != null) {
                    panel.setBackgroundResource(R.drawable.exif_background_scrim);
                }
            }
        }
        scheduleExifBlurShow();
    }

    /** Shows the backdrop as soon as possible: panel opened, page changed or panel resized. */
    private void scheduleExifBlurShow() {
        if (!isAdded() || fragmentGalleryImageViewerBinding == null) {
            return;
        }
        if (!Boolean.TRUE.equals(fragmentGalleryImageViewerBinding.getExifDialogVisible())) {
            return;
        }
        exifBlurRetries = 0;
        exifBlurHandler.removeCallbacks(exifBlurShowRunnable);
        exifBlurHandler.post(exifBlurShowRunnable);
    }

    /**
     * Puts the current page's preview bitmap behind the panel as frosted
     * glass. The small preview the gallery already keeps cached is blurred
     * entirely on the GPU (RenderEffect) — there is no view capture, nothing
     * tracks pan/zoom, and the bitmap is simply reused for the whole page.
     */
    private void showExifBackdrop() {
        if (!isAdded() || fragmentGalleryImageViewerBinding == null) {
            return;
        }
        if (!Boolean.TRUE.equals(fragmentGalleryImageViewerBinding.getExifDialogVisible())) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        View panel = fragmentGalleryImageViewerBinding.exifLayout.getRoot();
        ExifBackdropView backdrop = fragmentGalleryImageViewerBinding.exifLayout.exifBlurBackdrop;
        if (panel == null || backdrop == null) {
            return;
        }
        Bitmap preview = adapter != null && viewPager != null
                ? adapter.getPreviewBitmap(viewPager.getCurrentItem()) : null;
        if (panel.getWidth() <= 0 || panel.getHeight() <= 0
                || preview == null || preview.isRecycled()) {
            // Panel not laid out yet or the preview is still decoding: nudge the
            // decode and retry briefly.
            if (adapter != null && viewPager != null && preview == null) {
                adapter.requestPreview(viewPager.getCurrentItem());
            }
            if (exifBlurRetries++ < EXIF_BLUR_MAX_RETRIES) {
                exifBlurHandler.removeCallbacks(exifBlurShowRunnable);
                exifBlurHandler.postDelayed(exifBlurShowRunnable, EXIF_BLUR_RETRY_MS);
            }
            return;
        }
        exifBlurRetries = 0;
        if (!prepareExifBackdropEffect(panel.getWidth(), panel.getHeight())) {
            return;
        }
        panel.getLocationOnScreen(exifPanelLocation);
        setupExifBackdropTracking(preview, panel);
        exifWorkMatrix.set(exifBaseMatrix);
        // Apply the effect before showing the bitmap so the first visible frame
        // is already blurred (no sharp flash).
        backdrop.setRenderEffect(exifBackdropEffect);
        backdrop.setBackdropBitmap(preview);
        backdrop.setBackdropMatrix(exifBaseMatrix);
        backdrop.setVisibility(View.VISIBLE);
        if (!exifBackdropShown) {
            // Keep a rounded (transparent) background so the panel outline stays round.
            panel.setBackgroundResource(R.drawable.exif_background_transparent);
            exifBackdropShown = true;
        }
    }

    /**
     * Places the backdrop so its content matches the image currently behind
     * the panel EXACTLY: the base matrix is solved from the SSIV's live
     * coordinate mapping (orientation, zoom and pan included) at 1:1 scale —
     * no crop, no shrink, nothing offset. Areas of the panel beyond the image
     * fill from the bitmap's clamped edges (the letterbox bands they cover).
     * Falls back to a plain center-crop when the SSIV cannot map yet. Always
     * re-anchors gesture tracking afterwards.
     */
    private void setupExifBackdropTracking(Bitmap preview, View panel) {
        int panelW = panel.getWidth();
        int panelH = panel.getHeight();
        exifBackdropBmpW = preview.getWidth();
        exifBackdropBmpH = preview.getHeight();
        exifPanelW = panelW;
        exifPanelH = panelH;
        exifTrackPivot.set(panelW * 0.5f, panelH * 0.5f);
        panel.getLocationOnScreen(exifPanelLocation);
        CustomSSIV ssiv = getCurrentSSIV();
        boolean mapped = false;
        if (ssiv != null && ssiv.isReady() && ssiv.getSWidth() > 0 && ssiv.getSHeight() > 0
                && viewPager != null) {
            // Page origin from the ViewPager, never from the SSIV: during a
            // swipe the page (SSIV included) is transiently translated inside
            // the pager, so the SSIV's own screen location would bake the
            // swipe offset into the solved mapping. The pager itself is fixed.
            viewPager.getLocationOnScreen(exifSsivLocation);
            mapped = buildMappedBaseMatrix(ssiv,
                    exifPanelLocation[0] - exifSsivLocation[0],
                    exifPanelLocation[1] - exifSsivLocation[1]);
        }
        if (!mapped) {
            float cover = Math.max(panelW / exifBackdropBmpW, panelH / exifBackdropBmpH);
            exifBaseMatrix.reset();
            exifBaseMatrix.postScale(cover, cover);
            exifBaseMatrix.postTranslate((panelW - exifBackdropBmpW * cover) * 0.5f,
                    (panelH - exifBackdropBmpH * cover) * 0.5f);
        }
        anchorExifBackdropTracking();
    }

    /**
     * Solves the preview-bitmap → panel-local affine from three corner
     * correspondences of the SSIV's live mapping. The upright image corners
     * are identified numerically from the mapped source corners (the mapping
     * only rotates in 90° steps), so every EXIF orientation is handled.
     */
    private boolean buildMappedBaseMatrix(CustomSSIV ssiv, float panelOffX, float panelOffY) {
        float srcW = ssiv.getSWidth();
        float srcH = ssiv.getSHeight();
        PointF v00 = ssiv.sourceToViewCoord(0, 0);
        PointF v10 = ssiv.sourceToViewCoord(srcW, 0);
        PointF v01 = ssiv.sourceToViewCoord(0, srcH);
        PointF v11 = ssiv.sourceToViewCoord(srcW, srcH);
        if (v00 == null || v10 == null || v01 == null || v11 == null) {
            return false;
        }
        // Upright corners by their extremes in view space.
        PointF tl = extremalCorner(v00, v10, v01, v11, 1f, 1f);   // min x+y
        PointF tr = extremalCorner(v00, v10, v01, v11, -1f, 1f);  // min y-x
        PointF bl = extremalCorner(v00, v10, v01, v11, 1f, -1f);  // min x-y
        if (tl == tr || tl == bl || tr == bl) {
            return false;
        }
        // Preview corners (0,0), (bmpW,0), (0,bmpH) -> panel-local positions.
        float qtlX = tl.x - panelOffX, qtlY = tl.y - panelOffY;
        float f1X = (tr.x - panelOffX) - qtlX, f1Y = (tr.y - panelOffY) - qtlY;
        float f2X = (bl.x - panelOffX) - qtlX, f2Y = (bl.y - panelOffY) - qtlY;
        if (exifBackdropBmpW <= 0f || exifBackdropBmpH <= 0f) {
            return false;
        }
        exifBaseMatrix.setValues(new float[]{
                f1X / exifBackdropBmpW, f2X / exifBackdropBmpH, qtlX,
                f1Y / exifBackdropBmpW, f2Y / exifBackdropBmpH, qtlY,
                0f, 0f, 1f});
        return true;
    }

    /** The corner minimizing {@code sx*x + sy*y}. */
    private static PointF extremalCorner(PointF a, PointF b, PointF c, PointF d, float sx, float sy) {
        PointF best = a;
        float bestKey = a.x * sx + a.y * sy;
        for (PointF p : new PointF[]{b, c, d}) {
            float key = p.x * sx + p.y * sy;
            if (key < bestKey) {
                best = p;
                bestKey = key;
            }
        }
        return best;
    }

    /**
     * Records the content anchor the tracking follows: the source coordinate
     * currently at the panel's centre. Returns true when tracking is live
     * (a ready SSIV could map the coordinates).
     */
    private boolean anchorExifBackdropTracking() {
        exifTrackSsiv = null;
        if (fragmentGalleryImageViewerBinding == null || exifPanelW <= 0) {
            return false;
        }
        View panel = fragmentGalleryImageViewerBinding.exifLayout.getRoot();
        CustomSSIV ssiv = getCurrentSSIV();
        if (panel == null || ssiv == null || !ssiv.isReady() || viewPager == null) {
            return false;
        }
        panel.getLocationOnScreen(exifPanelLocation);
        // Pager origin, not the SSIV's (see setupExifBackdropTracking): stable
        // even while the page is mid-swipe.
        viewPager.getLocationOnScreen(exifSsivLocation);
        float cx = exifPanelLocation[0] + exifPanelW * 0.5f - exifSsivLocation[0];
        float cy = exifPanelLocation[1] + exifPanelH * 0.5f - exifSsivLocation[1];
        PointF src = ssiv.viewToSourceCoord(cx, cy);
        if (src == null) {
            return false;
        }
        exifTrackSsiv = ssiv;
        exifTrackAnchorSrc.set(src.x, src.y);
        exifTrackAnchorView.set(cx, cy);
        exifTrackBaseScale = ssiv.getScale() > 0f ? ssiv.getScale() : 1f;
        return true;
    }

    /**
     * Moves the frozen backdrop with the gesture: the already-blurred preview
     * bitmap is transformed, never recaptured — under the 32dp blur this is
     * indistinguishable from blurring the live view. Pan slides the bitmap
     * along the SSIV's live coordinate mapping; zoom scales it by the same
     * ratio around the anchor. Pans that run past the bitmap's bounds fill
     * from its clamped edges (invisible under the blur) until the settle
     * re-anchor re-centres the true mapping.
     */
    private void updateExifBackdropTransform() {
        if (exifTrackSsiv == null || fragmentGalleryImageViewerBinding == null
                || exifBackdropBmpW <= 0f) {
            return;
        }
        if (!Boolean.TRUE.equals(fragmentGalleryImageViewerBinding.getExifDialogVisible())) {
            return;
        }
        ExifBackdropView backdrop = fragmentGalleryImageViewerBinding.exifLayout.exifBlurBackdrop;
        if (backdrop == null || backdrop.getVisibility() != View.VISIBLE || !exifTrackSsiv.isReady()) {
            return;
        }
        PointF now = exifTrackSsiv.sourceToViewCoord(exifTrackAnchorSrc.x, exifTrackAnchorSrc.y);
        if (now == null) {
            return;
        }
        // The zoom ratio is tracked unclamped: the SSIV's own scale bounds
        // (minScale..2 absolute) are the only zoom limits, and clamping here
        // would descale the backdrop while the pan delta keeps tracking —
        // visible as drift at deep zoom on high-megapixel images.
        float ratio = exifTrackSsiv.getScale() / exifTrackBaseScale;
        if (!(ratio > 0f) || !Float.isFinite(ratio)) {
            return;
        }
        float dx = now.x - exifTrackAnchorView.x;
        float dy = now.y - exifTrackAnchorView.y;
        exifWorkMatrix.set(exifBaseMatrix);
        exifWorkMatrix.postScale(ratio, ratio, exifTrackPivot.x, exifTrackPivot.y);
        exifWorkMatrix.postTranslate(dx, dy);
        backdrop.setBackdropMatrix(exifWorkMatrix);
        exifBlurHandler.removeCallbacks(exifBlurSettleRunnable);
        exifBlurHandler.postDelayed(exifBlurSettleRunnable, EXIF_BLUR_REANCHOR_MS);
    }

    /**
     * Settle, {@link #EXIF_BLUR_REANCHOR_MS} after the last gesture event:
     * bakes the tracked transform into the base (so nothing pops) and resets
     * the anchor to the current framing.
     */
    private void reanchorExifBackdrop() {
        if (fragmentGalleryImageViewerBinding == null
                || !Boolean.TRUE.equals(fragmentGalleryImageViewerBinding.getExifDialogVisible())) {
            return;
        }
        ExifBackdropView backdrop = fragmentGalleryImageViewerBinding.exifLayout.exifBlurBackdrop;
        if (backdrop == null || backdrop.getVisibility() != View.VISIBLE || exifBackdropBmpW <= 0f) {
            return;
        }
        exifBaseMatrix.set(exifWorkMatrix);
        anchorExifBackdropTracking();
    }

    /**
     * Builds (or refreshes) the GPU effect that blurs the backdrop and then masks
     * it to the panel's rounded rectangle. Returns false when unavailable.
     */
    private boolean prepareExifBackdropEffect(int width, int height) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || width <= 0 || height <= 0) {
            return false;
        }
        if (exifMaskShader == null) {
            exifMaskShader = new RuntimeShader(EXIF_MASK_AGSL);
        }
        if (exifBackdropEffect == null || exifEffectWidth != width || exifEffectHeight != height) {
            exifMaskShader.setFloatUniform("size", width, height);
            exifMaskShader.setFloatUniform("radius",
                    getResources().getDimension(R.dimen.cam_panel_corner_radius));
            exifMaskShader.setFloatUniform("scrim",
                    ((EXIF_BLUR_SCRIM >> 16) & 0xFF) / 255f,
                    ((EXIF_BLUR_SCRIM >> 8) & 0xFF) / 255f,
                    (EXIF_BLUR_SCRIM & 0xFF) / 255f,
                    ((EXIF_BLUR_SCRIM >>> 24) & 0xFF) / 255f);
            float blurRadius = BlurSupport.dpToPx(requireContext(), EXIF_BLUR_RADIUS_DP);
            RenderEffect mask = RenderEffect.createRuntimeShaderEffect(exifMaskShader, "content");
            RenderEffect blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP);
            exifBackdropEffect = RenderEffect.createChainEffect(mask, blur);
            exifEffectWidth = width;
            exifEffectHeight = height;
        }
        return true;
    }

    private void clearExifBlur() {
        exifBlurHandler.removeCallbacks(exifBlurShowRunnable);
        exifBlurHandler.removeCallbacks(exifBlurSettleRunnable);
        exifTrackSsiv = null;
        exifBackdropBmpW = 0f;
        exifBackdropBmpH = 0f;
        if (fragmentGalleryImageViewerBinding != null && fragmentGalleryImageViewerBinding.exifLayout != null) {
            ExifBackdropView backdrop = fragmentGalleryImageViewerBinding.exifLayout.exifBlurBackdrop;
            if (backdrop != null) {
                backdrop.setVisibility(View.GONE);
                backdrop.setBackdropBitmap(null);
                BlurSupport.clearBlur(backdrop);
            }
            View panel = fragmentGalleryImageViewerBinding.exifLayout.getRoot();
            if (panel != null) {
                panel.setBackgroundResource(R.drawable.exif_background);
            }
        }
        exifBackdropShown = false;
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
        if (!isAdded()) return;
        if (isDeleted && indexToDelete >= 0) {
            if (vibration != null) vibration.confirm();
            galleryItems.remove(indexToDelete);
            seek_position=indexToDelete;
            if (!galleryItems.isEmpty()) initImageAdapter(galleryItems);
            updateExif();
            Toast.makeText(getContext(), R.string.image_deleted, Toast.LENGTH_SHORT).show();
            indexToDelete = -1;
            if (galleryItems.isEmpty()) { viewModel.setUpdatePending(true); navController.navigateUp(); }
        } else {
            if (vibration != null) vibration.reject();
            Toast.makeText(getContext(), "Deletion Failed!", Toast.LENGTH_SHORT).show();
        }
    }
}
