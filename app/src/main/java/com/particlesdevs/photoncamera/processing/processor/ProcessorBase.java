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
