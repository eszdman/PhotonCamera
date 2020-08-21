package com.eszdman.photoncamera.gallery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;
import android.util.Size;
import android.view.View;

import static android.graphics.Color.BLUE;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.RED;

public class Histogram extends View {
    private int SIZE = 256;
    private int maxY = 0;
    float offset = 1;
    private int[][] colorsMap;
    public Histogram(Context context) {
        super(context);
    }
    public void Analyze(Bitmap bitmap){
        colorsMap = new int[3][SIZE];
        maxY = 0;
        for(int h = 0; h<bitmap.getHeight()/8;h++){
            for(int w = 0; w<bitmap.getWidth()/8;w++){
                int rgba = bitmap.getPixel(w,h);
                colorsMap[0][((rgba) & 0xff)]++;
                colorsMap[1][((rgba >>  8) & 0xff)]++;
                colorsMap[2][((rgba >>  16) & 0xff)]++;
            }
        }
        //Find max
        for(int i =0; i<SIZE;i++){
            if(maxY < colorsMap[0][i]){
                maxY = colorsMap[0][i];
            }
            if(maxY < colorsMap[1][i]){
                maxY = colorsMap[1][i];
            }
            if(maxY < colorsMap[2][i]){
                maxY = colorsMap[2][i];
            }
        }
        bitmap.recycle();

    }
    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        Log.d("NIRAV", "Height : " + getHeight() + ", Width : " + getWidth());
        float xInterval = ((float) getWidth() / ((float) SIZE + 1));
        for (int i = 0; i < 3; i++) {
            Paint wallpaint;
            wallpaint = new Paint();

            if (i == 0) {
                wallpaint.setColor(RED);
            } else if (i == 1) {
                wallpaint.setColor(GREEN);
            } else {
                wallpaint.setColor(BLUE);
            }
            wallpaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            wallpaint.setStyle(Paint.Style.FILL);
            Path wallpath = new Path();
            wallpath.reset();
            wallpath.moveTo(0, getHeight());
            for (int j = 0; j < SIZE; j++) {
                int value = (int) (((double) colorsMap[i][j]) * ((double) (getHeight())/maxY));
                //if(j==0) {
                //   wallpath.moveTo(j * xInterval* offset, getHeight() - value);
                //}
                // else {
                wallpath.lineTo(j * xInterval, getHeight() - value);
                // }
            }
            wallpath.lineTo(SIZE * offset, getHeight());
            canvas.drawPath(wallpath, wallpaint);
        }

    }
}