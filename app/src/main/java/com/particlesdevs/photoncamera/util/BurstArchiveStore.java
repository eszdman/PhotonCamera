package com.particlesdevs.photoncamera.util;

import android.content.Context;
import android.os.Build;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/** Saves burst ZIPs through the same DCIM folder grant used for RAW video. */
public final class BurstArchiveStore {
    public static final String MIME_TYPE = "application/zip";

    private BurstArchiveStore() {}

    public static DocumentFile getRawFolder(Context context, boolean create) {
        DocumentFile photon = SimpleStorageHelper.getPhotonCameraFolder(context);
        if (photon != null) {
            DocumentFile raw = photon.findFile("Raw");
            return raw != null || !create ? raw : photon.createDirectory("Raw");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (create) FileManager.sPHOTON_RAW_DIR.mkdirs();
            return DocumentFile.fromFile(FileManager.sPHOTON_RAW_DIR);
        }
        return null;
    }

    public static DocumentFile save(Context context, String stem, int frameCount,
                                     BurstArchive.FrameWriter writer) throws IOException {
        DocumentFile folder = getRawFolder(context, true);
        if (folder == null || !folder.isDirectory()) {
            throw new IOException("No folder access to DCIM/PhotonCamera/Raw");
        }
        // Publish the .zip name only after the central directory has been closed.
        // The gallery ignores .pending files, including interrupted exports.
        DocumentFile temporary = folder.createFile("application/octet-stream",
                ".burst-" + UUID.randomUUID() + ".pending");
        if (temporary == null) throw new IOException("Cannot create burst ZIP");
        boolean published = false;
        try {
            OutputStream output = context.getContentResolver().openOutputStream(temporary.getUri(), "w");
            if (output == null) throw new IOException("Cannot open burst ZIP");
            BurstArchive.write(output, stem, frameCount, writer);
            String name = stem + "_burst.zip";
            for (int suffix = 1; folder.findFile(name) != null; suffix++) {
                name = stem + "_burst_" + suffix + ".zip";
            }
            if (!temporary.renameTo(name)) throw new IOException("Cannot publish burst ZIP");
            published = true;
            return temporary;
        } finally {
            if (!published) temporary.delete();
        }
    }
}
