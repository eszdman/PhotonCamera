package com.particlesdevs.photoncamera.gallery.helper;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.particlesdevs.photoncamera.gallery.files.MediaFile;
import com.particlesdevs.photoncamera.util.BurstArchiveStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GalleryShare {
    private GalleryShare() {}

    public static Intent createIntent(List<? extends MediaFile> files) {
        if (files.isEmpty()) throw new IllegalArgumentException("No files to share");
        ArrayList<Uri> uris = new ArrayList<>();
        String type = mimeType(files.get(0));
        boolean onlyImages = true;
        boolean sameType = true;
        for (MediaFile file : files) {
            String fileType = mimeType(file);
            onlyImages &= fileType.startsWith("image/");
            sameType &= type.equals(fileType);
            uris.add(file.getFileUri());
        }
        if (!sameType) type = onlyImages ? "image/*" : "*/*";
        Intent intent = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        if (uris.size() == 1) intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.setType(type);
        ClipData clips = new ClipData("Gallery files", new String[]{type}, new ClipData.Item(uris.get(0)));
        for (int i = 1; i < uris.size(); i++) clips.addItem(new ClipData.Item(uris.get(i)));
        intent.setClipData(clips);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private static String mimeType(MediaFile file) {
        if (file.isZip()) return BurstArchiveStore.MIME_TYPE;
        String name = file.getDisplayName();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return type == null ? "image/*" : type;
    }
}
