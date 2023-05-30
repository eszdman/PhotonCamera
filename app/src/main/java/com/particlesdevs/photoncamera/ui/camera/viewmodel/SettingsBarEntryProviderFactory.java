package com.particlesdevs.photoncamera.ui.camera.viewmodel;

public class SettingsBarEntryProviderFactory{
    public static SettingsBarEntryProvider SettingsBarEntryProver(SettingsBarEntryProviderID settingsBarEntryProviderID){
        SettingsBarEntryProvider settingsBarEntryProvider = null;

        switch (settingsBarEntryProviderID){
            case V1 : settingsBarEntryProvider = new SettingsBarEntryProviderVer1(); break;
            case V2 : break;
        }
        return settingsBarEntryProvider;
    }

    public enum SettingsBarEntryProviderID{
        V1,
        V2
    }
}