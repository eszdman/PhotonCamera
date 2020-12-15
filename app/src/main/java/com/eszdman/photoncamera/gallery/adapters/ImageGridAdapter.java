package com.eszdman.photoncamera.gallery.adapters;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
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
import java.util.List;

public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.GridItemViewHolder> {

    private final List<File> imageList;

    public ImageGridAdapter(List<File> imageList) {
        this.imageList = imageList;
    }

    @Override
    public GridItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ThumbnailSquareImageViewBinding thumbnailSquareImageViewBinding = ThumbnailSquareImageViewBinding.inflate(layoutInflater, parent, false);
        return new GridItemViewHolder(thumbnailSquareImageViewBinding);
    }

    @Override
    public void onBindViewHolder(GridItemViewHolder holder, int position) {
        final File file = imageList.get(position);
        if (FileUtils.getExtension(file.getName()).equalsIgnoreCase("dng")) {
            holder.thumbnailSquareImageViewBinding.thumbTagText.setText("RAW");
        }
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
            Bundle b = new Bundle();
            b.putInt("imagePosition", position);
            NavController navController = Navigation.findNavController(view);
            navController.navigate(R.id.action_imageLibraryFragment_to_imageViewerFragment, b);
//            navController.setGraph(navController.getGraph(), b);
        });
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

    public static class GridItemViewHolder extends RecyclerView.ViewHolder {
        private final ThumbnailSquareImageViewBinding thumbnailSquareImageViewBinding;

        public GridItemViewHolder(ThumbnailSquareImageViewBinding squareImageViewBinding) {
            super(squareImageViewBinding.getRoot());
            this.thumbnailSquareImageViewBinding = squareImageViewBinding;
        }
    }
}