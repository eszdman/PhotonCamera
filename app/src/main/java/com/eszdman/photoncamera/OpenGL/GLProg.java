package com.eszdman.photoncamera.OpenGL;

import android.opengl.GLES30;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.opengl.GLES30.GL_FRAGMENT_SHADER;
import static android.opengl.GLES30.GL_TEXTURE0;
import static android.opengl.GLES30.glGetAttribLocation;
import static android.opengl.GLES30.glGetUniformLocation;
import static android.opengl.GLES30.glUniform1f;
import static android.opengl.GLES30.glUniform1i;
import static android.opengl.GLES30.glUniform2f;
import static android.opengl.GLES30.glUniform2i;
import static android.opengl.GLES30.glUniform3f;
import static android.opengl.GLES30.glUniform3i;
import static android.opengl.GLES30.glUniform4f;
import static android.opengl.GLES30.glUniform4i;
import static android.opengl.GLES30.glUniformMatrix3fv;
import static android.opengl.GLES30.glUseProgram;
import static android.opengl.GLES30.GL_COMPILE_STATUS;
import static android.opengl.GLES30.GL_LINK_STATUS;
import static android.opengl.GLES30.GL_VERTEX_SHADER;
import static android.opengl.GLES30.glAttachShader;
import static android.opengl.GLES30.glCompileShader;
import static android.opengl.GLES30.glCreateProgram;
import static android.opengl.GLES30.glCreateShader;
import static android.opengl.GLES30.glDeleteProgram;
import static android.opengl.GLES30.glDeleteShader;
import static android.opengl.GLES30.glFlush;
import static android.opengl.GLES30.glGetProgramInfoLog;
import static android.opengl.GLES30.glGetProgramiv;
import static android.opengl.GLES30.glGetShaderInfoLog;
import static android.opengl.GLES30.glGetShaderiv;
import static android.opengl.GLES30.glLinkProgram;
import static android.opengl.GLES30.glShaderSource;
import static android.opengl.GLES30.glUniform1ui;
import static android.opengl.GLES30.glUniform2ui;
import static android.opengl.GLES30.glUniform3ui;
import static android.opengl.GLES30.glUniform4ui;
import static android.opengl.GLES30.glViewport;
import static com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing.checkEglError;

public class GLProg implements AutoCloseable  {
    private static final String TAG = "GLProgram";
    private final List<Integer> mPrograms = new ArrayList<>();
    private final int vertexShader;
    private final GLSquareModel mSquare = new GLSquareModel();
    private int mCurrentProgramActive;
    private final Map<String, Integer> mTextureBinds = new HashMap<>();
    private int mNewTextureId;
    final String vertexShaderSource = "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec4 vPosition;\n" +
            "void main() {\n" +
            "gl_Position = vPosition;\n" +
            "}\n";
    public GLProg() {
        this.vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource);
    }
    public void useProgram(int fragmentRes) {
        int nShader = compileShader(GL_FRAGMENT_SHADER, GLInterface.loadShader(fragmentRes));
        //this.vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource);
        int program = createProgram(vertexShader,nShader);
        glLinkProgram(program);
        GLES30.glGetError();
        //checkEglError("glLinkProgram");
        glUseProgram(program);
        checkEglError("glUseProgram");
        mCurrentProgramActive = program;
        mTextureBinds.clear();
        mNewTextureId = 0;
    }
    /**
     * Helper function to compile a shader.
     *
     * @param shaderType   The shader type.
     * @param shaderSource The shader source code.
     * @return An OpenGL handle to the shader.
     */
    public int compileShader(final int shaderType, final String shaderSource) {
        int shaderHandle = glCreateShader(shaderType);
        if (shaderHandle != 0) {
            // Pass in the shader source.
            glShaderSource(shaderHandle, shaderSource);
            // Compile the shader.
            glCompileShader(shaderHandle);
            // Get the compilation status.
            final int[] compileStatus = new int[1];
            glGetShaderiv(shaderHandle, GL_COMPILE_STATUS, compileStatus, 0);
            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + glGetShaderInfoLog(shaderHandle));
                glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }
        if (shaderHandle == 0) {
            throw new RuntimeException("Error creating shader.");
        }
        return shaderHandle;
    }
    /**
     * Helper function to compile and link a program.
     *
     * @param vertexShaderHandle   An OpenGL handle to an already-compiled vertex shader.
     * @param fragmentShaderHandle An OpenGL handle to an already-compiled fragment shader.
     * @return An OpenGL handle to the program.
     */
    public int createProgram(final int vertexShaderHandle, final int fragmentShaderHandle) {
        int programHandle = glCreateProgram();
        if (programHandle != 0) {
            // Bind the vertex shader to the program.
            glAttachShader(programHandle, vertexShaderHandle);
            // Bind the fragment shader to the program.
            glAttachShader(programHandle, fragmentShaderHandle);
            // Link the two shaders together into a program.
            glLinkProgram(programHandle);
            // Get the link status.
            final int[] linkStatus = new int[1];
            glGetProgramiv(programHandle, GL_LINK_STATUS, linkStatus, 0);
            // If the link failed, delete the program.
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error compiling program: " + glGetProgramInfoLog(programHandle));
                glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }
        if (programHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }
        mPrograms.add(programHandle);
        return programHandle;
    }

    private int vPosition() {
        return glGetAttribLocation(mCurrentProgramActive, "vPosition");
    }

    public void draw() {
        mSquare.draw(vPosition());
        glFlush();
    }

    public void drawBlocks(GLTexture glTexture) {
        glTexture.BufferLoad();
        drawBlocks(glTexture.mSize.x, glTexture.mSize.y);
    }

    public void drawBlocks(int w, int h) {
        GLBlockDivider divider = new GLBlockDivider(h, GLConst.TileSize);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            glViewport(0, row[0], w, row[1]);
            draw();
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void setTexture(String var, GLTexture tex) {
        int textureId;
        if (mTextureBinds.containsKey(var)) {
            textureId = mTextureBinds.get(var);
        } else {
            textureId = mNewTextureId;
            mTextureBinds.put(var, textureId);
            mNewTextureId += 2;
        }
        setvar(var, textureId);
        tex.bind(GL_TEXTURE0 + textureId);
    }
    public void setvar(String name, int ...vars){
        int addr = glGetUniformLocation(mCurrentProgramActive,name);
        switch (vars.length) {
            case 1: glUniform1i(addr, vars[0]); break;
            case 2: glUniform2i(addr, vars[0], vars[1]); break;
            case 3: glUniform3i(addr, vars[0], vars[1], vars[2]); break;
            case 4: glUniform4i(addr, vars[0], vars[1], vars[2], vars[3]); break;
            default: throw new RuntimeException("Wrong var size " + name);
        }
    }
    public void setvar(String name, float ...vars){
        int addr = glGetUniformLocation(mCurrentProgramActive,name);
        switch (vars.length) {
            case 1: glUniform1f(addr, vars[0]); break;
            case 2: glUniform2f(addr, vars[0], vars[1]); break;
            case 3: glUniform3f(addr, vars[0], vars[1], vars[2]); break;
            case 4: glUniform4f(addr, vars[0], vars[1], vars[2], vars[3]); break;
            case 9: glUniformMatrix3fv(addr, 1, true, vars, 0); break;
            default: throw new RuntimeException("Wrong var size " + name);
        }
    }
    public void setvaru(String name, int ...vars){
        int addr = glGetUniformLocation(mCurrentProgramActive,name);
        switch (vars.length) {
            case 1: glUniform1ui(addr, vars[0]); break;
            case 2: glUniform2ui(addr, vars[0], vars[1]); break;
            case 3: glUniform3ui(addr, vars[0], vars[1], vars[2]); break;
            case 4: glUniform4ui(addr, vars[0], vars[1], vars[2], vars[3]); break;
            default: throw new RuntimeException("Wrong var size " + name);
        }
    }

    @Override
    public void close() {
        for(int prog : mPrograms){
            glDeleteProgram(prog);
        }
    }
}
