/*
 *
 *  PhotonCamera
 *  SettingsBarButtonModel.java
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

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

public class SettingsBarButtonModel {
    private final int buttonDrawableId;
    private final int buttonStateNameStringId;
    private final int buttonValue;
    private final int id;
    private View.OnClickListener buttonClickListener;
    private boolean selected;


    private SettingsBarButtonModel(@IdRes int id, @DrawableRes int buttonDrawableId, @StringRes int buttonStateNameStringId, int buttonValue) {
        this.id = id;
        this.buttonDrawableId = buttonDrawableId;
        this.buttonStateNameStringId = buttonStateNameStringId;
        this.buttonValue = buttonValue;
    }

    public static SettingsBarButtonModel newButtonModel(@IdRes int id, @DrawableRes int buttonDrawableId, @StringRes int buttonStateNameStringId, int buttonValue, SettingsBarEntryModel entryModel) {
        SettingsBarButtonModel buttonModel = new SettingsBarButtonModel(id, buttonDrawableId, buttonStateNameStringId, buttonValue);
        buttonModel.setButtonClickListener(v -> entryModel.select(buttonModel));
        return buttonModel;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getButtonDrawableId() {
        return buttonDrawableId;
    }

    public int getButtonStateNameStringId() {
        return buttonStateNameStringId;
    }

    public int getButtonValue() {
        return buttonValue;
    }

    public View.OnClickListener getButtonClickListener() {
        return buttonClickListener;
    }

    public void setButtonClickListener(View.OnClickListener buttonClickListener) {
        this.buttonClickListener = buttonClickListener;
    }

    public int getId() {
        return id;
    }

}
