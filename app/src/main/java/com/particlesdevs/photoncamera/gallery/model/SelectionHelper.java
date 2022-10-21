package com.particlesdevs.photoncamera.gallery.model;

import android.widget.Checkable;

import java.util.ArrayList;

/**
 * Created by Vibhor Srivastava on October 14 2021
 *
 * @param <T> Checkable item
 */
public class SelectionHelper<T extends Checkable> {
    private final ArrayList<T> selectedItems = new ArrayList<>();
    private boolean selectionStarted;


    public boolean isSelectionStarted() {
        return selectionStarted;
    }

    public boolean isEmpty() {
        return selectedItems.isEmpty();
    }

    public boolean toggleSelection(T checkable) {
        if (selectedItems.contains(checkable)) {
            deselectItem(checkable);
            return false;
        } else {
            selectItem(checkable);
            return true;
        }
    }

    public ArrayList<T> getSelectedItems() {
        return selectedItems;
    }

    public void selectItem(T checkableItem) {
        checkableItem.setChecked(true);
        selectedItems.add(checkableItem);
        selectionStarted = true;
    }

    public void deselectItem(T checkableItem) {
        selectedItems.remove(checkableItem);
        checkableItem.setChecked(false);
    }

    public void deselectAll() {
        selectedItems.forEach(item -> item.setChecked(false));
        selectedItems.clear();
        selectionStarted = false;
    }
}
