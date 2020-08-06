package com.eszdman.photoncamera.Control;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraReflectionApi;
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
        preview.setOnTouchListener(previewListener);
    }

    OnTouchListener focusListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            activated = false;
            focusEl.setVisibility(View.GONE);
            focusEl.setX((float) getMax().x/2.f);
            focusEl.setY((float) getMax().y/2.f);
            return true;
        }
    };
    OnTouchListener previewListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            activated = true;
            v.getScaleX();
            float x = event.getX();
            float y = Math.min(event.getY(),v.getHeight()-140.f);
            y = Math.max(90.f,y);
            focusEl.setX(x-150.f);
            focusEl.setY(y+110.f);
            focusEl.setVisibility(View.VISIBLE);
            setFocus();
            return true;
        }
    };
    public void setFocus(){
        Point size = new Point(Interface.i.camera.mImageReaderPreview.getWidth(),Interface.i.camera.mImageReaderPreview.getHeight());
        Point MaxUi = getMax();
        Point CurUi = getMax();
        Rect sizee = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if(sizee == null){
            sizee = new Rect(0,0,size.x,size.y);
        }
        double sizex = sizee.width();
        double sizey = sizee.height();
        //double sizex = MaxUi.x;
        //double sizey = MaxUi.y;
        double kx = (double)(CurUi.x)/MaxUi.x;
        double ky = (double)(CurUi.y)/MaxUi.y;
        MeteringRectangle[] rectaf = new MeteringRectangle[1];
        rectaf[0] =  new MeteringRectangle(new Point((int)(sizex*kx),(int)(sizey*ky)),new Size((int)(sizex/4),(int)(sizey/4)),MeteringRectangle.METERING_WEIGHT_MAX-1);
        CaptureRequest.Builder build = Interface.i.camera.mPreviewRequestBuilder;
        build.set(CaptureRequest.CONTROL_AF_REGIONS,rectaf);
        build.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        build.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        build.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        build.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
        //CameraReflectionApi.set(Interface.i.camera.mPreviewRequest,CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        Interface.i.camera.rebuildPreviewBuilder();
    }
    public Point getMax(){
        return new Point(preview.getWidth(),preview.getHeight());
    }
    public Point getCurrent(){
        return new Point((int)(focusEl.getX()+150.f),(int)(focusEl.getY()-110.f));
    }
}
