package com.particlesdevs.photoncamera.circularbarlib.api;

import com.particlesdevs.photoncamera.circularbarlib.console.ManualModeConsoleImpl;

public class ManualInstanceProvider {

    private static volatile HapticPerformer hapticPerformer = HapticPerformer.NO_OP;

    /**
     * @return Singleton Instance
     */
    public static ManualModeConsole getManualModeConsole() {
        return ManualModeConsoleImpl.getInstance();
    }

    /**
     * @return new Instance
     */
    public static ManualModeConsole getNewManualModeConsole() {
        return ManualModeConsoleImpl.newInstance();
    }

    public static void setHapticPerformer(HapticPerformer performer) {
        hapticPerformer = performer != null ? performer : HapticPerformer.NO_OP;
    }

    public static HapticPerformer getHapticPerformer() {
        return hapticPerformer;
    }
}
