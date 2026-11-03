package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.viewmodel.GalleryViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class GallerySettingsFragment extends PreferenceFragmentCompat {
    private GalleryViewModel viewModel;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gallery_preferences, rootKey);
        viewModel = new ViewModelProvider(requireActivity()).get(GalleryViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final MultiSelectListPreference foldersList = findPreference(getString(R.string.pref_folders_list));
        if (foldersList != null) {
            // Enumerating MediaStore is expensive: load the folder list off the
            // main thread and populate the picker once it arrives.
            viewModel.loadAllFolders(folders -> {
                if (folders == null || !isAdded()) return;
                CharSequence[] folderNames = folders.stream().map(GalleryFileOperations.ImagesFolder::getFolderName).collect(Collectors.toList()).toArray(new String[]{});
                CharSequence[] folderIds = folders.stream().map(imagesFolder -> String.valueOf(imagesFolder.getFolderId())).collect(Collectors.toList()).toArray(new String[]{});
                foldersList.setEntries(folderNames);
                foldersList.setEntryValues(folderIds);
                HashMap<Long, String> entrymap = new HashMap<>();
                folders.forEach(f -> entrymap.put(f.getFolderId(), f.getFolderName()));

                foldersList.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                        foldersList.setSummary(updateValuesAndGetSummaryText((MultiSelectListPreference) preference, (HashSet<String>) newValue, entrymap));
                        viewModel.setUpdatePending(true);
                        return true;
                    }
                });
                foldersList.setSummary(updateValuesAndGetSummaryText(foldersList, (HashSet<String>) foldersList.getValues(), entrymap));
            });
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }
    private String updateValuesAndGetSummaryText(MultiSelectListPreference pref, HashSet<String> values, HashMap<Long, String> entrymap) {
        ArrayList<String> l = new ArrayList<>();
        HashSet<String> newVals = new HashSet<>();
        values.forEach(val -> {
            String name = entrymap.get(Long.valueOf(val));
            if (name != null) newVals.add(val);
            else viewModel.setUpdatePending(true);
        });
        pref.setValues(newVals);
        newVals.forEach(v -> l.add(entrymap.get(Long.valueOf(v))));
        l.sort(String::compareTo);
        return String.join(", ", l);
    }
}