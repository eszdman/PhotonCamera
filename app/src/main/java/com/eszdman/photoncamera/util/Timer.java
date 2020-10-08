package com.eszdman.photoncamera.util;

import android.util.Log;

public class Timer {
    private static final String TAG = "Timer";
    private long timeStart;
    private String inTag, inName;

    public static Timer InitTimer(String tag, String name) {
        Timer timer = new Timer();
        timer.startTimer(tag, name);
        return timer;
    }

    public void startTimer(String tag, String name) {
        timeStart = System.currentTimeMillis();
        inTag = tag;
        inName = name;
    }

    public void endTimer() {
        Log.d(TAG + ":" + inTag, inName + " elapsed->" + (System.currentTimeMillis() - timeStart) + " ms");
    }
}
