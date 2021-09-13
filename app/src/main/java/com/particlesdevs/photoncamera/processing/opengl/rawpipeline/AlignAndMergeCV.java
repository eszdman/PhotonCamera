package com.particlesdevs.photoncamera.processing.opengl.rawpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;
import com.particlesdevs.photoncamera.util.Utilities;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.AKAZE;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.GFTTDetector;
import org.opencv.features2d.KAZE;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static com.particlesdevs.photoncamera.processing.ImageSaver.jpgFilePathToSave;
import static org.opencv.calib3d.Calib3d.RANSAC;
import static org.opencv.calib3d.Calib3d.RHO;
import static org.opencv.calib3d.Calib3d.findHomography;
import static org.opencv.features2d.Features2d.drawKeypoints;
import static org.opencv.features2d.Features2d.drawMatches;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AlignAndMergeCV extends Node {
    public AlignAndMergeCV() {
        super(0, "AlignAndMerge");
    }
    static {
        if (!OpenCVLoader.initDebug()) {
             //Handle initialization error
        }
    }

    @Override
    public void Compile() {
    }

    private void CorrectedRaw(GLTexture out, int number) {
        float bl = Math.min(Math.min(Math.min(PhotonCamera.getParameters().blackLevel[0],PhotonCamera.getParameters().blackLevel[1]),
                PhotonCamera.getParameters().blackLevel[2]),PhotonCamera.getParameters().blackLevel[3]);
        float mpy = minMpy / images.get(number).pair.layerMpy;
        glProg.setDefine("BL",PhotonCamera.getParameters().blackLevel);
        glProg.setDefine("WP",PhotonCamera.getParameters().whitePoint);
        glProg.setDefine("MPY",mpy);
        glProg.setDefine("BAYER",PhotonCamera.getParameters().cfaPattern);
        Log.d("Align","mpy:"+mpy);
        glProg.useProgram(R.raw.precorrection);
        GLTexture inraw = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), images.get(number).buffer);
        glProg.setTexture("InputBuffer",inraw);
        glProg.setVar("WhiteLevel",(float)PhotonCamera.getParameters().whiteLevel);
        glProg.drawBlocks(out);
        inraw.close();
    }
    private void BoxDown22(GLTexture input,GLTexture out) {
        glProg.useProgram(R.raw.boxdown22);
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("GainMap", GainMap);
        glProg.setVar("CfaPattern", PhotonCamera.getParameters().cfaPattern);
        glProg.drawBlocks(basePipeline.main3,out.mSize);

        glUtils.median(basePipeline.main3,out,new android.graphics.Point(1,1));
        //glUtils.SaveProgResult(output.mSize,"boxdown");
        //glProg.close();
        //GLTexture median = glUtils.blur(output,5.0);
        //GLTexture laplaced = glUtils.ops(median,output,"in2.rgb,3.0*(in1.a-in2.a)");
        //median.close();

        //glUtils.median(basePipeline.main3,out,new android.graphics.Point(1,1));
        glUtils.convertVec4(out,"in1.r,in1.g,in1.b,0.0");
        //glUtils.SaveProgResult(out.mSize,"box");
        //GLTexture median = glUtils.blur(output,1.5);
    }
    Mat OneMatrix(){
        return OneMatrix(1,1,new Point(0.0,0.0));
    }
    Mat OneMatrix(double x,double y, Point c){
        Mat h2 = new Mat();
        h2.create(3,3,CvType.CV_64F);
        double[] mat = new double[9];
        mat[0] = x;  mat[1] = 0.0;mat[2] = c.x;
        mat[3] = 0.0;mat[4] = y;  mat[5] = c.y;
        mat[6] = 0.0;mat[7] = 0.0;mat[8] = 1.0;
        h2.put(0,0,mat);
        return h2;
    }
    Mat Rotation(double x,double y, double z){
        Mat h2 = new Mat();
        h2.create(3,3,CvType.CV_64F);
        double ca = Math.cos(x), sa = Math.sin(x);
        double cb = Math.cos(y), sb = Math.sin(y);
        double cy = Math.cos(z), sy = Math.sin(z);
        double[] mat = new double[9];
        mat[0] = ca*cb;mat[1] = ca*sb*sy - sa*cy;mat[2] = ca*sb*cy + sa*sy;
        mat[3] = sa*cb;mat[4] = sa*sb*sy + ca*cy;mat[5] = sa*sb*cy - ca*sy;
        mat[6] = -sb;mat[7] = cb*sy;mat[8] = cb*cy;
        h2.put(0,0,mat);
        return h2;
    }
    ORB orb;
    GFTTDetector gfft;
    AKAZE akaze;
    DescriptorMatcher matcher;
    List<KeyPoint> keypointsRef = null;
    Mat descriptors1=new Mat();
    MatOfKeyPoint keyPoints1 = new MatOfKeyPoint();
    Mat findFrameHomography(Mat need, Mat from){
        Mat descriptors2=new Mat();
        MatOfKeyPoint keyPoints2 = new MatOfKeyPoint();
        if(keypointsRef == null) {
            akaze.detectAndCompute(need,new Mat(),keyPoints1,descriptors1);
            descriptors1.convertTo(descriptors1,CvType.CV_8UC1);
        }
        akaze.detectAndCompute(from,new Mat(),keyPoints2,descriptors2);
        descriptors2.convertTo(descriptors2,CvType.CV_8UC1);
        MatOfDMatch matches = new MatOfDMatch();
        Log.d("AlignAndMergeCV","src1:"+descriptors1.type()+":"+descriptors1.cols()+":"+descriptors1.rows());
        Log.d("AlignAndMergeCV","src2:"+descriptors2.type()+":"+descriptors2.cols()+":"+descriptors2.rows());
        keypointsRef = keyPoints1.toList();
        Log.d("AlignAndMergeCV","keyp size:"+keyPoints1.size());
        if(keypointsRef.size() < 10){
            return OneMatrix();
        }
        Log.d(Name,"Descr:"+descriptors1.toString());
        matcher.match(descriptors1, descriptors2, matches, new Mat());
        MatOfPoint2f points1=new MatOfPoint2f(), points2=new MatOfPoint2f();
        DMatch[]arr = matches.toArray();

        List<KeyPoint> keypoints2 = keyPoints2.toList();
        if(PhotonCamera.getSettings().DebugData) {
            Mat imMatches = new Mat();
            Mat keyPoints = new Mat();
            drawMatches(need, keyPoints1, from, keyPoints2, matches, imMatches);
            drawKeypoints(need, keyPoints1,keyPoints);
            Imgcodecs.imwrite(jpgFilePathToSave.toString().replace(".jpg", "cvin.jpg"), imMatches);
            Imgcodecs.imwrite(jpgFilePathToSave.toString().replace(".jpg", "kp.jpg"), keyPoints);
            imMatches.release();
            keyPoints.release();
        }
        ArrayList<Point> keypoints1f = new ArrayList<>();
        ArrayList<Point> keypoints2f = new ArrayList<>();
        int step = arr.length/3500 + 1;
        for(int i =0; i<arr.length;i+=step){
            DMatch dMatch = arr[i];
            Point on1 = keypointsRef.get(dMatch.queryIdx).pt;
            Point on2 = keypoints2.get(dMatch.trainIdx).pt;
            keypoints1f.add(on1);
            keypoints2f.add(on2);
        }
        Log.d(Name,"After filtering:"+keypoints1f.size());
        if(arr.length < 5){
            return OneMatrix();
        }
        points1.fromArray(keypoints1f.toArray(new Point[keypoints1f.size()]));
        points2.fromArray(keypoints2f.toArray(new Point[keypoints2f.size()]));
        Mat h = null;
        if(!points1.empty() && !points2.empty()){
            Log.d(Name,"points1:"+points1.toString());
            int iters = 7000/(arr.length/3000 + 1);
            h = findHomography(points1,points2,RANSAC,3.0,new Mat(),iters);
        }
        //keyPoints1.release();
        keyPoints2.release();
        if(h == null || h.size().height == 0) return OneMatrix();
        return h;
    }
    float[][] alignmentsH;
    int[][][] vectorMaps;
    int vectorsize = 4;
    private void Align(int num){
        Mat hm = findFrameHomography(BaseFrame2m,brTex2m);
        hm.convertTo(hm,CvType.CV_64F);
        double[] hfloats = new double[9];
        Log.d(Name,"HM"+hm.toString());
        hm.get(0,0,hfloats);
        float[] hfloats2 = new float[9];
        for(int i =0; i<9;i++){
            Log.d("AlignAndMerge","HMatrix:"+hfloats[i]);
            hfloats2[i] = (float)hfloats[i];
        }

        /*Mat X = new Mat(3,3,CvType.CV_64F);
        Mat K = new Mat(3,3,CvType.CV_64F);
        Mat KNeg = new Mat(3,3,CvType.CV_64F);
        Mat outp = new Mat(3,3,CvType.CV_64F);
        Log.d(Name,"ROT:"+"X:"+images.get(num).rX*180.0/Math.PI+",Y:"+images.get(num).rY*180.0/Math.PI+",Z:"+images.get(num).rZ*180.0/Math.PI);
        //Mat R = Rotation(images.get(num).rY,images.get(num).rX,images.get(num).rZ);
        Mat R = Rotation(images.get(num).rX,images.get(num).rY,0.0);
        K.put(0,0,basePipeline.mParameters.cameraIntrinsic);
        Core.invert(K,KNeg);

        X = OneMatrix();
        Core.multiply(X,KNeg,X);
        Core.multiply(K,R,outp);
        Core.multiply(outp,X,outp);

        double[] hfloats = new double[9];
        outp  = outp.t();
        outp.get(0,0,hfloats);

        float[] hfloats2 = new float[9];
        for(int i =0; i<9;i++){
            Log.d("AlignAndMerge","HMatrix:"+hfloats[i]);
            hfloats2[i] = (float)hfloats[i];
        }*/

        /*Mat hm = findFrameHomography(BaseFrame2m,brTex2m);
        double[] hfloats = new double[9];
        hm.get(0,0,hfloats);
        float[] hfloats2 = new float[9];
        for(int i =0; i<9;i++){
            Log.d("AlignAndMerge","HMatrix:"+hfloats[i]);
            hfloats2[i] = (float)hfloats[i];
        }
         */

        /*
        for(int i =0; i<rawSize.x/2;i+=vectorsize){
            for(int j =0; j<rawSize.y/2;j+=vectorsize){
                double x = (((double)i)*hfloats2[0] + ((double)j)*hfloats2[1] + hfloats2[2])/
                           (((double)i)*hfloats2[6] + ((double)j)*hfloats2[7] + hfloats2[8]);
                double y = (((double)i)*hfloats2[3] + ((double)j)*hfloats2[4] + hfloats2[5])/
                           (((double)i)*hfloats2[6] + ((double)j)*hfloats2[7] + hfloats2[8]);

            }
        }*/
        alignmentsH[num-1] = hfloats2.clone();
    }
    private void Weight(int num){
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("FRAMECOUNT",images.size());
        glProg.setDefine("MATMUL",((float)Math.min(rawSize.x,rawSize.y)));
        glProg.useProgram(R.raw.weightscv);
        glProg.setVar("HMatrix",false,alignmentsH[num-1]);
        glProg.setTexture("BaseFrame", BaseFrame2);
        glProg.setTexture("InputFrame", brTex2);
        glProg.drawBlocks(Weight[num-1]);
    }
    private void Weights() {
        GLTexture out = Weights;
        GLTexture alt = WeightsAlt;
        GLTexture t = Weights;
        glProg.useProgram(R.raw.sumweights);
        for(int i =1; i<images.size();i++){
            glProg.setTexture("WeightsIn", Weight[i-1]);
            glProg.setTexture("WeightsOut", out);
            glProg.drawBlocks(alt);
            t = alt;
            alt = out;
            out = t;
        }
        Weights = t;
    }

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw,int num) {
        //startT();
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("MIN",minMpy);
        glProg.setDefine("MPY",minMpy / images.get(num).pair.layerMpy);
        glProg.setDefine("WP",PhotonCamera.getParameters().whitePoint);
        glProg.setDefine("BAYER",PhotonCamera.getParameters().cfaPattern);
        glProg.setDefine("HDR",IsoExpoSelector.HDR);
        glProg.setDefine("ROTATION", (float) images.get(num).rotation);
        glProg.setDefine("FRAMECOUNT",images.size());
        glProg.setDefine("MATMUL",((float)Math.min(rawSize.x,rawSize.y)));


        glProg.useProgram(R.raw.cvmerge);

        glProg.setVar("HMatrix",false,alignmentsH[num-1]);
        glProg.setTexture("InputBuffer", inputRaw);
        glProg.setTexture("OutputBuffer", Output);
        glProg.setTexture("Weights",Weights);
        glProg.setTexture("Weight",Weight[num-1]);
        glProg.setVar("alignk", 1.f / (float) (((RawPipeline) (basePipeline)).imageObj.size()));
        glProg.setVar("number",num);
        glProg.setVarU("rawsize", rawSize);
        GLTexture output = basePipeline.getMain();
        glProg.drawBlocks(output);
        return output;
    }
    private GLTexture RawOutput(GLTexture input) {
        //startT();
        float[] outBL = new float[4];
        for(int i=0;i<outBL.length;i++) outBL[i] = PhotonCamera.getParameters().blackLevel[i]*(ProcessorBase.FAKE_WL/((float)PhotonCamera.getParameters().whiteLevel));
        glProg.setDefine("BL",outBL);
        glProg.setDefine("BAYER",PhotonCamera.getParameters().cfaPattern);
        glProg.useProgram(R.raw.toraw);
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("whitelevel", ProcessorBase.FAKE_WL);
        GLTexture output = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), null);
        glProg.drawBlocks(output);
        glProg.closed = true;
        //endT("RawOutput");
        return output;
    }

    android.graphics.Point rawSize;
    ArrayList<ImageFrame> images;
    GLTexture BaseFrame, BaseFrame2;
    byte[] BaseFrame2b,brTex2b;
    Mat BaseFrame2m;
    Mat brTex2m;
    GLTexture GainMap;
    GLTexture Weights;
    GLTexture WeightsAlt;
    GLTexture[] Weight;
    GLTexture brTex2;
    final int tileSize = 32;
    float minMpy = 1000.f;
    CLAHE clahe;
    void equalize(Mat in){
        ArrayList<Mat> mats = new ArrayList<>();
        Core.split(in,mats);
        for(int i =0; i<3;i++){
            //Imgproc.equalizeHist(mats.get(i),mats.get(i));
            clahe.apply(mats.get(i),mats.get(i));
        }
        Core.merge(mats,in);
        mats.get(0).release();
        mats.get(1).release();
        mats.get(2).release();
    }
    @Override
    public void Run() {

        Log.d("AlignAndMerge","Loading");

        Log.d("AlignAndMerge","Started");
        glProg = basePipeline.glint.glProgram;
        RawPipeline rawPipeline = (RawPipeline) basePipeline;
        rawSize = rawPipeline.glint.parameters.rawSize;
        images = rawPipeline.images;
        float perlevel = 2.5f;
        int levelcount = (int)(Math.log10(rawSize.y)/Math.log10(perlevel))+1;
        orb = ORB.create(1500,perlevel,levelcount);
        gfft = GFTTDetector.create();
        akaze = AKAZE.create();
        akaze.setDescriptorChannels(3);
        clahe = Imgproc.createCLAHE(2);
        //orb = ORB.create();
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        for (int i = 0; i < IsoExpoSelector.fullpairs.size(); i++) {
            if (IsoExpoSelector.fullpairs.get(i).layerMpy < minMpy) {
                minMpy = IsoExpoSelector.fullpairs.get(i).layerMpy;
            }
        }
        if (images.get(0).pair.layerMpy != minMpy) {
            for (int i = 1; i < images.size(); i++) {
                if (images.get(i).pair.layerMpy == minMpy) {
                    ImageFrame frame = images.get(0);
                    images.set(0, images.get(i));
                    images.set(i, frame);
                    break;
                }
            }
        }
        alignmentsH = new float[images.size()-1][9];
        vectorMaps = new int[images.size()-1][][];

        GainMap = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4),
                FloatBuffer.wrap(basePipeline.mParameters.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        basePipeline.main1 = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16));
        basePipeline.main2 = new GLTexture(basePipeline.main1);
        BaseFrame2 = new GLTexture(Utilities.div(rawSize,2),new GLFormat(GLFormat.DataType.FLOAT_16,3),GL_LINEAR,GL_CLAMP_TO_EDGE);
        brTex2 = new GLTexture(BaseFrame2);
        Weights = new GLTexture(new android.graphics.Point(brTex2.mSize.x/tileSize + 0,brTex2.mSize.y/tileSize + 0),new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        WeightsAlt = new GLTexture(Weights);
        Weight = new GLTexture[images.size()-1];
        for(int i = 1; i<images.size();i++){
            Weight[i-1] = new GLTexture(new android.graphics.Point(brTex2.mSize.x/tileSize + 0,brTex2.mSize.y/tileSize + 0),new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        }
        BaseFrame2b = new byte[BaseFrame2.mSize.x*BaseFrame2.mSize.y*4];
        brTex2b = new byte[BaseFrame2.mSize.x*BaseFrame2.mSize.y*4];
        basePipeline.main3 = new GLTexture(BaseFrame2);
        GLTexture Output = basePipeline.getMain();
        CorrectedRaw(Output,0);
        Log.d("AlignAndMerge","Initialized0");
        BoxDown22(Output,BaseFrame2);
        ByteBuffer baseBuff = ByteBuffer.allocateDirect(BaseFrame2.mSize.x*BaseFrame2.mSize.y*4);
        glInt.glProcessing.drawBlocksToOutput(BaseFrame2.mSize, new GLFormat(GLFormat.DataType.UNSIGNED_8,4),baseBuff);
        //ByteBuffer buffer = glInt.glProcessing.drawBlocksToOutput(BaseFrame2.mSize, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));

        //buffer.get(BaseFrame2b);
        Log.d("AlignAndMerge","Initialized01");
        BaseFrame2m = new Mat(BaseFrame2.mSize.y,BaseFrame2.mSize.x, CvType.CV_8UC(4),baseBuff);
        //BaseFrame2m.put(0,0,BaseFrame2b);
        equalize(BaseFrame2m);
        Log.d("AlignAndMerge","Initialized03");
        ByteBuffer inbuff = ByteBuffer.allocateDirect(BaseFrame2.mSize.x*BaseFrame2.mSize.y*4);

        brTex2m = new Mat(BaseFrame2.mSize.y,BaseFrame2.mSize.x, CvType.CV_8UC(4),inbuff);

        GLTexture inputraw = new GLTexture(Output);
        Log.d("AlignAndMerge","Initialized");
        for (int i = 1; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            BoxDown22(inputraw,brTex2);
            //ByteBuffer buffer1 = glInt.glProcessing.drawBlocksToOutput(BaseFrame2.mSize, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
            glInt.glProcessing.drawBlocksToOutput(brTex2.mSize, new GLFormat(GLFormat.DataType.UNSIGNED_8,4),inbuff);
            equalize(brTex2m);
            //buffer1.get(brTex2b);
            //brTex2m.put(0,0,brTex2b);

            Log.d("AlignAndMerge","Align");
            Align(i);
            Weight(i);
            //Output = Merge(Output, inputraw,i);
            Log.d("AlignAndMerge","Weight");
        }
        Log.d("AlignAndMerge","Weights");
        Weights();
        for (int i = 1; i < images.size(); i++) {
            Log.d("AlignAndMerge","CorrectedRaw");
            CorrectedRaw(inputraw,i);
            images.get(i).image.close();
            Log.d("AlignAndMerge","Merging");
            Output = Merge(Output, inputraw,i);
        }
        WorkingTexture = RawOutput(Output);
        //Output.close();
        Weights.close();
        for(GLTexture tex : Weight){
            tex.close();
        }
        BaseFrame2m.release();
        brTex2m.release();
        brTex2.close();
        BaseFrame2.close();
        GainMap.close();
        inputraw.close();
    }
}
