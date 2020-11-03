package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.*;
import android.util.Log;

import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.processing.opengl.*;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.settings.PreferenceKeys;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import static com.eszdman.photoncamera.processing.ImageSaver.imageFileToSave;

public class PostPipeline extends GLBasePipeline {
    public ByteBuffer stackFrame;
    public ByteBuffer lowFrame;
    public ByteBuffer highFrame;
    /**
     * Embeds an image watermark over a source image to produce
     * a watermarked one.
     * @param source The source image where watermark should be placed
     * @param ratio A float value < 1 to give the ratio of watermark's height to image's height,
     *             try changing this from 0.20 to 0.60 to obtain right results
     */
    public static void addWatermark(Bitmap source, float ratio) {
        Canvas canvas;
        Paint paint;
        Matrix matrix;
        RectF r;
        Bitmap watermark = BitmapFactory.decodeResource(PhotonCamera.getCameraActivity().getResources(), R.drawable.photoncamera_watermark);
        int width, height;
        float scale;
        width = source.getWidth();
        height = source.getHeight();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        // Copy the original bitmap into the new one
        canvas = new Canvas(source);
        canvas.drawBitmap(source, 0, 0, paint);
        // Scale the watermark to be approximately to the ratio given of the source image height
        scale = ((float) height * ratio) / (float) watermark.getHeight();
        // Create the matrix
        matrix = new Matrix();
        matrix.postScale(scale, scale);
        // Determine the post-scaled size of the watermark
        r = new RectF(0, 0, watermark.getWidth(), watermark.getHeight());
        matrix.mapRect(r);
        // Move the watermark to the bottom right corner
        matrix.postTranslate(15, height - r.height());
        // Draw the watermark
        canvas.drawBitmap(watermark, matrix, paint);
    }
    public int selectSharp(){
        long resolution = glint.parameters.rawSize.x*glint.parameters.rawSize.y;
        int output = R.raw.sharpen33;
        if(resolution >= 16000000) output = R.raw.sharpen55;
        return output;
    }
    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Log.d("PostPipeline","Rotation:"+angle);
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
    public int getRotation() {
        int rotation = PhotonCamera.getParameters().cameraRotation;
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCameraFragment().mSensorOrientation);
        return rotation;
    }
    private Point getRotatedCoords(Point in){
        switch (getRotation()){
            case 0:
                return in;
            case 90:
                return new Point(in.y,in.x);
            case 180:
                return in;
            case 270:
                return new Point(in.y,in.x);
        }
        return in;
    }
    public void Run(ByteBuffer inBuffer, Parameters parameters){
        Point rotated = getRotatedCoords(parameters.rawSize);
        Bitmap output = Bitmap.createBitmap(rotated.x,rotated.y, Bitmap.Config.ARGB_8888);
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(rotated,output, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        glint = new GLInterface(glproc);
        stackFrame = inBuffer;
        glint.parameters = parameters;
        if(!IsoExpoSelector.HDR) {
            if (PhotonCamera.getSettings().cfaPattern != -2) {
                add(new Demosaic("Demosaic"));
                //add(new Debug3(R.raw.debugraw,"Debug3"));
            } else {
                add(new MonoDemosaic(R.raw.monochrome, "Monochrome"));
            }
        } else {
            add(new LFHDR(0, "LFHDR"));
        }
        /*
         * * * All filters after demosaicing * * *
         */
        add(new ExposureFusion("ExposureFusion"));
        add(new Initial(R.raw.initial,"Initial"));
        add(new AWB(0,"AWB"));

        //add(new GlobalToneMapping(0,"GlobalTonemap"));

        if(PhotonCamera.getSettings().hdrxNR) {
            add(new SmartNR("SmartNR"));
            //add(new Bilateral(R.raw.bilateral, "Bilateral"));
            //add(new Median(R.raw.medianfilter,"SmartMedian"));
            if(PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT){
                    for(int i =1; i<5;i++){
                    add(new Median(new Point(i,i/2),"FastMedian"));
                    add(new Median(new Point(i/2,i),"FastMedian"));
                    //add(new Median(new Point(i,i),"FastMedian"));
                }
            }
        }
        //if(PhotonCamera.getParameters().focalLength <= 3.0)
        //add(new LensCorrection());
        add(new Sharpen(selectSharp(),"Sharpening"));
        add(new Watermark(getRotation(),PreferenceKeys.isShowWatermarkOn()));
        //add(new ShadowTexturing(R.raw.shadowtexturing,"Shadow Texturing"));
        //add(new Debug3(R.raw.debugraw,"Debug3"));

        Bitmap img = runAll();
        //img = RotateBitmap(img,getRotation());
        //if (PreferenceKeys.isShowWatermarkOn()) addWatermark(img,0.06f);
        //Canvas canvas = new Canvas(img);
        //canvas.drawBitmap(img, 0, 0, null);
        //canvas.drawBitmap(waterMark, 0, img.getHeight()-400, null);
        try {
            //noinspection ResultOfMethodCallIgnored
            imageFileToSave.createNewFile();
            FileOutputStream fOut = new FileOutputStream(imageFileToSave);
            img.compress(Bitmap.CompressFormat.JPEG, 97, fOut);
            fOut.flush();
            fOut.close();
            img.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
