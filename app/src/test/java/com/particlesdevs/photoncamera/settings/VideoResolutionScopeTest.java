package com.particlesdevs.photoncamera.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the per-resolution video scope key derivation.
 */
public class VideoResolutionScopeTest {

    @Test
    public void tunableIdsCarryResolutionAndFacing() {
        assertEquals("video_3840x2160",
                VideoResolutionScope.tunableId(false, false, "3840x2160"));
        assertEquals("video_selfie_1920x1080",
                VideoResolutionScope.tunableId(false, true, "1920x1080"));
        assertEquals("video_hdr_3840x2160",
                VideoResolutionScope.tunableId(true, false, "3840x2160"));
        assertEquals("video_hdr_selfie_1280x720",
                VideoResolutionScope.tunableId(true, true, "1280x720"));
    }

    @Test
    public void sessionTypeKeysCarryResolutionAndFacing() {
        assertEquals("pref_video_hdr_session_type_1920x1080",
                VideoResolutionScope.sessionTypeKey(true, false, "1920x1080"));
        assertEquals("pref_video_hdr_session_type_selfie_1920x1080",
                VideoResolutionScope.sessionTypeKey(true, true, "1920x1080"));
        assertEquals("pref_video_sdr_session_type_1280x720",
                VideoResolutionScope.sessionTypeKey(false, false, "1280x720"));
        assertEquals("pref_video_sdr_session_type_selfie_3840x2160",
                VideoResolutionScope.sessionTypeKey(false, true, "3840x2160"));
    }

    @Test
    public void differentResolutionsGetDifferentKeys() {
        assertFalse(VideoResolutionScope.tunableId(false, false, "3840x2160")
                .equals(VideoResolutionScope.tunableId(false, false, "1920x1080")));
        assertFalse(VideoResolutionScope.sessionTypeKey(true, false, "3840x2160")
                .equals(VideoResolutionScope.sessionTypeKey(true, false, "1920x1080")));
        assertFalse(VideoResolutionScope.sessionTypeKey(false, false, "1920x1080")
                .equals(VideoResolutionScope.sessionTypeKey(false, true, "1920x1080")));
    }

    @Test
    public void missingResolutionUsesDefaultToken() {
        assertEquals(VideoResolutionScope.tunableId(false, false, null),
                VideoResolutionScope.tunableId(false, false, VideoResolutionScope.DEFAULT_TOKEN));
        assertEquals(VideoResolutionScope.tunableId(false, false, "  "),
                VideoResolutionScope.tunableId(false, false, VideoResolutionScope.DEFAULT_TOKEN));
    }

    @Test
    public void tokensAreKeySafe() {
        assertEquals("3840x2160", VideoResolutionScope.token(" 3840x2160 "));
        assertFalse(VideoResolutionScope.token("1920 1080").contains(" "));
        assertTrue(VideoResolutionScope.token("").length() > 0);
    }
}
