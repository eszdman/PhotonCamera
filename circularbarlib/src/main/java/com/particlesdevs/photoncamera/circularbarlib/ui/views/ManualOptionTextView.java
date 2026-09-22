package com.particlesdevs.photoncamera.circularbarlib.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * Manual-bar option label that can mark a touched (manual) value with a small
 * dot. The dot is painted to the left of the centered text instead of being
 * part of the string, so the value keeps its exact centered position in both
 * the auto/live and the touched states.
 */
public class ManualOptionTextView extends AppCompatTextView {

    private static final float DOT_RADIUS_DP = 1.75f;
    private static final float DOT_GAP_DP = 2.5f;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean touched;

    public ManualOptionTextView(Context context) {
        this(context, null);
    }

    public ManualOptionTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public ManualOptionTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
    }

    /** Shows (or hides) the touched marker dot left of the centered value. */
    public void setTouched(boolean touched) {
        if (this.touched != touched) {
            this.touched = touched;
            invalidate();
        }
    }

    public boolean isTouched() {
        return touched;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!touched) {
            return;
        }
        Layout layout = getLayout();
        if (layout == null || layout.getLineCount() == 0) {
            return;
        }
        float textLeft = getCompoundPaddingLeft() + layout.getLineLeft(0);
        float centerY = getExtendedPaddingTop()
                + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f;
        dotPaint.setColor(getCurrentTextColor());
        canvas.drawCircle(textLeft - DOT_GAP_DP * density - DOT_RADIUS_DP * density,
                centerY, DOT_RADIUS_DP * density, dotPaint);
    }
}
