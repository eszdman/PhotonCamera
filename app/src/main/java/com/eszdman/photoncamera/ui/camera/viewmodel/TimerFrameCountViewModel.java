package com.eszdman.photoncamera.ui.camera.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.ui.camera.model.TimerFrameCountModel;

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

    public void setFrameTimeCnt(int cnt, int maxcnt, double frametime) {
        FrameCntTime frameCntTime = new FrameCntTime();
        Message msg = new Message();
        switch (PhotonCamera.getSettings().selectedMode) {
            case NIGHT:
            case PHOTO:
                frameCntTime.frame = cnt;
                frameCntTime.maxframe = maxcnt;
                frameCntTime.time = frametime;
                msg.obj = frameCntTime;
                changeFrameTimeCnt.sendMessage(msg);
                return;
            case UNLIMITED:
                frameCntTime.frame = cnt;
                frameCntTime.maxframe = 0;
                frameCntTime.time = frametime;
                msg.obj = frameCntTime;
                changeFrameTimeCnt.sendMessage(msg);

        }
    }

    public void clearFrameTimeCnt() {
        //mframeCount.setText("");
        //mframeTimer.setText("");
        Message message = new Message();
        changeFrameTimeCnt.sendMessage(message);
    }

    static class FrameCntTime {
        int frame;
        int maxframe;
        double time;
    }
}
