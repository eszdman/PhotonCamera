package com.particlesdevs.photoncamera.capture;

/**
 * Serializes camera close/open cycles caused by lens switches and coalesces
 * the requests produced by a fast pinch sweeping across several lens
 * thresholds.
 *
 * <p>At most one cycle is active at a time. Requests arriving while a cycle is
 * in flight only replace a pending slot (last request wins) and are promoted
 * either when the cycle settles or when the caller notices a superseding
 * request before an open is issued. A zoom-driven request for the lens that is
 * already being opened is folded away; a manual restart (settings/mode change)
 * is never folded because it must re-run the setup path.
 *
 * <p>{@link #generation()} is bumped whenever the promoted target changes (or
 * on {@link #cancel()}), so delayed open/retry runnables can be invalidated.
 */
public class LensSwitchScheduler {
    /** Number of open retries after a failed attempt before giving up. */
    public static final int MAX_RETRIES = 3;

    /** One lens-switch request. {@code attempt} is 0 for a fresh cycle, &gt;0 for a retry. */
    public static final class Request {
        public final String cameraId;
        public final boolean zoomDriven;
        public final int attempt;

        Request(String cameraId, boolean zoomDriven, int attempt) {
            this.cameraId = cameraId;
            this.zoomDriven = zoomDriven;
            this.attempt = attempt;
        }

        @Override
        public String toString() {
            return "Request{" + cameraId + ", zoomDriven=" + zoomDriven + ", attempt=" + attempt + '}';
        }
    }

    private String pendingId;
    private boolean pendingZoomDriven;
    private String inFlightId;
    private boolean inFlightZoomDriven;
    private boolean active;
    private int retryCount;
    private int generation;

    /**
     * Queues a request. Returns true when the pipeline was idle, in which case
     * the caller must start a cycle; otherwise the request is coalesced.
     */
    public synchronized boolean request(String cameraId, boolean zoomDriven) {
        if (cameraId == null) return false;
        pendingId = cameraId;
        pendingZoomDriven = zoomDriven;
        if (!active) {
            active = true;
            return true;
        }
        return false;
    }

    /** True while a close/open cycle is active (including retries). */
    public synchronized boolean isActive() {
        return active;
    }

    /** The target currently being opened, or null. */
    public synchronized String inFlightId() {
        return inFlightId;
    }

    /** Bumped when the promoted target changes or the pipeline is cancelled. */
    public synchronized int generation() {
        return generation;
    }

    /** Promotes the pending target and returns it, or null when there is none. */
    public synchronized Request beginNext() {
        if (pendingId == null) {
            active = false;
            return null;
        }
        return promoteLocked();
    }

    /**
     * Returns the pending target when it differs from the in-flight one; folds
     * (drops) a redundant zoom request for the in-flight lens and returns null.
     * Used before opening so a target queued during the settle window replaces
     * the one that has not been opened yet.
     */
    public synchronized Request pollSuperseding() {
        if (pendingId == null) return null;
        if (pendingZoomDriven && pendingId.equals(inFlightId)) {
            clearPendingLocked();
            return null;
        }
        return promoteLocked();
    }

    /**
     * Reports that the current cycle settled (device opened and preview
     * configured). Returns the next request to run, or null when idle.
     */
    public synchronized Request onSettled() {
        retryCount = 0;
        if (pendingId != null && !(pendingZoomDriven && pendingId.equals(inFlightId))) {
            return promoteLocked();
        }
        clearPendingLocked();
        inFlightId = null;
        active = false;
        return null;
    }

    /**
     * Reports a failed open attempt. Returns a request to retry the same
     * target, or null when retries are exhausted (the pipeline then idles and
     * any remaining request is dropped). A failure that belongs to no scheduled
     * cycle (e.g. a failed cold open) instead promotes a queued request, if
     * any, without consuming the retry budget.
     */
    public synchronized Request onFailure() {
        if (inFlightId == null) {
            // No scheduled cycle owned this open (e.g. a failed cold open):
            // steer to a queued request instead of consuming the retry budget.
            if (pendingId != null) return promoteLocked();
            active = false;
            generation++;
            return null;
        }
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            generation++;
            return new Request(inFlightId, inFlightZoomDriven, retryCount);
        }
        clearPendingLocked();
        inFlightId = null;
        active = false;
        generation++;
        return null;
    }

    /** Clears all state and invalidates pending delayed runnables. */
    public synchronized void cancel() {
        clearPendingLocked();
        inFlightId = null;
        retryCount = 0;
        active = false;
        generation++;
    }

    private Request promoteLocked() {
        inFlightId = pendingId;
        inFlightZoomDriven = pendingZoomDriven;
        clearPendingLocked();
        retryCount = 0;
        generation++;
        return new Request(inFlightId, inFlightZoomDriven, 0);
    }

    private void clearPendingLocked() {
        pendingId = null;
        pendingZoomDriven = false;
    }
}
