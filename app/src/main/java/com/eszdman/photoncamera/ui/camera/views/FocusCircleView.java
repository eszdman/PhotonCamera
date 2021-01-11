package com.eszdman.photoncamera.ui.camera.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.eszdman.photoncamera.R;

/**
 * Created by Vibhor on 11/01/2021
 */
public class FocusCircleView extends View {
    private static final int[] STATE_FOCUSED_LOCKED = {R.attr.focused_locked};
    private static final int[] STATE_UNFOCUSED_LOCKED = {R.attr.unfocused_locked};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ColorStateList colorStateList;
    private boolean focused_locked;
    private boolean unfocused_locked;

    public FocusCircleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.FocusCircleView,
                0, 0
        );
        colorStateList = a.getColorStateList(R.styleable.FocusCircleView_android_color);
        float thickness = a.getDimension(R.styleable.FocusCircleView_android_thickness, 2.5f);
        if (colorStateList == null) {
            colorStateList = ColorStateList.valueOf(Color.WHITE);
        }
        a.recycle();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(getPaintColor());
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2.5f, paint);
    }

    private int getPaintColor() {
        if (focused_locked)
            return colorStateList.getColorForState(STATE_FOCUSED_LOCKED, colorStateList.getDefaultColor());
        else if (unfocused_locked)
            return colorStateList.getColorForState(STATE_UNFOCUSED_LOCKED, colorStateList.getDefaultColor());
        else
            return colorStateList.getDefaultColor();
    }

    public void setAfState(int afState) {
        focused_locked = false;
        unfocused_locked = false;
        switch (afState) {
            case 4:
                focused_locked = true;
                break;
            case 5:
                unfocused_locked = true;
                break;
            default:
                break;
        }
        invalidate();
    }
}
