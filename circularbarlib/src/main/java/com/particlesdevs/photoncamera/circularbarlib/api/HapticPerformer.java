package com.particlesdevs.photoncamera.circularbarlib.api;

public interface HapticPerformer {
    HapticPerformer NO_OP = new HapticPerformer() {
        @Override
        public void tick() {
        }

        @Override
        public void click() {
        }

        @Override
        public void longPress() {
        }
    };

    void tick();

    void click();

    void longPress();
}
