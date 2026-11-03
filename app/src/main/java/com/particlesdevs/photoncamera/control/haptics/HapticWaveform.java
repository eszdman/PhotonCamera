package com.particlesdevs.photoncamera.control.haptics;

public final class HapticWaveform {
    public final long[] timingsMs;
    public final int[] amplitudes;

    public HapticWaveform(long[] timingsMs, int[] amplitudes) {
        if (timingsMs.length == 0 || timingsMs.length % 2 != 0 || timingsMs.length != amplitudes.length) {
            throw new IllegalArgumentException("Timings must be non-empty, even-length and match amplitudes");
        }
        this.timingsMs = timingsMs;
        this.amplitudes = amplitudes;
    }
}
