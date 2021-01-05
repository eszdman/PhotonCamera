package com.eszdman.photoncamera.ui.camera.views.viewfinder;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.eszdman.photoncamera.settings.PreferenceKeys;

public class SurfaceViewOverViewfinder extends SurfaceView {

    private static final String TAG = "SurfaceViewOverViewfinder";
    private final SurfaceHolder mHolder;
    public boolean isCanvasDrawn = false;
    private RectF rectToDraw = new RectF();
    private String debugText = null;

    public SurfaceViewOverViewfinder(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setZOrderOnTop(true);
        mHolder = this.getHolder();
        mHolder.setFormat(PixelFormat.TRANSPARENT);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawGrid(canvas);
        drawRoundEdges(canvas);
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

    public void setMeteringRect(RectF rect) {
        this.rectToDraw = rect;
    }

    public void setDebugText(String debugText) {
        this.debugText = debugText;
    }

    public void refresh() {
        drawOnCanvas(mHolder);
    }

    private void drawOnCanvas(SurfaceHolder surfaceHolder) {
        try {
            Canvas canvas = surfaceHolder.lockHardwareCanvas();
            if (canvas == null) {
                Log.e(TAG, "Canvas is null");
            } else {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);//Clears the canvas
                drawMeteringRect(canvas);
                drawAFDebugText(canvas);
                surfaceHolder.unlockCanvasAndPost(canvas);
                isCanvasDrawn = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clear() {
        try {
            Canvas canvas = mHolder.lockHardwareCanvas();
            if (canvas == null) {
                Log.e(TAG, "Canvas is null");
            } else {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);//Clears the canvas
                mHolder.unlockCanvasAndPost(canvas);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        rectToDraw = null;
        debugText = null;
        isCanvasDrawn = false;
    }

    private void drawAFDebugText(Canvas canvas) {
        if (PreferenceKeys.isAfDataOn()) {
            if (debugText != null) {
                TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.WHITE);
                paint.setTextSize(30);
                paint.setTextAlign(Paint.Align.CENTER);
                int y = 180;
                for (String line : debugText.split("\n")) {
                    canvas.drawText(line, canvas.getWidth() / 2f, y, paint);
                    y += paint.descent() - paint.ascent();
                }
            }
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
}

