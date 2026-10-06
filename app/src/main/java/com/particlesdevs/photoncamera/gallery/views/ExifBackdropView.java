package com.particlesdevs.photoncamera.gallery.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Frosted-glass backdrop surface: fills its whole bounds with a bitmap drawn
 * through a {@link BitmapShader} in {@link Shader.TileMode#CLAMP} mode, so
 * sampling beyond the bitmap's rect replicates the edge pixels instead of
 * leaving transparent gaps. An {@code ImageView} with {@code scaleType=matrix}
 * cannot do this — it draws exactly the bitmap rect, and any panel area the
 * transformed rect does not cover renders as an opaque dark bar once the
 * blur/mask RenderEffect composites it. The blur, scrim and rounded mask are
 * applied on top of this view by a RenderEffect chain set from outside.
 */
public class ExifBackdropView extends View {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private BitmapShader shader;
    private final Matrix matrix = new Matrix();

    public ExifBackdropView(Context context) {
        super(context);
    }

    public ExifBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExifBackdropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setBackdropBitmap(Bitmap bitmap) {
        shader = bitmap == null
                ? null
                : new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);
        invalidate();
    }

    public void setBackdropMatrix(Matrix m) {
        matrix.set(m);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (shader == null) {
            return;
        }
        shader.setLocalMatrix(matrix);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
