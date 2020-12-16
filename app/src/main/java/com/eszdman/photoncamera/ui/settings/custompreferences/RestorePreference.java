package com.eszdman.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.preference.ListPreference;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.util.FileManager;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
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
        setOnPreferenceChangeListener((preference, newValue) -> {
            File toRestore = new File(FileManager.sPHOTON_DIR, newValue.toString());
            File data_dir = getContext().getDataDir();
            File shared_prefs_file = Paths.get(
                    data_dir.toPath()
                            + File.separator
                            + "shared_prefs"
                            + File.separator
                            + context.getPackageName() + "_preferences.xml"
            ).toFile();

            try {
                FileUtils.copyFile(toRestore, shared_prefs_file);
                Toast.makeText(context, "Restored:" + toRestore.getName(), Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).postDelayed(PhotonCamera::restartApp, 1000);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(context, "Failed!", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }
}
