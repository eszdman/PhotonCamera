package com.particlesdevs.photoncamera.control;

/** Bounded, chronological gyro history. All times are SensorEvent timestamps, never callback time. */
public final class GyroHistory {
    private static final long MAX_GAP_NS = 100_000_000L;
    private final long[] times, segments;
    private final float[][] rates;
    private final double[][] poses;
    private int head, count;
    private long segment = 1;
    private boolean discontinuity;

    public GyroHistory(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("Need at least two gyro samples");
        times = new long[capacity];
        segments = new long[capacity];
        rates = new float[3][capacity];
        poses = new double[4][capacity];
    }

    private int index(int chronological) {
        return (head-count+chronological+times.length)%times.length;
    }

    public synchronized void breakSequence() { discontinuity = true; }

    public synchronized void add(long timestamp, float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            discontinuity = true;
            return;
        }
        int previous = index(count-1);
        if (count > 0 && timestamp <= times[previous]) return;
        double[] q = {1,0,0,0};
        if (count > 0 && !discontinuity && timestamp-times[previous] <= MAX_GAP_NS) {
            double dt = (timestamp-times[previous])*0.5e-9;
            for (int c = 0; c < 4; c++) q[c] = poses[c][previous];
            q = GyroMotion.normalize(GyroMotion.multiply(q, GyroMotion.delta(
                    (rates[0][previous]+x)*dt, (rates[1][previous]+y)*dt,
                    (rates[2][previous]+z)*dt)));
        } else if (count > 0) segment++;
        discontinuity = false;
        times[head] = timestamp;
        segments[head] = segment;
        rates[0][head] = x; rates[1][head] = y; rates[2][head] = z;
        for (int c = 0; c < 4; c++) poses[c][head] = q[c];
        head = (head+1)%times.length;
        count = Math.min(count+1, times.length);
        notifyAll();
    }

    public synchronized void awaitTimestamp(long timestamp, long timeoutMs) {
        long deadline = System.nanoTime()+timeoutMs*1_000_000L;
        while (count == 0 || times[index(count-1)] < timestamp) {
            long remaining = deadline-System.nanoTime();
            if (remaining <= 0) return;
            try { wait(Math.max(1, remaining/1_000_000L)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    public synchronized GyroBurst snapshot(long timestamp, long exposureNs, long skewNs,
                                             boolean sharedTimebase) {
        long duration = Math.max(0, exposureNs)+Math.max(0, skewNs);
        long end = timestamp+duration;
        long midpoint = timestamp+duration/2;
        int first = -1, last = -1;
        for (int i = 0; i < count; i++) {
            long t = times[index(i)];
            if (t <= timestamp) first = i;
            if (t >= end) { last = i; break; }
        }
        boolean covered = sharedTimebase && exposureNs > 0 && first >= 0 && last > first
                && segments[index(first)] == segments[index(last)];
        GyroBurst out = new GyroBurst(covered ? last-first : 0);
        out.frameTimestamp = timestamp;
        out.exposureTimeNs = exposureNs;
        out.rollingShutterSkewNs = skewNs;
        out.orientationTimestamp = midpoint;
        if (!covered) return out;
        out.orientationSegment = segments[index(first)];
        for (int i = first+1; i <= last; i++) {
            int a = index(i-1), b = index(i);
            long lo = Math.max(timestamp, times[a]), hi = Math.min(end, times[b]);
            if (hi > lo) {
                double span = times[b]-times[a];
                double fraction = ((lo-times[a])+(hi-times[a]))/(2.0*span);
                for (int c = 0; c < 3; c++) {
                    float movement = (float)((rates[c][a]+(rates[c][b]-rates[c][a])*fraction)
                            *(hi-lo)*1e-9);
                    out.movementss[c][out.samples] = movement;
                    out.integrated[c] += movement;
                    out.shakiness += Math.abs(movement);
                }
                out.timestampss[out.samples++] = hi;
            }
            if (times[a] <= midpoint && midpoint <= times[b]) {
                double dt = (midpoint-times[a])*1e-9;
                double f = (double)(midpoint-times[a])/(times[b]-times[a]);
                double[] q = new double[4];
                for (int c = 0; c < 4; c++) q[c] = poses[c][a];
                out.orientation = GyroMotion.normalize(GyroMotion.multiply(q, GyroMotion.delta(
                        (rates[0][a]+0.5*(rates[0][b]-rates[0][a])*f)*dt,
                        (rates[1][a]+0.5*(rates[1][b]-rates[1][a])*f)*dt,
                        (rates[2][a]+0.5*(rates[2][b]-rates[2][a])*f)*dt)));
                out.orientationValid = true;
            }
        }
        return out;
    }
}
