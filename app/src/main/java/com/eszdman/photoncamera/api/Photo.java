package com.eszdman.photoncamera.api;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;

import androidx.loader.content.CursorLoader;

import com.eszdman.photoncamera.ui.MainActivity;

import java.io.File;
import java.util.Comparator;

import rapid.decoder.BitmapDecoder;

import static com.eszdman.photoncamera.gallery.GalleryActivity.activity;

public class Photo {
    public static Photo instance;
    private static Handler galleryHandler;
    static class GalleryHandler extends Handler {
        GalleryHandler() {
        }
        @Override
        public void handleMessage(Message msg)
        {
            Uri uri = null;
            Bitmap bmp = null;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bmp = (Bitmap) msg.obj;
            } else{
                uri = (Uri)msg.obj;
            }
            try {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Interface.i.cameraui.galleryImageButton.setImageBitmap(bmp);
                } else {
                    Interface.i.cameraui.galleryImageButton.setImageURI(uri);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public Photo() {
        instance = this;
        galleryHandler = new GalleryHandler();
    }
    //Intent imageIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
    public void ShowPhoto(File imageFile) {
        String mediaId = "";
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
        };
        Cursor cursor = MainActivity.act.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
            String name = cursor.getString((cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME)));
            if (name.equals(imageFile.getName())) {
                mediaId = cursor.getString((cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID)));
                break;
            }
        }

        Uri mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        if (!mediaId.equals("")) {
            mediaUri = mediaUri.buildUpon()
                    .authority("media")
                    .appendPath(mediaId)
                    .build();
        }
        Log.d("TagInfo", "Uri:  " + mediaUri);
        Intent intent = new Intent(Intent.ACTION_VIEW, mediaUri);
        MainActivity.act.startActivity(intent);
    }

    public void ShowPhoto() {
        if (Interface.i.settings.lastPicture != null) {
            ShowPhoto(new File(Interface.i.settings.lastPicture));
        }
    }

    static class CompareFilesByDate implements Comparator<File> {
        @Override
        public int compare(File lhs, File rhs) {
            return Long.signum(rhs.lastModified() - lhs.lastModified());
        }
    }
    public void SaveImg(File in) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        boolean wasNull = Interface.i.settings.lastPicture == null;
        Interface.i.settings.lastPicture = in.getAbsolutePath();
        if (wasNull) Interface.i.settings.save();
        Uri contentUri = Uri.fromFile(in);
        try {
            Bitmap thumb = null;
            Message uriMessage = new Message();
            thumb = BitmapDecoder.from(Uri.fromFile(in)).scaleBy(0.1f).decode();
            uriMessage.obj = thumb;
            galleryHandler.sendMessage(uriMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mediaScanIntent.setData(contentUri);
        MainActivity.act.sendBroadcast(mediaScanIntent);
    }
}