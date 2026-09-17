package com.particlesdevs.photoncamera.util;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Central helper for lockscreen ("secure camera") support.
 *
 * <p>When the system (lockscreen affordance, power double-tap, etc.) fires
 * {@code android.media.action.STILL_IMAGE_CAMERA_SECURE} or
 * {@code android.media.action.IMAGE_CAPTURE_SECURE}, PhotonCamera must be able to
 * show its viewfinder <em>over</em> the keyguard while still protecting the user's
 * existing library and settings until the device is unlocked.
 *
 * <p>Authorship rule: {@link com.particlesdevs.photoncamera.ui.camera.CameraActivity}
 * owns the authoritative {@code secureSession} flag (secure intent action at launch
 * time, or device locked at launch time). {@link com.particlesdevs.photoncamera.gallery.ui.GalleryActivity}
 * and {@link com.particlesdevs.photoncamera.ui.settings.SettingsActivity} additionally
 * consult the <em>live</em> lock state via {@link #isDeviceLocked(Context)} as a
 * defense-in-depth backstop against direct launches while locked.
 */
public final class SecureCameraHelper {
    private SecureCameraHelper() {
    }

    public static final String ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
            "android.media.action.IMAGE_CAPTURE_SECURE";
    /** Set on the CameraActivity-bound session once the user authenticates mid-session. */
    public static final String EXTRA_SECURE_SESSION =
            "com.particlesdevs.photoncamera.extra.SECURE_SESSION";
    /**
     * Set when launching a guarded activity immediately after a successful
     * credential confirmation, so it is not bounced even if the keyguard has
     * not fully reported unlocked yet.
     */
    public static final String EXTRA_UNLOCKED_JUST_NOW =
            "com.particlesdevs.photoncamera.extra.UNLOCKED_JUST_NOW";

    /** True if the launch intent is one of the secure-camera actions. */
    public static boolean isSecureIntent(@Nullable Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        return ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)
                || ACTION_IMAGE_CAPTURE_SECURE.equals(action);
    }

    /** True if the device keyguard is currently locked. Safe on all supported API levels. */
    public static boolean isDeviceLocked(@Nullable Context context) {
        if (context == null) return false;
        try {
            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (km.isDeviceLocked()) return true;
            }
            return km.isKeyguardLocked();
        } catch (Throwable t) {
            Log.e("SecureCameraHelper", "isDeviceLocked: " + t.getMessage());
            return false;
        }
    }

    /**
     * Authoritative secure-session check for {@code CameraActivity}-family callers:
     * explicit session extra, secure intent action, or device currently locked.
     */
    public static boolean isSecureSession(@NonNull Activity activity) {
        Intent intent = activity.getIntent();
        if (intent != null && intent.getBooleanExtra(EXTRA_SECURE_SESSION, false)) return true;
        if (isSecureIntent(intent)) return true;
        return isDeviceLocked(activity);
    }

    /**
     * Allows the activity to be shown over the lockscreen and to turn the screen on.
     * Uses the API 27+ methods with a window-flag fallback for API 26 (our minSdk).
     */
    public static void applyLockscreenFlags(@NonNull Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true);
                activity.setTurnScreenOn(true);
            } else {
                activity.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            }
        } catch (Throwable t) {
            Log.e("SecureCameraHelper", "applyLockscreenFlags: " + t.getMessage());
        }
    }

    /**
     * Launches the system credential-confirmation UI from a fragment.
     *
     * @return true if the confirmation UI was launched (result arrives in
     * {@code Fragment.onActivityResult}), false if the device has no secure lock
     * screen set up (caller should treat "no credentials" as unlocked).
     */
    @SuppressWarnings("deprecation")
    public static boolean promptUnlock(@NonNull Fragment fragment,
                                       int requestCode,
                                       @NonNull String title,
                                       @NonNull String description) {
        try {
            Context context = fragment.getContext();
            if (context == null) return false;
            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !km.isKeyguardSecure()) {
                // No PIN/pattern/biometric enrolled: nothing to confirm.
                return false;
            }
            Intent confirm = km.createConfirmDeviceCredentialIntent(title, description);
            if (confirm == null) return false;
            fragment.startActivityForResult(confirm, requestCode);
            return true;
        } catch (Throwable t) {
            Log.e("SecureCameraHelper", "promptUnlock: " + t.getMessage());
            return false;
        }
    }

    /** Callback for {@link #requestDismissKeyguard(Activity, DismissCallback)}. */
    public interface DismissCallback {
        void onDismissSucceeded();

        void onDismissCancelled();

        void onDismissError();
    }

    /**
     * Asks the system to dismiss the keyguard (prompting for credentials if the
     * keyguard is secure). On success the device is genuinely unlocked, so guarded
     * activities can be launched normally. Falls back to {@code false} on API &lt; 26.
     *
     * @return true if the dismiss request was handed to the system.
     */
    public static boolean requestDismissKeyguard(@NonNull Activity activity,
                                                 @NonNull DismissCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        try {
            KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) return false;
            km.requestDismissKeyguard(activity, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    callback.onDismissSucceeded();
                }

                @Override
                public void onDismissCancelled() {
                    callback.onDismissCancelled();
                }

                @Override
                public void onDismissError() {
                    callback.onDismissError();
                }
            });
            return true;
        } catch (Throwable t) {
            Log.e("SecureCameraHelper", "requestDismissKeyguard: " + t.getMessage());
            return false;
        }
    }
}
