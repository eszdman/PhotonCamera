package com.particlesdevs.photoncamera.gallery.files;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.particlesdevs.photoncamera.gallery.interfaces.ImagesDeletedCallback;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by Vibhor Srivastava on October 13, 2021
 */
public class GalleryFileOperations {
    public static final int REQUEST_PERM_DELETE = 1010;
    private static final String[] INCLUDED_IMAGE_FOLDERS = new String[]{"%DCIM/PhotonCamera/%", "%DCIM/PhotonCamera/Raw/%", "%DCIM/Camera/%"};

    public static List<ImageFile> fetchAllImageFiles(ContentResolver contentResolver) {
        ArrayList<ImageFile> images = new ArrayList<>();
        String[] projection = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.SIZE};

        String selectionColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Images.Media.RELATIVE_PATH : MediaStore.Images.Media.DATA;
        String selection = selectionColumn + " like ? OR " + selectionColumn + " like ? OR " + selectionColumn + " like ?";
        String[] selectionArgs = INCLUDED_IMAGE_FOLDERS;

        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        final Cursor cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder);

        if (cursor != null) {

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                String displayName = cursor.getString(displayNameColumn);
                long dateModified = TimeUnit.SECONDS.toMillis(cursor.getLong(dateModifiedColumn));
                long size = cursor.getLong(sizeColumn);

                ImageFile image = new ImageFile(id, contentUri, displayName, dateModified, size);
                images.add(image);
            }
            cursor.close();
        }
        return images;
    }

    @Nullable
    public static ImageFile fetchLatestImage(ContentResolver contentResolver) {
        ImageFile imageFile = null;

        String[] projection = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.SIZE};

        String selectionColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Images.Media.RELATIVE_PATH : MediaStore.Images.Media.DATA;
        String selection = selectionColumn + " like ? OR " + selectionColumn + " like ? OR " + selectionColumn + " like ?";
        String[] selectionArgs = INCLUDED_IMAGE_FOLDERS;

        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        final Cursor cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder);
        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(idColumn);
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                String displayName = cursor.getString(displayNameColumn);
                long dateModified = TimeUnit.SECONDS.toMillis(cursor.getLong(dateModifiedColumn));
                long size = cursor.getLong(sizeColumn);
                imageFile = new ImageFile(id, contentUri, displayName, dateModified, size);
            }
            cursor.close();
        }
        return imageFile;
    }

    public static Uri createNewImageFile(ContentResolver contentResolver, String relativePath, String newImageName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, newImageName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, URLConnection.guessContentTypeFromName(newImageName));
        String column = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA;
        values.put(column, relativePath);
        return contentResolver.insert(MediaStore.Files.getContentUri("external"), values);
    }

    public static void deleteImageFiles(Activity activity, List<ImageFile> toDelete, ImagesDeletedCallback deletedCallback) {
        List<Uri> toDeleteUriList = toDelete.stream().map(ImageFile::getFileUri).collect(Collectors.toList());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            PendingIntent pi = MediaStore.createDeleteRequest(activity.getContentResolver(), toDeleteUriList);
            try {
                ActivityCompat.startIntentSenderForResult(activity, pi.getIntentSender(), REQUEST_PERM_DELETE, null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
                deletedCallback.deleted(false);
            }
        } else {
            ContentResolver contentResolver = activity.getContentResolver();
            for (ImageFile file : toDelete) {
                try {
                    contentResolver.delete(file.getFileUri(), MediaStore.Images.Media._ID + "= ?", new String[]{String.valueOf(file.getId())});
                } catch (SecurityException e) {
                    e.printStackTrace();
                    deletedCallback.deleted(false);
                    return;
                }
            }
            deletedCallback.deleted(true);
        }
    }


}
