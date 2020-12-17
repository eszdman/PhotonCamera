package com.eszdman.photoncamera.processing.opengl;

import static android.opengl.EGL14.EGL_ALPHA_SIZE;
import static android.opengl.EGL14.EGL_BIND_TO_TEXTURE_RGBA;
import static android.opengl.EGL14.EGL_BLUE_SIZE;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_DEPTH_SIZE;
import static android.opengl.EGL14.EGL_GREEN_SIZE;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_OPENGL_ES2_BIT;
import static android.opengl.EGL14.EGL_PBUFFER_BIT;
import static android.opengl.EGL14.EGL_RED_SIZE;
import static android.opengl.EGL14.EGL_RENDERABLE_TYPE;
import static android.opengl.EGL14.EGL_STENCIL_SIZE;
import static android.opengl.EGL14.EGL_SURFACE_TYPE;
import static android.opengl.EGL14.EGL_TRUE;

public class GLDrawParams {
    public static int TileSize = 256;
    public final static int WorkDim = 3;
    final static int EGLDisplay = EGL_DEFAULT_DISPLAY;
    final static int[] attribList = {
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
    final static int[] contextAttributeList = new int[]{
            EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL_NONE
    };
}
