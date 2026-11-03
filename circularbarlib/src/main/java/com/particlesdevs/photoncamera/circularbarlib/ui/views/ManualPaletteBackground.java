package com.particlesdevs.photoncamera.circularbarlib.ui.views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Background of the manual palette: a single translucent rounded rect covering
 * the option bar plus the slider row(s) above it. The bar's top padding grows
 * when the secondary row appears, so this scrim extends upward with the same
 * rounded edges — no dome math.
 */
public class ManualPaletteBackground extends Drawable {
    private final Paint m_Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path m_Path = new Path();
    private final RectF m_Oval = new RectF();
    private final float m_CornerRadius;

    public ManualPaletteBackground(int color, float cornerRadiusPx) {
        m_Paint.setStyle(Paint.Style.FILL);
        m_Paint.setColor(color);
        m_CornerRadius = cornerRadiusPx;
    }

    public float getCornerRadiusPx() {
        return m_CornerRadius;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        rebuildPath();
    }

    @Override
    public void draw(Canvas canvas) {
        if (!m_Path.isEmpty()) {
            canvas.drawPath(m_Path, m_Paint);
        }
    }

    private void rebuildPath() {
        m_Path.rewind();
        Rect b = getBounds();
        float w = b.width();
        float h = b.height();
        if (w <= 0f || h <= 0f) {
            return;
        }
        float rc = Math.min(m_CornerRadius, Math.min(w, h) / 2f);
        m_Oval.set(b.left, b.top, b.right, b.bottom);
        m_Path.addRoundRect(m_Oval, rc, rc, Path.Direction.CW);
    }

    @Override
    public void setAlpha(int alpha) {
        if (m_Paint.getAlpha() != alpha) {
            m_Paint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return m_Paint.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
