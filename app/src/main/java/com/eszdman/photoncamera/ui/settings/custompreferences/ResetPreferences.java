package com.eszdman.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class ResetPreferences extends DialogPreference {
    public ResetPreferences(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setPersistent(false);
        setDialogTitle(android.R.string.dialog_alert_title);
        setDialogMessage(R.string.reset_preferences_warning);
        setPositiveButtonText(R.string.yes);
    }

    public static class Dialog extends PreferenceDialogFragmentCompat {
        public static Dialog newInstance(Preference preference) {
            final Dialog fragment = new Dialog();
            final Bundle bundle = new Bundle(1);
            bundle.putString(ARG_KEY, preference.getKey());
            fragment.setArguments(bundle);
            return fragment;
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                try {
                    if (getContext() != null) {
                        File data_dir = getContext().getDataDir();
                        File shared_prefs_dir = Paths.get(data_dir.toPath() + File.separator + "shared_prefs").toFile();
                        FileUtils.deleteDirectory(shared_prefs_dir);
                        Toast.makeText(getContext(), R.string.app_will_restart, Toast.LENGTH_SHORT).show();
                        new Handler(Looper.getMainLooper()).postDelayed(PhotonCamera::restartApp, 1000);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}