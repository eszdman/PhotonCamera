package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.settings.TunableKeyManager;

import java.util.List;

/**
 * Displays a user-defined {@link VendorTagUtils.TunableKey} for a physical sensor with a
 * status circle: green when supported, red when tested but unsupported, grey when untested
 * (muted while the key is disabled). Clicking opens the edit dialog; long-pressing toggles
 * the key between enabled and disabled.
 */
public class TunableKeyPreference extends Preference {
    private final String sensorId;
    private final int index;
    private final Runnable onChanged;

    public TunableKeyPreference(Context context, String sensorId, int index, Runnable onChanged) {
        super(context);
        this.sensorId = sensorId;
        this.index = index;
        this.onChanged = onChanged;
        setLayoutResource(R.layout.preference_with_margin);
        setIconSpaceReserved(true);
        bindData();
        setOnPreferenceClickListener(preference -> {
            TunableKeyDialog.show(getContext(), sensorId, index, onChanged);
            return true;
        });
    }

    /**
     * Set the displayed data once, before the preference is attached. Must not be done in
     * {@link #onBindViewHolder} because those setters trigger {@code notifyChanged()} which
     * crashes while the RecyclerView is computing a layout.
     */
    private void bindData() {
        List<VendorTagUtils.TunableKey> keys = TunableKeyManager.loadKeys(getContext(), sensorId);
        if (index < 0 || index >= keys.size()) return;
        VendorTagUtils.TunableKey key = keys.get(index);
        setTitle(key.name);
        boolean enabled = VendorTagUtils.TunableKey.isEnabled(key);
        String status = key.tested ? (key.supported ? "\u2714 supported" : "\u2718 not supported") : "untested";
        String init = VendorTagUtils.TunableKey.isSessionInit(key) ? "Session" : "Capture";
        setSummary(key.valueType + " = " + key.value + " \u00B7 " + init
                + "  (" + status + ", " + (enabled ? "enabled" : "disabled") + ")");
        GradientDrawable icon = createCircleDrawable(getContext(), getStatusColor(getContext(), key));
        if (!enabled) icon.setAlpha(77);
        setIcon(icon);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setOnLongClickListener(this::toggleEnabled);
    }

    /** Flips the enabled state of the stored key and refreshes the section. */
    private boolean toggleEnabled(View view) {
        Context context = getContext();
        List<VendorTagUtils.TunableKey> keys = TunableKeyManager.loadKeys(context, sensorId);
        if (index < 0 || index >= keys.size()) return true;
        VendorTagUtils.TunableKey key = keys.get(index);
        key.enabled = !VendorTagUtils.TunableKey.isEnabled(key);
        TunableKeyManager.saveKeys(context, sensorId, keys);
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        PhotonCamera.showToast(key.name + (key.enabled ? " enabled" : " disabled"));
        if (onChanged != null) onChanged.run();
        return true;
    }

    private static int getStatusColor(Context context, VendorTagUtils.TunableKey key) {
        if (!key.tested) {
            return MaterialColors.getColor(context, R.attr.colorOutline, 0xFF9E9E9E);
        }
        return key.supported
                ? MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF4CAF50)
                : MaterialColors.getColor(context, R.attr.colorError, 0xFFF44336);
    }

    static GradientDrawable createCircleDrawable(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        int size = (int) (24 * context.getResources().getDisplayMetrics().density);
        drawable.setSize(size, size);
        return drawable;
    }
}
