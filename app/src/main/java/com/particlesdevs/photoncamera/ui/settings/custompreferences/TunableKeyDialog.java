package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.settings.TunableKeyManager;

import java.util.List;

/**
 * Dialog for creating, editing and deleting a {@link VendorTagUtils.TunableKey}.
 * Pass editIndex = -1 to create a new key.
 */
public class TunableKeyDialog {
    private static final String[] VALUE_TYPES = {"Integer", "Long", "Float", "Double", "Byte", "Short", "int[]", "byte[]", "long[]", "float[]", "String"};

    private TunableKeyDialog() {}

    public static void show(Context context, String sensorId, int editIndex, Runnable onDone) {
        if (context == null || sensorId == null) return;
        List<VendorTagUtils.TunableKey> keys = TunableKeyManager.loadKeys(context, sensorId);
        VendorTagUtils.TunableKey existing = (editIndex >= 0 && editIndex < keys.size()) ? keys.get(editIndex) : null;
        boolean isNew = existing == null;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 24);
        container.setPadding(pad, dp(context, 8), pad, 0);

        TextInputLayout nameLayout = new TextInputLayout(context);
        nameLayout.setHint("Key name");
        TextInputEditText nameEdit = new TextInputEditText(nameLayout.getContext());
        nameEdit.setSingleLine(true);
        nameEdit.setText(existing != null ? existing.name : "");
        nameLayout.addView(nameEdit);
        container.addView(nameLayout, matchWrap());

        TextInputLayout typeLayout = new TextInputLayout(context);
        typeLayout.setHint("Value type");
        typeLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        MaterialAutoCompleteTextView typeView = new MaterialAutoCompleteTextView(typeLayout.getContext());
        typeView.setInputType(InputType.TYPE_NULL);
        typeView.setAdapter(new ArrayAdapter<>(context,
                com.google.android.material.R.layout.mtrl_auto_complete_simple_item, VALUE_TYPES));
        typeView.setText(existing != null ? existing.valueType : VALUE_TYPES[0], false);
        typeView.setOnClickListener(v -> typeView.showDropDown());
        typeLayout.addView(typeView);
        LinearLayout.LayoutParams typeParams = matchWrap();
        typeParams.topMargin = dp(context, 8);
        container.addView(typeLayout, typeParams);

        TextInputLayout valueLayout = new TextInputLayout(context);
        valueLayout.setHint("Value");
        TextInputEditText valueEdit = new TextInputEditText(valueLayout.getContext());
        valueEdit.setSingleLine(true);
        valueEdit.setText(existing != null ? existing.value : "0");
        valueLayout.addView(valueEdit);
        LinearLayout.LayoutParams valueParams = matchWrap();
        valueParams.topMargin = dp(context, 8);
        container.addView(valueLayout, valueParams);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(isNew ? "Add Tunable Key" : "Edit Tunable Key")
                .setView(container)
                .setPositiveButton("Set", (dialog, which) -> {
                    String name = nameEdit.getText().toString().trim();
                    if (name.isEmpty()) {
                        PhotonCamera.showToast("Key name cannot be empty");
                        return;
                    }
                    VendorTagUtils.TunableKey key = new VendorTagUtils.TunableKey();
                    key.type = "CaptureRequest";
                    key.name = name;
                    key.valueType = typeView.getText().toString();
                    key.value = valueEdit.getText().toString().trim();
                    key.tested = existing != null && existing.tested;
                    key.supported = existing != null && existing.supported;

                    List<VendorTagUtils.TunableKey> list = TunableKeyManager.loadKeys(context, sensorId);
                    if (isNew) {
                        list.add(key);
                    } else if (editIndex < list.size()) {
                        list.set(editIndex, key);
                    }
                    TunableKeyManager.saveKeys(context, sensorId, list);
                    if (onDone != null) onDone.run();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        if (!isNew) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                List<VendorTagUtils.TunableKey> list = TunableKeyManager.loadKeys(context, sensorId);
                if (editIndex >= 0 && editIndex < list.size()) {
                    list.remove(editIndex);
                    TunableKeyManager.saveKeys(context, sensorId, list);
                }
                if (onDone != null) onDone.run();
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();
        nameEdit.requestFocus();
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
