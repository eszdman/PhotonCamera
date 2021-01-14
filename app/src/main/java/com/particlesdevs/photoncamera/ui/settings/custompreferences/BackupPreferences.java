package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.util.FileManager;

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

