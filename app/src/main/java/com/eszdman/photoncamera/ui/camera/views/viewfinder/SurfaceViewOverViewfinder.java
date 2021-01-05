package com.eszdman.photoncamera.ui.camera.views.viewfinder;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;
import com.eszdman.photoncamera.settings.PreferenceKeys;

public class SurfaceViewOverViewfinder extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "SurfaceViewOverViewfinder";
    private final SurfaceHolder mHolder = this.getHolder();
    public RectF rectToDraw = new RectF();

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
                drawGrid(canvas);
                drawMeteringRect(canvas);
                drawRoundEdges(canvas);
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

    private void drawGrid(Canvas canvas) {
        if (PreferenceKeys.isShowGridOn()) {
            int w = canvas.getWidth();
            int h = canvas.getHeight();
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            canvas.drawLine(w / 3f, 0, w / 3f, h, paint);
            canvas.drawLine(2.f * w / 3f, 0, 2f * w / 3f, h, paint);
            canvas.drawLine(0, h / 3f, w, h / 3f, paint);
            canvas.drawLine(0, 2f * h / 3f, w, 2f * h / 3f, paint);
        }
    }

    private void drawRoundEdges(Canvas canvas) {
        if (PreferenceKeys.isRoundEdgeOn()) {
            Path mPath = new Path();
            mPath.reset();
            mPath.addRoundRect(new RectF(canvas.getClipBounds()), 40, 40, Path.Direction.CW);
            mPath.setFillType(Path.FillType.INVERSE_EVEN_ODD);
            canvas.clipPath(mPath);
            canvas.drawColor(Color.BLACK);
        }
    }

    private void drawMeteringRect(Canvas canvas) {
        if (PreferenceKeys.isAfDataOn()) {
            if (rectToDraw != null && !rectToDraw.isEmpty()) {
                Paint myPaint = new Paint();
                myPaint.setColor(Color.rgb(0, 255, 0));
                myPaint.setStrokeWidth(3);
                myPaint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(rectToDraw, myPaint);
            }
        }
    }

    public void setMeteringRect(RectF rect) {
        this.rectToDraw = rect;
        refresh();
    }

    public void refresh() {
        surfaceCreated(mHolder);
    }
}

