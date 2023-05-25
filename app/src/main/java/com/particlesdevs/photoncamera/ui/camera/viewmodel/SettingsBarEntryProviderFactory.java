package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import static com.particlesdevs.photoncamera.ui.camera.viewmodel.SettingsBarEntryProviderFactory.SettingsBarEntryProviderID;

public class SettingsBarEntryProviderFactory{
    public static void SettingsBarEntryProver(SettingsBarEntryProviderID settingsBarEntryProviderID){
        SettingsBarEntryProvider settingsBarEntryProvider = null;

        switch (settingsBarEntryProviderID){
            case V1 : settingsBarEntryProvider = new SettingsBarEntryProviderV1() break;
            case V2 : break;
        }
    }

    public enum SettingsBarEntryProviderID{
        V1,
        V2
    }
}