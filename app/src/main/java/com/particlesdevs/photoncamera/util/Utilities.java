package com.particlesdevs.photoncamera.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.core.content.res.ResourcesCompat;

import com.particlesdevs.photoncamera.processing.ImagePath;
import com.particlesdevs.photoncamera.processing.ImageSaver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import static java.lang.Math.min;
//import static java.lang.Math.max;

public class Utilities {
    private static final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.ADD);

    public static Bitmap drawKernels(float[][][] inputKernels, Point kernelSize, Point kernelCount){
        int width = kernelSize.x*kernelCount.x;
        int height = kernelSize.y*kernelCount.y;
        Bitmap output = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);
        Paint pointPaint = new Paint();
        pointPaint.setAntiAlias(true);
        pointPaint.setStyle(Paint.Style.FILL);
        for(int i =0; i<inputKernels.length;i++)
            for(int j =0; j<inputKernels[i].length;j++)
                for(int k =0; k<inputKernels[i][j].length;k++){
                    int x = k%kernelSize.x;
                    int y = k/kernelSize.x;
                    int br = min((int)(inputKernels[i][j][k]*255),255);
                    pointPaint.setARGB(255,br,br,br);
                    //canvas.drawPoint(i*kernelSize.x + kernelSize.x/2.f + x,j*kernelSize.y + kernelSize.y/2.f +y,pointPaint);
                    canvas.drawCircle(i*kernelSize.x + x,j*kernelSize.y + y,5.f,pointPaint);
                }
        pointPaint.setARGB(100, 255, 255, 0);
        for(int i =1; i<kernelCount.x;i++)
            canvas.drawLine(i*kernelSize.x,0,i*kernelSize.x+2,kernelSize.y*kernelCount.y,pointPaint);
        for(int i =1; i<kernelCount.y;i++)
            canvas.drawLine(0,i*kernelSize.y,kernelSize.x*kernelCount.x,i*kernelSize.y+2,pointPaint);

        return output;
    }
    public static void drawPoints(Point[] inputPoints, float pointSize,Bitmap io){
        Canvas canvas = new Canvas(io);
        Paint wallPaint = new Paint();
        wallPaint.setAntiAlias(true);
        wallPaint.setStyle(Paint.Style.FILL);
        wallPaint.setARGB(255, 0, 255, 0);
        for(Point p : inputPoints)
            canvas.drawCircle(p.x,p.y,pointSize,wallPaint);
    }
    public static void saveBitmap(Bitmap in, String name){
        File debug = new File(ImagePath.newJPGFilePath().toString().replace(".jpg","") + name + ".png");
        FileOutputStream fOut = null;
        try {
            debug.createNewFile();
            fOut = new FileOutputStream(debug);
        } catch (IOException e) {
            e.printStackTrace();
        }
        in.compress(Bitmap.CompressFormat.PNG, 100, fOut);
    }
    public static void drawBL(float[] rgb, Bitmap io){
        float max = 0.f;
        int width = io.getWidth();
        int height = io.getHeight();
        Canvas canvas = new Canvas(io);
        Paint wallPaint = new Paint();
        wallPaint.setAntiAlias(true);
        wallPaint.setStyle(Paint.Style.FILL);
        wallPaint.setARGB(255, (int)(rgb[0]*255.f), (int)(rgb[1]*255.f), (int)(rgb[2]*255.f));
        canvas.drawRect(width*0.50f, height, width*0.50f+32.f, height-32, wallPaint);
    }
    public static void drawWB(float[] rgb, Bitmap io){
        float max = 1f;//max(max(rgb[0],rgb[1]),rgb[2]);
        //max = 1.f;
        int width = io.getWidth();
        int height = io.getHeight();
        Canvas canvas = new Canvas(io);
        Paint wallPaint = new Paint();
        wallPaint.setAntiAlias(true);
        wallPaint.setStyle(Paint.Style.FILL);
        wallPaint.setARGB(255, (int)(rgb[0]*255.f/max), (int)(rgb[1]*255.f/max), (int)(rgb[2]*255.f/max));
        canvas.drawRect(height, width*0.50f, height-32, width*0.50f+32.f, wallPaint);
    }

    private static float[] convertToFloatArray(int[] input) {
        float[] floatArray = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            floatArray[i] = (float) input[i];
        }
        return floatArray;
    }

    private static float findMaxValue(float[] data) {
        float max = 0.f;
        for (float value : data) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private static Paint setWallPaint(Paint wallPaint){
        wallPaint.setAntiAlias(true);
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setARGB(100, 255, 255, 255);
        return wallPaint;
    }
    private static Canvas drawCanvas(Bitmap output, Paint wallPaint){
        int width = output.getWidth();
        int height = output.getHeight();
        Canvas canvas = new Canvas(output);
        canvas.drawRect(0, 0, width, height, wallPaint);
        canvas.drawLine(width / 3.f, 0, width / 3.f, height, wallPaint);
        canvas.drawLine(2.f * width / 3.f, 0, 2.f * width / 3.f, height, wallPaint);
        return canvas;
    }

    private static void drawGraph(float[] data, int width, int height, float max, Paint paint, Canvas canvas, int r, int g, int b) {
        float xInterval = ((float) width / ((float) data.length + 1));
        Path path = new Path();
        paint.setARGB(255, r, g, b);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(0, height);
        for (int j = 0; j < data.length; j++) {
            float value = ((data[j]) * ((float) (height) / max));
            path.lineTo(j * xInterval, height - value);
        }
        path.lineTo(data.length * xInterval, height);
        canvas.drawPath(path, paint);
    }

    public static void drawArray(float[] input, Bitmap output){
        float max = findMaxValue(input);
        int width = output.getWidth();
        int height = output.getHeight();
        Paint wallPaint = new Paint();
        wallPaint = setWallPaint(wallPaint);
        Canvas canvas = drawCanvas(output, wallPaint);
        drawGraph(input, width, height, max, wallPaint, canvas, 255, 255, 255);
    }

    public static void drawArray(int[] input, Bitmap output){
        float[] input_float = convertToFloatArray(input);
        drawArray(input_float, output);
    }

    public static void drawArray(float[] r,float[] g,float[] b, Bitmap output){
        int width = output.getWidth();
        int height = output.getHeight();
        Paint wallPaint = new Paint();
        wallPaint = setWallPaint(wallPaint);
        Canvas canvas = drawCanvas(output, wallPaint);

        float max = findMaxValue(r);
        drawGraph(r, width, height, max, wallPaint, canvas, 255, 0, 0);

        max = findMaxValue(g);
        drawGraph(g, width, height, max, wallPaint, canvas, 0, 255, 0);

        max = findMaxValue(b);
        drawGraph(b, width, height, max, wallPaint, canvas, 0, 0, 255);

    }

    public static void drawArray(int[] r,int[] g,int[] b, Bitmap output){
        float[] r_float = convertToFloatArray(r);
        float[] g_float = convertToFloatArray(g);
        float[] b_float = convertToFloatArray(b);
        drawArray(r_float, g_float, b_float, output);
    }

    public static float linearRegressionK(float[] input){
        float k = 0.f;
        float cnt = 0.f;
        for(int i = 1; i<input.length;i++){
            float x = (float)(i)/input.length;
            k+=input[i]/x;
            cnt+=1.f;
        }
        k/=cnt;
        return k;
    }
    public static float linearRegressionC(float[] input){
        float k = linearRegressionK(input);
        float cnt = 0.f;
        float c = 0.f;
        cnt = 0.f;
        for(int i = 1; i<input.length;i++){
            float x = (float)(i)/input.length;
            c+=input[i] - x*k;
            cnt+=1.f;
        }
        c/=cnt;
        return c;
    }

    public static float[] interpolateArr(float[] in, int requiredSize){
        float[] output = new float[requiredSize];
        ArrayList<Float> mY,mx;
        mY = new ArrayList<>();
        mx = new ArrayList<>();
        for(int xi = 0; xi<in.length; xi++){
            mx.add((float)xi/(float)(in.length-1));
            mY.add(in[xi]);
        }
        SplineInterpolator splineInterpolator = SplineInterpolator.createMonotoneCubicSpline(mx,mY);
        for(int i =0; i<output.length;i++)
            output[i] = splineInterpolator.interpolate(i/(float)(output.length-1));
        return output;
    }
    public static float[] interpolateTonemap(float[] in, int requiredSize){
        float[] output = new float[requiredSize];
        ArrayList<Float> mY,mx;
        mY = new ArrayList<>();
        mx = new ArrayList<>();
        for(int xi = 0; xi<in.length; xi+=2){
            float line = xi / (in.length-1.f);
            mx.add(in[xi]);
            mY.add((float) Math.pow(line,1.0/2.0));
        }
        SplineInterpolator splineInterpolator = SplineInterpolator.createMonotoneCubicSpline(mx,mY);
        for(int i =0; i<output.length;i++)
            output[i] = splineInterpolator.interpolate(i/(float)(output.length-1));
        return output;
    }
    public static float luminocity(float[] in){
        return (in[0]*0.299f+in[1]*0.587f+in[2]*0.114f);
    }
    public static float[] saturate(float[] in, float saturation){
        float br = luminocity(in);
        float[] vec = new float[]{in[0],in[1],in[2]};
        vec[0]=br*(-saturation) + vec[0]*(1.f+saturation);
        vec[1]=br*(-saturation) + vec[1]*(1.f+saturation);
        vec[2]=br*(-saturation) + vec[2]*(1.f+saturation);
        float min = min(min(vec[0],vec[1]),vec[2]);
        /*if(min < 0.f) {
            vec[0] -= min;
            vec[1] -= min;
            vec[2] -= min;
        }*/
        //float br2 = luminocity(vec);
        return vec;
    }
    public static Point div(Point in, int divider){
        return new Point(in.x/divider,in.y/divider);
    }
    public static Point mpy(Point in, int mpy){
        return new Point(in.x*mpy,in.y*mpy);
    }
    public static Point addP(Point in, int add){
        return new Point(in.x+add,in.y+add);
    }

    public static String formatExposureTime(final double value) {
        String output;

        if (value < 1.0f)
            output = String.format(Locale.getDefault(), "%d/%d", 1, (int) (0.5f + 1 / value));
        else {
            final int integer = (int) value;
            final double time = value - integer;
            output = String.format(Locale.getDefault(), "%d''", integer);

            if (time > 0.0001f)
                output += String.format(Locale.getDefault(), " %d/%d", 1, (int) (0.5f + 1 / time));
        }

        return output;
    }

    @ColorInt
    public static int resolveColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    public static Drawable resolveDrawable(Context context, int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int attributeResourceId = a.getResourceId(0, 0);
        return ResourcesCompat.getDrawable(context.getResources(), attributeResourceId, context.getTheme());
    }

    public static int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
    }
}
