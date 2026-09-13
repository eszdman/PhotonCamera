package com.particlesdevs.photoncamera.processing.encoder;

import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES30;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal 10-bit EGL renderer that feeds a {@code MediaCodec} input surface
 * from the pipeline's packed ABGR1010102 sink.
 *
 * <p>Hardware HEVC encoders commonly accept 10-bit frames only through an
 * input surface (HDR video capture works this way; buffer P010 input is
 * rejected by the QTI V4L2 component). This renders each tile/frame as a
 * full-surface quad into an RGB10_A2 EGL window surface created over
 * {@code codec.createInputSurface()}, so the encoder performs the
 * RGB10 -> P010 conversion itself.
 */
final class SurfaceInputRenderer {

    private static final String VERTEX_SHADER =
            "#version 300 es\n"
                    + "out vec2 vTex;\n"
                    + "void main() {\n"
                    + "  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));\n"
                    + "  vTex = p;\n"
                    + "  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n"
                    + "precision highp float;\n"
                    + "in vec2 vTex;\n"
                    + "uniform sampler2D uTex;\n"
                    + "out vec4 oColor;\n"
                    + "void main() {\n"
                    + "  oColor = texture(uTex, vTex);\n"
                    + "}\n";

    /** Shared process-wide display: EGL init/terminate is expensive. */
    private static android.opengl.EGLDisplay sDisplay = EGL14.EGL_NO_DISPLAY;

    private android.opengl.EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private android.opengl.EGLContext context = EGL14.EGL_NO_CONTEXT;
    private android.opengl.EGLSurface surface = EGL14.EGL_NO_SURFACE;
    private int program = 0;
    private int texture = 0;
    private int textureUniform = -1;
    private ByteBuffer tileBuffer;

    private static synchronized android.opengl.EGLDisplay sharedDisplay() {
        if (sDisplay == EGL14.EGL_NO_DISPLAY) {
            android.opengl.EGLDisplay d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (d == null || d == EGL14.EGL_NO_DISPLAY) {
                throw new IllegalStateException("No EGL display");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(d, version, 0, version, 1)) {
                throw new IllegalStateException("eglInitialize failed");
            }
            sDisplay = d;
        }
        return sDisplay;
    }

    /** Creates a 10-bit ES3 window surface over the codec input surface. */
    void connect(Surface input) {
        display = sharedDisplay();
        int[] configAttribs = {
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 10,
                EGL14.EGL_GREEN_SIZE, 10,
                EGL14.EGL_BLUE_SIZE, 10,
                EGL14.EGL_NONE
        };
        android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1,
                numConfigs, 0) || numConfigs[0] == 0) {
            throw new IllegalStateException("No 10-bit EGL config");
        }
        int[] redBits = new int[1];
        EGL14.eglGetConfigAttrib(display, configs[0], EGL14.EGL_RED_SIZE, redBits, 0);
        if (redBits[0] < 10) {
            throw new IllegalStateException("EGL config is not 10-bit (R=" + redBits[0] + ")");
        }
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            throw new IllegalStateException("eglCreateContext failed");
        }
        surface = EGL14.eglCreateWindowSurface(display, configs[0], input,
                new int[]{EGL14.EGL_NONE}, 0);
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("eglCreateWindowSurface failed (surface not 10-bit RGB?)");
        }
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw new IllegalStateException("eglMakeCurrent failed");
        }
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        textureUniform = GLES30.glGetUniformLocation(program, "uTex");
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        texture = textures[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
    }

    /**
     * Renders one frame (full sink, or the tile at {@code tileX/tileY}) into
     * the encoder surface and swaps buffers with the given presentation time.
     */
    void render(ByteBuffer source, int fullWidth, int fullHeight,
            int tileX, int tileY, int tileW, int tileH, long presentationTimeNs) {
        ByteBuffer pixels;
        if (tileW == fullWidth && tileH == fullHeight) {
            pixels = source;
        } else {
            if (tileBuffer == null || tileBuffer.capacity() < tileW * tileH * 4) {
                tileBuffer = ByteBuffer.allocateDirect(tileW * tileH * 4)
                        .order(ByteOrder.nativeOrder());
            }
            TenBitHeicEncoder.copyAbgr1010102Tile(source, fullWidth, fullHeight,
                    tileX, tileY, tileW, tileH, tileBuffer);
            pixels = tileBuffer;
        }
        pixels.position(0);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB10_A2,
                tileW, tileH, 0, GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_INT_2_10_10_10_REV, pixels);
        GLES30.glViewport(0, 0, tileW, tileH);
        GLES30.glUseProgram(program);
        GLES30.glUniform1i(textureUniform, 0);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        EGLExt.eglPresentationTimeANDROID(display, surface, presentationTimeNs);
        if (!EGL14.eglSwapBuffers(display, surface)) {
            throw new IllegalStateException("eglSwapBuffers failed");
        }
    }

    void release() {
        // Delete GL objects while the context is current, then tear the
        // window surface/context down. The EGLDisplay is process-wide and
        // deliberately not terminated.
        if (display != EGL14.EGL_NO_DISPLAY && surface != EGL14.EGL_NO_SURFACE
                && context != EGL14.EGL_NO_CONTEXT) {
            try {
                EGL14.eglMakeCurrent(display, surface, surface, context);
            } catch (Exception ignored) {
            }
        }
        if (program != 0) {
            try {
                GLES30.glDeleteProgram(program);
            } catch (Exception ignored) {
            }
            program = 0;
        }
        if (texture != 0) {
            try {
                GLES30.glDeleteTextures(1, new int[]{texture}, 0);
            } catch (Exception ignored) {
            }
            texture = 0;
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            try {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            } catch (Exception ignored) {
            }
            if (surface != EGL14.EGL_NO_SURFACE) {
                try {
                    EGL14.eglDestroySurface(display, surface);
                } catch (Exception ignored) {
                }
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                try {
                    EGL14.eglDestroyContext(display, context);
                } catch (Exception ignored) {
                }
            }
        }
        display = EGL14.EGL_NO_DISPLAY;
        context = EGL14.EGL_NO_CONTEXT;
        surface = EGL14.EGL_NO_SURFACE;
        tileBuffer = null;
    }

    private static int buildProgram(String vertexSrc, String fragmentSrc) {
        int vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSrc);
        int fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc);
        int prog = GLES30.glCreateProgram();
        GLES30.glAttachShader(prog, vertex);
        GLES30.glAttachShader(prog, fragment);
        GLES30.glLinkProgram(prog);
        int[] linked = new int[1];
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linked, 0);
        GLES30.glDeleteShader(vertex);
        GLES30.glDeleteShader(fragment);
        if (linked[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(prog);
            GLES30.glDeleteProgram(prog);
            throw new IllegalStateException("10-bit surface program link failed: " + log);
        }
        return prog;
    }

    private static int compile(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new IllegalStateException("10-bit surface shader failed: " + log);
        }
        return shader;
    }
}
