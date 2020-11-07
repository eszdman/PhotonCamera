package com.eszdman.photoncamera.util.log;
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
    public String createTextFrom(Map<String, String> treeMap) {
        Set<String> keys = treeMap.keySet();
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(k);
            sb.append(" : ");
            sb.append(treeMap.get(k));
            sb.append("\n");
        }
        return sb.toString();
    }
}
