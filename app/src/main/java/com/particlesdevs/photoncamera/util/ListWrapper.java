package com.particlesdevs.photoncamera.util;

import java.util.ArrayList;

public class ListWrapper<T> {
    private ArrayList<T> list;

    public ListWrapper(ArrayList<T> list) {
        this.list = list;
    }

    public ArrayList<T> getList() {
        return list;
    }

    public void setList(ArrayList<T> list) {
        this.list = list;
    }
}
