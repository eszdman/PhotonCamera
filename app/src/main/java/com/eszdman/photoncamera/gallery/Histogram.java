package com.eszdman.photoncamera.gallery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.View;

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
        for(int h = 0; h<bitmap.getHeight();h++){
            for(int w = 0; w<bitmap.getWidth();w++){
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
        int width = getWidth();
        int height = getHeight();
        float xInterval =((float)getWidth()/((float)SIZE+1));
        Paint wallpaint;
        wallpaint = new Paint();
        wallpaint.setAntiAlias(true);
        wallpaint.setStyle(Paint.Style.STROKE);
        wallpaint.setARGB(100,255,255,255);
        canvas.drawRect(0,0,width,height,wallpaint);
        canvas.drawLine(width/3.f,0,width/3.f,height,wallpaint);
        canvas.drawLine(2.f*width/3.f,0,2.f*width/3.f,height,wallpaint);
        Path wallpath = new Path();
        for (int i = 0; i < 3; i++) {
                if (i == 0) {
                    //wallpaint.setColor(0xFF0700);
                    wallpaint.setARGB(0xFF,0xFF,0x07,0x00);
                } else if (i == 1) {
                    //wallpaint.setColor(0x1924B1);
                    wallpaint.setARGB(0xFF,0x19,0x24,0xB1);
                } else {
                    //wallpaint.setColor(0x00C90D);
                    wallpaint.setARGB(0xFF,0x00,0xC9,0x0D);
                }
            wallpaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            wallpaint.setStyle(Paint.Style.FILL);
            wallpath.reset();
            wallpath.moveTo(0, height);
            for (int j = 0; j < SIZE; j++) {
                float value = (((float)colorsMap[i][j])*((float)(height)/maxY));
                wallpath.lineTo(j * xInterval, height - value);
            }
            wallpath.lineTo(SIZE * offset, height);
            canvas.drawPath(wallpath, wallpaint);
        }

    }
}
