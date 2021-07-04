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

public class SettingsBarEntryProvider extends ViewModel {
    private final SettingsBarEntryModel hdrxEntry = SettingsBarEntryModel.newEntry(R.id.hdrx_entry_layout, R.string.hdrx, SettingType.HDRX);
    private final SettingsBarEntryModel timerEntry = SettingsBarEntryModel.newEntry(R.id.timer_entry_layout, R.string.countdown_timer, SettingType.TIMER);
    private final SettingsBarEntryModel quadEntry = SettingsBarEntryModel.newEntry(R.id.quad_entry_layout, R.string.quad_bayer_toggle_text, SettingType.QUAD);
    private final SettingsBarEntryModel fpsEntry = SettingsBarEntryModel.newEntry(R.id.fps_entry_layout, R.string.fps_60_toggle_text, SettingType.FPS_60);
    private final SettingsBarEntryModel flashEntry = SettingsBarEntryModel.newEntry(R.id.flash_entry_layout, R.string.flash, SettingType.FLASH);
    private final SettingsBarEntryModel gridEntry = SettingsBarEntryModel.newEntry(R.id.grid_entry_layout, R.string.turn_on_grid, SettingType.GRID);
    private final SettingsBarEntryModel eisEntry = SettingsBarEntryModel.newEntry(R.id.eis_entry_layout, R.string.eis_toggle_text, SettingType.EIS);
    private final SettingsBarEntryModel saveRawEntry = SettingsBarEntryModel.newEntry(R.id.saveraw_entry_layout, R.string.raw_string, SettingType.RAW);
    private final SettingsBarEntryModel batterySaverEntry = SettingsBarEntryModel.newEntry(R.id.batterysaver_entry_layout, R.string.energy_saving, SettingType.BATTERY_SAVER);
    private final List<SettingsBarEntryModel> allEntries = new ArrayList<>(8);

    public SettingsBarEntryProvider() {
//        allEntries.add(hdrxEntry);
        allEntries.add(flashEntry);
        allEntries.add(timerEntry);
        allEntries.add(saveRawEntry);
        allEntries.add(quadEntry);
        allEntries.add(eisEntry);
        allEntries.add(fpsEntry);
        allEntries.add(gridEntry);
        allEntries.add(batterySaverEntry);
    }

    public void createEntries() {
        createHdrxEntry();
        createQuadBayerEntry();
        createEisEntry();
        createFlashEntry();
        createFpsEntry();
        createTimerEntry();
        createSaveRawEntry();
        createGridEntry();
        createBatterySaverEntry();
        updateAllEntries();
    }

    public void updateAllEntries() {
        updateEntry(gridEntry, PreferenceKeys.getGridValue());
        updateEntry(flashEntry, PreferenceKeys.getAeMode());
        updateEntry(timerEntry, PreferenceKeys.getCountdownTimerIndex());
        updateEntry(hdrxEntry, PreferenceKeys.isHdrXOn());
        updateEntry(eisEntry, PreferenceKeys.isEisPhotoOn());
        updateEntry(fpsEntry, PreferenceKeys.isFpsPreviewOn());
        updateEntry(quadEntry, PreferenceKeys.isQuadBayerOn());
        updateEntry(saveRawEntry, PreferenceKeys.isSaveRawOn());
        updateEntry(batterySaverEntry, PreferenceKeys.isBatterySaverOn());
    }

    public void addObserver(Observer<TopBarSettingsData<?, ?>> observer) {
        allEntries.forEach(settingsBarEntryModel -> settingsBarEntryModel.getTopBarSettingsData().observeForever(observer));
    }

    public void removeObserver(Observer<TopBarSettingsData<?, ?>> observer) {
        allEntries.forEach(settingsBarEntryModel -> settingsBarEntryModel.getTopBarSettingsData().removeObserver(observer));
    }

    public void addEntries(SettingsBarLayout settingsBarLayout) {
        settingsBarLayout.removeEntries();
        allEntries.forEach(settingsBarLayout::addEntry);
    }

    private void createHdrxEntry() {
        hdrxEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.hdrx_off_button, R.drawable.ic_hdrx_off, R.string.off, 0, hdrxEntry),
                SettingsBarButtonModel.newButtonModel(R.id.hdrx_on_button, R.drawable.ic_hdrx_on, R.string.on, 1, hdrxEntry)
        );
    }

    private void createQuadBayerEntry() {
        quadEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.quad_off_button, R.drawable.ic_quad_off, R.string.off, 0, quadEntry),
                SettingsBarButtonModel.newButtonModel(R.id.quad_on_button, R.drawable.ic_quad_on, R.string.on, 1, quadEntry)
        );
    }

    private void createEisEntry() {
        eisEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.eis_off_button, R.drawable.ic_eis_off, R.string.off, 0, eisEntry),
                SettingsBarButtonModel.newButtonModel(R.id.eis_on_button, R.drawable.ic_eis_on, R.string.on, 1, eisEntry)
        );
    }

    private void  createSaveRawEntry() {
        saveRawEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.raw_off_button, R.drawable.ic_raw_off, R.string.jpg_only, 0, saveRawEntry),
                SettingsBarButtonModel.newButtonModel(R.id.raw_on_button, R.drawable.ic_raw, R.string.raw_plus_jpg, 1, saveRawEntry)
        );
    }

    private void createBatterySaverEntry() {
        batterySaverEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.btsvr_off_button, R.drawable.ic_round_battery_alert_24, R.string.off, 0, batterySaverEntry),
                SettingsBarButtonModel.newButtonModel(R.id.btsvr_on_button, R.drawable.leaf_icon_15, R.string.on, 1, batterySaverEntry)
        );
    }

    private void createFlashEntry() {
        flashEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.torch_button, R.drawable.ic_torch, R.string.torch, 0, flashEntry),
                SettingsBarButtonModel.newButtonModel(R.id.flash_odd_button, R.drawable.ic_flash_off, R.string.off, 1, flashEntry),
                SettingsBarButtonModel.newButtonModel(R.id.flash_auto_button, R.drawable.ic_flash_auto, R.string.auto, 2, flashEntry),
                SettingsBarButtonModel.newButtonModel(R.id.flash_on_button, R.drawable.ic_flash_on, R.string.on, 3, flashEntry)
        );
    }

    private void createFpsEntry() {
        fpsEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.fps60_off_button, R.drawable.ic_60fps_off, R.string.off, 0, fpsEntry),
                SettingsBarButtonModel.newButtonModel(R.id.fps60_on_button, R.drawable.ic_60fps_on, R.string.on, 1, fpsEntry)
        );
    }

    private void createTimerEntry() {
        timerEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.timer_off_button, R.drawable.ic_timeroff, R.string.off, 0, timerEntry),
                SettingsBarButtonModel.newButtonModel(R.id.timer3s_button, R.drawable.ic_timer3s, R.string.t_3s, 1, timerEntry),
                SettingsBarButtonModel.newButtonModel(R.id.timer10s_button, R.drawable.ic_timer10s, R.string.t_10s, 2, timerEntry)
        );
    }

    private void createGridEntry() {
        gridEntry.addSettingsBarButtonModels(
                SettingsBarButtonModel.newButtonModel(R.id.grid_off_button, R.drawable.ic_grid_off, R.string.off, 0, gridEntry),
                SettingsBarButtonModel.newButtonModel(R.id.grid_33_button, R.drawable.ic_grid_on, R.string.three_x3, 1, gridEntry),
                SettingsBarButtonModel.newButtonModel(R.id.grid_44_button, R.drawable.ic_grid_on, R.string.four_x4, 2, gridEntry),
                SettingsBarButtonModel.newButtonModel(R.id.grid_gr_button, R.drawable.ic_grid_on, R.string.golden_ratio, 3, gridEntry),
                SettingsBarButtonModel.newButtonModel(R.id.grid_dt_button, R.drawable.ic_grid_on, R.string.diag_triangle, 4, gridEntry)
        );
    }

    private void updateEntry(SettingsBarEntryModel entry, int value) {
        for (SettingsBarButtonModel buttonModel : entry.getSettingsBarButtonModels()) {
            if (buttonModel.getButtonValue() == value) {
                buttonModel.setSelected(true);
                entry.setStateTextStringId(buttonModel.getButtonStateNameStringId());
            } else {
                buttonModel.setSelected(false);
            }
        }
    }

    private void updateEntry(SettingsBarEntryModel entry, boolean value) {
        for (SettingsBarButtonModel buttonModel : entry.getSettingsBarButtonModels()) {
            if ((buttonModel.getButtonValue() == 1) == value) {
                buttonModel.setSelected(true);
                entry.setStateTextStringId(buttonModel.getButtonStateNameStringId());
            } else {
                buttonModel.setSelected(false);
            }
        }
    }
}
