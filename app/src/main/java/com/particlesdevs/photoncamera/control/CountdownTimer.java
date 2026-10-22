package com.particlesdevs.photoncamera.control;

import android.os.CountDownTimer;
import android.widget.TextView;

import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CountdownTimer extends CountDownTimer {
    private final TextView tv;
    private final TimerCallback callback;
    private final long interval;
    private final boolean hapticTicks;

    /**
     * @param millisInFuture    The number of millis in the future from the call
     *                          to {@link #start()} until the countdown is done and {@link #onFinish()}
     *                          is called.
     * @param countDownInterval The interval along the way to receive
     *                          {@link #onTick(long)} callbacks.
     */
    public CountdownTimer(TextView tv, long millisInFuture, long countDownInterval, TimerCallback callback) {
        this(tv, millisInFuture, countDownInterval, true, callback);
    }

    public CountdownTimer(TextView tv, long millisInFuture, long countDownInterval,
                          boolean hapticTicks, TimerCallback callback) {
        super(millisInFuture, countDownInterval);
        this.callback = callback;
        this.tv = tv;
        this.interval = countDownInterval;
        this.hapticTicks = hapticTicks;
    }

    @Override
    public void onTick(long millisUntilFinished) {
        if (hapticTicks) {
            Vibration vibration = PhotonCamera.getVibration();
            if (vibration != null) {
                vibration.countdownTick();
            }
        }
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) + 1;
        tv.post(() -> {
            tv.setScaleX(3);
            tv.setScaleY(3);
            tv.setAlpha(1);
            tv.setText((String.format(Locale.ROOT, "%d", seconds)));
            tv.animate().scaleXBy(2).scaleYBy(2).alpha(0).setDuration(interval - 50).start();
        });
    }

    @Override
    public void onFinish() {
        if (hapticTicks) {
            Vibration vibration = PhotonCamera.getVibration();
            if (vibration != null) {
                vibration.countdownFinal();
            }
        }
        tv.post(() -> {
            tv.setText("");
            tv.setAlpha(1);
            tv.setScaleY(1);
            tv.setScaleX(1);
        });
        callback.onFinished();
    }

    public interface TimerCallback {
        void onFinished();
    }
}
