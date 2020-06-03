package com.eszdman.photoncamera.OpenGL;

import android.content.Context;
import android.graphics.Point;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Process extends GLSurfaceView implements GLSurfaceView.Renderer{
    private static String TAG = "OpenGL_Process";
    final static String vs_source =
                    "#version 310 es\n" +
                    "in vec2 position;\n" +
                    //"out vec2 texpos;\n" +
                    "void main() {\n" +
                            "gl_Position = vec4(position,0.0, 1.0);" +
                            "" +
                    //"  gl_Position = vec4(position, 0.0, 1.0);\n" +
                    //"  texpos = position * 0.5 + 0.5;\n" +
                    "}\n";
    final static String fs_source =
            "uniform sampler2D tex;\n" +
            "varying highp vec2 texpos;\n" +
            "void main(void) {\n" +
            "gl_FragColor = texture2D(tex, texpos.xy);\n" +
            "}\n";

    public Process(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        setDebugFlags(DEBUG_CHECK_GL_ERROR);
        setRenderer(this);
    }
    public Process(Context context, AttributeSet set){
        super(context,set);
    }

    public static void checkEglError(String op) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            String msg = op + ": glError: " + GLUtils.getEGLErrorString(error) + " (" + Integer.toHexString(error) + ")";
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }
    private static int compileShader(int type, String source) {
                int shader;
                int[] compiled = new int[1];
                shader = GLES30.glCreateShader (type);
                checkEglError("createshader");
                Log.d(TAG,"shader:"+shader);
                if ( shader == 0 ) return 0;
                GLES30.glShaderSource (shader,source);
                GLES30.glCompileShader ( shader );
                GLES30.glGetShaderiv ( shader, GLES30.GL_COMPILE_STATUS, compiled, 0 );
                if (compiled[0] == 0)
                {
                    Log.e ( TAG,"ESSHADED:"+GLES30.glGetShaderInfoLog(shader));
                    GLES30.glDeleteShader (shader);
                    return 0;
                }
                return shader;
        }
        private static int program;
        private static void prepareShaders() {
        int vertex_shader = compileShader(GLES30.GL_VERTEX_SHADER,
                vs_source);
            Log.d(TAG,"vertex done!~");
        int fragment_shader = compileShader(GLES30.GL_FRAGMENT_SHADER,
                fs_source);
            Log.d(TAG,"fragment done!~");
        //program = GLES30.glCreateProgram();
            program = GLES30.glCreateProgram();             // create empty OpenGL Program
            GLES30.glAttachShader(program, vertex_shader);   // add the vertex shader to program
            GLES30.glAttachShader(program, fragment_shader); // add the fragment shader to program
            GLES30.glLinkProgram(program);                  // create OpenGL program executables

        /*if (program == 0) {
            throw new RuntimeException("Invalid GLSL program");
        }
            GLES30.glAttachShader(program, vertex_shader);
            GLES30.glAttachShader(program, fragment_shader);
            GLES30.glBindAttribLocation(program, 0, "position");
            GLES30.glLinkProgram(program);

        int[] status = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(program);
            throw new RuntimeException("Linking GLSL program failed");
        } */
    }
    public static ByteBuffer Run(ByteBuffer in, Point size){
        final float[] vertices = new float[] {
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f, 1.0f,
                1.0f, 1.0f,
        };
        FloatBuffer quad_vertices =
                ByteBuffer.allocateDirect(4 * vertices.length)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        quad_vertices.put(vertices);
        prepareShaders();
        int output = TextureHelper.loadTexture(size,in);
        GLES30.glUseProgram(program);
        int positionLoc = GLES30.glGetAttribLocation(program, "position");
        quad_vertices.position(0);
        GLES30.glVertexAttribPointer(positionLoc, 2, GLES30.GL_FLOAT, false, 0, quad_vertices);
        GLES30.glEnableVertexAttribArray(positionLoc);

        int texLoc = GLES30.glGetUniformLocation(program, "tex");
        GLES30.glUniform1i(texLoc, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, output);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        GLES30.glDisableVertexAttribArray(positionLoc);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glUseProgram(0);
        GLES30.glDisableVertexAttribArray(0);
        return TextureHelper.saveTexture(size,output);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG,"SurfaceCreated");
        Process.Run(ByteBuffer.allocate(2),new Point(1,1));
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG,"SurfaceChanged");
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        Log.d(TAG,"SurfaceDraw");
    }
}
