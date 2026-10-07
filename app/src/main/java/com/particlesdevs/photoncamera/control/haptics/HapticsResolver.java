package com.particlesdevs.photoncamera.control.haptics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class HapticsResolver {

    public enum Backend {
        NONE,
        COMPOSITION,
        PREDEFINED,
        WAVEFORM
    }

    public static final class Resolved {
        public final Backend backend;
        public final List<HapticStep> steps;
        public final HapticPredefined predefined;
        public final HapticWaveform waveform;

        public Resolved(Backend backend, List<HapticStep> steps,
                        HapticPredefined predefined, HapticWaveform waveform) {
            this.backend = backend;
            this.steps = steps == null ? Collections.emptyList() : steps;
            this.predefined = predefined == null ? HapticPredefined.NONE : predefined;
            this.waveform = waveform;
        }

        public static Resolved none() {
            return new Resolved(Backend.NONE, Collections.emptyList(), HapticPredefined.NONE, null);
        }
    }

    private HapticsResolver() {
    }

    public static Resolved resolve(HapticEvent event, HapticCapabilities capabilities) {
        return resolve(HapticPatterns.of(event), capabilities);
    }

    public static Resolved resolve(HapticPattern pattern, HapticCapabilities capabilities) {
        if (pattern == null || capabilities == null || !capabilities.hasVibrator) {
            return Resolved.none();
        }
        if (capabilities.supportsPrimitives()) {
            List<HapticStep> steps = substituteSteps(pattern.steps, capabilities.primitives);
            if (!steps.isEmpty()) {
                return new Resolved(Backend.COMPOSITION, steps, HapticPredefined.NONE, null);
            }
        }
        if (capabilities.predefinedEffects && pattern.predefined != HapticPredefined.NONE) {
            return new Resolved(Backend.PREDEFINED, Collections.emptyList(),
                    pattern.predefined, null);
        }
        if (pattern.waveform != null) {
            return new Resolved(Backend.WAVEFORM, Collections.emptyList(),
                    HapticPredefined.NONE, pattern.waveform);
        }
        return Resolved.none();
    }

    static List<HapticStep> substituteSteps(List<HapticStep> steps, Set<HapticPrimitive> supported) {
        List<HapticStep> resolved = new ArrayList<>(steps.size());
        for (HapticStep step : steps) {
            HapticPrimitive primitive = substitute(step.primitive, supported);
            if (primitive != null) {
                resolved.add(new HapticStep(primitive, step.scale, step.delayMs));
            }
        }
        return resolved;
    }

    private static HapticPrimitive substitute(HapticPrimitive primitive, Set<HapticPrimitive> supported) {
        if (primitive == null || supported == null || supported.isEmpty()) {
            return null;
        }
        if (supported.contains(primitive)) {
            return primitive;
        }
        switch (primitive) {
            case CLICK:
                return pick(supported, HapticPrimitive.TICK, HapticPrimitive.LOW_TICK);
            case TICK:
                return pick(supported, HapticPrimitive.LOW_TICK, HapticPrimitive.CLICK);
            case LOW_TICK:
                return pick(supported, HapticPrimitive.TICK, HapticPrimitive.CLICK);
            case QUICK_RISE:
                return pick(supported, HapticPrimitive.SLOW_RISE, HapticPrimitive.TICK, HapticPrimitive.CLICK);
            case SLOW_RISE:
                return pick(supported, HapticPrimitive.QUICK_RISE, HapticPrimitive.TICK, HapticPrimitive.CLICK);
            case QUICK_FALL:
                return pick(supported, HapticPrimitive.LOW_TICK, HapticPrimitive.TICK, HapticPrimitive.CLICK);
            case THUD:
                return pick(supported, HapticPrimitive.LOW_TICK, HapticPrimitive.TICK, HapticPrimitive.CLICK);
            default:
                return null;
        }
    }

    private static HapticPrimitive pick(Set<HapticPrimitive> supported, HapticPrimitive... candidates) {
        for (HapticPrimitive candidate : candidates) {
            if (supported.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
