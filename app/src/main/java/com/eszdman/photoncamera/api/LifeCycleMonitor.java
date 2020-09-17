package com.eszdman.photoncamera.api;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LifeCycleMonitor implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "LifeCycleMonitor";

    void log(String msg) {
        Log.d(TAG, msg);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        log(activity.getLocalClassName() + " : onCreated");
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onStarted");

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onResumed");

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onPaused");

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onStopped");

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
        log(activity.getLocalClassName() + " : onSaveInstanceState");

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onDestroyed");

    }
}