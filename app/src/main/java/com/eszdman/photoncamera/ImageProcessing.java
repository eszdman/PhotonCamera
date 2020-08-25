package com.eszdman.photoncamera;


import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.params.BlackLevelPattern;
import android.media.Image;
import android.util.Log;
import androidx.exifinterface.media.ExifInterface;

import com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline.PostPipeline;
import com.eszdman.photoncamera.OpenGL.Nodes.RawPipeline.RawPipeline;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.CameraReflectionApi;
import com.eszdman.photoncamera.api.ImageSaver;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.AlignMTB;
import org.opencv.photo.MergeMertens;
import org.opencv.photo.Photo;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL;
import static com.eszdman.photoncamera.Parameters.IsoExpoSelector.baseFrame;
import static org.opencv.calib3d.Calib3d.RANSAC;
import static org.opencv.calib3d.Calib3d.findHomography;

public class ImageProcessing {
    static final String TAG = "ImageProcessing";
    public ArrayList<Image> curimgs;
    public Boolean israw;
    public Boolean isyuv;
    public String path;
    public ImageProcessing(ArrayList<Image> images) {
        curimgs = images;
    }
    public ImageProcessing() {
    }
    Mat convertyuv(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
        int error = image.getPlanes()[0].getRowStride() - image.getWidth(); //BufferFix
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize + error, vSize - error);
        uBuffer.get(nv21, ySize + vSize + error, uSize - error);
        Mat mYuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth() + error, CvType.CV_8UC1);
        mYuv.put(0, 0, nv21);
        Imgproc.cvtColor(mYuv, mYuv, Imgproc.COLOR_YUV2BGR_NV21, 3);
        mYuv = mYuv.colRange(0, image.getWidth());
        return mYuv;
    }
    Mat load_rawsensor(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        Mat mat = new Mat();
        if (israw) {
            if (image.getFormat() == ImageFormat.RAW_SENSOR)
                mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_16UC1, plane.getBuffer());
            if (image.getFormat() == ImageFormat.RAW10)
                mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_16UC1, plane.getBuffer());
        } else {
            if (!isyuv)
                mat = Imgcodecs.imdecode(new Mat(1, plane.getBuffer().remaining(), CvType.CV_8U, plane.getBuffer()), Imgcodecs.IMREAD_UNCHANGED);
        }
        if (isyuv) {
            mat = convertyuv(image);
        }
        return mat;
    }
    Mat[][] EqualizeImages() {
        Mat[][] out = new Mat[2][curimgs.size()];
        Mat lut = new Mat(1, 256, CvType.CV_8U);
        for (int i = 0; i < curimgs.size(); i++) {
            out[0][i] = new Mat();
            out[1][i] = new Mat();
            if (israw) {
                out[0][i] = load_rawsensor(curimgs.get(i));
                out[0][i].convertTo(out[1][i], CvType.CV_8UC1);
            } else {
                out[0][i] = load_rawsensor(curimgs.get(i));
                Imgproc.cvtColor(out[0][i], out[1][i], Imgproc.COLOR_BGR2GRAY);
            }
            processingstep();
        }
        return out;
    }
    final ORB orb = ORB.create();
    final DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    Mat findFrameHomography(Mat need, Mat from) {
        Mat descriptors1 = new Mat(), descriptors2 = new Mat();
        MatOfKeyPoint keyPoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keyPoints2 = new MatOfKeyPoint();
        orb.detectAndCompute(need, new Mat(), keyPoints1, descriptors1);
        orb.detectAndCompute(from, new Mat(), keyPoints2, descriptors2);
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(descriptors1, descriptors2, matches, new Mat());
        MatOfPoint2f points1 = new MatOfPoint2f(), points2 = new MatOfPoint2f();
        DMatch[] arr = matches.toArray();
        List<KeyPoint> keypoints1 = keyPoints1.toList();
        List<KeyPoint> keypoints2 = keyPoints2.toList();
        ArrayList<Point> keypoints1f = new ArrayList<Point>();
        ArrayList<Point> keypoints2f = new ArrayList<Point>();
        for (int i = 0; i < arr.length; i++) {
            Point on1 = keypoints1.get(arr[i].queryIdx).pt;
            Point on2 = keypoints2.get(arr[i].trainIdx).pt;
            if (arr[i].distance < 50) {
                keypoints1f.add(on1);
                keypoints2f.add(on2);
            }
        }
        points1.fromArray(keypoints1f.toArray(new Point[keypoints1f.size()]));
        points2.fromArray(keypoints2f.toArray(new Point[keypoints2f.size()]));
        Mat h = null;
        if (!points1.empty() && !points2.empty()) h = findHomography(points2, points1, RANSAC);
        keyPoints1.release();
        keyPoints2.release();

        return h;
    }
    void clearProcessingCycle(){
        try {
            Interface.i.cameraui.loadingcycle.setProgress(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    void incrementProcessingCycle(){
        try {
            int progress = (Interface.i.cameraui.loadingcycle.getProgress() + 1) % (Interface.i.cameraui.loadingcycle.getMax() + 1);
            progress = Math.max(1, progress);
            Interface.i.cameraui.loadingcycle.setProgress(progress);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    void processingstep() {
        incrementProcessingCycle();
    }
    void ApplyStabilization() {
        Mat[] grey = null;
        Mat[] col = null;
        Mat[][] readed = EqualizeImages();
        col = readed[0];
        grey = readed[1];
        Log.d("ImageProcessing Stab", "Curimgs size " + curimgs.size());
        Mat output = new Mat();
        col[col.length - 1].copyTo(output);
        boolean aligning = Interface.i.settings.align;
        ArrayList<Mat> imgsmat = new ArrayList<>();
        MergeMertens merge = Photo.createMergeMertens();
        AlignMTB align = Photo.createAlignMTB();
        Point shiftprev = new Point();
        int alignopt = 2;
        for (int i = 0; i < curimgs.size() - 1; i += alignopt) {
            if (aligning) {
                //Mat h=new Mat();
                Point shift = new Point();
                {
                    if (i == 0) shift = align.calculateShift(grey[grey.length - 1], grey[i]);
                    else {
                        shift = align.calculateShift(grey[grey.length - 1], grey[i]);
                        Point calc = new Point((shift.x + shiftprev.x) / 2, (shift.y + shiftprev.y) / 2);
                        align.shiftMat(col[i - 1], col[i - 1], calc);
                        Core.addWeighted(output, 0.7, col[i - 1], 0.3, 0, output);
                    }
                    shiftprev = new Point(shift.x, shift.y);
                }
                align.shiftMat(col[i], col[i], shift);
            }
            processingstep();
            Core.addWeighted(output, 0.7, col[i], 0.3, 0, output);
        }
        Mat merging = new Mat();
        Log.d("ImageProcessing Stab", "imgsmat size:" + imgsmat.size());
        processingstep();
        if (!israw) {
            Mat outb = new Mat();
            double params = Math.sqrt(Math.log(CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * 22) + 9;
            Log.d("ImageProcessing Denoise", "params:" + params + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
            params = Math.min(params, 50);
            int ind = imgsmat.size() / 2;
            if (ind % 2 == 0) ind -= 1;
            int wins = imgsmat.size() - ind;
            if (wins % 2 == 0) wins -= 1;
            wins = Math.max(0, wins);
            ind = Math.max(0, ind);
            Log.d("ImageProcessing Denoise", "index:" + ind + " wins:" + wins);
            //imgsmat.set(ind,output);
            Mat outbil = new Mat();
            Mat cols = new Mat();
            ArrayList<Mat> cols2 = new ArrayList<>();
            Imgproc.cvtColor(output, cols, Imgproc.COLOR_BGR2YUV);
            Core.split(cols, cols2);
            for (int i = 0; i < 3; i++) {
                Mat out = new Mat();
                Mat cur = cols2.get(i);
                processingstep();
                if (i == 0) {
                    Mat sharp = new Mat();
                    Mat struct = new Mat();
                    Mat temp = new Mat();
                    struct.release();
                    temp.release();
                    cur.copyTo(temp);
                    Imgproc.pyrDown(temp, temp);
                    Imgproc.pyrDown(temp, temp);
                    Mat diff = new Mat();
                    Photo.fastNlMeansDenoising(temp, diff, (float) (Interface.i.settings.lumenCount) / 10, 7, 15);
                    Core.subtract(temp, diff, diff);
                    Imgproc.pyrUp(diff, diff);
                    Imgproc.pyrUp(diff, diff, new Size(cur.width(), cur.height()));
                    Core.addWeighted(cur, 1, diff, -1, 0, cur);
                    if (!Interface.i.settings.enhancedProcess) {
                        Imgproc.blur(cur, cur, new Size(2, 2));
                        Imgproc.bilateralFilter(cur, out, Interface.i.settings.lumenCount / 4, Interface.i.settings.lumenCount, Interface.i.settings.lumenCount);
                    }
                    if (Interface.i.settings.enhancedProcess)
                        Photo.fastNlMeansDenoising(cur, out, (float) (Interface.i.settings.lumenCount) / 6.5f, 3, 15);
                    out.copyTo(temp);
                    Imgproc.blur(temp, temp, new Size(4, 4));
                    Core.subtract(out, temp, sharp);
                    Imgproc.blur(temp, temp, new Size(8, 8));
                    Core.subtract(out, temp, struct);
                }
                if (i != 0) {
                    Mat temp = new Mat();
                    Size bef = cur.size();
                    Imgproc.pyrDown(cur, cur);
                    Imgproc.bilateralFilter(cur, out, Interface.i.settings.chromaCount, Interface.i.settings.chromaCount * 3, Interface.i.settings.chromaCount * 3);//Xphoto.oilPainting(cols2.get(i),cols2.get(i),Settings.instance.chromacount,(int)(Settings.instance.chromacount*0.1));
                    Imgproc.pyrUp(out, out, bef);
                }
                cur.release();
                cols2.set(i, out);
            }
            Core.merge(cols2, cols);
            Imgproc.cvtColor(cols, output, Imgproc.COLOR_YUV2BGR);
            processingstep();
            outb = outbil;
            Imgcodecs.imwrite(path, output, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 100));
        }
        clearProcessingCycle();
    }
    void ApplyHdrX() {
        boolean debugAlignment = false;
        if(Interface.i.settings.alignAlgorithm == 1) debugAlignment = true;
        CaptureResult res = CameraFragment.mCaptureResult;
        processingstep();
        long startTime = System.currentTimeMillis();
        int width = curimgs.get(0).getPlanes()[0].getRowStride() / curimgs.get(0).getPlanes()[0].getPixelStride(); //curimgs.get(0).getWidth()*curimgs.get(0).getHeight()/(curimgs.get(0).getPlanes()[0].getRowStride()/curimgs.get(0).getPlanes()[0].getPixelStride());
        int height = curimgs.get(0).getHeight();
        Log.d(TAG, "APPLYHDRX: buffer:" + curimgs.get(0).getPlanes()[0].getBuffer().asShortBuffer().remaining());
        Log.d(TAG,"Api WhiteLevel:"+CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        if(!debugAlignment) {
            if(IsoExpoSelector.HDR) Wrapper.init(width, height, curimgs.size()-2);
            else Wrapper.init(width, height, curimgs.size());
        }
        Object level = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        int levell = 1023;
        if(level !=null) levell = (int)level;
        float fakelevel = levell;//(float)Math.pow(2,16)-1.f;//bits raw
        float k = fakelevel/levell;
        CameraReflectionApi.set(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL,(int)fakelevel);
        BlackLevelPattern blevel = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        int[] levelarr = new int[4];
        blevel.copyTo(levelarr,0);
        if(blevel !=null){
            for(int i =0; i<4;i++){
                levelarr[i]=(int)(levelarr[i]*k);
            }
            CameraReflectionApi.PatchBL(blevel,levelarr);
            CameraReflectionApi.set(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN,blevel);
        }
        float[] dynBL = res.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        if(dynBL != null){
            for(int i =0; i<dynBL.length;i++){
                dynBL[i]*=k;
            }
            CameraReflectionApi.set(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL,dynBL,res);
        }
        Object wl = res.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        if(wl !=null){
            int wll = (int)wl;
            wl=(int)(wll*k);
            CameraReflectionApi.set(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL,wll);
        }
        Log.d(TAG,"Api WhiteLevel:"+CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG,"Api Blacklevel:"+CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        Interface.i.parameters.FillParameters(res,CameraFragment.mCameraCharacteristics, new android.graphics.Point(width,height));
        if(Interface.i.parameters.realWL == -1) Interface.i.parameters.realWL = levell;
        Log.d(TAG, "Wrapper.init");
        RawPipeline rawPipeline = new RawPipeline();
        ArrayList<ByteBuffer> images = new ArrayList<>();
        ByteBuffer lowexp = null;
        ByteBuffer highexp = null;
        for (int i = 0; i < curimgs.size(); i++) {
            ByteBuffer byteBuffer = null;
            if(i == 0){
                byteBuffer = curimgs.get(baseFrame).getPlanes()[0].getBuffer();
            } else
            if(i == baseFrame){
                byteBuffer = curimgs.get(0).getPlanes()[0].getBuffer();
            } else {
                byteBuffer = curimgs.get(i).getPlanes()[0].getBuffer();
            }
            if(i == 3 && IsoExpoSelector.HDR){
                //rawPipeline.sensivity = k*0.7f;
                highexp = byteBuffer;
                continue;
            }
            if(i == 2 && IsoExpoSelector.HDR){
                //rawPipeline.sensivity = k*6.0f;
                lowexp = byteBuffer;
                continue;
            }
            byteBuffer.position(0);
            images.add(byteBuffer);
            if(!debugAlignment) Wrapper.loadFrame(byteBuffer);
        }
        rawPipeline.imageobj = curimgs;
        rawPipeline.images = images;
        Log.d(TAG,"WhiteLevel:"+Interface.i.parameters.whitelevel);
        Log.d(TAG, "Wrapper.loadFrame");
        float deghostlevel = (float)Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY))* IsoExpoSelector.getMPY() - 50.)/16.2f;
        deghostlevel = Math.min(0.25f,deghostlevel);
        Log.d(TAG,"Deghosting level:"+deghostlevel);
        ByteBuffer output = null;
        if(!debugAlignment) output = Wrapper.processFrame(0.9f+deghostlevel);
        else output = rawPipeline.Run();
        if(IsoExpoSelector.HDR){
            /*Wrapper.init(width,height,2);
            RawSensivity rawSensivity = new RawSensivity(new android.graphics.Point(width,height),null);
            RawParams rawParams = new RawParams(res);
            rawParams.input = curimgs.get(0).getPlanes()[0].getBuffer();
            rawParams.sensivity = 0.7f;
            rawSensivity.additionalParams = rawParams;
            rawSensivity.Run();
            Wrapper.loadFrame(rawSensivity.Output);
            Wrapper.loadFrame(highexp);
            highexp = Wrapper.processFrame(0.9f+deghostlevel);

            Wrapper.init(width,height,2);
            rawSensivity = new RawSensivity(new android.graphics.Point(width,height),null);
            rawParams = new RawParams(res);
            rawParams.input = curimgs.get(0).getPlanes()[0].getBuffer();
            rawParams.sensivity = 6.0f;
            rawSensivity.Run();
            Wrapper.loadFrame(rawSensivity.Output);
            Wrapper.loadFrame(lowexp);
            lowexp = Wrapper.processFrame(0.9f+deghostlevel);
            rawSensivity.close();*/
        }
        //Black shot fix
        curimgs.get(0).getPlanes()[0].getBuffer().position(0);
        curimgs.get(0).getPlanes()[0].getBuffer().put(output);
        curimgs.get(0).getPlanes()[0].getBuffer().position(0);
        for (int i = 1; i < curimgs.size(); i++) {
            if((i == 3 || i == 2) && IsoExpoSelector.HDR) continue;
            curimgs.get(i).close();
        }
        if(debugAlignment) rawPipeline.close();
        Log.d(TAG,"HDRX Alignment elapsed:"+(System.currentTimeMillis()-startTime) + " ms");
        if(Interface.i.settings.rawSaver) {
            saveRaw(curimgs.get(0));
            return;
        }
        Log.d(TAG, "Wrapper.processFrame()");
        Interface.i.parameters.path = path;
        PostPipeline pipeline = new PostPipeline();
        pipeline.lowFrame = lowexp;
        pipeline.highFrame = highexp;
        pipeline.Run(curimgs.get(0).getPlanes()[0].getBuffer(),Interface.i.parameters);
        pipeline.close();
        curimgs.get(0).close();
    }
    private void saveRaw(Image in){
        DngCreator dngCreator = new DngCreator(CameraFragment.mCameraCharacteristics, CameraFragment.mCaptureResult);
        try {
            FileOutputStream outB = new FileOutputStream(ImageSaver.outimg);
            dngCreator.setDescription(Interface.i.parameters.toString());
            int rotation = Interface.i.gravity.getCameraRotation();
            Log.d(TAG,"Gravity rotation:"+Interface.i.gravity.getRotation());
            Log.d(TAG,"Sensor rotation:"+Interface.i.camera.mSensorOrientation);
            int orientation = ORIENTATION_NORMAL;
            switch (rotation) {
                case 90:
                    orientation = ExifInterface.ORIENTATION_ROTATE_90;
                    break;
                case 180:
                    orientation = ExifInterface.ORIENTATION_ROTATE_180;
                    break;
                case 270:
                    orientation = ExifInterface.ORIENTATION_ROTATE_270;
                    break;
            }
            dngCreator.setOrientation(orientation);
            dngCreator.writeImage(outB, in);
            in.close();
            outB.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void Run() {
        Image.Plane plane = curimgs.get(0).getPlanes()[0];
        Log.d(TAG, "buffer parameters:");
        Log.d(TAG, "bufferpixelstride" + plane.getPixelStride());
        Log.d(TAG, "bufferrowstride" + plane.getRowStride());
        Camera2ApiAutoFix.ApplyRes();
        Log.d("ImageProcessing", "Camera bayer:" + CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT));
        if (israw) ApplyHdrX();
        if (isyuv) ApplyStabilization();
        clearProcessingCycle();
    }
}