package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import androidx.lifecycle.ViewModelProvider;

import com.particlesdevs.photoncamera.ui.camera.CameraFragment;

public class SettingsBarEntryProviderFactory{
    public SettingsBarEntryProviderFactory(){}
    public SettingsBarEntryProvider getSettingsBarEntryProvider(CameraFragment cameraFragment, SettingsBarEntryProviderID settingsBarEntryProviderID){
        SettingsBarEntryProvider settingsBarEntryProvider = null;

        switch (settingsBarEntryProviderID){
            case V1 : settingsBarEntryProvider = new ViewModelProvider(cameraFragment).get(SettingsBarEntryProviderVer1.class); break;
            case V2 : break;
        }
        return settingsBarEntryProvider;
    }

    public enum SettingsBarEntryProviderID{
        V1,
        V2
    }
}