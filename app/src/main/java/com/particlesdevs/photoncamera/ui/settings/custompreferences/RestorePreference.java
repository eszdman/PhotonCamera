package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import com.particlesdevs.photoncamera.util.FileManager;

import org.apache.commons.io.FileUtils;

import java.util.Arrays;

public class RestorePreference extends ListPreference {
    public RestorePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
        setOnPreferenceClickListener(preference -> {
            String[] filesNames = FileManager.sPHOTON_DIR.list((dir, name) ->
                    FileUtils.getExtension(name).equalsIgnoreCase("xml"));

            filesNames = filesNames != null ? filesNames : new String[0]; //null check

            Arrays.sort(filesNames);
            setEntries(filesNames);
            setEntryValues(filesNames);
            return true;
        });
    }
}
