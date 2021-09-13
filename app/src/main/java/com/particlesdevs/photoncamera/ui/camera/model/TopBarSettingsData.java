/*
 *
 *  PhotonCamera
 *  TopBarSettingsData.java
 *  Copyright (C) 2020 - 2021  Vibhor Srivastava
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

public class TopBarSettingsData<T, V extends Comparable<? super V>> {
    private final T type;
    private V value;

    public TopBarSettingsData(T type) {
        this.type = type;
    }

    public TopBarSettingsData(T type, V value) {
        this.type = type;
        this.value = value;
    }

    public T getType() {
        return type;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }
}
