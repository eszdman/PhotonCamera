package com.particlesdevs.photoncamera.gallery.helper;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import com.particlesdevs.photoncamera.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utilities for Ultra HDR display support in the gallery: device HDR
 * capability checks, cheap Ultra HDR JPEG detection via a header scan, and
 * window HDR color mode toggling.
 */
public final class UltraHdrGalleryUtil {

    private static final String TAG = "UltraHdrGalleryUtil";
    private static final int SCAN_BYTES = 64 * 1024;
    private static final byte[] XMP_NS = "http://ns.adobe.com/xap/1.0/\0"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HDRGM_NS = "hdr-gain-map".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MPF_SIG = new byte[]{'M', 'P', 'F', 0};
    private static final byte[] ISO_NS = "urn:iso:std:iso:ts:21496:-1"
            .getBytes(StandardCharsets.US_ASCII);

    private UltraHdrGalleryUtil() {
    }

    /**
     * Whether the gainmap can be rendered in HDR by HWUI: requires Android 14+
     * and an HDR capable display.
     */
    public static boolean isDeviceHdrCapable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        try {
            WindowManager windowManager =
                    (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return false;
            }
            Display display = windowManager.getDefaultDisplay();
            return display != null && display.isHdr();
        } catch (Exception e) {
            Log.d(TAG, "HDR capability check failed: " + e);
            return false;
        }
    }

    /**
     * Cheap detection of an Ultra HDR (ISO 21496-1) JPEG: scans the first
     * 64 KB of the file for the MPF APP2 segment, the hdrgm XMP packet or
     * the ISO 21496-1 APP2 segment. Never decodes image data.
     */
    public static boolean isUltraHdrJpeg(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[SCAN_BYTES];
            int total = 0;
            int read;
            while (total < buffer.length
                    && (read = in.read(buffer, total, buffer.length - total)) > 0) {
                total += read;
            }
            return containsUltraHdrMarkers(buffer, total);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Scans JPEG marker segments for Ultra HDR signatures:
     * APP1 (XMP) with the hdrgm namespace, APP2 with the MPF signature, or
     * APP2 with the ISO 21496-1 URN.
     */
    public static boolean containsUltraHdrMarkers(byte[] data, int length) {
        if (data == null || length < 4) {
            return false;
        }
        int pos = 0;
        while (pos + 4 <= length) {
            if ((data[pos] & 0xFF) != 0xFF) {
                pos++;
                continue;
            }
            int marker = data[pos + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0xD9) {
                pos += 2;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            int segmentLength = ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            if (segmentLength < 2 || pos + 2 + segmentLength > length) {
                break;
            }
            int start = pos + 4;
            int end = pos + 2 + segmentLength;
            if (marker == 0xE1) {
                if (startsWith(data, start, end, XMP_NS)
                        && indexOf(data, start, end, HDRGM_NS) >= 0) {
                    return true;
                }
            } else if (marker == 0xE2) {
                if (startsWith(data, start, end, MPF_SIG)
                        || startsWith(data, start, end, ISO_NS)) {
                    return true;
                }
            }
            pos += 2 + segmentLength;
        }
        return false;
    }

    /**
     * Toggles the window HDR color mode. HWUI automatically applies a
     * bitmap's gainmap when it is drawn on a hardware canvas inside an
     * {@link ActivityInfo#COLOR_MODE_HDR} window.
     */
    public static void setWindowHdr(Activity activity, boolean enabled) {
        if (activity == null) {
            return;
        }
        try {
            activity.getWindow().setColorMode(
                    enabled ? ActivityInfo.COLOR_MODE_HDR : ActivityInfo.COLOR_MODE_DEFAULT);
        } catch (Exception e) {
            Log.d(TAG, "Failed to set window color mode: " + e);
        }
    }

    private static boolean startsWith(byte[] data, int start, int end, byte[] prefix) {
        if (end - start < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[start + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, int start, int end, byte[] needle) {
        for (int i = start; i + needle.length <= end; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }
}
