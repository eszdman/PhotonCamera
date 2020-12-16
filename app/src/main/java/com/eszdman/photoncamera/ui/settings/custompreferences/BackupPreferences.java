package com.eszdman.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.util.FileManager;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupPreferences extends EditTextPreference {
    public BackupPreferences(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setPersistent(false);
        setSummary(FileManager.sPHOTON_DIR.toString());
        setDialogMessage("(Per lens settings are not backed up at the moment)"); //temporary
        
        setOnBindEditTextListener(editText -> {
            editText.setText(getContext().getString(R.string.backup_file_name,
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())));
            editText.setFilters(new InputFilter[]{textInputFilter});

        });

        setOnPreferenceChangeListener((preference, newValue) -> {
            File data_dir = getContext().getDataDir();
            File shared_prefs_file = Paths.get(
                    data_dir.toPath()
                            + File.separator
                            + "shared_prefs"
                            + File.separator
                            + context.getPackageName() + "_preferences.xml"
            ).toFile();
            File toSave = new File(FileManager.sPHOTON_DIR, newValue.toString().concat(".xml"));
            try {
                FileUtils.copyFile(shared_prefs_file, toSave);
                Toast.makeText(context, "Saved:" + toSave, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(context, "Failed!", Toast.LENGTH_LONG).show();
            }
            return true;
        });
    }

    private final InputFilter textInputFilter = (source, start, end, dest, dstart, dend) -> {
        if (source instanceof SpannableStringBuilder) {
            SpannableStringBuilder sourceAsSpannableBuilder = (SpannableStringBuilder) source;
            for (int i = end - 1; i >= start; i--) {
                char currentChar = source.charAt(i);
                if (!isAcceptedCharacter(currentChar)) {
                    Toast.makeText(getContext(), "Invalid '" + currentChar + "'", Toast.LENGTH_SHORT).show();
                    sourceAsSpannableBuilder.delete(i, i + 1);
                }
            }
            return source;
        } else {
            StringBuilder filteredStringBuilder = new StringBuilder();
            for (int i = start; i < end; i++) {
                char currentChar = source.charAt(i);
                if (isAcceptedCharacter(currentChar)) {
                    filteredStringBuilder.append(currentChar);
                } else {
                    Toast.makeText(getContext(), "Invalid '" + currentChar + "'", Toast.LENGTH_SHORT).show();
                }
            }
            return filteredStringBuilder.toString();
        }
    };

    boolean isAcceptedCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}

