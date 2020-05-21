package com.eszdman.photoncamera;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.Image;
import android.util.Log;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.opencv.calib3d.Calib3d.RANSAC;
import static org.opencv.calib3d.Calib3d.findHomography;

public class ImageProcessing {
    static String TAG = "ImageProcessing";
    ArrayList<Image> curimgs;
    Boolean israw;
    Boolean isyuv;
    String path;
    ImageProcessing(ArrayList<Image> images) {
        curimgs = images;
    }
    Mat convertyuv(Image image){
        byte[] nv21;

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
        int error = image.getPlanes()[0].getRowStride()-image.getWidth(); //BufferFix
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize+error, vSize-error);
        uBuffer.get(nv21, ySize + vSize+error, uSize-error);
        Mat mYuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth()+error, CvType.CV_8UC1);
        mYuv.put(0, 0, nv21);
        Imgproc.cvtColor(mYuv, mYuv, Imgproc.COLOR_YUV2BGR_NV21, 3);
        mYuv = mYuv.colRange(0,image.getWidth());
        return mYuv;
    }
    Mat load_rawsensor(Image image){
        Image.Plane plane = image.getPlanes()[0];
        Mat mat = new Mat();
        if(israw) {
            if(image.getFormat() == ImageFormat.RAW_SENSOR) mat = new Mat(image.getHeight(),image.getWidth(),CvType.CV_16UC1,plane.getBuffer());
            if(image.getFormat() == ImageFormat.RAW10) mat = new Mat(image.getHeight(),image.getWidth(),CvType.CV_16UC1,plane.getBuffer());
        }
        else {
            if(!isyuv) mat =  Imgcodecs.imdecode(new Mat(1,plane.getBuffer().remaining(), CvType.CV_8U,plane.getBuffer()),Imgcodecs.IMREAD_UNCHANGED);
        }
        if(isyuv){
            mat = convertyuv(image);
            //Imgproc.cvtColor(mat,mat,Imgproc.COLOR_YUV2RGB_NV21,3);
        }
        return mat;
    }
    Mat[][] EqualizeImages(){
        Mat[][] out = new Mat[2][curimgs.size()];
        Mat lut = new Mat(1,256, CvType.CV_8U);
        for(int i =0; i<curimgs.size();i++){
            out[0][i] = new Mat();
            out[1][i] = new Mat();
            if(israw){
                out[0][i]=load_rawsensor(curimgs.get(i));
                out[0][i].convertTo(out[1][i], CvType.CV_8UC1);
            }
            else {
                out[0][i] = load_rawsensor(curimgs.get(i));
                Imgproc.cvtColor(out[0][i],out[1][i],Imgproc.COLOR_BGR2GRAY);
            }
            processingstep();
        }
        return out;
    }
    ORB orb = ORB.create();
    DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    Mat findFrameHomography(Mat need, Mat from){
        Mat descriptors1=new Mat(), descriptors2=new Mat();
        MatOfKeyPoint keyPoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keyPoints2 = new MatOfKeyPoint();
        orb.detectAndCompute(need,new Mat(),keyPoints1,descriptors1);
        orb.detectAndCompute(from,new Mat(),keyPoints2,descriptors2);
        MatOfDMatch matches = new MatOfDMatch();
        //DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        matcher.match(descriptors1, descriptors2, matches, new Mat());
        MatOfPoint2f points1=new MatOfPoint2f(), points2=new MatOfPoint2f();
        DMatch[]arr =matches.toArray();
        List<KeyPoint> keypoints1 = keyPoints1.toList();
        List<KeyPoint> keypoints2 = keyPoints2.toList();
        //Mat imMatches = new Mat();
        //drawMatches(need, keyPoints1, from, keyPoints2, matches, imMatches);
        ArrayList<Point> keypoints1f = new ArrayList<Point>();
        ArrayList<Point> keypoints2f = new ArrayList<Point>();
        for( int i = 0; i < arr.length; i++ )
        {
            Point on1 = keypoints1.get(arr[i].queryIdx).pt;
            Point on2 = keypoints2.get(arr[i].trainIdx).pt;
            /*on1.x/=0.75;
            on1.y/=0.75;
            on2.x/=0.75;
            on2.y/=0.75;*/
            if(arr[i].distance < 50) {
                keypoints1f.add(on1);
                keypoints2f.add(on2);
            }
        }
        points1.fromArray(keypoints1f.toArray(new Point[keypoints1f.size()]));
        points2.fromArray(keypoints2f.toArray(new Point[keypoints2f.size()]));
        Mat h = null;
        if(!points1.empty() && !points2.empty())h = findHomography(points2,points1,RANSAC);
        keyPoints1.release();
        keyPoints2.release();

        return h;
    }
    void processingstep(){
        int progress =(Camera2Api.loadingcycle.getProgress()+1)%(Camera2Api.loadingcycle.getMax()+1);
        progress = Math.max(1,progress);
        Camera2Api.loadingcycle.setProgress(progress);
    }
    Mat[][] getTiles(Mat[] input, int tilesize){
        Mat[][] output = new Mat[input.length][];
        ArrayList<Mat> out = new ArrayList<>();
        int width = input[0].width();
        int height = input[0].height();
        int oversize = 0;
        for(int i =0; i<input.length;i++){
            for(int h =0; h<(height-tilesize+1)/tilesize;h++){//rows
                for(int w =0;w<(width-tilesize+1)/tilesize;w++){//cols
                    out.add(input[i].submat(h*tilesize,(h+1)*tilesize +oversize,w*tilesize,(w+1)*tilesize +oversize));
                }
            }
            output[i] = out.toArray(new Mat[out.size()]);
        }
        return output;
    }
    Mat[] mergeFrames(Mat[][] tiles, Point[][]shifts, int tilesize, int width, int height){
        ArrayList<Mat> out = new ArrayList<>();

        int oversize = 0;
        int cnt = 0;
        for(int i =0; i<tiles.length;i++){
            for(int h =0; h<(height-tilesize+1)/tilesize;h++){//rows
                for(int w =0;w<(width-tilesize+1)/tilesize;w++){//cols
                    //out.add(tiles[][])
                    cnt++;
                    //out.add(input[i].submat(h*tilesize,(h+1)*tilesize +oversize,w*tilesize,(w+1)*tilesize +oversize));
                }
            }
            //output[i] = out.toArray(new Mat[out.size()]);
        }
        return null;
    }

    void ApplyStabilization(){
        Mat[] grey = null;
        Mat[] col = null;
        Mat[][] readed = EqualizeImages();
        col = readed[0];
        grey = readed[1];
        Log.d("ImageProcessing Stab", "Curimgs size "+curimgs.size());
        Mat output = new Mat();
        col[col.length -1].copyTo(output);
        boolean aligning = Settings.instance.align;
        ArrayList<Mat> imgsmat = new ArrayList<>();
        MergeMertens merge = Photo.createMergeMertens();
        AlignMTB align = Photo.createAlignMTB();
        Point shiftprev = new Point();
        int alignopt = 2;
        for(int i =0; i<curimgs.size()-1;i+=alignopt) {
            if(aligning){
                //Mat h=new Mat();
                Point shift = new Point();
                {
                    //Video.findTransformECC(output,cur,h,Video.MOTION_HOMOGRAPHY, new TermCriteria(TermCriteria.COUNT+TermCriteria.EPS,20,1),new Mat(),5);
                    //h = findFrameHomography(grey[grey.length -1], grey[i]);
                    if(i == 0) shift = align.calculateShift(grey[grey.length -1], grey[i]);
                    else {
                        shift = align.calculateShift(grey[grey.length -1], grey[i]);
                        Point calc = new Point((shift.x+shiftprev.x)/2,(shift.y+shiftprev.y)/2);
                        align.shiftMat(col[i-1],col[i-1],calc);
                        Core.addWeighted(output,0.7,col[i-1],0.3,0,output);
                    }
                    shiftprev = new Point(shift.x,shift.y);
                }
                align.shiftMat(col[i],col[i],shift);
            }
            processingstep();
            //imgsmat.add(col[i]);
            Core.addWeighted(output,0.7,col[i],0.3,0,output);
        }
        Mat merging = new Mat();
        Log.d("ImageProcessing Stab", "imgsmat size:"+imgsmat.size());
        /*if(curimgs.size() > 4) for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.7,imgsmat.get(i+1),0.3,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        if(curimgs.size() > 6)for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.7,imgsmat.get(i+1),0.3,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        if(curimgs.size() > 11)for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.7,imgsmat.get(i+1),0.3,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }*/
        processingstep();
        //merge.process(imgsmat,merging);
        //Core.convertScaleAbs(merging,output,255);
        if(!israw) {
            Mat outb = new Mat();
            double params = Math.sqrt(Math.log(Camera2Api.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY))*22) + 9;
            Log.d("ImageProcessing Denoise2", "params:"+params + " iso:"+Camera2Api.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
            params = Math.min(params,50);
            int ind = imgsmat.size()/2;
            if(ind %2 == 0) ind-=1;
            int wins = imgsmat.size() - ind;
            if(wins%2 ==0 ) wins-=1;
            wins = Math.max(0,wins);
            ind = Math.max(0,ind);
            Log.d("ImageProcessing Denoise", "index:"+ind + " wins:"+wins);
            //imgsmat.set(ind,output);
            Mat outbil = new Mat();
            Mat cols = new Mat();
            ArrayList<Mat> cols2 = new ArrayList<>();
            Imgproc.cvtColor(output,cols,Imgproc.COLOR_BGR2YUV);
            Core.split(cols,cols2);
            for(int i =0; i<3; i++) {
                Mat out = new Mat();
                Mat cur = cols2.get(i);
                processingstep();
                if(i==0) {
                    //Core.multiply(cur,new Scalar(1.05),cur);
                    //Core.add(cur,new Scalar(-0.1*16),cur);
                    //Xphoto.oilPainting(cur,out,Settings.instance.lumacount,(int)(Settings.instance.lumacount*0.2 + 1));
                    Mat sharp = new Mat();
                    Mat struct = new Mat();
                    Mat temp = new Mat();
                    struct.release();
                    temp.release();
                    cur.copyTo(temp);
                    Imgproc.pyrDown(temp,temp);
                    Imgproc.pyrDown(temp,temp);
                    Mat diff = new Mat();
                    //Imgproc.bilateralFilter(temp,diff,10,20,20);
                    Photo.fastNlMeansDenoising(temp,diff,(float)(Settings.instance.lumacount)/10,7,15);
                    Core.subtract(temp,diff,diff);
                    Imgproc.pyrUp(diff,diff);
                    Imgproc.pyrUp(diff,diff,new Size(cur.width(),cur.height()));
                    Core.addWeighted(cur,1,diff,-1,0,cur);
                    //Imgproc.pyrMeanShiftFiltering();
                    //Imgproc.medianBlur(cur,out,Settings.instance.lumacount);
                    if(!Settings.instance.enhancedprocess){
                        Imgproc.blur(cur,cur,new Size(2,2));
                        Imgproc.bilateralFilter(cur,out,Settings.instance.lumacount/4,Settings.instance.lumacount,Settings.instance.lumacount);

                    }
                    if(Settings.instance.enhancedprocess) Photo.fastNlMeansDenoising(cur,out,(float)(Settings.instance.lumacount)/6.5f,3,15);


                    out.copyTo(temp);
                    Imgproc.blur(temp,temp,new Size(4,4));
                    Core.subtract(out,temp,sharp);
                    Imgproc.blur(temp,temp,new Size(8,8));
                    Core.subtract(out,temp,struct);
                    //Core.addWeighted(out,1,struct,0.15,0,out);



                    //Imgproc.bilateralFilter(cur,out,Settings.instance.lumacount,Settings.instance.lumacount*2,Settings.instance.lumacount*2);
                }
                if(i!=0) {
                    Mat temp = new Mat();
                    Size bef = cur.size();
                    Imgproc.pyrDown(cur,cur);
                    Imgproc.bilateralFilter(cur,out,Settings.instance.chromacount,Settings.instance.chromacount*3,Settings.instance.chromacount*3);//Xphoto.oilPainting(cols2.get(i),cols2.get(i),Settings.instance.chromacount,(int)(Settings.instance.chromacount*0.1));
                    //Imgproc.pyrUp(out,out,bef);

                    /*out.copyTo(temp);
                    Imgproc.pyrDown(temp,temp);
                    Imgproc.pyrDown(temp,temp);
                    Mat diff = new Mat();
                    Imgproc.bilateralFilter(temp,diff,10,20,20);
                    Core.subtract(temp,diff,diff);
                    Imgproc.pyrUp(diff,diff);
                    Imgproc.pyrUp(diff,diff,new Size(cur.width(),cur.height()));
                    Core.addWeighted(out,1,diff,-1,0,out);*/

                    Imgproc.pyrUp(out,out,bef);
                }
                cur.release();
                cols2.set(i,out);
            }
            Core.merge(cols2,cols);
            Imgproc.cvtColor(cols,output,Imgproc.COLOR_YUV2BGR);
            processingstep();
            //Core.merge(cols,output);
            //Imgproc.bilateralFilter(outb,outbil, (int) (params*1.2),params*1.5,params*3.5);
            outb = outbil;
            Imgcodecs.imwrite(path,output, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY,100));
        }
        Camera2Api.loadingcycle.setProgress(0);

    }
    int GetCFAPattern(){
        Object integ = Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if(integ !=null) {
            System.out.println("CFA pattern"+(int)integ);
            switch ((int)(integ)) {
                case 0: return 1;
                case 1: return 2;
                case 2: return 4;
            }
        }
        return 3;
    }
    void ApplyHdrX(){
        CaptureResult res = Camera2Api.mCaptureResult;
        int width =curimgs.get(0).getPlanes()[0].getRowStride()/curimgs.get(0).getPlanes()[0].getPixelStride(); //curimgs.get(0).getWidth()*curimgs.get(0).getHeight()/(curimgs.get(0).getPlanes()[0].getRowStride()/curimgs.get(0).getPlanes()[0].getPixelStride());
        int height = curimgs.get(0).getHeight();
        Log.d(TAG,"APPLYHDRX: buffer:"+curimgs.get(0).getPlanes()[0].getBuffer().asShortBuffer().remaining());
        Wrapper.init(width,height,curimgs.size());
        Log.d(TAG,"Wrapper.init");
        processingstep();
        Wrapper.setCFA(GetCFAPattern());
        processingstep();
        ColorSpaceTransform tr = res.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        RggbChannelVector vec = res.get(CaptureResult.COLOR_CORRECTION_GAINS);
        Log.d(TAG,"CCG:"+vec.toString());
        Wrapper.setBWLWB(64,1023,vec.getRed()*1.13*0.931*1.021,vec.getGreenEven(),vec.getGreenOdd(),vec.getBlue()*0.93*1.13*0.917);
        double contr = 0.8 + 1.8/(1+Settings.instance.contrast_mpy*20);
        double compr = Settings.instance.compressor;
        compr = Math.max(1,compr);
        Wrapper.setCompGain(compr,Settings.instance.gain,contr,Settings.instance.contrast_const);
        Wrapper.setSharpnessSaturation(Settings.instance.saturation,Settings.instance.sharpness*20);
        Log.d(TAG,"Wrapper.setBWLWB");
        processingstep();
        double ccm[] = new double[9];
        int c =0;
        ccm[0] = 1.776;ccm[3] = -0.837;ccm[6] = 0.071;
        ccm[1] = -0.163;ccm[4] = 1.406;ccm[7] = -0.242;
        ccm[2] = 0.0331;ccm[5] = -0.526;ccm[8] = 1.492;
        /*for(int h=0; h<3;h++){
            for(int w=0; w<3;w++){ccm[c] = tr.getElement(h,w).doubleValue();//ccm[c] = 0.5;
                c++;
            }
        }*/
        Wrapper.setCCM(ccm);
        Log.d(TAG,"Wrapper.setCCM");
        processingstep();
        for(int i =0; i<curimgs.size();i++) {
            Wrapper.loadFrame(curimgs.get(i).getPlanes()[0].getBuffer());
        }
        //ByteBuffer out = curimgs.get(3).getPlanes()[0].getBuffer();
        Log.d(TAG,"Wrapper.loadFrame");
        processingstep();
        ByteBuffer output = Wrapper.processFrame();
        Log.d(TAG,"Wrapper.processFrame()");
        processingstep();
        Mat out = new Mat(height,width,CvType.CV_8UC3,output);
        //Imgproc.pyrDown();
        Imgproc.cvtColor(out,out,Imgproc.COLOR_RGB2BGR);
        Imgcodecs.imwrite(path,out, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY,100));
    }
    public void Run(){
        Image.Plane plane = curimgs.get(0).getPlanes()[0];
        byte buffval = plane.getBuffer().get();
        Log.d("ImageProcessing", "Camera bayer:"+Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT));
        if(israw) {
            /*Log.d(TAG,"RawProcessingHDRX: RowStride:"+curimgs.get(0).getPlanes()[0].getRowStride());
            Log.d(TAG,"RawProcessingHDRX: PixelStride:"+curimgs.get(0).getPlanes()[0].getPixelStride());
            Mat in = load_rawsensor(curimgs.get(0));
            Imgproc.demosaicing(in,in,Imgproc.COLOR_BayerRG2RGB);
            Imgcodecs.imwrite(path,in, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY,100));*/
            ApplyHdrX();
        }
        if(isyuv)ApplyStabilization();
        Log.d(TAG,"buffer parameters:");
        Log.d(TAG,"bufferpixelstride"+plane.getPixelStride());
        Log.d(TAG,"bufferrowstride"+plane.getRowStride());
        Log.d(TAG,"buffervalue"+buffval);
        Camera2Api.loadingcycle.setProgress(0);
    }
}