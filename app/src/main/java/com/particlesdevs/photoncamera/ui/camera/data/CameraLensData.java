/*
 *
 *  PhotonCamera
 *  CameraLensData.java
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

package com.particlesdevs.photoncamera.ui.camera.data;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Data class which stores basic data related to camera lens
 * Mainly for usage with multi-camera buttons.
 */
public class CameraLensData {
    @SerializedName("id")
    private final String cameraId;
    @SerializedName("face")
    private int facing;
    @SerializedName("fl")
    private float cameraFocalLength;
    @SerializedName("ap")
    private float cameraAperture;
    @SerializedName("fl35")
    private float camera35mmFocalLength;
    @SerializedName("zf")
    private float zoomFactor;
    @SerializedName("fs")
    private boolean flashSupported;

    public CameraLensData(String cameraId) {
        this.cameraId = cameraId;
    }

    public int getFacing() {
        return facing;
    }

    public void setFacing(int facing) {
        this.facing = facing;
    }

    public String getCameraId() {
        return cameraId;
    }

    public float getCameraFocalLength() {
        return cameraFocalLength;
    }

    public void setCameraFocalLength(float cameraFocalLength) {
        this.cameraFocalLength = cameraFocalLength;
    }

    public float getCamera35mmFocalLength() {
        return camera35mmFocalLength;
    }

    public void setCamera35mmFocalLength(float camera35mmFocalLength) {
        this.camera35mmFocalLength = camera35mmFocalLength;
    }

    public float getZoomFactor() {
        return zoomFactor;
    }

    public void setZoomFactor(float zoomFactor) {
        this.zoomFactor = zoomFactor;
    }

    public float getCameraAperture() {
        return cameraAperture;
    }

    public void setCameraAperture(float cameraAperture) {
        this.cameraAperture = cameraAperture;
    }

    public void setFlashSupported(boolean flashSupported) {
        this.flashSupported = flashSupported;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CameraLensData that = (CameraLensData) o;
        return facing == that.facing && Float.compare(that.cameraFocalLength, cameraFocalLength) == 0 && Float.compare(that.cameraAperture, cameraAperture) == 0 && flashSupported == that.flashSupported;
    }

    @Override
    public int hashCode() {
        return Objects.hash(facing, cameraFocalLength, cameraAperture, flashSupported);
    }

    @Override
    @NonNull
    public String toString() {
        return "CameraLensData{" +
                "cameraId='" + cameraId + '\'' +
                ", facing=" + facing +
                ", cameraFocalLength=" + cameraFocalLength +
                ", cameraAperture=" + cameraAperture +
                ", camera35mmFocalLength=" + camera35mmFocalLength +
                ", zoomFactor=" + zoomFactor +
                "}\n";
    }
}
