package com.eszdman.photoncamera.api;

import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.ui.MainActivity;

public class Interface {
    public static Interface i;
    public MainActivity mainActivity;
    public Settings settings;
    public Photo photo;
    public Wrapper wrapper;

    public Interface() {
        i = this;
        settings = new Settings();
        photo = new Photo();
        wrapper = new Wrapper();
    }
}
