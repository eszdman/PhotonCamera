package com.particlesdevs.photoncamera.settings;

import android.content.Context;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.FileManager;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class BackupRestoreUtil {
    public static String backupSettings(Context context, String fileName) {
        File data_dir = context.getDataDir();
        File shared_prefs_file = Paths.get(
                data_dir.toPath()
                        + File.separator
                        + "shared_prefs"
                        + File.separator
                        + context.getPackageName() + "_preferences.xml"
        ).toFile();
        File toSave = new File(FileManager.sPHOTON_DIR, fileName.concat(".xml"));
        try {
            if (fileName.equals("")) {
                throw new IOException(context.getString(R.string.empty_file_name_error));
            }
            FileUtils.copyFile(shared_prefs_file, toSave);
            return "Saved:" + toSave;
        } catch (IOException e) {
            e.printStackTrace();
            return e.getLocalizedMessage();
        }
    }

    public static String restorePreferences(Context context, String fileName) {
        File toRestore = new File(FileManager.sPHOTON_DIR, fileName);
        File data_dir = context.getDataDir();
        File shared_prefs_file = Paths.get(
                data_dir.toPath()
                        + File.separator
                        + "shared_prefs"
                        + File.separator
                        + context.getPackageName() + "_preferences.xml"
        ).toFile();

        try {
            FileUtils.copyFile(toRestore, shared_prefs_file);
            PhotonCamera.restartWithDelay(1000);
            return "Restored:" + toRestore.getName();
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed!";
        }
    }

    public static boolean resetPreferences(Context context) {
        File data_dir = context.getDataDir();
        File shared_prefs_dir = Paths.get(data_dir.toPath() + File.separator + "shared_prefs").toFile();
        try {
            FileUtils.deleteDirectory(shared_prefs_dir);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
