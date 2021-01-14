package com.particlesdevs.photoncamera.util.log;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.particlesdevs.photoncamera.app.PhotonCamera;

/**
 * Added by vibhorSrv on 17/09/2020
 */
public class FragmentLifeCycleMonitor extends FragmentManager.FragmentLifecycleCallbacks {
    private static final String TAG = "FragmentMonitor";

    private void log(String msg) {
        if (PhotonCamera.DEBUG)
            Log.d(TAG, msg);
    }

    String getNameOf(Object obj) {
        return "[" + obj.getClass().getSimpleName() + "]";
//        return "[" + obj.getClass().getSimpleName() + '@' + Integer.toHexString(obj.hashCode()) + "]";
    }

    @Override
    public void onFragmentPreAttached(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Context context) {
        super.onFragmentPreAttached(fm, f, context);
        log(getNameOf(f) + " : onPreAttached(), context = " + getNameOf(context));
    }

    @Override
    public void onFragmentAttached(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Context context) {
        super.onFragmentAttached(fm, f, context);
        log(getNameOf(f) + " : onAttached(), context = " + getNameOf(context));
    }

    @Override
    public void onFragmentPreCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle savedInstanceState) {
        super.onFragmentPreCreated(fm, f, savedInstanceState);
        log(getNameOf(f) + " : onPreCreated(), savedInstanceState = [" + savedInstanceState + "]");
    }

    @Override
    public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle savedInstanceState) {
        super.onFragmentCreated(fm, f, savedInstanceState);
        log(getNameOf(f) + " : onCreated(), savedInstanceState = [" + savedInstanceState + "]");
    }

    @Override
    public void onFragmentActivityCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle savedInstanceState) {
        super.onFragmentActivityCreated(fm, f, savedInstanceState);
        log(getNameOf(f) + " : onActivityCreated(), savedInstanceState = [" + savedInstanceState + "]");
    }

    @Override
    public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onFragmentViewCreated(fm, f, v, savedInstanceState);
        log(getNameOf(f) + " : onViewCreated(), v = [" + v + "], savedInstanceState = [" + savedInstanceState + "]");
    }

    @Override
    public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentStarted(fm, f);
        log(getNameOf(f) + " : onStarted()");
    }

    @Override
    public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentResumed(fm, f);
        log(getNameOf(f) + " : onResumed()");
    }

    @Override
    public void onFragmentPaused(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentPaused(fm, f);
        log(getNameOf(f) + " : onPaused()");
    }

    @Override
    public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentStopped(fm, f);
        log(getNameOf(f) + " : onStopped()");
    }

    @Override
    public void onFragmentSaveInstanceState(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Bundle outState) {
        super.onFragmentSaveInstanceState(fm, f, outState);
        log(getNameOf(f) + " : onSaveInstanceState(), outState = [" + outState + "]");
    }

    @Override
    public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentViewDestroyed(fm, f);
        log(getNameOf(f) + " : onViewDestroyed()");
    }

    @Override
    public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentDestroyed(fm, f);
        log(getNameOf(f) + " : onDestroyed()");
    }

    @Override
    public void onFragmentDetached(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentDetached(fm, f);
        log(getNameOf(f) + " : onDetached()");
    }
}
