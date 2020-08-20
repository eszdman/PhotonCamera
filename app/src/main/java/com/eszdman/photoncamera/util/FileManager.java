package com.eszdman.photoncamera.util;

import android.os.Environment;

import java.io.File;

public class FileManager {
    public static final File EXTERNAL_DIR = Environment.getExternalStorageDirectory();
    public static final String EXTERNAL_DIR_PATH = EXTERNAL_DIR.getAbsolutePath();
    public static final File DCIM_CAMERA = new File(EXTERNAL_DIR_PATH + "/DCIM/Camera");
    public static final File PHOTON_DIR =new File(EXTERNAL_DIR_PATH + "/DCIM/Camera/PhotonCamera");

}