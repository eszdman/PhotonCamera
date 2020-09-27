package com.eszdman.photoncamera.util.log;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.eszdman.photoncamera.app.PhotonCamera;

/**
 * Added by vibhorSrv on 16/09/2020
 */
public class ActivityLifecycleMonitor implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "ActivityMonitor";

    void log(String msg) {
        if (PhotonCamera.DEBUG)
            Log.d(TAG, msg);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        log(activity.getLocalClassName() + " : onCreated(), bundle = [" + bundle + "]");
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onStarted()");

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onResumed()");

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onPaused()");

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onStopped()");

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
        log(activity.getLocalClassName() + " : onSaveInstanceState(), bundle = [" + bundle + "]");

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        log(activity.getLocalClassName() + " : onDestroyed()");

    }
}