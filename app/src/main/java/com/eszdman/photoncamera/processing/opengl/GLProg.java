package com.eszdman.photoncamera.processing.opengl;

import android.graphics.Point;
import android.opengl.GLES30;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glReadPixels;
import static android.opengl.GLES30.GL_COMPILE_STATUS;
import static android.opengl.GLES30.GL_FRAGMENT_SHADER;
import static android.opengl.GLES30.GL_LINK_STATUS;
import static android.opengl.GLES30.GL_RGBA_INTEGER;
import static android.opengl.GLES30.GL_TEXTURE0;
import static android.opengl.GLES30.GL_VERTEX_SHADER;
import static android.opengl.GLES30.glAttachShader;
import static android.opengl.GLES30.glCompileShader;
import static android.opengl.GLES30.glCreateProgram;
import static android.opengl.GLES30.glCreateShader;
import static android.opengl.GLES30.glDeleteProgram;
import static android.opengl.GLES30.glDeleteShader;
import static android.opengl.GLES30.glFlush;
import static android.opengl.GLES30.glGetAttribLocation;
import static android.opengl.GLES30.glGetProgramInfoLog;
import static android.opengl.GLES30.glGetProgramiv;
import static android.opengl.GLES30.glGetShaderInfoLog;
import static android.opengl.GLES30.glGetShaderiv;
import static android.opengl.GLES30.glGetUniformLocation;
import static android.opengl.GLES30.glLinkProgram;
import static android.opengl.GLES30.glShaderSource;
import static android.opengl.GLES30.glUniform1f;
import static android.opengl.GLES30.glUniform1i;
import static android.opengl.GLES30.glUniform1ui;
import static android.opengl.GLES30.glUniform2f;
import static android.opengl.GLES30.glUniform2i;
import static android.opengl.GLES30.glUniform2ui;
import static android.opengl.GLES30.glUniform3f;
import static android.opengl.GLES30.glUniform3i;
import static android.opengl.GLES30.glUniform3ui;
import static android.opengl.GLES30.glUniform4f;
import static android.opengl.GLES30.glUniform4i;
import static android.opengl.GLES30.glUniform4ui;
import static android.opengl.GLES30.glUniformMatrix3fv;
import static android.opengl.GLES30.glUseProgram;
import static android.opengl.GLES30.glViewport;
import static com.eszdman.photoncamera.processing.opengl.GLCoreBlockProcessing.checkEglError;

public class GLProg implements AutoCloseable {
    private static final String TAG = "GLProgram";
    private final ByteBuffer mFlushBuffer = ByteBuffer.allocateDirect(4 * 4 * 4096);
    private final List<Integer> mPrograms = new ArrayList<>();
    private final Map<String, Integer> mProgramCache = new HashMap<>();
    private final int vertexShader;
    private final GLSquareModel mSquare = new GLSquareModel();
    private int mCurrentProgramActive;
    private final Map<String, Integer> mTextureBinds = new HashMap<>();
    private int mNewTextureId;
    public boolean closed = true;
    public int currentShader;
    final String vertexShaderSource = "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec4 vPosition;\n" +
            "void main() {\n" +
            "gl_Position = vPosition;\n" +
            "}\n";

    public GLProg() {
        this.vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource);
        mFlushBuffer.mark();
    }
    boolean changedDef = false;
    ArrayList<String[]> Defines = new ArrayList<>();
    public void setDefine(String DefineName, String DefineVal){
        Defines.add(new String[]{DefineName,DefineVal});
        changedDef = true;
    }
    public void useProgram(int fragmentRes) {
        closed = false;
        String shader = "";
        if(changedDef) {
            shader = GLInterface.loadShader(fragmentRes,Defines);
        }
        else {
            shader = GLInterface.loadShader(fragmentRes);
        }
        if(mProgramCache.containsKey(shader)) {
            Defines.clear();
            changedDef = false;
            Integer prog = mProgramCache.get(shader);
            if(prog == null) return;
            glUseProgram(prog);
            checkEglError("glUseProgram");
            mCurrentProgramActive = prog;
            mTextureBinds.clear();
            mNewTextureId = 0;
        } else {
            int nShader = compileShader(GL_FRAGMENT_SHADER, shader);
            Defines.clear();
            changedDef = false;
            currentShader = nShader;
            int program = createProgram(vertexShader, nShader);
            glLinkProgram(program);
            GLES30.glGetError();
            //checkEglError("glLinkProgram");
            glUseProgram(program);
            checkEglError("glUseProgram");
            mCurrentProgramActive = program;
            mTextureBinds.clear();
            mNewTextureId = 0;
            mProgramCache.put(shader,program);
        }
    }

    public void useProgram(String prog) {
        closed = false;
        String shader = "";
        if(changedDef) shader = GLInterface.loadShader(prog,Defines);
        else {
            shader = GLInterface.loadShader(prog);
        }
        int nShader = compileShader(GL_FRAGMENT_SHADER, shader);
        Defines.clear();
        changedDef = false;
        //this.vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource);
        int program = createProgram(vertexShader, nShader);
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
        GLBlockDivider divider = new GLBlockDivider(h, GLDrawParams.TileSize);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            glViewport(0, row[0], w, row[1]);
            draw();
        }
    }

    public void drawBlocks(GLTexture texture, int bh) {
        drawBlocks(texture, bh, false);
    }

    public void drawBlocks(GLTexture texture, boolean forceFlush) {
        if(!forceFlush) {
            drawBlocks(texture);
            return;
        }
        texture.BufferLoad();
        drawBlocks(texture.mSize.x, texture.mSize.y, GLDrawParams.TileSize, -1, texture.mFormat.getGLType());
    }
    public void drawBlocks(GLTexture texture, int bh, boolean forceFlush) {
        texture.BufferLoad();
        drawBlocks(texture.mSize.x, texture.mSize.y, bh, -1, forceFlush ? texture.mFormat.mFormat.mID : -1);
    }

    public void drawBlocks(int w, int h, int bh, int flushFormat, int flushType) {
        mFlushBuffer.reset();
        if (flushFormat == -1) {
            flushFormat = flushType == GL_FLOAT ? GL_RGBA : GL_RGBA_INTEGER;
        }

        GLBlockDivider divider = new GLBlockDivider(h, bh);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            glViewport(0, row[0], w, row[1]);
            mSquare.draw(vPosition());

            // Force flush.
            glFlush();
            if (flushType != -1) {
                glReadPixels(0, row[0], 1, 1, flushFormat, flushType, mFlushBuffer);
                int glError = glGetError();
                if (glError != 0) {
                    Log.d("GLPrograms", "GLError: " + glError);
                }
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void setTexture(String var, GLTexture tex) {
        if(tex == null) try {
            throw new Exception("Wrong Texture:"+var);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int textureId;
        if (mTextureBinds.containsKey(var)) {
            textureId = mTextureBinds.get(var);
        } else {
            textureId = mNewTextureId;
            mTextureBinds.put(var, textureId);
            mNewTextureId += 2;
        }
        setVar(var, textureId);
        tex.bind(GL_TEXTURE0 + textureId);
    }

    public void setVar(String name, int... vars) {
        int addr = glGetUniformLocation(mCurrentProgramActive, name);
        switch (vars.length) {
            case 1:
                glUniform1i(addr, vars[0]);
                break;
            case 2:
                glUniform2i(addr, vars[0], vars[1]);
                break;
            case 3:
                glUniform3i(addr, vars[0], vars[1], vars[2]);
                break;
            case 4:
                glUniform4i(addr, vars[0], vars[1], vars[2], vars[3]);
                break;
            default:
                throw new RuntimeException("Wrong var size " + name);
        }
    }

    public void setVar(String name, Point in) {
        setVar(name, in.x, in.y);
    }

    public void setVar(String name, float... vars) {
        int address = glGetUniformLocation(mCurrentProgramActive, name);
        switch (vars.length) {
            case 1:
                glUniform1f(address, vars[0]);
                break;
            case 2:
                glUniform2f(address, vars[0], vars[1]);
                break;
            case 3:
                glUniform3f(address, vars[0], vars[1], vars[2]);
                break;
            case 4:
                glUniform4f(address, vars[0], vars[1], vars[2], vars[3]);
                break;
            case 9:
                glUniformMatrix3fv(address, 1, true, vars, 0);
                break;
            default:
                throw new RuntimeException("Wrong var size " + name);
        }
    }

    public void setVarU(String name, Point var) {
        setVarU(name,var.x,var.y);
    }

    public void setVarU(String name, int... vars) {
        int address = glGetUniformLocation(mCurrentProgramActive, name);
        switch (vars.length) {
            case 1:
                glUniform1ui(address, vars[0]);
                break;
            case 2:
                glUniform2ui(address, vars[0], vars[1]);
                break;
            case 3:
                glUniform3ui(address, vars[0], vars[1], vars[2]);
                break;
            case 4:
                glUniform4ui(address, vars[0], vars[1], vars[2], vars[3]);
                break;
            default:
                throw new RuntimeException("Wrong var size " + name);
        }
    }

    @Override
    public void close() {
        closed = true;
        for (int program : mPrograms) {
            glDeleteProgram(program);
        }
    }
}
