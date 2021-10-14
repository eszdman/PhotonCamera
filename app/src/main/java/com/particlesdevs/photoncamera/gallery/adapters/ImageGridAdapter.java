package com.particlesdevs.photoncamera.gallery.adapters;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.ThumbnailSquareImageViewBinding;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;

import org.apache.commons.io.FileUtils;

import java.util.ArrayList;
import java.util.List;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.IMAGE_POSITION_KEY;

/**
 * Created by Vibhor Srivastava on 03-Dec-2020
 */
public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.GridItemViewHolder> {

    private static final int SELECTION_ANIMATION_DURATION = 100;
    private static final float SELECTION_SCALE_DOWN_FACTOR = 0.8f;
    private final ArrayList<ImageFile> selectedFiles = new ArrayList<>();
    private final ArrayList<GridItemViewHolder> selectedViewHolders = new ArrayList<>();
    private List<ImageFile> imageList;
    private boolean selectionStarted;
    private ImageSelectionListener imageSelectionListener;

    public ImageGridAdapter(List<ImageFile> imageList) {
        this.imageList = imageList;
    }

    public void setImageList(List<ImageFile> imageList) {
        this.imageList = imageList;
    }

    public ArrayList<ImageFile> getSelectedFiles() {
        return selectedFiles;
    }

    public void setImageSelectionListener(ImageSelectionListener imageSelectionListener) {
        this.imageSelectionListener = imageSelectionListener;
    }

    @NonNull
    @Override
    public GridItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ThumbnailSquareImageViewBinding thumbnailSquareImageViewBinding = ThumbnailSquareImageViewBinding.inflate(layoutInflater, parent, false);
        return new GridItemViewHolder(thumbnailSquareImageViewBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull GridItemViewHolder holder, int position) {
        final ImageFile imageFile = imageList.get(position);
        setTagText(holder, imageFile.getDisplayName());
        checkSelectable(holder);
        Glide
                .with(holder.itemView.getContext())
                .asBitmap()
                .load(imageFile.getFileUri())
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .signature(new ObjectKey(imageFile.getDisplayName() + imageFile.getLastModified()))
                        .override(200, 200)
                        .centerCrop()
                )
                .into(holder.thumbnailSquareImageViewBinding.squareImageView);

        holder.thumbnailSquareImageViewBinding.setClicklistener(view -> {
            if (selectionStarted) {
                if (selectedFiles.contains(imageFile)) {
                    deselectFile(holder, imageFile);
                } else {
                    selectFile(holder, imageFile);
                }
            } else {
                Bundle b = new Bundle();
                b.putInt(IMAGE_POSITION_KEY, position);
                NavController navController = Navigation.findNavController(view);
                navController.navigate(R.id.action_imageLibraryFragment_to_imageViewerFragment, b);
//            navController.setGraph(navController.getGraph(), b);
            }
        });

        holder.thumbnailSquareImageViewBinding.squareImageCardView.setOnLongClickListener(v -> {
            if (selectedFiles.contains(imageFile)) {
                deselectFile(holder, imageFile);
            } else {
                selectFile(holder, imageFile);
            }
            Toast.makeText(v.getContext(), R.string.long_press_to_deselect, Toast.LENGTH_SHORT).show();
            return true;
        });

    }

    private void setTagText(GridItemViewHolder holder, String fileName) {
        if (FileUtils.getExtension(fileName).equalsIgnoreCase("dng")) {
            holder.thumbnailSquareImageViewBinding.thumbTagText.setText("RAW");
        } else {
            holder.thumbnailSquareImageViewBinding.thumbTagText.setText("");
        }
    }

    private void checkSelectable(GridItemViewHolder holder) {
        holder.thumbnailSquareImageViewBinding.selectionCircle.setVisibility(selectionStarted ? ViewGroup.VISIBLE : ViewGroup.GONE);
    }

    private void selectFile(GridItemViewHolder holder, ImageFile imageFile) {
        selectedFiles.add(imageFile);
        selectedViewHolders.add(holder);
        selectionStarted = true;
        holder.thumbnailSquareImageViewBinding.selectionCircle.setSelected(true);
        ImageView imageView = holder.thumbnailSquareImageViewBinding.squareImageView;
        imageView.animate().setDuration(SELECTION_ANIMATION_DURATION).scaleX(SELECTION_SCALE_DOWN_FACTOR);
        imageView.animate().setDuration(SELECTION_ANIMATION_DURATION).scaleY(SELECTION_SCALE_DOWN_FACTOR);
        if (imageSelectionListener != null) {
            imageSelectionListener.onImageSelectionChanged(selectedFiles.size());
        }
        notifyDataSetChanged();
    }

    private void deselectFile(GridItemViewHolder holder, ImageFile imageFile) {
        selectedFiles.remove(imageFile);
        selectedViewHolders.remove(holder);
        holder.thumbnailSquareImageViewBinding.selectionCircle.setSelected(false);
        ImageView imageView = holder.thumbnailSquareImageViewBinding.squareImageView;
        imageView.animate().setDuration(SELECTION_ANIMATION_DURATION).scaleX(1f);
        imageView.animate().setDuration(SELECTION_ANIMATION_DURATION).scaleY(1f);
        if (imageSelectionListener != null) {
            imageSelectionListener.onImageSelectionChanged(selectedFiles.size());
        }
        if (selectedFiles.isEmpty()) {
            selectionStarted = false;
            if (imageSelectionListener != null) {
                imageSelectionListener.onImageSelectionStopped();
            }
            notifyDataSetChanged();
        }
    }

    public void deselectAll() {
        selectedFiles.clear();
        for (GridItemViewHolder holder : selectedViewHolders) {
            holder.thumbnailSquareImageViewBinding.squareImageView.animate().setDuration(SELECTION_ANIMATION_DURATION).scaleX(1f);
            holder.thumbnailSquareImageViewBinding.squareImageView.animate().setDuration(SELECTION_ANIMATION_DURATION).scaleY(1f);
            holder.thumbnailSquareImageViewBinding.selectionCircle.setSelected(false);
        }
        selectedViewHolders.clear();
        selectionStarted = false;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return imageList != null ? imageList.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    public interface ImageSelectionListener {
        void onImageSelectionChanged(int numOfSelectedFiles);

        void onImageSelectionStopped();
    }

    public static class GridItemViewHolder extends RecyclerView.ViewHolder {
        private final ThumbnailSquareImageViewBinding thumbnailSquareImageViewBinding;

        public GridItemViewHolder(ThumbnailSquareImageViewBinding squareImageViewBinding) {
            super(squareImageViewBinding.getRoot());
            this.thumbnailSquareImageViewBinding = squareImageViewBinding;
        }
    }
}