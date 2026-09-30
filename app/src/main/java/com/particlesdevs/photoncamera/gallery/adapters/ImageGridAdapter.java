package com.particlesdevs.photoncamera.gallery.adapters;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.databinding.ThumbnailSquareImageViewBinding;
import com.particlesdevs.photoncamera.gallery.helper.Constants;
import com.particlesdevs.photoncamera.gallery.interfaces.GalleryItemClickedListener;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.model.SelectionHelper;
import com.particlesdevs.photoncamera.util.Utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Vibhor Srivastava on 03-Dec-2020
 */
public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.GridItemViewHolder> {

    private static final int RESTING_RADIUS = Utilities.dpToPx(12);
    private static final int ANIMATE_RADIUS = Utilities.dpToPx(20);
    private static final int SELECTION_STROKE_WIDTH = Utilities.dpToPx(2);
    private static final float SELECTION_SCALE_DOWN_FACTOR = 0.8f;
    /**
     * Payload for {@link #notifyItemChanged(int, Object)} when only the
     * selection state changed: the payload rebind touches the selection circle
     * and skips the DataBinding pass, so no thumbnail is requested again.
     */
    private static final String PAYLOAD_SELECTION = "gallery_selection";
    private final ArrayList<View> selectedViews = new ArrayList<>();
    private final SelectionHelper<GalleryItem> selectionHelper = new SelectionHelper<>();
    private final int itemType;
    private List<GalleryItem> galleryItemList;
    private GridAdapterCallback gridAdapterCallback;

    public ImageGridAdapter(List<GalleryItem> galleryItemList, int itemType) {
        this.galleryItemList = galleryItemList;
        this.itemType = itemType;
        setHasStableIds(true);
    }

    public void setGalleryItemList(List<GalleryItem> galleryItemList) {
        this.galleryItemList = galleryItemList;
    }

    @Override
    public void onViewRecycled(@NonNull GridItemViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.binding instanceof ThumbnailSquareImageViewBinding) {
            ThumbnailSquareImageViewBinding b = (ThumbnailSquareImageViewBinding) holder.binding;
            // Cancel Glide thumbnail load and free drawable to avoid flash on fast scroll
            try {
                com.particlesdevs.photoncamera.gallery.binding.CustomBinding.clearImage(b.squareImageView);
            } catch (Exception ignored) {}
            b.squareImageView.setImageDrawable(null);
        }
        holder.itemView.animate().cancel();
    }

    public ArrayList<GalleryItem> getSelectedItems() {
        return selectionHelper.getSelectedItems();
    }

    public void setGridAdapterCallback(GridAdapterCallback gridAdapterCallback) {
        this.gridAdapterCallback = gridAdapterCallback;
    }

    @NonNull
    @Override
    public GridItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ThumbnailSquareImageViewBinding thumbnailSquareImageViewBinding = ThumbnailSquareImageViewBinding.inflate(layoutInflater, parent, false);
        if (itemType == Constants.GALLERY_ITEM_TYPE_LINEAR || itemType == Constants.GALLERY_ITEM_TYPE_LINEAR_FOLDER) {
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(dpToPx(70), dpToPx(70));
            layoutParams.setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4));
            thumbnailSquareImageViewBinding.getRoot().setLayoutParams(layoutParams);
        }
        return new GridItemViewHolder(thumbnailSquareImageViewBinding);
    }

    private int dpToPx(int dp) {
        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    @Override
    public void onBindViewHolder(@NonNull GridItemViewHolder holder, int position) {
        final GalleryItem galleryItem = galleryItemList.get(position);
        if (holder.binding instanceof ThumbnailSquareImageViewBinding) {

            ThumbnailSquareImageViewBinding thumbnailSquareImageViewBinding = (ThumbnailSquareImageViewBinding) holder.binding;
            thumbnailSquareImageViewBinding.selectionCircle.setVisibility(selectionHelper.isSelectionStarted() ? View.VISIBLE : View.GONE);
            thumbnailSquareImageViewBinding.setGalleryitem(galleryItem);
            thumbnailSquareImageViewBinding.videoPlayOverlay.setVisibility(galleryItem.isVideo() ? View.VISIBLE : View.GONE);
            if(itemType==Constants.GALLERY_ITEM_TYPE_LINEAR_FOLDER)
            {
                thumbnailSquareImageViewBinding.thumbCaptionText.setVisibility(View.VISIBLE);
                thumbnailSquareImageViewBinding.thumbTagText.setVisibility(View.GONE);
            }
            thumbnailSquareImageViewBinding.setGalleryitemclickedlistener(new GalleryItemClickedListener() {
                @Override
                public void onItemClicked(View view, GalleryItem galleryItem) {
                    if (selectionHelper.isSelectionStarted() && itemType == Constants.GALLERY_ITEM_TYPE_GRID) {
                        selectGalleryItem(view, galleryItem, holder.getBindingAdapterPosition());
                    } else {
                        gridAdapterCallback.onItemClicked(holder.getAbsoluteAdapterPosition(), view, galleryItem);
                    }
                }

                @Override
                public boolean onItemLongClicked(View view, GalleryItem galleryItem) {
                    return false;
                }
            });
        }
    }

    @Override
    public void onBindViewHolder(@NonNull GridItemViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            // Selection-only change: refresh the circle without re-running the
            // DataBinding executes, which would re-request every thumbnail.
            if (holder.binding instanceof ThumbnailSquareImageViewBinding) {
                ThumbnailSquareImageViewBinding binding = (ThumbnailSquareImageViewBinding) holder.binding;
                GalleryItem item = position >= 0 && position < galleryItemList.size()
                        ? galleryItemList.get(position) : null;
                binding.selectionCircle.setVisibility(selectionHelper.isSelectionStarted() ? View.VISIBLE : View.GONE);
                binding.selectionCircle.setSelected(item != null && item.isChecked());
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void selectView(View view, int position) {
        selectedViews.add(view);
        animatedSelect(view, true);
        Vibration vibration = PhotonCamera.getVibration();
        if (vibration != null) vibration.select();
        if (gridAdapterCallback != null) gridAdapterCallback.onImageSelectionChanged(selectedViews.size());
        if (selectedViews.size() == 1) {
            // Selection just started: every cell shows its (empty) circle, so
            // the whole grid must be invalidated - with the selection payload
            // only, so no thumbnail is re-requested.
            notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        } else {
            notifySelectionChanged(position);
        }
    }

    private void deselectView(View view, int position) {
        selectedViews.remove(view);
        animatedSelect(view, false);
        Vibration vibration = PhotonCamera.getVibration();
        if (vibration != null) vibration.deselect();
        if (selectionHelper.isEmpty()) {
            // Last item deselected: reset the selection state and hide every
            // circle. onImageSelectionStopped() then sees an idle adapter and
            // only restores the chrome.
            selectionHelper.deselectAll();
            if (gridAdapterCallback != null) gridAdapterCallback.onImageSelectionStopped();
            notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        } else {
            if (gridAdapterCallback != null) gridAdapterCallback.onImageSelectionChanged(selectedViews.size());
            notifySelectionChanged(position);
        }
    }

    private void notifySelectionChanged(int position) {
        if (position >= 0 && position < getItemCount()) {
            notifyItemChanged(position, PAYLOAD_SELECTION);
        } else {
            // Position unknown: still avoid a full rebind, the payload pass is
            // limited to the selection circle.
            notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        }
    }

    public void deselectAll() {
        if (selectedViews.isEmpty() && selectionHelper.isEmpty() && !selectionHelper.isSelectionStarted()) {
            // Nothing is selected: called on every navigation change, don't
            // invalidate the grid for a no-op.
            return;
        }
        selectionHelper.deselectAll();
        for (View view : selectedViews) animatedSelect(view, false);
        selectedViews.clear();
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
    }

    @Override
    public int getItemCount() {
        return galleryItemList != null ? galleryItemList.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        if (galleryItemList != null && position >= 0 && position < galleryItemList.size()) {
            GalleryItem item = galleryItemList.get(position);
            // Image and video MediaStore id spaces can collide: fold the
            // content URI (images vs videos table) into the stable id.
            if (item != null && item.getFile() != null && item.getFile().getFileUri() != null) {
                return (((long) item.getFile().getFileUri().hashCode()) << 32)
                        | (item.getFile().getId() & 0xffffffffL);
            }
        }
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return itemType;
    }

    public boolean selectGalleryItem(View view, GalleryItem item, int position) {
        if (itemType == Constants.GALLERY_ITEM_TYPE_GRID) {
            if (selectionHelper.toggleSelection(item)) {
                selectView(view, position);
            } else {
                deselectView(view, position);
            }
            return true;
        }
        return false;
    }


    public interface GridAdapterCallback {
        void onItemClicked(int position, View view, GalleryItem galleryItem);

        void onImageSelectionChanged(int numOfSelectedFiles);

        void onImageSelectionStopped();
    }

    public static class GridItemViewHolder extends RecyclerView.ViewHolder {
        private final ViewBinding binding;

        public GridItemViewHolder(ViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public ViewBinding getBinding() {
            return binding;
        }
    }

    private void animatedSelect(View view, boolean select) {
        view.animate().setDuration(Motion.durationShort4(view.getContext()))
                .setInterpolator(Motion.emphasized(view.getContext()))
                .scaleX(select ? SELECTION_SCALE_DOWN_FACTOR : 1f).scaleY(select ? SELECTION_SCALE_DOWN_FACTOR : 1f);
        MaterialCardView card = (MaterialCardView) view;
        card.setStrokeColor(MaterialColors.getColor(card, R.attr.colorPrimary, 0));
        card.setStrokeWidth(select ? SELECTION_STROKE_WIDTH : 0);
        final ValueAnimator animator = ValueAnimator.ofFloat(select ? RESTING_RADIUS : ANIMATE_RADIUS, select ? ANIMATE_RADIUS : RESTING_RADIUS);
        animator.setDuration(Motion.durationShort4(view.getContext()));
        animator.setInterpolator(Motion.emphasized(view.getContext()));
        animator.addUpdateListener(animation -> {
                    float value = (float) animation.getAnimatedValue();
                    card.setRadius(value);
                });
        animator.start();
    }
}