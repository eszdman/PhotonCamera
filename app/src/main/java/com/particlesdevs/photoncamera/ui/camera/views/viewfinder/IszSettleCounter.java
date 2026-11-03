package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

/**
 * Counts freshly arrived preview frames while an In-Sensor Zoom (ISZ) lens
 * transition is masked.
 *
 * <p>Switching to an ISZ virtual lens changes the physical sensor's readout
 * mode, and the first frames out of the new session still stream the old
 * full-sensor view. The viewfinder keeps showing its frozen last frame until
 * this counter has seen enough new frames for the sensor to have settled,
 * then the frozen overlay crossfades away.
 *
 * <p>All methods are synchronized: frames are counted off the GL callback
 * thread while arming/resetting happens on camera threads.
 */
public final class IszSettleCounter {
    private final int defaultThreshold;
    private int threshold;
    private int count;

    public IszSettleCounter(int threshold) {
        this.defaultThreshold = Math.max(1, threshold);
        this.threshold = this.defaultThreshold;
    }

    /** Rearms with the constructor's threshold. */
    public synchronized void reset() {
        reset(defaultThreshold);
    }

    /** Rearms with a per-arm threshold (e.g. a preview fade's frame count). */
    public synchronized void reset(int threshold) {
        this.threshold = Math.max(1, threshold);
        count = 0;
    }

    /**
     * @return true once the threshold has been reached (stays true until reset)
     */
    public synchronized boolean onFrame() {
        if (count < threshold) count++;
        return count >= threshold;
    }

    public synchronized int getCount() {
        return count;
    }

    public synchronized boolean isSettled() {
        return count >= threshold;
    }
}
