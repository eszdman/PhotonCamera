package com.eszdman.photoncamera.OpenGL;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import static android.opengl.EGL14.EGL_ALPHA_SIZE;
import static android.opengl.EGL14.EGL_BIND_TO_TEXTURE_RGBA;
import static android.opengl.EGL14.EGL_BLUE_SIZE;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_DEPTH_SIZE;
import static android.opengl.EGL14.EGL_GREEN_SIZE;
import static android.opengl.EGL14.EGL_HEIGHT;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_OPENGL_ES2_BIT;
import static android.opengl.EGL14.EGL_PBUFFER_BIT;
import static android.opengl.EGL14.EGL_RED_SIZE;
import static android.opengl.EGL14.EGL_RENDERABLE_TYPE;
import static android.opengl.EGL14.EGL_STENCIL_SIZE;
import static android.opengl.EGL14.EGL_SURFACE_TYPE;
import static android.opengl.EGL14.EGL_TRUE;
import static android.opengl.EGL14.EGL_WIDTH;
import static android.opengl.EGL14.eglChooseConfig;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglCreatePbufferSurface;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;

public class GLContext {
    private final EGLDisplay mDisplay;
    private final EGLContext mContext;
    private final EGLSurface mSurface;
    public GLContext(int surfaceWidth, int surfaceHeight) {
        int[] major = new int[2];
        int[] minor = new int[2];
        mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(mDisplay, major, 0, minor, 0);
        int[] attribList = {
                EGL_DEPTH_SIZE, 0,
                EGL_STENCIL_SIZE, 0,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_BIND_TO_TEXTURE_RGBA, EGL_TRUE,
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
        };
        int[] numConfig = new int[1];
        if (!eglChooseConfig(mDisplay, attribList, 0,
                null, 0, 0, numConfig, 0)
                || numConfig[0] == 0) {
            throw new RuntimeException("OpenGL config count zero");
        }
        int configSize = numConfig[0];
        EGLConfig[] configs = new EGLConfig[configSize];
        if (!eglChooseConfig(mDisplay, attribList, 0,
                configs, 0, configSize, numConfig, 0)) {
            throw new RuntimeException("OpenGL config loading failed");
        }
        if (configs[0] == null) {
            throw new RuntimeException("OpenGL config is null");
        }
        mContext = eglCreateContext(mDisplay, configs[0], EGL_NO_CONTEXT, new int[] {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_NONE
        }, 0);
        mSurface = eglCreatePbufferSurface(mDisplay, configs[0], new int[] {
                EGL_WIDTH, surfaceWidth,
                EGL_HEIGHT, surfaceHeight,
                EGL_NONE
        }, 0);
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
    }
}
