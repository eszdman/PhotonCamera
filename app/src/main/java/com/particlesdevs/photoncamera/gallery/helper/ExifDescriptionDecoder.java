package com.particlesdevs.photoncamera.gallery.helper;

import java.nio.charset.StandardCharsets;

/**
 * Decodes a raw EXIF string attribute (e.g. {@code ImageDescription}) into a
 * display string.
 *
 * <p>{@link androidx.exifinterface.media.ExifInterface#getAttribute} sanitizes
 * ASCII values by replacing every control character (newlines included) with
 * '?', which destroys the camera's multi-line processing-parameter dump. The
 * gallery therefore reads {@code getAttributeBytes} and decodes the raw bytes
 * here so the original line breaks survive.
 */
public final class ExifDescriptionDecoder {

    /** Charset marker some writers prepend to EXIF ASCII/UNDEFINED strings. */
    private static final byte[] ASCII_PREFIX = {'A', 'S', 'C', 'I', 'I', 0, 0, 0};

    private ExifDescriptionDecoder() {
    }

    public static String decode(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        int start = 0;
        int end = raw.length;
        if (end - start >= ASCII_PREFIX.length && startsWithPrefix(raw, start)) {
            start += ASCII_PREFIX.length;
        }
        for (int i = start; i < end; i++) {
            if (raw[i] == 0) {
                end = i;
                break;
            }
        }
        if (end <= start) {
            return "";
        }
        return new String(raw, start, end - start, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private static boolean startsWithPrefix(byte[] raw, int offset) {
        for (int i = 0; i < ASCII_PREFIX.length; i++) {
            if (raw[offset + i] != ASCII_PREFIX[i]) {
                return false;
            }
        }
        return true;
    }
}
