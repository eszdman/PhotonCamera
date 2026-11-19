package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.particlesdevs.photoncamera.settings.annotations.TunableAction;
import com.particlesdevs.photoncamera.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Button generated from an annotated static action, with optional confirmation. */
public class TunableActionPreference extends Preference {
    public TunableActionPreference(Context context, Method method, TunableAction action) {
        super(context);
        setPersistent(false);
        setIconSpaceReserved(false);
        setTitle(action.title());
        setSummary(action.description());
        setOnPreferenceClickListener(preference -> {
            if (!isEnabled()) return true;
            if (action.confirmation().isEmpty()) {
                execute(method, action);
            } else {
                new MaterialAlertDialogBuilder(context)
                        .setTitle(action.title())
                        .setMessage(action.confirmation())
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> execute(method, action))
                        .show();
            }
            return true;
        });
    }

    private void execute(Method method, TunableAction action) {
        if (!isEnabled()) return;
        setEnabled(false);
        Context applicationContext = getContext().getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String message;
            try {
                method.invoke(null);
                message = action.successMessage();
            } catch (Exception e) {
                Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                Log.e("TunableAction", "Failed: " + method.getName(), cause);
                message = action.failureMessage();
            }
            String result = message;
            main.post(() -> {
                setEnabled(true);
                Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show();
            });
        }, "TunableAction-" + method.getName()).start();
    }
}
