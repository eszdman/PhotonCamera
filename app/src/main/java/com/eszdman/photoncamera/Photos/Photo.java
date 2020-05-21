package com.eszdman.photoncamera.Photos;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.eszdman.photoncamera.MainActivity;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Settings;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import de.hdodenhof.circleimageview.CircleImageView;

public class Photo {
    public static Photo instance;
    public Photo(){
        instance = this;
    }
    Intent imgintent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
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
    public void ShowPhoto(){
        if(Settings.instance.lastpic != null){
            ShowPhoto(new File(Settings.instance.lastpic));
        }
    }
    static class CompareFilesByDate implements Comparator<File> {
        @Override
        public int compare(File lhs, File rhs) {
            return Long.signum(rhs.lastModified() - lhs.lastModified());
        }
    }
    public void SaveImg(File in){
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            boolean wasnull = Settings.instance.lastpic == null;
            Settings.instance.lastpic = in.getAbsolutePath();
            if(wasnull) Settings.instance.saveSettings();
            Uri contentUri = Uri.fromFile(in);
            mediaScanIntent.setData(contentUri);
            MainActivity.act.sendBroadcast(mediaScanIntent);
            File outputDir = MainActivity.act.getCacheDir();
            File outputFile = null;
        try {    outputFile = File.createTempFile("bitmap", "jpg", outputDir);
        } catch (IOException e) {e.printStackTrace();}
        try {
            CircleImageView button = MainActivity.act.findViewById(R.id.ImageOut);
            button.setImageURI(contentUri);

        } catch (Exception e){}
    }
}