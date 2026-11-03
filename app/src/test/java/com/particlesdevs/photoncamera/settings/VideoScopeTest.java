package com.particlesdevs.photoncamera.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the per-resolution/per-fps video scope key derivation.
 */
public class VideoScopeTest {

    @Test
    public void tunableIdsCarryResolutionFpsAndFacing() {
        assertEquals("video_3840x2160_60",
                VideoScope.tunableId(false, false, "3840x2160", 3));
        assertEquals("video_selfie_1920x1080_auto",
                VideoScope.tunableId(false, true, "1920x1080", 0));
        assertEquals("video_hdr_3840x2160_30",
                VideoScope.tunableId(true, false, "3840x2160", 2));
        assertEquals("video_hdr_selfie_1280x720_24",
                VideoScope.tunableId(true, true, "1280x720", 1));
    }

    @Test
    public void sessionTypeKeysCarryResolutionFpsAndFacing() {
        assertEquals("pref_video_hdr_session_type_1920x1080_60",
                VideoScope.sessionTypeKey(true, false, "1920x1080", 3));
        assertEquals("pref_video_hdr_session_type_selfie_1920x1080_auto",
                VideoScope.sessionTypeKey(true, true, "1920x1080", 0));
        assertEquals("pref_video_sdr_session_type_1280x720_30",
                VideoScope.sessionTypeKey(false, false, "1280x720", 2));
        assertEquals("pref_video_sdr_session_type_selfie_3840x2160_24",
                VideoScope.sessionTypeKey(false, true, "3840x2160", 1));
    }

    @Test
    public void resolutionOnlyScopeIsADistinctFallback() {
        assertFalse(VideoScope.tunableId(false, false, "3840x2160", 3)
                .equals(VideoScope.resolutionTunableId(false, false, "3840x2160")));
        assertFalse(VideoScope.sessionTypeKey(true, false, "3840x2160", 3)
                .equals(VideoScope.resolutionSessionTypeKey(true, false, "3840x2160")));
    }

    @Test
    public void differentResolutionsAndFpsGetDifferentKeys() {
        assertFalse(VideoScope.tunableId(false, false, "3840x2160", 3)
                .equals(VideoScope.tunableId(false, false, "1920x1080", 3)));
        assertFalse(VideoScope.tunableId(false, false, "3840x2160", 3)
                .equals(VideoScope.tunableId(false, false, "3840x2160", 2)));
        assertFalse(VideoScope.sessionTypeKey(false, false, "1920x1080", 2)
                .equals(VideoScope.sessionTypeKey(false, true, "1920x1080", 2)));
    }

    @Test
    public void fpsTokensFollowTheSetting() {
        assertEquals("auto", VideoScope.fpsToken(0));
        assertEquals("24", VideoScope.fpsToken(1));
        assertEquals("30", VideoScope.fpsToken(2));
        assertEquals("60", VideoScope.fpsToken(3));
        // Out-of-range modes fall back to Auto.
        assertEquals("auto", VideoScope.fpsToken(-1));
        assertEquals("auto", VideoScope.fpsToken(9));
    }

    @Test
    public void missingResolutionUsesDefaultToken() {
        assertEquals(VideoScope.tunableId(false, false, null, 3),
                VideoScope.tunableId(false, false, VideoScope.DEFAULT_TOKEN, 3));
        assertEquals(VideoScope.tunableId(false, false, "  ", 3),
                VideoScope.tunableId(false, false, VideoScope.DEFAULT_TOKEN, 3));
    }

    @Test
    public void tokensAreKeySafe() {
        assertEquals("3840x2160", VideoScope.token(" 3840x2160 "));
        assertFalse(VideoScope.token("1920 1080").contains(" "));
        assertTrue(VideoScope.token("").length() > 0);
    }
}
