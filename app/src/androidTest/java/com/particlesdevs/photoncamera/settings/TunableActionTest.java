package com.particlesdevs.photoncamera.settings;

import android.content.Context;
import android.os.Looper;
import android.view.ContextThemeWrapper;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.particlesdevs.photoncamera.settings.annotations.TunableAction;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableActionPreference;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TunableActionTest {
    public static class Actions {
        static volatile int calls;
        static volatile boolean ranOnMain;
        static CountDownLatch finished;

        @TunableAction(title = "Test action", category = "Test actions")
        public static void run() {
            ranOnMain = Looper.myLooper() == Looper.getMainLooper();
            calls++;
            finished.countDown();
        }

        @TunableAction(title = "Invalid action", category = "Test actions")
        public void invalidInstanceMethod() {}
    }

    @Test public void generatedActionIsExplicitNonPersistentAndRunsOffMainThread() throws Exception {
        Actions.calls = 0;
        Actions.ranOnMain = true;
        Actions.finished = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                    androidx.appcompat.R.style.Theme_AppCompat);
            PreferenceScreen screen = new PreferenceManager(context).createPreferenceScreen(context);
            screen.setKey("pref_tunable_submenu");
            TunablePreferenceGenerator.registerTunableClass(Actions.class);
            TunablePreferenceGenerator.generatePreferences(context, screen);
            Preference button = screen.findPreference("pref_tunable_action_actions_run");
            assertTrue(button instanceof TunableActionPreference);
            assertFalse(button.isPersistent());
            assertEquals("Test action", button.getTitle());
            assertNotNull(screen.findPreference("pref_category_tunable_test_actions"));
            assertNull(screen.findPreference("pref_tunable_action_actions_invalidinstancemethod"));
            assertEquals(0, Actions.calls);
            // Generating again must not duplicate or execute an action.
            TunablePreferenceGenerator.generatePreferences(context, screen);
            assertEquals(1, screen.getPreferenceCount());
            assertEquals(0, Actions.calls);
            assertTrue(button.getOnPreferenceClickListener().onPreferenceClick(button));
        });
        assertTrue(Actions.finished.await(5, TimeUnit.SECONDS));
        assertEquals(1, Actions.calls);
        assertFalse(Actions.ranOnMain);
    }
}
