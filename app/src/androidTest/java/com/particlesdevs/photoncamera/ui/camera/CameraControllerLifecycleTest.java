package com.particlesdevs.photoncamera.ui.camera;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

@RunWith(AndroidJUnit4.class)
public class CameraControllerLifecycleTest {
    @Test public void resumedCameraRestoresControllerBeforeCapture() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Application app=(Application)instrumentation.getTargetContext().getApplicationContext();
        Handler main=new Handler(Looper.getMainLooper());
        BlockingQueue<CameraActivity> resumed=new LinkedBlockingQueue<>(), destroyed=new LinkedBlockingQueue<>();
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity activity,Bundle state) { }
            public void onActivityStarted(Activity activity) { }
            public void onActivityResumed(Activity activity) {
                if(activity instanceof CameraActivity) main.post(() -> resumed.add((CameraActivity)activity));
            }
            public void onActivityPaused(Activity activity) { }
            public void onActivityStopped(Activity activity) { }
            public void onActivitySaveInstanceState(Activity activity,Bundle state) { }
            public void onActivityDestroyed(Activity activity) {
                if(activity instanceof CameraActivity) main.post(() -> destroyed.add((CameraActivity)activity));
            }
        };
        app.registerActivityLifecycleCallbacks(callbacks);
        CameraActivity first=null, second=null;
        try {
            instrumentation.runOnMainSync(() -> app.startActivity(new Intent(app,CameraActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)));
            first=resumed.poll(15,TimeUnit.SECONDS);
            assertNotNull("First camera did not resume",first);
            CameraActivity originalActivity=first;
            instrumentation.runOnMainSync(() -> originalActivity.startActivity(new Intent(originalActivity,CameraActivity.class)));
            second=resumed.poll(15,TimeUnit.SECONDS);
            assertNotNull("Second camera did not resume",second);
            assertNotSame(first,second);
            CameraActivity topActivity=second;
            instrumentation.runOnMainSync(topActivity::finish);
            assertSame("Original camera should resume",first,resumed.poll(15,TimeUnit.SECONDS));
            assertSame("Second camera should be destroyed",second,destroyed.poll(15,TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> {
                CameraFragment fragment=(CameraFragment)originalActivity.getSupportFragmentManager().findFragmentById(R.id.container);
                assertNotNull(fragment);
                CaptureController controller=fragment.getCaptureController();
                assertNotNull(controller);
                assertSame("Resumed camera must reclaim the controller used by capture",
                        controller,PhotonCamera.getCaptureController());
                // This is the first capture operation in the reported ZSL crash.
                FrameNumberSelector.getFrames();
            });
        } finally {
            CameraActivity originalActivity=first, topActivity=second;
            instrumentation.runOnMainSync(() -> {
                if(topActivity!=null && !topActivity.isDestroyed()) topActivity.finish();
                if(originalActivity!=null && !originalActivity.isDestroyed()) originalActivity.finish();
            });
            app.unregisterActivityLifecycleCallbacks(callbacks);
        }
    }
}
