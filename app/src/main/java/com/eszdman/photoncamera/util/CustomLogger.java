package com.eszdman.photoncamera.util;
/*
  Created by Vibhor on 18/08/2020
 */

import android.app.Activity;

import java.util.Map;
import java.util.Set;

public class CustomLogger extends OnScreenLogger {
    public CustomLogger(Activity activity, int textViewID) {
        super(activity, textViewID);
    }

    @Override
    public String createTextFrom(String... msg) {
        //Not Implemented
        return null;
    }

    @Override
    public String createTextFrom(Map<String, String> treemap) {
        Set<String> keys = treemap.keySet();
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(k);
            sb.append(" : ");
            sb.append(treemap.get(k));
            sb.append("\n");
        }
        return sb.toString();
    }
}
