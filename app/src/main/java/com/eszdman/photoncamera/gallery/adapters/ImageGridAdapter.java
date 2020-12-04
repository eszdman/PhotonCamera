package com.eszdman.photoncamera.gallery.adapters;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.ThumbnailSquareImageViewBinding;
import com.eszdman.photoncamera.gallery.model.GridThumbnailModel;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
        Handler handler = new Handler(Looper.getMainLooper(), msg -> {
            holder.bind((GridThumbnailModel) msg.obj);
            return true;
        });
        Thread th = new Thread(() -> {
            Message msg = new Message();
            try {
                Bitmap preview = Glide
                        .with(holder.itemView.getContext())
                        .asBitmap()
                        .load(file)
                        .submit(200, 0)
                        .get();
                msg.obj = new GridThumbnailModel(preview);
                handler.sendMessage(msg);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        th.start();
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

        public void bind(GridThumbnailModel gridThumbnailModel) {
            thumbnailSquareImageViewBinding.setThumbnailmodel(gridThumbnailModel);
            thumbnailSquareImageViewBinding.executePendingBindings();
        }
    }
}