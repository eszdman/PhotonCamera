package com.particlesdevs.photoncamera.util;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

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
    public static File sPHOTON_DIR = new File(sEXTERNAL_DIR + "//DCIM//PhotonCamera//");
    public static File sPHOTON_RAW_DIR = new File(sEXTERNAL_DIR + "//DCIM//PhotonCamera//Raw//");
    public static File sPHOTON_TUNING_DIR = new File(sEXTERNAL_DIR + "//DCIM//PhotonCamera//Tuning//");
    public static File sDCIM_CAMERA = new File(sEXTERNAL_DIR + "//DCIM//Camera//");
    public static List<File> imageFiles;


    public static void CreateFolders() {
        Log.d(TAG, "CreatedFolder : " + sDCIM_CAMERA + '=' + sDCIM_CAMERA.mkdirs());
        Log.d(TAG, "CreatedFolder : " + sPHOTON_RAW_DIR + '=' + sPHOTON_RAW_DIR.mkdirs());
        Log.d(TAG, "CreatedFolder : " + sPHOTON_TUNING_DIR + '=' + sPHOTON_TUNING_DIR.mkdirs());
    }

    @NonNull
    public static List<File> getAllImageFiles() {
        File[] dcimFiles = FileManager.sDCIM_CAMERA.listFiles(FILENAME_FILTER);
        File[] photonRawFiles = FileManager.sPHOTON_RAW_DIR.listFiles(FILENAME_FILTER);
        List<File> filesList = new ArrayList<>();
        filesList.addAll(Arrays.asList(dcimFiles != null ? dcimFiles : new File[0]));
        filesList.addAll(Arrays.asList(photonRawFiles != null ? photonRawFiles : new File[0]));
        if(imageFiles != null && imageFiles.size() > 2) {
            if (imageFiles.size() < filesList.size()) {
                List<File> diffList = new ArrayList<>();
                long lastDate = imageFiles.get(0).lastModified();
                for(File f : filesList){
                    if(lastDate < f.lastModified()){
                        diffList.add(f);
                    }
                }
                diffList.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
                Log.d(TAG, "Diff:"+diffList.toString());
                diffList.addAll(imageFiles);
                Log.d(TAG, "FastFile:"+diffList.get(0).toString());
                Log.d(TAG, "FirstFile:"+diffList.get(0).toString());
                return diffList;
            } else {
                if (filesList.size() < imageFiles.size()) {
                    if (imageFiles.size() >= 1)
                        imageFiles.remove(0);
                    Log.d(TAG, "Removed, firstFile:"+imageFiles.get(0).toString());
                    return imageFiles;
                } else {
                    Log.d(TAG, "Similar, firstFile:"+imageFiles.get(0).toString());
                    return imageFiles;
                }
            }
        }
        if (!filesList.isEmpty()) {
            filesList.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
            imageFiles = filesList;
        } else {
            Log.e(TAG, "getAllImageFiles(): Could not find any Image Files");
        }
        Log.d(TAG,"LastFile:"+filesList.get(filesList.size()-1));
        Log.d(TAG,"FirstFile:"+filesList.get(0));

        return filesList;
    }
}