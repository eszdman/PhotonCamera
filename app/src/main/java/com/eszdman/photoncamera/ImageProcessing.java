package com.eszdman.photoncamera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import java.util.ArrayList;
import java.util.List;
import static org.opencv.calib3d.Calib3d.RANSAC;
import static org.opencv.calib3d.Calib3d.findHomography;
import static org.opencv.features2d.Features2d.drawMatches;

public class ImageProcessing {
    ArrayList<Image> curimgs;
    Boolean israw;
    String path;
    ImageProcessing(ArrayList<Image> images) {
        curimgs = images;
    }
    Mat load_rawsensor(Image image){
        Image.Plane plane = image.getPlanes()[0];
        Mat mat;
        if(israw) mat = new Mat(image.getHeight(),image.getWidth(),CvType.CV_16UC1,plane.getBuffer());
        else {
            mat =  Imgcodecs.imdecode(new Mat(1,plane.getBuffer().remaining(), CvType.CV_8U,plane.getBuffer()),Imgcodecs.IMREAD_UNCHANGED);
        }
        return mat;
    }
    Mat[] EqualizeImages(){
        Mat[] out = new Mat[curimgs.size()];
        Mat lut = new Mat(1,256, CvType.CV_8U);
        for(int i =0; i<curimgs.size();i++){
            out[i] = new Mat();
            if(israw)load_rawsensor(curimgs.get(i)).convertTo(out[i], CvType.CV_8UC1,0.6);
            else out[i] = load_rawsensor(curimgs.get(i));
            //Core.divide(out[i],new Scalar(256),out[i]);
            //Core.pow(out[i],0.5,out[i]);
            //Core.multiply(out[i],new Scalar(256),out[i]);
            //Core.multiply(out[i],new Scalar(1.3),out[i]);
            //Core.subtract(out[i],new Scalar(256*0.3),out[i]);
            //Mat temp = new Mat();
            //Core.LUT(out[i],lut,temp);
            //out[i] = temp;
        }
        return out;
    }
    Mat findFrameHomography(Mat need, Mat from){
        Mat descriptors1=new Mat(), descriptors2=new Mat();
        MatOfKeyPoint keyPoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keyPoints2 = new MatOfKeyPoint();
        ORB orb = ORB.create();
        orb.detectAndCompute(need,new Mat(),keyPoints1,descriptors1);
        orb.detectAndCompute(from,new Mat(),keyPoints2,descriptors2);
        MatOfDMatch matches = new MatOfDMatch();
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        matcher.match(descriptors1, descriptors2, matches, new Mat());
        MatOfPoint2f points1=new MatOfPoint2f(), points2=new MatOfPoint2f();
        DMatch[]arr =matches.toArray();
        List<KeyPoint> keypoints1 = keyPoints1.toList();
        List<KeyPoint> keypoints2 = keyPoints2.toList();
        Mat imMatches = new Mat();
        drawMatches(need, keyPoints1, from, keyPoints2, matches, imMatches);
        ArrayList<Point> keypoints1f = new ArrayList<Point>();
        ArrayList<Point> keypoints2f = new ArrayList<Point>();
        for( int i = 0; i < arr.length; i++ )
        {
            Point on1 = keypoints1.get(arr[i].queryIdx).pt;
            Point on2 = keypoints2.get(arr[i].trainIdx).pt;
            if(arr[i].distance < 50) {
                keypoints1f.add(on1);
                keypoints2f.add(on2);
            }
            //points1.push_back( new MatOfPoint(keypoints1.get(arr[i].queryIdx).pt));
            //points2.push_back( new MatOfPoint(keypoints2.get( arr[i].trainIdx).pt));
        }
        points1.fromArray(keypoints1f.toArray(new Point[keypoints1f.size()]));
        points2.fromArray(keypoints2f.toArray(new Point[keypoints2f.size()]));
        Mat h = null;
        if(!points1.empty() && !points2.empty())h = findHomography(points2,points1,RANSAC);
        return h;
    }
    void ApplyStabilization(){
        Mat[] imgs = null;
        if(israw) imgs = EqualizeImages();
        Log.d("ImageProcessing Stab", "Curimgs size "+curimgs.size());
        Image outp =curimgs.get(curimgs.size()-1);
        Mat output = load_rawsensor(outp);
        boolean aligning = Settings.instance.align;
        ArrayList<Mat> imgsmat = new ArrayList<>();
        for(int i =0; i<curimgs.size()-1;i++) {
            Mat cur = load_rawsensor(curimgs.get(i));
            if(aligning){
                Mat h=null;
                if(israw) h = findFrameHomography(imgs[imgs.length - 1], imgs[i]);
                else h = findFrameHomography(output, cur);
                if(h != null) Imgproc.warpPerspective(cur, cur, h, cur.size());
                else Log.e("ImageProcessing ApplyStabilization","Can't find FrameHomography");
            }
            Camera2Api.loadingcycle.setProgress(i+1);
            Log.d("ImageProcessing Stab", "Curimgs iter:"+i);
            imgsmat.add(cur);
            Core.addWeighted(output,0.5,cur,0.5,0,output);
        }
        if(curimgs.size() > 4)for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.5,imgsmat.get(i+1),0.5,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        if(curimgs.size() > 6)for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.5,imgsmat.get(i+1),0.5,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        if(curimgs.size() > 11)for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.5,imgsmat.get(i+1),0.5,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        if(!israw) {
            Mat outb = new Mat();
            double params = Math.sqrt(Math.log(Camera2Api.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY))*22) + 9;
            Log.d("ImageProcessing Denoise", "params:"+params + " iso:"+Camera2Api.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
            params = Math.min(params,50);
            //Photo.fastNlMeansDenoisingColored(output,outb,1,15,10);
            if(imgsmat.size()%2 == 0) imgsmat.remove(0);
            int ind = imgsmat.size()/2;
            if(ind %2 == 0) ind+=1;
            int wins = imgsmat.size()/2;
            if(wins%2 ==0 ) wins-=1;
            wins = Math.max(0,wins);
            Log.d("ImageProcessing Denoise", "index:"+ind + " wins:"+wins);
            //Photo.fastNlMeansDenoisingColored(output,output,Settings.instance.lumacount,Settings.instance.chromacount,16);
            //Imgproc.bilateralFilter(imgsmat.get(ind),imgs., (int) (params*1.2),params*3.5,params*1.7);
            imgsmat.set(ind,output);
            Mat outbil = new Mat();


            //imgsmat.set(ind,outbil);
            Photo.fastNlMeansDenoisingColoredMulti(imgsmat,outb,ind,wins,Settings.instance.lumacount,Settings.instance.chromacount,7,13);
            Imgproc.bilateralFilter(outb,outbil, (int) (params*1.2),params*1.5,params*3.5);
            outb = outbil;
            //Ximgproc.bilateralTextureFilter(output,outb,1000,3,0.5,0.2);
            //Photo.denoise_TVL1(imgsmat,output);
            //Imgproc.bilateralFilter(output,outb, (int) (params*1.2),params*3.5,params*1.7);
            //Photo.detailEnhance(output,output);
            //Imgproc.cvtColor(output,output,Imgproc.Color);
            //Imgcodecs.imwrite(path+"t.jpg",imgsmat.get(ind));
            Imgcodecs.imwrite(path,outb);
        }
        //short[] data = new short[ curimgs.get(0).getPlanes()[0].getBuffer().asShortBuffer().capacity()];
        //output.get(0,0,data);
        //Core.multiply(out,new Scalar(100),out);
        Camera2Api.loadingcycle.setProgress(0);

    }
    public void Run(){
        Image.Plane plane = curimgs.get(0).getPlanes()[0];
        byte buffval = plane.getBuffer().get();
        Image img = curimgs.get(0);
        //Mat mat = loadPlane(img);
        //Mat res = Imgcodecs.imdecode(mat,0);
        //Mat mat = load_rawsensor(img);
        //Core.multiply(mat,new Scalar(200),mat);
        Log.d("ImageProcessing", "Camera bayer:"+Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT));

        CaptureResult res = Camera2Api.mCaptureResult;
        //RggbChannelVector rate = res.get(CaptureResult.COLOR_CORRECTION_GAINS);
        //Core.divide(mat,new Scalar(rate.getBlue(),rate.getGreenOdd()),mat);
        //mat = mat.t();
        //Core.divide(mat,new Scalar(rate.getGreenEven(),rate.getRed()),mat);
        //mat = mat.t();
        //Imgproc.cvtColor(mat,mat,Imgproc.COLOR_BayerBG2BGR);

        //Imgproc.demosaicing(mat,mat,Imgproc.COLOR_BayerBG2BGR);

        /*ArrayList<Mat> rgb = new ArrayList<>();
        Core.split(mat,rgb);
        Core.divide(rgb.get(0),new Scalar(rate.getBlue()),rgb.get(0));
        Core.divide(rgb.get(1),new Scalar(rate.getGreenEven()+rate.getGreenOdd()),rgb.get(1));
        Core.divide(rgb.get(2),new Scalar(rate.getRed()),rgb.get(2));
        Core.merge(rgb,mat);*/

        //mat = Imgcodecs.imdecode(mat,Imgcodecs.IMREAD_UNCHANGED);
        //Mat[] imgs = EqualizeImages();
        ApplyStabilization();
        //Imgcodecs.imwrite(ImageSaver.curDir()+"//"+ImageSaver.curName()+"_CV.jpg",imgs[0]);
        Log.d("ImageProcessing","buffer parameters:");
        Log.d("ImageProcessing","bufferpixelstride"+plane.getPixelStride());
        Log.d("ImageProcessing","bufferrowstride"+plane.getRowStride());
        Log.d("ImageProcessing","buffervalue"+buffval);
    }
}
