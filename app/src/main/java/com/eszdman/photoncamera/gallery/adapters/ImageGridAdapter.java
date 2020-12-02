package com.eszdman.photoncamera.gallery.adapters;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.databinding.ThumbnailSquareImageViewBinding;
import com.eszdman.photoncamera.gallery.model.GridThumbnailModel;
import rapid.decoder.BitmapDecoder;

import java.io.File;
import java.util.List;

public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.GridItemViewHolder> {

    private final List<File> imageList;


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
        final File model = imageList.get(position);
        Handler handler = new Handler(Looper.getMainLooper(), msg -> {
            holder.bind((GridThumbnailModel) msg.obj);
            return true;
        });
        Thread th = new Thread(() -> {
            Bitmap preview = BitmapDecoder.from(Uri.fromFile(model)).scale(200, 0).decode();
            if (preview != null) {
                Message msg = new Message();
                msg.obj = new GridThumbnailModel(preview);
                handler.sendMessage(msg);
            }
        });
        th.start();
        holder.thumbnailSquareImageViewBinding.squareImageView.setOnClickListener(view -> {
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

}