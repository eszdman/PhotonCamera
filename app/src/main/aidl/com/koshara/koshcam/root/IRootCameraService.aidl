package com.koshara.koshcam.root;

interface IRootCameraService {
    int configureHal(String config);
    int captureBurst(int frameCount);
    int setPerformanceGovernor();
    int getThermalTemperature();
}
