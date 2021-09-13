package com.particlesdevs.photoncamera.processing.processor;
/*
 * Abstract Class defining base for an image processor class.
 *
 *         Copyright (C) 2020-2021  Eszdman
 *         This program is free software: you can redistribute it and/or modify
 *         it under the terms of the GNU General Public License as published by
 *         the Free Software Foundation, either version 3 of the License, or
 *         (at your option) any later version.
 *
 *         This program is distributed in the hope that it will be useful,
 *         but WITHOUT ANY WARRANTY; without even the implied warranty of
 *         MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *         GNU General Public License for more details.
 *
 *         You should have received a copy of the GNU General Public License
 *         along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;

import java.nio.file.Path;

/**
 * Created by Vibhor Srivastava on 02/Jan/2021
 */
public abstract class ProcessorBase {
    public static float FAKE_WL = 65535.f;
    protected final ProcessingEventsListener processingEventsListener;
    protected Path dngFile;
    protected Path jpgFile;
    protected CameraCharacteristics characteristics;
    protected CaptureResult captureResult;
    protected ProcessingCallback callback;
    protected ParseExif.ExifData exifData;
    protected int cameraRotation;
    public Bitmap overlay(Bitmap bmp1, Bitmap bmp2, int cnt) {
        if(bmp2 == null) return bmp1;
        Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bmp1, new Matrix(), null);
        Matrix mat = new Matrix();
        mat.setTranslate(bmp2.getWidth()*cnt,0);
        canvas.drawBitmap(bmp2, mat, null);
        return bmOverlay;
    }
    public Bitmap overlay(Bitmap bmp1, Bitmap[] bmp2) {
        if(bmp2 == null || bmp2.length == 0) return bmp1;
        int prevS = 0;
        Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bmp1, new Matrix(), null);
        for(Bitmap inb : bmp2) {
            if(inb == null) continue;
            Matrix mat = new Matrix();
            mat.setTranslate(prevS, 0);
            canvas.drawBitmap(inb, mat, null);
            prevS+=inb.getWidth();
        }
        return bmOverlay;
    }
    public ProcessorBase(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
    }

    public void process() {
    }

    public void IncreaseWLBL() {
        //Increase WL and BL for processing
        for (int i = 0; i < 4; i++) {
            PhotonCamera.getParameters().blackLevel[i] *= FAKE_WL / PhotonCamera.getParameters().whiteLevel;
        }
        IncreaseWL();
    }

    public void IncreaseWL() {
        PhotonCamera.getParameters().whiteLevel = (int) (FAKE_WL);
    }

    public interface ProcessingCallback {
        void onStarted();

        void onFailed();

        void onFinished();
    }

}
