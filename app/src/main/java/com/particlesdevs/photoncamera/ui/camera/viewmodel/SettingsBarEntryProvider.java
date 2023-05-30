/*
 *
 *  PhotonCamera
 *  SettingsBarEntryProvider.java
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

package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingType;
import com.particlesdevs.photoncamera.ui.camera.model.SettingsBarButtonModel;
import com.particlesdevs.photoncamera.ui.camera.model.SettingsBarEntryModel;
import com.particlesdevs.photoncamera.ui.camera.model.TopBarSettingsData;
import com.particlesdevs.photoncamera.ui.camera.views.settingsbar.SettingsBarLayout;

import java.util.ArrayList;
import java.util.List;

public abstract class SettingsBarEntryProvider extends ViewModel {

    protected SettingsBarEntryModel hdrxEntry;
    protected SettingsBarEntryModel timerEntry;
    protected SettingsBarEntryModel quadEntry;
    protected SettingsBarEntryModel fpsEntry;
    protected SettingsBarEntryModel flashEntry;
    protected SettingsBarEntryModel gridEntry;
    protected SettingsBarEntryModel eisEntry;
    protected SettingsBarEntryModel saveRawEntry;
    protected SettingsBarEntryModel batterySaverEntry;
    protected List<SettingsBarEntryModel> allEntries;

    public SettingsBarEntryProvider(){}
    public abstract void createEntries();
    public abstract void updateAllEntries();
    public abstract void addObserver(Observer<TopBarSettingsData<?, ?>> observer);
    public abstract void removeObserver(Observer<TopBarSettingsData<?, ?>> observer);
    public abstract void addEntries(SettingsBarLayout settingsBarLayout);
    protected abstract void createHdrxEntry();
    protected abstract void createQuadBayerEntry();
    protected abstract void createEisEntry();
    protected abstract void createSaveRawEntry();
    protected abstract void createBatterySaverEntry();
    protected abstract void createFlashEntry();
    protected abstract void createFpsEntry();
    protected abstract void createTimerEntry();
    protected abstract void createGridEntry();
    protected abstract void updateEntry(SettingsBarEntryModel entry, int value);
    protected abstract void updateEntry(SettingsBarEntryModel entry, boolean value);
}
