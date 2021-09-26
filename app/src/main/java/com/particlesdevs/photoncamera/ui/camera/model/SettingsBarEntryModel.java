/*
 *
 *  PhotonCamera
 *  SettingsBarEntryModel.java
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

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.lifecycle.MutableLiveData;

import com.particlesdevs.photoncamera.settings.SettingType;
import com.particlesdevs.photoncamera.ui.camera.views.settingsbar.SettingsBarListener;

public class SettingsBarEntryModel {
    private final int id;
    private final MutableLiveData<TopBarSettingsData<?, ?>> topBarSettingsData = new MutableLiveData<>();
    private int titleStringId;
    private int stateTextStringId;
    private SettingsBarButtonModel[] settingsBarButtonModels;
    private SettingsBarListener settingsBarListener;
    private Enum<SettingType> type;

    public SettingsBarEntryModel(@IdRes int id) {
        this.id = id;
    }

    private SettingsBarEntryModel(@IdRes int id, @StringRes int titleStringId, Enum<SettingType> type) {
        this.id = id;
        setTitleStringId(titleStringId);
        setTypeAndData(type);
    }

    public static SettingsBarEntryModel newEntry(@IdRes int id, @StringRes int titleStringId, Enum<SettingType> type) {
        return new SettingsBarEntryModel(id, titleStringId, type);
    }

    public void setType(Enum<SettingType> type) {
        this.type = type;
    }

    public void setTypeAndData(Enum<SettingType> type) {
        this.type = type;
        this.topBarSettingsData.setValue(new TopBarSettingsData<>(type));
    }

    public MutableLiveData<TopBarSettingsData<?, ?>> getTopBarSettingsData() {
        return topBarSettingsData;
    }

    public void setSettingsBarListener(SettingsBarListener settingsBarListener) {
        this.settingsBarListener = settingsBarListener;
    }

    public int getId() {
        return id;
    }

    public int getTitleStringId() {
        return titleStringId;
    }

    public void setTitleStringId(@StringRes int titleStringId) {
        this.titleStringId = titleStringId;
    }

    public int getStateTextStringId() {
        return stateTextStringId;
    }

    public void setStateTextStringId(@StringRes int stateTextStringId) {
        this.stateTextStringId = stateTextStringId;
    }

    public SettingsBarButtonModel[] getSettingsBarButtonModels() {
        return settingsBarButtonModels;
    }

    public void addSettingsBarButtonModels(SettingsBarButtonModel... settingsBarButtonModels) {
        this.settingsBarButtonModels = settingsBarButtonModels;
    }

    public void select(SettingsBarButtonModel buttonModel) {
        if (settingsBarButtonModels != null) {
            for (SettingsBarButtonModel model : settingsBarButtonModels)
                model.setSelected(model.getId() == buttonModel.getId());
            setStateTextStringId(buttonModel.getButtonStateNameStringId());
        }
        if (settingsBarListener != null) {
            settingsBarListener.onEntryUpdated(this, buttonModel);
        }
        topBarSettingsData.setValue(new TopBarSettingsData<>(type, buttonModel.getButtonValue()));
    }
}
