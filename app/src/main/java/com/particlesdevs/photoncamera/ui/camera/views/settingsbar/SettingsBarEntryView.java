/*
 *
 *  PhotonCamera
 *  SettingsBarEntryView.java
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

package com.particlesdevs.photoncamera.ui.camera.views.settingsbar;

import android.content.Context;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.ui.camera.model.SettingsBarButtonModel;
import com.particlesdevs.photoncamera.ui.camera.model.SettingsBarEntryModel;
import com.particlesdevs.photoncamera.ui.camera.views.SelectorPillLayout;

import java.util.ArrayList;
import java.util.List;

import static android.view.Gravity.CENTER_VERTICAL;

public class SettingsBarEntryView extends LinearLayout {
    private final TextView titleTextView;
    private final TextView stateTextView;
    private final List<ImageButton> imageButtons = new ArrayList<>();
    private final Context context;
    /**
     * The option buttons live in their own row so it can paint the sliding
     * selection pill (see {@link SelectorPillLayout}) behind them.
     */
    private final SelectorPillLayout buttonRow;

    public SettingsBarEntryView(Context context) {
        super(context);
        this.context = context;
        setPadding(dp(10), dp(8), dp(10), dp(8));
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setOrientation(HORIZONTAL);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        textContainer.setOrientation(VERTICAL);

        titleTextView = new TextView(context);
        titleTextView.setId(android.R.id.title);
        titleTextView.setGravity(CENTER_VERTICAL);
        titleTextView.setTextAlignment(TEXT_ALIGNMENT_VIEW_END);
        titleTextView.setTextAppearance(R.style.BoldTextWithShadow);
        titleTextView.setTextSize(13);

        stateTextView = new TextView(context);
        stateTextView.setId(android.R.id.summary);
        stateTextView.setGravity(CENTER_VERTICAL);
        stateTextView.setTextAlignment(TEXT_ALIGNMENT_VIEW_END);
        stateTextView.setTextColor(resolveThemeColor(context,
                R.attr.colorPrimary, 0xFFA8C7FA));
        LayoutParams textViewParam = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textViewParam.setMargins(dp(2), dp(2), dp(2), dp(2));

        textContainer.addView(titleTextView, textViewParam);
        textContainer.addView(stateTextView, textViewParam);

        buttonRow = new SelectorPillLayout(context);
        buttonRow.setOrientation(HORIZONTAL);
        buttonRow.setGravity(CENTER_VERTICAL);
        buttonRow.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(textContainer);
        addView(buttonRow);
    }

    public void setSettingsBarEntryModel(SettingsBarEntryModel entryModel) {
        titleTextView.setText(entryModel.getTitleStringId());
        // Guard: id 0 (unset state label) throws Resources$NotFoundException.
        if (entryModel.getStateTextStringId() != 0) {
            stateTextView.setText(entryModel.getStateTextStringId());
        }

        imageButtons.clear();
        buttonRow.removeAllViews();
        if (entryModel.getSettingsBarButtonModels() != null) {
            for (SettingsBarButtonModel buttonModel : entryModel.getSettingsBarButtonModels()) {
                ImageButton button = new ImageButton(context);
                button.setId(buttonModel.getId());
                button.setImageResource(buttonModel.getButtonDrawableId());
                button.setImageTintList(AppCompatResources.getColorStateList(context, R.color.cam_icon_tint));
                // No per-button highlight: the row's sliding pill is the
                // selection and the icon tint follows the selected state.
                button.setBackground(null);
                button.setPadding(0, 0, 0, 0);
                button.setCropToPadding(false);
                button.setOnClickListener(buttonModel.getButtonClickListener());
                button.setSelected(buttonModel.isSelected());
                imageButtons.add(button);
            }
            addToLayout(imageButtons);
        }
        refreshSelection();
    }

    /**
     * Re-syncs the selection pill after in-place selected-state updates (an
     * option tap updates the models without rebuilding the views).
     */
    public void refreshSelection() {
        buttonRow.refreshSelection();
    }

    private void addToLayout(List<ImageButton> buttons) {
        LayoutParams buttonParam = new LayoutParams(dp(40), dp(40));
        buttonParam.setMargins(dp(5), dp(2), dp(5), dp(2));
        if (buttons != null) {
            for (ImageButton button : buttons) {
                buttonRow.addView(button, buttonParam);
            }
        }
    }

    private int dp(float f) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, f, getContext().getResources().getDisplayMetrics());
    }

    /**
     * Resolves a theme attribute to a color, falling back to a fixed value so
     * a lookup can never crash inflation or binding.
     */
    private static int resolveThemeColor(Context context, int attrRes, int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attrRes, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return fallback;
    }

}
