/*
 *
 *  PhotonCamera
 *  AuxButtonsModel.java
 *  Copyright (C) 2020 - 2021  Vibhor
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * /
 */

package com.particlesdevs.photoncamera.ui.camera.model;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import com.particlesdevs.photoncamera.BR;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.ui.camera.views.AuxButtonsLayout;

import java.util.List;

public class AuxButtonsModel extends BaseObservable {
    private List<CameraLensData> frontCameras;
    private List<CameraLensData> backCameras;
    private String currentCameraId;
    private AuxButtonsLayout.AuxButtonListener auxButtonListener;

    public AuxButtonsLayout.AuxButtonListener getAuxButtonListener() {
        return auxButtonListener;
    }

    public void setAuxButtonListener(AuxButtonsLayout.AuxButtonListener auxButtonListener) {
        this.auxButtonListener = auxButtonListener;
    }

    public List<CameraLensData> getFrontCameras() {
        return frontCameras;
    }

    public void setFrontCameras(List<CameraLensData> frontCameras) {
        this.frontCameras = frontCameras;
    }

    public List<CameraLensData> getBackCameras() {
        return backCameras;
    }

    public void setBackCameras(List<CameraLensData> backCameras) {
        this.backCameras = backCameras;
    }

    @Bindable
    public String getCurrentCameraId() {
        return currentCameraId;
    }

    public void setCurrentCameraId(String currentCameraId) {
        this.currentCameraId = currentCameraId;
        notifyPropertyChanged(BR.currentCameraId);
    }
}
