package com.manual;


public enum Rotation {
    LANDSCAPE(270),
    INVERSE_LANDSCAPE(90),
    PORTRAIT(0),
    INVERSE_PORTRAIT(180);

    private final int m_DeviceOrientation;

    Rotation(int deviceOrientation) {
        this.m_DeviceOrientation = deviceOrientation;
    }

    public static Rotation fromDeviceOrientation(int orientation) {
        while (orientation < 0) {
            orientation += 360;
        }
        int orientation2 = orientation % 360;
        if (orientation2 >= 45 && orientation2 < 135) {
            return INVERSE_LANDSCAPE;
        }
        if (orientation2 >= 135 && orientation2 < 225) {
            return INVERSE_PORTRAIT;
        }
        if (orientation2 < 225 || orientation2 >= 315) {
            return PORTRAIT;
        }
        return LANDSCAPE;
    }

    public static Rotation fromScreenOrientation(int screenOrientation) {
        switch (screenOrientation) {
            case 0:
                return LANDSCAPE;
            case 1:
                return PORTRAIT;
            case 8:
                return INVERSE_LANDSCAPE;
            case 9:
                return INVERSE_PORTRAIT;
            default:
                return PORTRAIT;
        }
    }

    public int getDeviceOrientation() {
        return this.m_DeviceOrientation;
    }

    public boolean isInverse() {
        switch (this) {
            case INVERSE_PORTRAIT:
            case INVERSE_LANDSCAPE:
                return true;
            default:
                return false;
        }
    }

    public boolean isLandscape() {
        switch (this) {
            case INVERSE_LANDSCAPE:
            case LANDSCAPE:
                return true;
            default:
                return false;
        }
    }

    public boolean isPortrait() {
        switch (this) {
            case INVERSE_PORTRAIT:
            case PORTRAIT:
                return true;
            default:
                return false;
        }
    }
}
