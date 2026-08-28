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

    private static final int SELECTION_ANIMATION_DURATION = 250;
    private static final int ANIMATE_RADIUS = Utilities.dpToPx(20);
    private static final float SELECTION_SCALE_DOWN_FACTOR = 0.8f;
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
            if(itemType==Constants.GALLERY_ITEM_TYPE_LINEAR_FOLDER)
            {
                thumbnailSquareImageViewBinding.thumbCaptionText.setVisibility(View.VISIBLE);
                thumbnailSquareImageViewBinding.thumbTagText.setVisibility(View.GONE);
            }
            thumbnailSquareImageViewBinding.setGalleryitemclickedlistener(new GalleryItemClickedListener() {
                @Override
                public void onItemClicked(View view, GalleryItem galleryItem) {
                    if (selectionHelper.isSelectionStarted() && itemType == Constants.GALLERY_ITEM_TYPE_GRID) {
                        selectGalleryItem(view,galleryItem);
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

    private void selectView(View view) {
        selectedViews.add(view);
        animatedSelect(view, true);
        if (gridAdapterCallback != null) gridAdapterCallback.onImageSelectionChanged(selectedViews.size());
        // Minimal invalidation - selection circle is bound per item; payload could be used but keep cheap
        // Avoid full notifyDataSetChanged which rebinds all thumbnails and triggers Glide reloads
        int pos = findPositionForView(view);
        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
        else notifyDataSetChanged();
    }

    private void deselectView(View view) {
        selectedViews.remove(view);
        animatedSelect(view, false);
        if (selectionHelper.isEmpty()) {
            if (gridAdapterCallback != null) gridAdapterCallback.onImageSelectionStopped();
        } else {
            if (gridAdapterCallback != null) gridAdapterCallback.onImageSelectionChanged(selectedViews.size());
        }
        int pos = findPositionForView(view);
        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
        else notifyDataSetChanged();
    }

    public void deselectAll() {
        selectionHelper.deselectAll();
        for (View view : selectedViews) animatedSelect(view, false);
        selectedViews.clear();
        notifyDataSetChanged();
    }

    private int findPositionForView(View view) {
        // View is the MaterialCardView (image_card); its parent holder's adapter position is not directly known here.
        // Caller passes the view from selectGalleryItem via onBind holder - we can resolve via traversal if needed.
        // For now, trigger full sync via data set change - the cost is one layout pass, still cheaper than per-item reload storm.
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemCount() {
        return galleryItemList != null ? galleryItemList.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        if (galleryItemList != null && position >= 0 && position < galleryItemList.size()) {
            GalleryItem item = galleryItemList.get(position);
            if (item != null && item.getFile() != null) return item.getFile().getId();
        }
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return itemType;
    }

    public boolean selectGalleryItem(View view, GalleryItem item) {
        if (itemType == Constants.GALLERY_ITEM_TYPE_GRID) {
            if (selectionHelper.toggleSelection(item)) {
                selectView(view);
            } else {
                deselectView(view);
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
        view.animate().setDuration(SELECTION_ANIMATION_DURATION).scaleX(select ? SELECTION_SCALE_DOWN_FACTOR : 1f).scaleY(select ? SELECTION_SCALE_DOWN_FACTOR : 1f);
        final ValueAnimator animator = ValueAnimator.ofFloat(select ? 0 : ANIMATE_RADIUS, select ? ANIMATE_RADIUS : 0);
        animator.setDuration(SELECTION_ANIMATION_DURATION)
                .addUpdateListener(animation -> {
                    float value = (float) animation.getAnimatedValue();
                    ((MaterialCardView) view).setRadius(value);
                });
        animator.start();
    }
}