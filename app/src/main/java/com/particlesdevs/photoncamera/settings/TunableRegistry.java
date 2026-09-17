package com.particlesdevs.photoncamera.settings;

import com.particlesdevs.photoncamera.processing.opengl.postpipeline.Bayer2Float;

/**
 * Single source of truth for all classes that carry {@code @Tunable} annotations.
 * Add or remove entries here; {@link TunableSettingsManager} and the settings UI
 * both derive their class lists from this array.
 */
public final class TunableRegistry {

    private TunableRegistry() {}

    public static final Class<?>[] TUNABLE_CLASSES = {
        com.particlesdevs.photoncamera.ui.camera.CameraUIViewImpl.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.Sharpen2.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.ESD3D2.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.AutoExposureCurve.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.Bayer2Float.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.LocalLaplacian2.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.Initial.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.LinearExposure.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.HeadroomRender.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.Amaze.class,
        com.particlesdevs.photoncamera.processing.opengl.scripts.PyramidAlignment.class,
        com.particlesdevs.photoncamera.processing.opengl.scripts.ESD4D.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.ABLC.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.UpscaleCrop.class,
        com.particlesdevs.photoncamera.processing.opengl.postpipeline.KernelNetPrep.class,
        com.particlesdevs.photoncamera.processing.render.Parameters.class,
        com.particlesdevs.photoncamera.processing.ImageSaverSettings.class,
    };
}
