package com.eszdman.photoncamera;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;

public class SurfaceViewOverViewfinder extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "SurfaceViewOverViewfinder";
    public RectF rectToDraw = new RectF();
    private final SurfaceHolder mHolder = this.getHolder();

    public SurfaceViewOverViewfinder(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setZOrderOnTop(true);
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        this.setWillNotDraw(false);
        try {
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas == null) {
                Log.e(TAG, "Canvas is null");
            } else {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);//Clears the canvas
                if (rectToDraw != null && !rectToDraw.isEmpty()) {
                    Paint myPaint = new Paint();
                    myPaint.setColor(Color.rgb(0, 255, 0));
                    myPaint.setStrokeWidth(3);
                    myPaint.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(rectToDraw, myPaint);
                }
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

    }

    public void update(RectF rect) {
        this.rectToDraw = rect;
        surfaceCreated(mHolder);
    }
}

