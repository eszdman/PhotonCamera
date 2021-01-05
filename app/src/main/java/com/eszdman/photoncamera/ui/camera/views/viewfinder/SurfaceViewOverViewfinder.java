package com.eszdman.photoncamera.ui.camera.views.viewfinder;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.eszdman.photoncamera.settings.PreferenceKeys;

public class SurfaceViewOverViewfinder extends SurfaceView {

    private static final String TAG = "SurfaceViewOverViewfinder";
    private final SurfaceHolder mHolder;
    private final float screenRatio;
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    public boolean isCanvasDrawn = false;
    private RectF rectToDraw = new RectF();
    private String debugText = null;

    public SurfaceViewOverViewfinder(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setZOrderOnTop(true);
        mHolder = this.getHolder();
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenRatio = (float) Math.max(dm.heightPixels, dm.widthPixels) / Math.min(dm.heightPixels, dm.widthPixels);
        initPaints();
    }

    private void initPaints() {
        whitePaint.setColor(Color.WHITE);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30);
        textPaint.setTextAlign(Paint.Align.CENTER);

        rectPaint.setColor(Color.GREEN);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(3);
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
            canvas.drawLine(w / 3f, 0, w / 3f, h, whitePaint);
            canvas.drawLine(2.f * w / 3f, 0, 2f * w / 3f, h, whitePaint);
            canvas.drawLine(0, h / 3f, w, h / 3f, whitePaint);
            canvas.drawLine(0, 2f * h / 3f, w, 2f * h / 3f, whitePaint);
        }
    }

    private void drawRoundEdges(Canvas canvas) {
        if (PreferenceKeys.isRoundEdgeOn()) {
            path.reset();
            path.addRoundRect(new RectF(canvas.getClipBounds()), 40, 40, Path.Direction.CW);
            path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
            canvas.clipPath(path);
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
                int y = 180;
                if (screenRatio > 16 / 9f) y = 50;
                for (String line : debugText.split("\n")) {
                    canvas.drawText(line, canvas.getWidth() / 2f, y, textPaint);
                    y += textPaint.descent() - textPaint.ascent();
                }
            }
        }
    }

    private void drawMeteringRect(Canvas canvas) {
        if (PreferenceKeys.isAfDataOn()) {
            if (rectToDraw != null && !rectToDraw.isEmpty()) {
                canvas.drawRect(rectToDraw, rectPaint);
            }
        }
    }
}

