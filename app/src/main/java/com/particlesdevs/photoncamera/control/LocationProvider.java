package com.particlesdevs.photoncamera.control;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.particlesdevs.photoncamera.util.Log;

/**
 * Keeps a single "last known" location fix for EXIF geotagging while the
 * camera is open. Uses the platform {@link LocationManager} only (no Play
 * Services): seeds from cached fixes on {@link #start()} and then listens to
 * every enabled provider. Callers must hold a location permission; every
 * entry point degrades to "no location" when it is missing or disabled.
 */
public class LocationProvider {
    private static final String TAG = "LocationProvider";

    /** Fixes older than this are not written to EXIF. */
    public static final long MAX_FIX_AGE_MS = 5 * 60 * 1000L;

    private final Context context;
    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            onNewLocation(location);
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private volatile Location lastLocation;
    private boolean started;

    public LocationProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    /** True when the app may access location (fine or approximate). */
    public static boolean hasPermission(Context context) {
        if (context == null) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Pure freshness test used by {@link #getLastLocation()}: true when the
     * fix timestamp lies within {@code maxAgeMs} of {@code nowMs} (future
     * timestamps from clock skew are tolerated within the same window).
     */
    public static boolean isFresh(long fixTimeMs, long nowMs, long maxAgeMs) {
        if (fixTimeMs <= 0) {
            return false;
        }
        long age = nowMs - fixTimeMs;
        return age <= maxAgeMs && age >= -maxAgeMs;
    }

    /** Starts listening for updates. No-op without permission or while started. */
    public synchronized void start() {
        if (started || !hasPermission(context)) {
            return;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return;
        }
        started = true;
        try {
            for (String provider : manager.getProviders(true)) {
                if (LocationManager.PASSIVE_PROVIDER.equals(provider)) {
                    continue;
                }
                // Seed with whatever is cached, then subscribe. Updates are
                // low-rate: only the freshest fix ends up in EXIF.
                try {
                    onNewLocation(manager.getLastKnownLocation(provider));
                } catch (Exception e) {
                    Log.w(TAG, "lastKnownLocation failed for " + provider, e);
                }
                try {
                    manager.requestLocationUpdates(provider, 30_000L, 0f, listener, Looper.getMainLooper());
                } catch (Exception e) {
                    Log.w(TAG, "requestLocationUpdates failed for " + provider, e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "start failed", e);
        }
    }

    /** Stops listening; the cached fix is kept for the same session. */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager != null) {
            try {
                manager.removeUpdates(listener);
            } catch (Exception e) {
                Log.w(TAG, "removeUpdates failed", e);
            }
        }
    }

    /** The most recent fix if it is still fresh, otherwise null. */
    public Location getLastLocation() {
        Location location = lastLocation;
        if (location == null
                || !isFresh(location.getTime(), System.currentTimeMillis(), MAX_FIX_AGE_MS)) {
            return null;
        }
        return location;
    }

    private void onNewLocation(Location location) {
        if (location == null) {
            return;
        }
        Location current = lastLocation;
        if (current == null
                || location.getTime() > current.getTime()
                || (location.getTime() == current.getTime() && isMoreAccurate(location, current))) {
            lastLocation = new Location(location);
        }
    }

    private static boolean isMoreAccurate(Location candidate, Location current) {
        if (!candidate.hasAccuracy()) {
            return false;
        }
        return !current.hasAccuracy() || candidate.getAccuracy() < current.getAccuracy();
    }
}
