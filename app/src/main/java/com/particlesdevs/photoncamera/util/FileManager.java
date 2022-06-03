package com.particlesdevs.photoncamera.util;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileManager {
    private static final String TAG = "FileManager";
    private static final List<String> ACCEPTED_FILES_EXTENSIONS = Arrays.asList("JPG", "JPEG", "DNG");
    private static final FilenameFilter FILENAME_FILTER = (dir, name) -> {
        int index = name.lastIndexOf(46);
        return ACCEPTED_FILES_EXTENSIONS.contains(-1 == index ? "" : name.substring(index + 1).toUpperCase()) && new File(dir, name).length() > 0;
    };
    public static File sEXTERNAL_DIR = Environment.getExternalStorageDirectory();
    public static File sCACHE_DIR;
    public static File sPHOTON_DIR = new File(sEXTERNAL_DIR + "//DCIM//PhotonCamera//");
    public static File sPHOTON_RAW_DIR = new File(sEXTERNAL_DIR + "//DCIM//PhotonCamera//Raw//");
    public static File sPHOTON_TUNING_DIR = new File(sEXTERNAL_DIR + "//DCIM//PhotonCamera//Tuning//");
    public static File sDCIM_CAMERA = new File(sEXTERNAL_DIR + "//DCIM//Camera//");
    public static List<File> tempImageFiles;


    public static void CreateFolders() {
        Log.d(TAG, "CreatedFolder : " + sDCIM_CAMERA + '=' + sDCIM_CAMERA.mkdirs());
        Log.d(TAG, "CreatedFolder : " + sPHOTON_RAW_DIR + '=' + sPHOTON_RAW_DIR.mkdirs());
        Log.d(TAG, "CreatedFolder : " + sPHOTON_TUNING_DIR + '=' + sPHOTON_TUNING_DIR.mkdirs());
    }

    public static void ScanRemovedFile(File f){
        long fileTime = f.lastModified();
        int ind = -1;
        int left = 0, right = tempImageFiles.size() - 1;

        while (left <= right)
        {
            int mid = left + (right - left) / 2;
            // Check if x is present at mid
            if (tempImageFiles.get(mid).lastModified() == fileTime)
                ind = mid;

            // If x greater, ignore left half
            if (tempImageFiles.get(mid).lastModified()  < fileTime)
                left = mid + 1;
                // If x is smaller, ignore right half
            else
                right = mid - 1;
        }
        if(ind == -1) return;
        tempImageFiles.remove(ind);
    }
    @NonNull
    public static List<File> getAllImageFiles() {
        File[] dcimFiles = FileManager.sDCIM_CAMERA.listFiles(FILENAME_FILTER);
        File[] photonRawFiles = FileManager.sPHOTON_RAW_DIR.listFiles(FILENAME_FILTER);
        List<File> filesList = new ArrayList<>();
        filesList.addAll(Arrays.asList(dcimFiles != null ? dcimFiles : new File[0]));
        filesList.addAll(Arrays.asList(photonRawFiles != null ? photonRawFiles : new File[0]));
        if(tempImageFiles != null && tempImageFiles.size() > 2) {
            //On added Images
            if (tempImageFiles.size() < filesList.size()) {
                List<File> fileDiff = new ArrayList<>();
                long lastDate = tempImageFiles.get(0).lastModified();
                for(File f : filesList)
                    if(lastDate < f.lastModified())
                        fileDiff.add(f);
                fileDiff.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
                fileDiff.addAll(tempImageFiles);
                tempImageFiles = fileDiff;
            }
            return tempImageFiles;
        }
        if (!filesList.isEmpty()) {
            filesList.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
            tempImageFiles = filesList;
        } else
            Log.e(TAG, "getAllImageFiles(): Could not find any Image Files");

        return filesList;
    }
}