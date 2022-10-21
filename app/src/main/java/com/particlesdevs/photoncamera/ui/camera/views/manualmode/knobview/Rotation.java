package com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview;


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
        while (orientation < 0)
            orientation += 360;
        if (orientation >= 45 && orientation < 135) {
            return LANDSCAPE;
        }
        if (orientation >= 135 && orientation < 225) {
            return INVERSE_PORTRAIT;
        }
        if (orientation < 225 || orientation >= 315) {
            return PORTRAIT;
        }
        return INVERSE_LANDSCAPE;
    }

    public static Rotation fromScreenOrientation(int screenOrientation) {
        switch (screenOrientation) {
            case 0:
                return LANDSCAPE;
            case 8:
                return INVERSE_LANDSCAPE;
            case 9:
                return INVERSE_PORTRAIT;
            case 1:
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
