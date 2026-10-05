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
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_RENDERBUFFER;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindRenderbuffer;
import static android.opengl.GLES20.glFramebufferRenderbuffer;
import static android.opengl.GLES20.glGenFramebuffers;
import static android.opengl.GLES20.glGenRenderbuffers;
import static android.opengl.GLES20.glRenderbufferStorage;
import static android.opengl.GLES30.GL_DRAW_FRAMEBUFFER;
import static android.opengl.GLES30.GL_RGBA8;

public class GLContext implements AutoCloseable {
    private EGLDisplay mDisplay;
    private EGLContext mContext;
    private EGLSurface mSurface;
    public GLProg mProgram;
    public final int[] bindFB = new int[1];
    public final int[] bindRB = new int[1];

    /**
     * Live contexts on the process display connection. The display is
     * initialized by the first and terminated by the last, so a context that
     * shares another's EGL group survives sibling passes instead of being
     * torn down by whichever context closes first - the old per-context
     * eglTerminate killed shared textures the moment any other pass ended.
     */
    private static int sDisplayRefs = 0;

    public GLContext(int surfaceWidth, int surfaceHeight) {
        createContext(surfaceWidth, surfaceHeight, null);
    }

    /** Creates the context in {@code shareWith}'s EGL group (null = unshared). */
    public GLContext(int surfaceWidth, int surfaceHeight, EGLContext shareWith) {
        createContext(surfaceWidth, surfaceHeight, shareWith);
    }

    public void createContext(int surfaceWidth, int surfaceHeight) {
        createContext(surfaceWidth, surfaceHeight, null);
    }

    public void createContext(int surfaceWidth, int surfaceHeight, EGLContext shareWith) {
        int[] major = new int[2];
        int[] minor = new int[2];
        mDisplay = eglGetDisplay(GLDrawParams.EGLDisplay);
        if (sDisplayRefs == 0) {
            eglInitialize(mDisplay, major, 0, minor, 0);
        }
        sDisplayRefs++;
        try {
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
            mContext = eglCreateContext(mDisplay, configs[0],
                    shareWith != null ? shareWith : EGL_NO_CONTEXT,
                    GLDrawParams.contextAttributeList, 0);
            if ((mContext == null || mContext == EGL_NO_CONTEXT) && shareWith != null) {
                // Sharing rejected: retry unshared so the caller's fallback path
                // (CPU ferry, SR skipped) still gets a working context.
                mContext = eglCreateContext(mDisplay, configs[0], EGL_NO_CONTEXT,
                        GLDrawParams.contextAttributeList, 0);
            }
            if (mContext == null) {
                throw new RuntimeException("OpenGL context creation failed");
            }
            // Use 1x1 pbuffer; FBO holds full frame, avoids EGL max pbuffer limit
            mSurface = eglCreatePbufferSurface(mDisplay, configs[0], new int[]{
                    EGL_WIDTH, 1,
                    EGL_HEIGHT, 1,
                    EGL_NONE
            }, 0);
            eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
            mProgram = new GLProg();
        } catch (Throwable t) {
            // No live context came out of this: drop the display reference so
            // the refcount still tracks live contexts exactly.
            sDisplayRefs = Math.max(0, sDisplayRefs - 1);
            if (sDisplayRefs == 0) {
                try {
                    eglTerminate(mDisplay);
                } catch (Exception ignored) {
                }
            }
            throw new RuntimeException("OpenGL context creation failed", t);
        }
    }

    /** Makes this context current again (e.g. after a probe context ran). */
    public void makeCurrent() {
        if (mDisplay != null && mContext != null && mSurface != null) {
            eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
        }
    }

    /** This context's EGL handle, for passes that want to share its group. */
    public EGLContext getEGLContext() {
        return mContext;
    }

    @Override
    public void close() {
        if (mDisplay == null) return;
        try {
            if (mProgram != null) mProgram.close();
        } catch (Exception ignored) {}
        try {
            // Only unbind if this context is the current one: a sibling (e.g.
            // the post sharing the merge's group) may legitimately own the
            // binding, and unbinding it would break its subsequent GL calls.
            if (android.opengl.EGL14.eglGetCurrentContext() == mContext) {
                eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            }
        } catch (Exception ignored) {}
        try {
            if (mContext != null) eglDestroyContext(mDisplay, mContext);
        } catch (Exception ignored) {}
        try {
            if (mSurface != null) eglDestroySurface(mDisplay, mSurface);
        } catch (Exception ignored) {}
        sDisplayRefs = Math.max(0, sDisplayRefs - 1);
        if (sDisplayRefs == 0) {
            try {
                eglTerminate(mDisplay);
            } catch (Exception ignored) {}
        }
        mDisplay = null;
        mContext = null;
        mSurface = null;
    }
}
