package com.manual;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.Paint.Style;
import android.os.Build;
import android.util.Size;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ShadowTextRenderer {
    private final Rect m_MeasuredTextBounds;
    private final Paint m_StrokePaint;
    private final Paint m_TextPaint;
    private boolean m_HasStroke;
    private Size m_MaximumSize;
    private String m_Text;
    private float m_TextSize;

    public ShadowTextRenderer() {
        this(null);
    }

    public ShadowTextRenderer(CharSequence text) {
        this.m_HasStroke = false;
        this.m_MaximumSize = new Size(-1, -1);
        this.m_MeasuredTextBounds = new Rect();
        this.m_TextPaint = new Paint(1);
        this.m_TextPaint.setStyle(Style.FILL);
        this.m_StrokePaint = new Paint(1);
        this.m_StrokePaint.setStyle(Style.STROKE);
        this.m_Text = text != null ? text.toString() : null;
    }

    public void draw(Canvas canvas, float left, float top) {
        if (canvas == null) {
            throw new IllegalArgumentException("Canvas could not be null");
        } else if (this.m_Text != null) {
            int length = this.m_Text.length();
            if (length != 0) {
                //measure();
                float bottom = top + ((float) this.m_MeasuredTextBounds.height());
                canvas.drawText(this.m_Text, 0, length, left, bottom, this.m_TextPaint);
                if (this.m_HasStroke) {
                    canvas.drawText(this.m_Text, 0, length, left, bottom, this.m_StrokePaint);
                }
            }
        }
    }

    public Rect getBounds() {
        measure();
        return new Rect(this.m_MeasuredTextBounds);
    }

    public void getBounds(Rect bounds) {
        if (bounds != null) {
            measure();
            bounds.set(this.m_MeasuredTextBounds);
        }
    }

    public String getText() {
        return this.m_Text;
    }

    public void setText(CharSequence text) {
        this.m_Text = text != null ? text.toString() : null;
        this.m_MeasuredTextBounds.setEmpty();
    }

    public float getTextSize() {
        return this.m_TextSize;
    }

    public void setTextSize(float size) {
        if (((double) Math.abs(this.m_TextPaint.getTextSize() - size)) >= 0.1d) {
            this.m_TextSize = size;
            this.m_TextPaint.setTextSize(size);
            this.m_StrokePaint.setTextSize(size);
            this.m_MeasuredTextBounds.setEmpty();
        }
    }

    private Typeface getTypefaceFromAttrs(String familyName) {
        return getTypefaceFromAttrs(familyName, 0);
    }

    private Typeface getTypefaceFromAttrs(String familyName, int style) {
        if (familyName == null) {
            return Typeface.defaultFromStyle(style);
        }
        Typeface typeface = Typeface.create(familyName, style);
        return typeface != null ? typeface : Typeface.SANS_SERIF;
    }

    private void measure() {
        if (this.m_TextSize > 0.0f && this.m_MeasuredTextBounds.isEmpty() && this.m_Text != null && this.m_Text.length() != 0) {
            this.m_TextPaint.setTextSize(this.m_TextSize);
            this.m_StrokePaint.setTextSize(this.m_TextSize);
            measure(this.m_TextSize);
        }
    }

    private void measure(float currentTextSize) {
        this.m_TextPaint.getTextBounds(this.m_Text, 0, this.m_Text.length(), this.m_MeasuredTextBounds);
        this.m_MeasuredTextBounds.left -= 5;
        this.m_MeasuredTextBounds.right += 5;
        if (this.m_MaximumSize.getWidth() >= 0 && this.m_MaximumSize.getHeight() >= 0 && currentTextSize > 12.0f && this.m_MeasuredTextBounds.width() > this.m_MaximumSize.getWidth()) {
            float currentTextSize2 = currentTextSize - 1.0f;
            this.m_TextPaint.setTextSize(currentTextSize2);
            this.m_StrokePaint.setTextSize(currentTextSize2);
            //measure(currentTextSize2);
        }
    }

    public void setAlpha(int alpha) {
        this.m_TextPaint.setAlpha(alpha);
        this.m_StrokePaint.setAlpha(alpha);
    }

    public void setColor(int color) {
        this.m_TextPaint.setColor(color);
    }

    public void setColorFilter(ColorFilter filter) {
        this.m_TextPaint.setColorFilter(filter);
    }

    public void setMaximumSize(int width, int height) {
        this.m_MaximumSize = new Size(width, height);
        this.m_MeasuredTextBounds.setEmpty();
    }

    public void setShadow(float radius, float dx, float dy, int color) {
        if (radius <= 0.0f) {
            this.m_TextPaint.setShadowLayer(0.0f, 0.0f, 0.0f, -1);
        } else {
            this.m_TextPaint.setShadowLayer(radius, dx, dy, color);
        }
    }

    public void setStroke(boolean hasStroke) {
        this.m_HasStroke = hasStroke;
    }

    public void setTextAppearance(Context context, int resId) {

        TypedArray style = context.obtainStyledAttributes(resId, new int[]{android.R.attr.textSize, android.R.attr.textStyle, android.R.attr.textColor, android.R.attr.shadowColor, android.R.attr.shadowDx, android.R.attr.shadowDy, android.R.attr.shadowRadius, android.R.attr.fontFamily});
        boolean isTextSizeChanged = false;
        if (style.hasValue(0)) {
            this.m_TextSize = style.getDimension(0, -1.0f);
            this.m_TextPaint.setTextSize(this.m_TextSize);
            this.m_StrokePaint.setTextSize(this.m_TextSize);
            isTextSizeChanged = false;
        }
        if (style.hasValue(2)) {
            this.m_TextPaint.setColor(style.getColor(2, -1));
        }
        if (style.hasValue(1) || style.hasValue(7)) {
            Typeface typeface = getTypefaceFromAttrs(style.getString(7), style.getInt(1, -1));
            this.m_TextPaint.setTypeface(typeface);
            this.m_StrokePaint.setTypeface(typeface);
            isTextSizeChanged = true;
        }
        if (style.hasValue(3) && style.hasValue(6)) {
            int shadowColor = style.getInt(3, -1);
            this.m_TextPaint.setShadowLayer(style.getFloat(6, -1.0f), style.getFloat(4, 0.0f), style.getFloat(5, 0.0f), shadowColor);
        }
        if (isTextSizeChanged) {
            this.m_MeasuredTextBounds.setEmpty();
        }
        style.recycle();
    }

    public void setTypeface(Typeface typeface) {
        this.m_TextPaint.setTypeface(typeface);
        this.m_StrokePaint.setTypeface(typeface);
        this.m_MeasuredTextBounds.setEmpty();
    }
}
