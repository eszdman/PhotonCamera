package com.eszdman.photoncamera.gallery.adapters;

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
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.ThumbnailSquareImageViewBinding;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.GridItemViewHolder> {

    private final ArrayList<File> selectedFiles = new ArrayList<>();
    private final ArrayList<GridItemViewHolder> selectedViewHolders = new ArrayList<>();
    private List<File> imageList;
    private boolean selectionStarted;
    private ImageSelectionListener imageSelectionListener;

    public ImageGridAdapter(List<File> imageList) {
        this.imageList = imageList;
    }

    public void setImageList(List<File> imageList) {
        this.imageList = imageList;
    }

    public ArrayList<File> getSelectedFiles() {
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
        final File file = imageList.get(position);
        setTagText(holder, file.getName());
        checkSelectable(holder);
        Glide
                .with(holder.itemView.getContext())
                .asBitmap()
                .load(file)
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .signature(new ObjectKey(file.getName() + file.lastModified()))
                        .override(200, 200)
                        .centerCrop()
                )
                .into(holder.thumbnailSquareImageViewBinding.squareImageView);

        holder.thumbnailSquareImageViewBinding.setClicklistener(view -> {
            if (selectionStarted) {
                if (selectedFiles.contains(file)) {
                    deselectFile(holder, file);
                } else {
                    selectFile(holder, file);
                }
            } else {
                Bundle b = new Bundle();
                b.putInt("imagePosition", position);
                NavController navController = Navigation.findNavController(view);
                navController.navigate(R.id.action_imageLibraryFragment_to_imageViewerFragment, b);
//            navController.setGraph(navController.getGraph(), b);
            }
        });

        holder.thumbnailSquareImageViewBinding.squareImageCardView.setOnLongClickListener(v -> {
            if (selectedFiles.contains(file)) {
                deselectFile(holder, file);
            } else {
                selectFile(holder, file);
            }
            Toast.makeText(v.getContext(), R.string.long_press_to_deselect,Toast.LENGTH_SHORT).show();
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

    private void selectFile(GridItemViewHolder holder, File file) {
        selectedFiles.add(file);
        selectedViewHolders.add(holder);
        selectionStarted = true;
        holder.thumbnailSquareImageViewBinding.selectionCircle.setSelected(true);
        ImageView imageView = holder.thumbnailSquareImageViewBinding.squareImageView;
        imageView.setScaleX(0.85f);
        imageView.setScaleY(0.85f);
        if (imageSelectionListener != null) {
            imageSelectionListener.onImageSelectionChanged(selectedFiles.size());
        }
        notifyDataSetChanged();
    }

    private void deselectFile(GridItemViewHolder holder, File file) {
        selectedFiles.remove(file);
        selectedViewHolders.remove(holder);
        holder.thumbnailSquareImageViewBinding.selectionCircle.setSelected(false);
        ImageView imageView = holder.thumbnailSquareImageViewBinding.squareImageView;
        imageView.setScaleX(1f);
        imageView.setScaleY(1f);
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
            holder.thumbnailSquareImageViewBinding.squareImageView.setScaleX(1.0f);
            holder.thumbnailSquareImageViewBinding.squareImageView.setScaleY(1.0f);
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