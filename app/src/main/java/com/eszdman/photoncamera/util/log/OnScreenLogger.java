package com.eszdman.photoncamera.util.log;
/*
  Created by Vibhor on 18/08/2020
 */

import android.app.Activity;
import android.widget.TextView;

import java.util.Map;

/**
 * Extend this class in order to use it.
 * Then you may directly use {@link OnScreenLogger#updateText(String)} with your subclass reference to update the textView field.
 * Or implement the methods for custom text formatting or when using multiple parameters and then pass that string to updateText(String) method.
 */
public abstract class OnScreenLogger {
    private final Activity activity;
    private TextView textView;

    /**
     * @param activity   the activity that contains the textView
     * @param textViewID resource id of the textView
     */
    OnScreenLogger(Activity activity, int textViewID) {
        this.activity = activity;
        try {
            textView = activity.findViewById(textViewID);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public abstract String createTextFrom(String... msg);

    public abstract String createTextFrom(Map<String, String> treeMap);

    public final void updateText(String text) {
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (textView != null)
                    textView.setText(text);
            });
        }
    }

    public final void setVisibility(int visibility) {
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (textView != null)
                    textView.setVisibility(visibility);
            });
        }
    }

}
