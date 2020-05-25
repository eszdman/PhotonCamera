package com.eszdman.photoncamera.api;

import com.eszdman.photoncamera.ui.MainActivity;

public class Interface {
    public static Interface i;
    public MainActivity m_activity;
    public Settings settings;
    public Interface(){
        i = this;
        settings = new Settings();
    }
}
