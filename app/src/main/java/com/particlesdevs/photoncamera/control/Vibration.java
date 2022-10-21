package com.particlesdevs.photoncamera.control;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class Vibration {
    private final Vibrator vibrator;
    private final VibrationEffect tick;
    private final VibrationEffect click;
    private VibrationEffect getTick(){
        VibrationEffect output;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            output = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
        } else output = VibrationEffect.createOneShot(9,255);
        return output;
    }
    private VibrationEffect getClick(){
        VibrationEffect output;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            output = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
        } else output = VibrationEffect.createOneShot(13,255);
        return output;
    }
    public Vibration(Context context){
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        tick = getTick();
        click = getClick();
    }
    public void Tick(){
        vibrator.vibrate(tick);
    }
    public void Click(){
        vibrator.vibrate(click);
    }
}
