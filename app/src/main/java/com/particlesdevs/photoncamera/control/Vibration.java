package com.particlesdevs.photoncamera.control;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

import com.particlesdevs.photoncamera.control.haptics.HapticCapabilities;
import com.particlesdevs.photoncamera.control.haptics.HapticEvent;
import com.particlesdevs.photoncamera.control.haptics.HapticPredefined;
import com.particlesdevs.photoncamera.control.haptics.HapticPrimitive;
import com.particlesdevs.photoncamera.control.haptics.HapticStep;
import com.particlesdevs.photoncamera.control.haptics.HapticsResolver;

import java.util.EnumSet;

public class Vibration {
    private static final long SYSTEM_FEEDBACK_CACHE_MS = 1000;

    private final Context context;
    private final Vibrator vibrator;
    private final HapticCapabilities capabilities;
    private final AudioAttributes legacyAttributes;
    private final Object touchAttributes;
    private final Object physicalAttributes;

    private volatile long systemFeedbackCheckedAt;
    private volatile boolean systemFeedbackEnabled = true;

    public Vibration(Context context) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.vibrator = acquireVibrator(this.context);
        this.capabilities = detectCapabilities(vibrator);
        this.legacyAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        this.touchAttributes = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Api33.createAttributes(android.os.VibrationAttributes.USAGE_TOUCH)
                : null;
        this.physicalAttributes = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Api33.createAttributes(android.os.VibrationAttributes.USAGE_PHYSICAL_EMULATION)
                : null;
    }

    public void play(HapticEvent event) {
        play(event, 1.0f);
    }

    public void play(HapticEvent event, float intensityScale) {
        if (event == null || !capabilities.hasVibrator || !enabled(event)) {
            return;
        }
        HapticsResolver.Resolved resolved = HapticsResolver.resolve(event, capabilities);
        VibrationEffect effect = buildEffect(resolved, intensityScale);
        if (effect == null) {
            return;
        }
        vibrate(effect, isPhysicalEvent(event));
    }

    public void cancel() {
        try {
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean hasVibrator() {
        return capabilities.hasVibrator;
    }

    public HapticCapabilities capabilities() {
        return capabilities;
    }

    /** True when the device renders effects through composed primitives. */
    public boolean usesComposedPrimitives() {
        return capabilities.supportsPrimitives();
    }

    public void toggle(boolean on) {
        play(on ? HapticEvent.TOGGLE_ON : HapticEvent.TOGGLE_OFF);
    }

    public void sliderTick() {
        play(HapticEvent.SLIDER_TICK);
    }

    public void modeChange() {
        play(HapticEvent.MODE_CHANGE);
    }

    public void longPress() {
        play(HapticEvent.LONG_PRESS);
    }

    public void shutterPress() {
        play(HapticEvent.SHUTTER_PRESS);
    }

    public void captureStart() {
        play(HapticEvent.CAPTURE_START);
    }

    public void captureComplete() {
        play(HapticEvent.CAPTURE_COMPLETE);
    }

    public void burstFrame() {
        play(HapticEvent.BURST_FRAME);
    }

    public void focusLocked() {
        play(HapticEvent.FOCUS_LOCKED);
    }

    public void focusFailed() {
        play(HapticEvent.FOCUS_FAILED);
    }

    public void recordStart() {
        play(HapticEvent.RECORD_START);
    }

    public void recordStop() {
        play(HapticEvent.RECORD_STOP);
    }

    public void countdownTick() {
        play(HapticEvent.COUNTDOWN_TICK);
    }

    public void countdownFinal() {
        play(HapticEvent.COUNTDOWN_FINAL);
    }

    public void zoomDetent() {
        play(HapticEvent.ZOOM_DETENT);
    }

    public void lensSwitch() {
        play(HapticEvent.LENS_SWITCH);
    }

    public void pageSnap() {
        play(HapticEvent.PAGE_SNAP);
    }

    public void chromeToggle() {
        play(HapticEvent.CHROME_TOGGLE);
    }

    public void select() {
        play(HapticEvent.SELECT);
    }

    public void deselect() {
        play(HapticEvent.DESELECT);
    }

    public void confirm() {
        play(HapticEvent.CONFIRM);
    }

    public void reject() {
        play(HapticEvent.REJECT);
    }

    public void error() {
        play(HapticEvent.ERROR);
    }

    @Deprecated
    public void Tick() {
        sliderTick();
    }

    @Deprecated
    public void Click() {
        confirm();
    }

    private boolean enabled(HapticEvent event) {
        if (!appHapticsEnabled()) {
            return false;
        }
        if (!isPhysicalEvent(event) && !systemFeedbackEnabled()) {
            return false;
        }
        // While a video recording runs, silence everything except the record
        // start/stop confirmations (zoom detents, viewfinder taps, mode
        // changes, etc. must not vibrate mid-recording).
        if (isVideoRecording()
                && event != HapticEvent.RECORD_START
                && event != HapticEvent.RECORD_STOP) {
            return false;
        }
        return true;
    }

    /**
     * True while MediaRecorder video capture is active. Null-safe by design:
     * gallery, settings and unit-test contexts have no capture controller.
     */
    private boolean isVideoRecording() {
        try {
            com.particlesdevs.photoncamera.capture.CaptureController controller =
                    com.particlesdevs.photoncamera.app.PhotonCamera.getCaptureController();
            return controller != null && controller.mIsRecordingVideo;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean appHapticsEnabled() {
        try {
            return com.particlesdevs.photoncamera.settings.PreferenceKeys.isHapticsOn();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean systemFeedbackEnabled() {
        long now = SystemClock.elapsedRealtime();
        if (now - systemFeedbackCheckedAt < SYSTEM_FEEDBACK_CACHE_MS) {
            return systemFeedbackEnabled;
        }
        boolean enabled = true;
        try {
            enabled = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        } catch (Throwable ignored) {
        }
        systemFeedbackEnabled = enabled;
        systemFeedbackCheckedAt = now;
        return enabled;
    }

    private VibrationEffect buildEffect(HapticsResolver.Resolved resolved, float intensityScale) {
        if (resolved == null) {
            return null;
        }
        try {
            switch (resolved.backend) {
                case COMPOSITION:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        return buildComposition(resolved, intensityScale);
                    }
                    return null;
                case PREDEFINED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        int effectId = predefinedId(resolved.predefined);
                        return effectId >= 0 ? VibrationEffect.createPredefined(effectId) : null;
                    }
                    return null;
                case WAVEFORM:
                    if (resolved.waveform == null) {
                        return null;
                    }
                    if (capabilities.amplitudeControl) {
                        return VibrationEffect.createWaveform(resolved.waveform.timingsMs,
                                scaleAmplitudes(resolved.waveform.amplitudes, intensityScale), -1);
                    }
                    return VibrationEffect.createWaveform(resolved.waveform.timingsMs, -1);
                default:
                    return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private VibrationEffect buildComposition(HapticsResolver.Resolved resolved, float intensityScale) {
        VibrationEffect.Composition composition = VibrationEffect.startComposition();
        for (HapticStep step : resolved.steps) {
            composition.addPrimitive(primitiveId(step.primitive),
                    clamp(step.scale * intensityScale), step.delayMs);
        }
        return composition.compose();
    }

    private void vibrate(VibrationEffect effect, boolean physical) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Object attributes = physical ? physicalAttributes : touchAttributes;
                if (attributes instanceof android.os.VibrationAttributes) {
                    vibrator.vibrate(effect, (android.os.VibrationAttributes) attributes);
                    return;
                }
                vibrator.vibrate(effect);
                return;
            }
            vibrator.vibrate(effect, legacyAttributes);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isPhysicalEvent(HapticEvent event) {
        switch (event) {
            case SHUTTER_PRESS:
            case CAPTURE_START:
            case CAPTURE_COMPLETE:
            case BURST_FRAME:
            case RECORD_START:
            case RECORD_STOP:
            case COUNTDOWN_TICK:
            case COUNTDOWN_FINAL:
            case LENS_SWITCH:
                return true;
            default:
                return false;
        }
    }

    private static Vibrator acquireVibrator(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (manager != null) {
                    return manager.getDefaultVibrator();
                }
            }
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static HapticCapabilities detectCapabilities(Vibrator vibrator) {
        if (vibrator == null) {
            return HapticCapabilities.none();
        }
        boolean hasVibrator = false;
        boolean amplitudeControl = false;
        try {
            hasVibrator = vibrator.hasVibrator();
        } catch (Throwable ignored) {
        }
        try {
            amplitudeControl = hasVibrator && vibrator.hasAmplitudeControl();
        } catch (Throwable ignored) {
        }
        boolean predefined = hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        EnumSet<HapticPrimitive> primitives = EnumSet.noneOf(HapticPrimitive.class);
        if (hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (HapticPrimitive primitive : HapticPrimitive.values()) {
                try {
                    if (vibrator.areAllPrimitivesSupported(primitiveId(primitive))) {
                        primitives.add(primitive);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return new HapticCapabilities(hasVibrator, amplitudeControl, predefined, primitives);
    }

    private static int primitiveId(HapticPrimitive primitive) {
        switch (primitive) {
            case CLICK:
                return VibrationEffect.Composition.PRIMITIVE_CLICK;
            case TICK:
                return VibrationEffect.Composition.PRIMITIVE_TICK;
            case LOW_TICK:
                return VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
            case QUICK_RISE:
                return VibrationEffect.Composition.PRIMITIVE_QUICK_RISE;
            case QUICK_FALL:
                return VibrationEffect.Composition.PRIMITIVE_QUICK_FALL;
            case SLOW_RISE:
                return VibrationEffect.Composition.PRIMITIVE_SLOW_RISE;
            case THUD:
                return VibrationEffect.Composition.PRIMITIVE_THUD;
            default:
                return VibrationEffect.Composition.PRIMITIVE_TICK;
        }
    }

    private static int predefinedId(HapticPredefined predefined) {
        switch (predefined) {
            case CLICK:
                return VibrationEffect.EFFECT_CLICK;
            case DOUBLE_CLICK:
                return VibrationEffect.EFFECT_DOUBLE_CLICK;
            case TICK:
                return VibrationEffect.EFFECT_TICK;
            case HEAVY_CLICK:
                return VibrationEffect.EFFECT_HEAVY_CLICK;
            default:
                return -1;
        }
    }

    private static int[] scaleAmplitudes(int[] amplitudes, float intensityScale) {
        int[] scaled = new int[amplitudes.length];
        for (int i = 0; i < amplitudes.length; i++) {
            scaled[i] = Math.max(0, Math.min(255, Math.round(amplitudes[i] * clamp(intensityScale))));
        }
        return scaled;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class Api33 {
        static Object createAttributes(int usage) {
            return android.os.VibrationAttributes.createForUsage(usage);
        }
    }
}
