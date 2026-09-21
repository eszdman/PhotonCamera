package com.particlesdevs.photoncamera.root;

interface IRootCameraService {
    int configureHal(String config);
    int captureBurst(int frameCount);
}
