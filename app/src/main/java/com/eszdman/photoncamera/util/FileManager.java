package com.eszdman.photoncamera.util;

import android.os.Environment;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileManager {
    public static final File EXTERNAL_DIR = Environment.getExternalStorageDirectory();
    public static final String EXTERNAL_DIR_PATH = EXTERNAL_DIR.getAbsolutePath();
    public static final File DCIM_CAMERA = new File(EXTERNAL_DIR_PATH + "/DCIM/Camera");
    public static final File PHOTON_DIR = new File(EXTERNAL_DIR_PATH + "/DCIM/PhotonCamera");
    public static final File PHOTON_RAW_DIR = new File(EXTERNAL_DIR_PATH + "/DCIM/PhotonCamera/Raw");

    private static final List<String> ACCEPTED_FILES_EXTENSIONS = Arrays.asList("JPG", "JPEG", "DNG");
    private static final FilenameFilter FILENAME_FILTER = (dir, name) -> {
        int index = name.lastIndexOf(46);
        return ACCEPTED_FILES_EXTENSIONS.contains(-1 == index ? "" : name.substring(index + 1).toUpperCase());
    };

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void CreateFolders() {
        if (!DCIM_CAMERA.exists()) {
            DCIM_CAMERA.mkdir();
        }
        if (!PHOTON_DIR.exists()) {
            PHOTON_DIR.mkdir();
        }
    }

    public static List<File> getAllImageFiles() {
        File[] dcimFiles = FileManager.DCIM_CAMERA.listFiles(FILENAME_FILTER);
        File[] photonFiles = FileManager.PHOTON_DIR.listFiles(FILENAME_FILTER);
        File[] photonRawFiles = FileManager.PHOTON_RAW_DIR.listFiles(FILENAME_FILTER);
        List<File> filesList = new ArrayList<>();
        if (dcimFiles != null && photonFiles != null && photonRawFiles != null) {
            filesList = new ArrayList<>(Arrays.asList(dcimFiles));
            filesList.addAll(Arrays.asList(photonFiles));
            filesList.addAll(Arrays.asList(photonRawFiles));
            filesList.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
        }
        return filesList;
    }
}