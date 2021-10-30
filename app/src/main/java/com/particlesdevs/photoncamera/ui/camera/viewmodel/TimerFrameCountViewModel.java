package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.ui.camera.model.TimerFrameCountModel;

public class TimerFrameCountViewModel extends ViewModel {
    private final TimerFrameCountModel timerFrameCountModel;
    private final Handler changeFrameTimeCnt = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.obj == null) {
                timerFrameCountModel.setFrameCount("");
                timerFrameCountModel.setTimerCount("");
                return;
            }
            FrameCntTime frameCntTime = (FrameCntTime) msg.obj;
            timerFrameCountModel.setFrameCount(String.valueOf(Math.abs(frameCntTime.maxframe - frameCntTime.frame)));
            if (frameCntTime.time * frameCntTime.maxframe > 4.0 || frameCntTime.maxframe == 0) {
                frameCntTime.time = Math.abs(frameCntTime.time * frameCntTime.maxframe - frameCntTime.time * frameCntTime.frame);
                timerFrameCountModel.setTimerCount(((int) (frameCntTime.time / 60) + ":" + ((int) (frameCntTime.time) % 60)));
            }
        }
    };

    public TimerFrameCountViewModel() {
        this.timerFrameCountModel = new TimerFrameCountModel();
    }

    public TimerFrameCountModel getTimerFrameCountModel() {
        return timerFrameCountModel;
    }

    public void setFrameTimeCnt(FrameCntTime frameCntTime) {
        Message msg = new Message();
        switch (PhotonCamera.getSettings().selectedMode) {
            case NIGHT:
            case PHOTO:
            case MOTION:
                break;
            case UNLIMITED:
                frameCntTime.maxframe = 0;
        }
        msg.obj = frameCntTime;
        changeFrameTimeCnt.sendMessage(msg);
    }

    public void setTimerText(String text) {
        timerFrameCountModel.setTimerCount(text);
    }

    public void clearFrameTimeCnt() {
        //mframeCount.setText("");
        //mframeTimer.setText("");
        Message message = new Message();
        changeFrameTimeCnt.sendMessage(message);
    }

    public static class FrameCntTime {
        int frame;
        int maxframe;
        double time;

        public FrameCntTime(int frame, int maxframe, double time) {
            this.frame = frame;
            this.maxframe = maxframe;
            this.time = time;
        }
    }
}
