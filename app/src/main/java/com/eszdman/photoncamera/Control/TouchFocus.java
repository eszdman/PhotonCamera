package com.eszdman.photoncamera.Control;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

public class TouchFocus {
    private final String TAG = "TouchFocus";
    boolean activated = false;
    ImageView focusEl;
    AutoFitTextureView preview;
    public void ReInit(){
        focusEl = Interface.i.mainActivity.findViewById(R.id.touchFocus);
        focusEl.setOnTouchListener(focusListener);
        preview = Interface.i.mainActivity.findViewById(R.id.texture);
    }

    OnTouchListener focusListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            activated = false;
            focusEl.setVisibility(View.GONE);
            focusEl.setX((float) getMax().x/2.f);
            focusEl.setY((float) getMax().y/2.f);
            setFocus(getMax().x/2,getMax().y/2);
            Interface.i.camera.rebuildPreviewBuilder();
            //CaptureRequest.Builder build = Interface.i.camera.mPreviewRequestBuilder;
            //build.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            //build.set(CaptureRequest.CONTROL_AF_MODE, Interface.i.settings.afMode);
            //set focus area repeating,else cam forget after one frame where it should focus
            //Interface.i.camera.rebuildPreviewBuilder();
            return true;
        }
    };

    public void processTochToFocus(View v, float fx, float fy) {
        activated = true;
        focusEl.setX(fx-focusEl.getMeasuredWidth()/2.0f);
        focusEl.setY(fy-focusEl.getMeasuredHeight()/2.0f);
        focusEl.setVisibility(View.VISIBLE);
        setFocus((int)fy,(int)fx);
        Interface.i.camera.rebuildPreviewBuilder();
    }

    public void setFocus(int x, int y){
        Point size = new Point(Interface.i.camera.mImageReaderPreview.getWidth(),Interface.i.camera.mImageReaderPreview.getHeight());
        Point CurUi = getMax();
        Rect sizee = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if(sizee == null){
            sizee = new Rect(0,0,size.x,size.y);
        }
        if (x< 0)
            x = 0;
        if (y<0)
            y = 0;
        if (y > CurUi.y)
            y = CurUi.y;
        if (x > CurUi.x)
            x  =CurUi.x;
        //use 1/8 from the the sensor size for the focus rect
        int width_to_set = sizee.width()/8;
        int height_to_set = width_to_set;
        float x_scale = (float)sizee.width()/(float)CurUi.x;
        float y_scale = (float)sizee.height()/(float)CurUi.y;
        int x_to_set = (int)(x * x_scale) -width_to_set/2;
        int y_to_set = (int)(y * y_scale) - height_to_set/2;
        if (x_to_set < 0)
            x_to_set = 0;
        if (y_to_set < 0)
            y_to_set = 0;
        if (y_to_set + height_to_set/2 > sizee.height())
            y_to_set = sizee.height() - height_to_set;
        if (x_to_set + width_to_set/2 > sizee.width())
            y_to_set = sizee.width() - width_to_set;
        MeteringRectangle rect_to_set =new MeteringRectangle(x_to_set,y_to_set, width_to_set,height_to_set,MeteringRectangle.METERING_WEIGHT_MAX-1);
        MeteringRectangle[] rectaf = new MeteringRectangle[1];
        Log.v(TAG, "\nInput x/y:" +x+"/"+y +"\n" +
                "sensor size width/height to set:" + width_to_set+"/" + height_to_set+"\n" +
                "preview/sensorsize: " + CurUi.toString() + " / " + sizee.toString() + "\n"+
                "scale x/y:" + x_scale+"/"+y_scale+"\n"+
                "final rect :" + rect_to_set.toString());
        rectaf[0] = rect_to_set;
        CaptureRequest.Builder build = Interface.i.camera.mPreviewRequestBuilder;
        build.set(CaptureRequest.CONTROL_AF_REGIONS,rectaf);
        build.set(CaptureRequest.CONTROL_AE_REGIONS,rectaf);
        build.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        build.set(CaptureRequest.CONTROL_AF_MODE, Interface.i.settings.afMode);
        build.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
        //set focus area repeating,else cam forget after one frame where it should focus
        //Interface.i.camera.rebuildPreviewBuilder();
        //trigger af start only once. cam starts focusing till its focused or failed

        //build.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        //build.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        //Interface.i.camera.rebuildPreviewBuilderOneShot();
        //set focus trigger back to idle to signal cam after focusing is done to do nothing

        //build.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
        //build.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        //Interface.i.camera.rebuildPreviewBuilderOneShot();
    }
    public Point getMax(){
        return new Point(preview.getWidth(),preview.getHeight());
    }
    public Point getCurrent(){
        return new Point((int)(focusEl.getX()+150.f),(int)(focusEl.getY()-110.f));
    }
}
