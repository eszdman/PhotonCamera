package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.content.res.AssetManager;
import android.graphics.*;
import android.os.FileUtils;
import android.util.Log;

import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.processing.opengl.*;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.util.FileManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Properties;

import static com.eszdman.photoncamera.processing.ImageSaver.imageFileToSave;

public class PostPipeline extends GLBasePipeline {
    public ByteBuffer stackFrame;
    public ByteBuffer lowFrame;
    public ByteBuffer highFrame;
    GLTexture LowPass40;
    float regenerationSense = 1.f;
    float AecCorr = 1.f;
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
        mParameters = parameters;
        mSettings = PhotonCamera.getSettings();
        Point rotated = getRotatedCoords(parameters.rawSize);
        Properties properties = new Properties();
        try {
            new File(FileManager.sPHOTON_DIR+"/tuning/").mkdir();
            File init = new File(FileManager.sPHOTON_DIR+"/tuning/PhotonCameraTuning.ini");
            if(!init.exists()) {
                init.createNewFile();
                InputStream inputStream = PhotonCamera.getCameraActivity().getResources().getAssets().open("tuning/PhotonCameraTuning.ini", AssetManager.ACCESS_BUFFER);
                byte[] buffer = new byte[inputStream.available()];
                inputStream.read(buffer);
                OutputStream outputStream = new FileOutputStream(init);
                outputStream.write(buffer);
                outputStream.close();
            }
            properties.load(new FileInputStream(init));
        } catch (IOException e) {
            Log.e("PostPipeline","Error at loading properties");
            e.printStackTrace();
        }
        mProp = properties;
        /*if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT) {
            rotated.x/=2;
            rotated.y/=2;
        }*/
        Bitmap output = Bitmap.createBitmap(rotated.x,rotated.y, Bitmap.Config.ARGB_8888);

        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(rotated,output, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        glint = new GLInterface(glproc);
        stackFrame = inBuffer;
        glint.parameters = parameters;
        add(new Bayer2Float(0,"Bayer2Float"));
        add(new ExposureFusionBayer("FusionBayer"));
        if(!IsoExpoSelector.HDR) {
            if (PhotonCamera.getSettings().cfaPattern != 4) {
                //if (PhotonCamera.getSettings().selectedMode != CameraMode.NIGHT) {
                    add(new Demosaic());
                //} else {
                //    add(new BinnedDemosaic());
                //}
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
        //add(new AEC("AEC"));
        //add(new ExposureFusionFast2("ExposureFusion"));
        if(PhotonCamera.getSettings().hdrxNR) {
            add(new SmartNR("SmartNR"));
        }
        //add(new GlobalToneMapping(0,"GlobalTonemap"));
        add(new Initial(R.raw.initial,"Initial"));
        add(new AWB(0,"AWB"));
        add(new Equalization(0,"Equalization"));

        //if(PhotonCamera.getParameters().focalLength <= 3.0)
        //add(new LensCorrection());
        add(new Sharpen(R.raw.sharpeningbilateral,"Sharpening"));
        add(new RotateWatermark(getRotation()));
        //add(new ShadowTexturing(R.raw.shadowtexturing,"Shadow Texturing"));
        Bitmap img = runAll();
        try {
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
