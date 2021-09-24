/*
 *
 *  PhotonCamera
 *  AuxButtonsViewModel.java
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

package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import android.hardware.camera2.CameraCharacteristics;

import androidx.lifecycle.ViewModel;

import com.particlesdevs.photoncamera.ui.camera.CameraFragment;
import com.particlesdevs.photoncamera.ui.camera.data.CameraLensData;
import com.particlesdevs.photoncamera.ui.camera.model.AuxButtonsModel;
import com.particlesdevs.photoncamera.ui.camera.views.AuxButtonsLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * ViewModel which connects {@link AuxButtonsModel} with {@link CameraFragment}
 */
public class AuxButtonsViewModel extends ViewModel {
    private static final Comparator<CameraLensData> SORT_BY_ZOOM_FACTOR = Comparator.comparingDouble(CameraLensData::getZoomFactor);
    private final AuxButtonsModel auxButtonsModel = new AuxButtonsModel();
    private boolean initialized = false;

    public void initCameraLists(Map<String, CameraLensData> cameraLensDataMap) {
        if (!initialized) {
            List<CameraLensData> frontCameras = new ArrayList<>();
            List<CameraLensData> backCameras = new ArrayList<>();
            cameraLensDataMap.forEach((id, cameraLensData) -> {
                if (cameraLensData.getFacing() == CameraCharacteristics.LENS_FACING_BACK)
                    backCameras.add(cameraLensData);
                else if (cameraLensData.getFacing() == CameraCharacteristics.LENS_FACING_FRONT)
                    frontCameras.add(cameraLensData);
            });
            backCameras.sort(SORT_BY_ZOOM_FACTOR);
            frontCameras.sort(SORT_BY_ZOOM_FACTOR);
            auxButtonsModel.setBackCameras(backCameras);
            auxButtonsModel.setFrontCameras(frontCameras);
            initialized = true;
        }
    }

    public void setAuxButtonListener(AuxButtonsLayout.AuxButtonListener auxButtonListener) {
        auxButtonsModel.setAuxButtonListener(auxButtonListener);
    }

    public void setActiveId(String cameraId) {
        auxButtonsModel.setCurrentCameraId(cameraId);
    }

    public AuxButtonsModel getAuxButtonsModel() {
        return auxButtonsModel;
    }
}
