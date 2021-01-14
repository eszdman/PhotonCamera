package com.particlesdevs.photoncamera.processing.opengl;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

import static android.opengl.EGL14.EGL_HEIGHT;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.EGL_WIDTH;
import static android.opengl.EGL14.eglChooseConfig;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglCreatePbufferSurface;
import static android.opengl.EGL14.eglDestroyContext;
import static android.opengl.EGL14.eglDestroySurface;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;
import static android.opengl.EGL14.eglTerminate;

public class GLContext implements AutoCloseable {
    private EGLDisplay mDisplay;
    private EGLContext mContext;
    private EGLSurface mSurface;
    public GLProg mProgram;

    public GLContext(int surfaceWidth, int surfaceHeight) {
        createContext(surfaceWidth,surfaceHeight);
    }
    public void createContext(int surfaceWidth, int surfaceHeight){
        int[] major = new int[2];
        int[] minor = new int[2];
        mDisplay = eglGetDisplay(GLDrawParams.EGLDisplay);
        eglInitialize(mDisplay, major, 0, minor, 0);
        int[] numConfig = new int[1];
        if (!eglChooseConfig(mDisplay, GLDrawParams.attribList, 0,
                null, 0, 0, numConfig, 0)
                || numConfig[0] == 0) {
            throw new RuntimeException("OpenGL config count zero");
        }
        int configSize = numConfig[0];
        EGLConfig[] configs = new EGLConfig[configSize];
        if (!eglChooseConfig(mDisplay, GLDrawParams.attribList, 0,
                configs, 0, configSize, numConfig, 0)) {
            throw new RuntimeException("OpenGL config loading failed");
        }
        if (configs[0] == null) {
            throw new RuntimeException("OpenGL config is null");
        }
        mContext = eglCreateContext(mDisplay, configs[0], EGL_NO_CONTEXT, GLDrawParams.contextAttributeList, 0);
        mSurface = eglCreatePbufferSurface(mDisplay, configs[0], new int[]{
                EGL_WIDTH, surfaceWidth,
                EGL_HEIGHT, surfaceHeight,
                EGL_NONE
        }, 0);
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
        mProgram = new GLProg();
    }

    @Override
    public void close() {
        mProgram.close();
        eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(mDisplay, mContext);
        eglDestroySurface(mDisplay, mSurface);
        eglTerminate(mDisplay);
    }
}
