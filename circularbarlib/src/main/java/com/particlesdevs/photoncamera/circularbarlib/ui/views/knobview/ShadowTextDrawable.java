package com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

public class ShadowTextDrawable extends Drawable {
    private final ShadowTextRenderer m_Renderer = new ShadowTextRenderer();
    private final Rect m_TextBounds = new Rect();

    public ShadowTextDrawable() {
    }

    public ShadowTextDrawable(CharSequence text) {
        this.m_Renderer.setText(text);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        this.m_Renderer.getBounds(this.m_TextBounds);
        if (!this.m_TextBounds.isEmpty()) {
            this.m_Renderer.draw(canvas, (float) (bounds.left + ((bounds.width() - this.m_TextBounds.width()) / 2)), (float) (bounds.top + ((bounds.height() - this.m_TextBounds.height()) / 2)));
        }    }

    @Override
    public int getIntrinsicHeight() {
        this.m_Renderer.getBounds(this.m_TextBounds);
        return this.m_TextBounds.height();
    }

    @Override
    public int getIntrinsicWidth() {
        this.m_Renderer.getBounds(this.m_TextBounds);
        return this.m_TextBounds.width();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    public void getTextBounds(Rect textBounds) {
        this.m_Renderer.getBounds(this.m_TextBounds);
        textBounds.set(this.m_TextBounds);
    }

    @Override
    public void setAlpha(int alpha) {
        this.m_Renderer.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        this.m_Renderer.setColorFilter(filter);
    }

    public void setShadow(float radius, float dx, float dy, int color) {
        this.m_Renderer.setShadow(radius, dx, dy, color);
    }


    public void setText(CharSequence text) {
        this.m_Renderer.setText(text);
    }

    public void setTextAppearance(Context context, int resId) {
        this.m_Renderer.setTextAppearance(context, resId);
    }

    public void setTextColor(int color) {
        this.m_Renderer.setColor(color);
    }

    public void setTextSize(float size) {
        this.m_Renderer.setTextSize(size);
    }

    public void setTypeface(Typeface typeface) {
        this.m_Renderer.setTypeface(typeface);
    }
}
