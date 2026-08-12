package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Build;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.parameters.ResolutionSolution;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapResult;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Allocator;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class PostPipeline extends GLBasePipeline {
    private static final int GAIN_MAP_JPEG_QUALITY = 100;
    public ByteBuffer stackFrame;
    public ByteBuffer lowFrame;
    public ByteBuffer highFrame;
    public GLTexture FusionMap;
    public GLTexture GainMap;
    public ArrayList<Bitmap> debugData = new ArrayList<>();
    public ArrayList<ImageFrame> SAGAIN;
    public Point cropSize;
    public float[] analyzedBL = new float[]{0.f, 0.f, 0.f};
    float regenerationSense = 1.f;
    float totalGain = 1.f;
    float AecCorr = 1.f;
    float fusionGain = 1.f;
    float softLight = 1.f;

    public boolean ultraHdrEnabled = false;
    public boolean hdrLinearValid = false;
    public Initial initialNode;
    public Bitmap gainMapBitmap;
    public Point gainMapSize;
    public float gainMapMaxBoost;
    public float gainMapMinBoost;
    public GainMapResult gainMapResult;

    public PostPipeline() {
        super("PostPipeline");
    }

    public int getRotation() {
        int rotation = mParameters.cameraRotation;
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCaptureController().mSensorOrientation);
        return rotation;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Point getRotatedCoords(Point in) {
        switch (getRotation()) {
            case 0:
            case 180:
                return in;
            case 90:
            case 270:
                return new Point(in.y, in.x);
        }
        return in;
    }

    float constShift = 0.0f;
    
    @Tunable(
        title = "Demosaicing Method",
        description = "0 = Demosaic (compatibility mode), 1 = Demosaic3 (better quality)",
        category = "Demosaic",
        min = 0.0f,
        max = 1.0f,
        defaultValue = 1.0f,
        step = 1.0f
    )
    int demosaicingMethod = 1;

    public Bitmap Run(ByteBuffer inBuffer, Parameters parameters) {
        mParameters = parameters;
        mSettings = PhotonCamera.getSettings();
        // Live preference read: the cached Settings object is refreshed via a
        // preference listener, but reading the preference directly guarantees
        // the Ultra HDR toggle is honoured on the very next capture.
        ultraHdrEnabled = PreferenceKeys.isUltraHdrOn()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
        hdrLinearValid = false;
        gainMapBitmap = null;
        gainMapSize = null;
        gainMapMaxBoost = 0.0f;
        gainMapMinBoost = 1.0f;
        gainMapResult = null;
        if (PreferenceKeys.isUltraHdrOn()) {
            Log.d("PostPipeline", "UltraHDR enabled=" + ultraHdrEnabled + " sdk="
                    + Build.VERSION.SDK_INT);
        }
        workSize = new Point(mParameters.rawSize.x, mParameters.rawSize.y);
        NoiseModeler modeler = mParameters.noiseModeler;
        noiseS = modeler.computeModel[0].first.floatValue() +
                modeler.computeModel[1].first.floatValue() +
                modeler.computeModel[2].first.floatValue();
        noiseO = modeler.computeModel[0].second.floatValue() +
                modeler.computeModel[1].second.floatValue() +
                modeler.computeModel[2].second.floatValue();
        noiseS /= 3.f;
        noiseO /= 3.f;
        double noisempy = Math.pow(2.0, mSettings.noiseRstr + constShift);
        Log.d("PostPipeline", "noisempy:" + noisempy);
        noiseS *= noisempy;
        noiseO *= noisempy;
        Log.d("PostPipeline", "NoiseS:" + noiseS + "\n" + "NoiseO:" + noiseO);
        /*if (!PhotonCamera.getSettings().hdrxNR) {
            noiseO = 0.f;
            noiseS = 0.f;
        }*/
        noiseO = Math.max(noiseO, 1.0f/4096.0f);
        noiseS = Math.max(noiseS, Float.MIN_NORMAL);
        Point rawSliced = parameters.rawSize;
        cropSize = new Point(parameters.rawSize);
        if (PhotonCamera.getSettings().aspect169) {
            if (rawSliced.x > rawSliced.y) {
                rawSliced = new Point(rawSliced.x, rawSliced.x * 9 / 16);
            } else {
                rawSliced = new Point(rawSliced.y * 9 / 16, rawSliced.y);
            }
            cropSize =  new Point(rawSliced);
        }
        Point rotatedSize = getRotatedCoords(rawSliced);
        if (PhotonCamera.getSettings().energySaving || mParameters.rawSize.x * mParameters.rawSize.y < ResolutionSolution.smallRes) {
            GLDrawParams.TileSize = 8;
        } else {
            GLDrawParams.TileSize = 256;
        }
        GLFormat format = new GLFormat(GLFormat.DataType.SIMPLE_8, 4);
        GLImage output = new GLImage(rotatedSize, format, false);
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(rotatedSize, output, format, GLDrawParams.Allocate.Direct);
        glint = new GLInterface(glproc);
        stackFrame = inBuffer;
        glint.parameters = parameters;

        // Inject tunable values for PostPipeline (since it doesn't extend Node)
        com.particlesdevs.photoncamera.settings.TunableInjector.inject(this);
        
        BuildDefaultPipeline();
        GLImage resImg = runAll();
        Bitmap res = resImg.getBufferedImage();
        Allocator.free(resImg.byteBuffer);
        buildGainMapResult();
        GLTexture.closeAll();
        return res;
    }

    private void buildGainMapResult() {
        if (gainMapBitmap == null || gainMapSize == null) {
            if (ultraHdrEnabled) {
                Log.d("PostPipeline", "UltraHDR: no gain map data (hdrLinearValid="
                        + hdrLinearValid + ")");
            }
            return;
        }
        try {
            float maxBoost = gainMapMaxBoost;
            if (maxBoost <= 0.0f) {
                Log.d("PostPipeline", "UltraHDR: no gain map data, skipping gain map");
                gainMapBitmap.recycle();
                gainMapBitmap = null;
                return;
            }
            float minBoost = gainMapMinBoost;
            Bitmap gainMap = gainMapBitmap;
            gainMapBitmap = null;
            ByteArrayOutputStream jpegStream = new ByteArrayOutputStream(1 << 20);
            if (!gainMap.compress(Bitmap.CompressFormat.JPEG, GAIN_MAP_JPEG_QUALITY, jpegStream)) {
                Log.e("PostPipeline", "UltraHDR gain map JPEG compression failed");
                gainMap.recycle();
                gainMapSize = null;
                return;
            }
            gainMap.recycle();
            gainMapResult = new GainMapResult(jpegStream.toByteArray(), maxBoost, minBoost);
            Log.d("PostPipeline", "UltraHDR gain map generated: " + gainMapSize.x + "x"
                    + gainMapSize.y + " jpeg=" + gainMapResult.gainMapJpeg.length
                    + "B maxContentBoost=" + maxBoost + " minContentBoost=" + minBoost);
        } catch (Exception e) {
            Log.e("PostPipeline", "UltraHDR gain map processing failed: "
                    + Log.getStackTraceString(e));
            gainMapResult = null;
        }
        if (gainMapBitmap != null) {
            gainMapBitmap.recycle();
            gainMapBitmap = null;
        }
        gainMapSize = null;
    }

    private void BuildDefaultPipeline() {
        boolean nightMode = PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
        add(new Bayer2Float());
        add(new ExposureFusionBayer2());
        switch (PhotonCamera.getSettings().cfaPattern) {
            case -2: {
                add(new DemosaicQUAD());
                break;
            }
            case 4: {
                add(new MonoDemosaic());
                break;
            }
            default: {
                //if (nightMode)
                //    add(new HotPixelFilter());
                //if(PhotonCamera.getSettings().hdrxNR) {
                //add(new ESD3DBayerCS());
                //}

                if (PhotonCamera.getSettings().hdrxNR) {

                    //add(new BayerFilter());
                    /*if (nightMode) {
                        add(new BayerConcat(true));
                        add(new BayerFilter());
                        add(new BayerConcat(false));
                    }*/
                    //add(new BayerMoire());

                }

                if(mSettings.alignAlgorithm != 2) {
                    //add(new HotPixelFilter());
                    // demosaicingMethod is automatically injected from settings
                    //noinspection SwitchStatementWithTooFewBranches
                    switch (demosaicingMethod){
                        case 0:
                            add(new Demosaic());
                            break;
                        default:
                            add(new Demosaic3());
                            break;
                    }
                }
                if (PhotonCamera.getSettings().hdrxNR) {
                    add(new ESD3D2(true));
                }
                //add(new ImpulsePixelFilter());
                break;
            }
        }
        add(new ABLC());
        /*
         * * * All filters after demosaicing * * *
         */

        //if (PhotonCamera.getSettings().hdrxNR) {
            //if (nightMode)
            //    add(new Wavelet());
            //add(new ESD3D(true));
            //add(new ESD3D(true));
        //}

        //add(new AWB());
        //add(new Equalization());

        initialNode = new Initial();
        add(initialNode);

        add(new AutoExposure());

        if (ultraHdrEnabled) {
            add(new GainMapGenerator());
        }


        //add(new GlobalToneMapping());

        add(new CaptureSharpening());

        add(new CorrectingFlow());

        //add(new ChromaticFlow());

        add(new Sharpen2());
        //add(new Sharpen("sharpen33"));

        add(new RotateWatermark(getRotation()));
    }
}
