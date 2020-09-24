package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import android.graphics.*;
import android.util.Log;
import com.eszdman.photoncamera.OpenGL.*;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.settings.PreferenceKeys;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import static com.eszdman.photoncamera.processing.ImageSaver.imageFileToSave;

public class PostPipeline extends GLBasePipeline {
    public ByteBuffer stackFrame;
    public ByteBuffer lowFrame;
    public ByteBuffer highFrame;
    GLTexture noiseMap;
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
        Bitmap watermark = BitmapFactory.decodeResource(PhotonCamera.getMainActivity().getResources(), R.drawable.photoncamera_watermark);
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
        int rotation = PhotonCamera.getGravity().getCameraRotation();
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCameraFragment().mSensorOrientation);
        int orientation = 0;
        switch (rotation) {
            case 90:
                orientation = 90;
                break;
            case 180:
                orientation = 180;
                break;
            case 270:
                orientation = 270;
                break;
        }
        return orientation;
    }
    public void Run(ByteBuffer inBuffer, Parameters parameters){
        Bitmap output = Bitmap.createBitmap(parameters.rawSize.x,parameters.rawSize.y, Bitmap.Config.ARGB_8888);
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(parameters.rawSize,output, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        glint = new GLInterface(glproc);
        stackFrame = inBuffer;
        glint.parameters = parameters;
        if(!IsoExpoSelector.HDR) {
            if (PhotonCamera.getSettings().cfaPattern != -2) {
                add(new DemosaicPart1(R.raw.demosaicp1, "Demosaic Part 1"));
                //add(new Debug3(R.raw.debugraw,"Debug3"));
                add(new DemosaicPart2(R.raw.demosaicp2, "Demosaic Part 2"));
            } else {
                add(new MonoDemosaic(R.raw.monochrome, "Monochrome"));
            }
        } else {
            add(new LFHDR(0, "LFHDR"));
        }
        add(new Initial(R.raw.initial,"Initial"));
        if(PhotonCamera.getSettings().hdrxNR) {
            add(new NoiseDetection(R.raw.noisedetection44,"NoiseDetection"));
            add(new NoiseMap(R.raw.gaussdown44,"GaussDownMap"));
            add(new BlurMap(R.raw.gaussblur33,"GaussBlurMap"));
            add(new BilateralColor(R.raw.bilateralcolor, "BilateralColor"));
            add(new Bilateral(R.raw.bilateral, "Bilateral"));
        }
        add(new Sharpen(selectSharp(),"Sharpening"));
        //add(new Debug3(R.raw.debugraw,"Debug3"));

        Bitmap img = runAll();
        img = RotateBitmap(img,getRotation());
        if (PreferenceKeys.isShowWatermarkOn()) addWatermark(img,0.06f);
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
