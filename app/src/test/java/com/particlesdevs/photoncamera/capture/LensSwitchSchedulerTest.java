package com.particlesdevs.photoncamera.capture;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link LensSwitchScheduler}: single-flight cycles, last-wins
 * coalescing, same-lens folding and bounded retries.
 */
public class LensSwitchSchedulerTest {

    private LensSwitchScheduler scheduler;

    @Before
    public void setUp() {
        scheduler = new LensSwitchScheduler();
    }

    @Test
    public void idleRequestStartsCycle() {
        assertTrue(scheduler.request("uw", true));
        assertTrue(scheduler.isActive());

        LensSwitchScheduler.Request req = scheduler.beginNext();
        assertEquals("uw", req.cameraId);
        assertTrue(req.zoomDriven);
        assertEquals(0, req.attempt);
    }

    @Test
    public void requestDuringCycleIsCoalescedLastWins() {
        assertTrue(scheduler.request("main", true));
        LensSwitchScheduler.Request first = scheduler.beginNext();
        assertEquals("main", first.cameraId);

        // Requests while the cycle is active only update the pending target.
        assertFalse(scheduler.request("tele", true));
        assertFalse(scheduler.request("uw", true));

        LensSwitchScheduler.Request next = scheduler.pollSuperseding();
        assertEquals("uw", next.cameraId);
        assertEquals("uw", scheduler.inFlightId());
    }

    @Test
    public void sameLensZoomRequestIsFolded() {
        assertTrue(scheduler.request("main", true));
        scheduler.beginNext();
        scheduler.request("main", true);

        assertNull(scheduler.pollSuperseding());
        assertNull(scheduler.onSettled());
        assertFalse(scheduler.isActive());
    }

    @Test
    public void manualSameLensRequestIsNotFolded() {
        assertTrue(scheduler.request("main", false));
        scheduler.beginNext();
        scheduler.request("main", false);

        // A manual restart must re-run the cycle even for the same lens so
        // settings changes (mode/format) are applied.
        LensSwitchScheduler.Request next = scheduler.pollSuperseding();
        assertEquals("main", next.cameraId);
        assertFalse(next.zoomDriven);
    }

    @Test
    public void onSettledStartsPendingTarget() {
        assertTrue(scheduler.request("main", true));
        scheduler.beginNext();
        scheduler.request("tele", true);

        LensSwitchScheduler.Request next = scheduler.onSettled();
        assertEquals("tele", next.cameraId);
        assertTrue(scheduler.isActive());

        // No further pending: the next settle idles the pipeline.
        assertNull(scheduler.onSettled());
        assertFalse(scheduler.isActive());
    }

    @Test
    public void onFailureRetriesBoundedTimes() {
        assertTrue(scheduler.request("tele", true));
        scheduler.beginNext();

        for (int attempt = 1; attempt <= LensSwitchScheduler.MAX_RETRIES; attempt++) {
            LensSwitchScheduler.Request retry = scheduler.onFailure();
            assertEquals("tele", retry.cameraId);
            assertEquals(attempt, retry.attempt);
            assertTrue(scheduler.isActive());
        }
        assertNull(scheduler.onFailure());
        assertFalse(scheduler.isActive());
    }

    @Test
    public void onFailureDropsPendingOnExhaustion() {
        assertTrue(scheduler.request("tele", true));
        scheduler.beginNext();
        scheduler.request("main", true);
        for (int i = 0; i < LensSwitchScheduler.MAX_RETRIES; i++) {
            scheduler.onFailure();
        }
        assertNull(scheduler.onFailure());
        assertFalse(scheduler.isActive());
    }

    @Test
    public void onFailureWithoutInFlightPromotesPending() {
        assertTrue(scheduler.request("main", true));
        // No beginNext(): simulates a non-scheduled (cold) open failing while a
        // lens request is queued.
        assertFalse(scheduler.request("tele", true));
        assertNull(scheduler.inFlightId());

        LensSwitchScheduler.Request promoted = scheduler.onFailure();
        assertEquals("tele", promoted.cameraId);
        assertTrue(scheduler.isActive());
        assertEquals("tele", scheduler.inFlightId());
    }

    @Test
    public void cancelClearsStateAndBumpsGeneration() {
        assertTrue(scheduler.request("tele", true));
        scheduler.beginNext();
        int generation = scheduler.generation();

        scheduler.cancel();

        assertFalse(scheduler.isActive());
        assertNull(scheduler.inFlightId());
        assertTrue(scheduler.generation() > generation);
        // A request after cancel starts a fresh cycle.
        assertTrue(scheduler.request("main", true));
    }

    @Test
    public void generationBumpsWhenTargetChanges() {
        assertTrue(scheduler.request("main", true));
        scheduler.beginNext();
        int generation = scheduler.generation();

        scheduler.request("tele", true);
        scheduler.pollSuperseding();
        assertTrue(scheduler.generation() > generation);
    }
}
