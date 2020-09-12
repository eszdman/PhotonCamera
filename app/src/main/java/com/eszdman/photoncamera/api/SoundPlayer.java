package com.eszdman.photoncamera.api;

import android.content.Context;
import android.media.MediaPlayer;

import com.eszdman.photoncamera.R;

public class SoundPlayer implements ImageCaptureResultCallback.CaptureEvents
{
    private MediaPlayer burstPlayer;
    public SoundPlayer(Context context)
    {
        burstPlayer = MediaPlayer.create(context, R.raw.sound_burst);
    }

    @Override
    public boolean runOnUiThread() {
        return false;
    }

    @Override
    public void onCaptureStarted() {
        burstPlayer.seekTo(0);
    }

    @Override
    public void onCaptureCompleted() {
        burstPlayer.start();
    }

    @Override
    public void onCaptureSequenceStarted(int burstcount) {

    }

    @Override
    public void onCaptureSequenceCompleted() {

    }

    @Override
    public void onCaptureProgressed() {

    }
}
