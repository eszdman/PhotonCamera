package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.opengl.GLES31.*;
import static com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing.checkEglError;

public class GLProg implements AutoCloseable {
    private static final String TAG = "GLProgram";
    private final ByteBuffer mFlushBuffer = ByteBuffer.allocateDirect(4 * 4 * 4096);
    private final List<Integer> mPrograms = new ArrayList<>();
    private final Map<String, Integer> mProgramCache = new HashMap<>();
    private final int vertexShader;
    private final GLSquareModel mSquare = new GLSquareModel();
    public int mCurrentProgramActive;
    private final Map<String, Integer> mTextureBinds = new HashMap<>();
    private Map<String, GLComputeLayout> mComputeLayouts = null;
    private int mNewTextureId;
    public boolean closed = true;
    public boolean isCompute = false;
    public int currentShader;
    public final static String glVersion = "#version 310 es\n";
    final String vertexShaderSource = glVersion +
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

    public void setDefine(String DefineName, Point in){
        setDefine(DefineName,in.x,in.y);
    }
    public void setDefine(String DefineName, boolean bool){
        if(bool)
            setDefine(DefineName,"1");
        else {
            setDefine(DefineName,"0");
        }
    }
    public void setLayout(int x, int y, int z){
        setDefine("LAYOUT","layout(local_size_x = "+x+", local_size_y = "+y+", local_size_z = "+z+") in;");
    }
    public void setDefine(String DefineName, float... vars){
        setDefine(DefineName,true,vars);
    }
    public void setDefine(String DefineName,boolean transposed, float... vars){
        setDefine(DefineName,Arrays.toString(vars).replace("]","").replace("[",""));
    }
    public void setDefine(String DefineName, int... vars){
        setDefine(DefineName,Arrays.toString(vars).replace("]","").replace("[",""));
    }
    public void setDefine(String DefineName, String DefineVal){
        Defines.add(new String[]{DefineName,DefineVal});
        changedDef = true;
    }
    public void useAssetProgram(String name){
        useAssetProgram(name,false);
    }
    public void useAssetProgram(String name,boolean compute){
        useProgram(PhotonCamera.getAssetLoader().getString("shaders/"+name+".glsl"),compute);
    }
    public void useUtilProgram(String name){
        useUtilProgram(name,false);
    }
    public void useUtilProgram(String name,boolean compute){
        useProgram(PhotonCamera.getAssetLoader().getString("shaders/utils/"+name+".glsl"),compute);
    }
    public void useProgram(int fragmentRes){
        useProgram(fragmentRes,false);
    }
    public void useProgram(int fragmentRes, boolean compute) {
        isCompute = compute;
        closed = false;
        String shader;
        if(changedDef) {
            shader = GLInterface.loadShader(fragmentRes,Defines);
        }
        else {
            shader = GLInterface.loadShader(fragmentRes);
        }
        useShader(shader,compute);
    }
    public void useProgram(String programSource) {
        useProgram(programSource,false);
    }
    public void useProgram(String programSource, boolean compute) {
        isCompute = compute;
        closed = false;
        String shader;
        if(changedDef) shader = GLInterface.loadShader(programSource,Defines);
        else {
            shader = GLInterface.loadShader(programSource);
        }
        useShader(shader,compute);
    }
    private void useShader(String shader, boolean compute){
        mComputeLayouts = GLInterface.getLayouts(shader);
        if(mProgramCache.containsKey(shader)) {
            Defines.clear();
            changedDef = false;
            Integer prog = mProgramCache.get(shader);
            if(prog == null) return;
            glUseProgram(prog);
            checkEglError("glUseProgram");
            mCurrentProgramActive = prog;
        } else {
            int program;
            int nShader;
            if(!compute) {
                nShader = compileShader(GL_FRAGMENT_SHADER, shader);
                program = createProgram(vertexShader, nShader);
            } else {
                nShader = compileShader(GL_COMPUTE_SHADER, shader);
                program = glCreateProgram();
                glAttachShader(program,nShader);
                glLinkProgram(program);
            }
            currentShader = nShader;
            glGetError();
            glUseProgram(program);
            checkEglError("glUseProgram");
            Defines.clear();
            changedDef = false;
            mCurrentProgramActive = program;
            mProgramCache.put(shader,program);
        }
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
            glGetShaderiv(shaderHandle, GL_COMPILE_STATUS, compileStatus,0);
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
            glGetProgramiv(programHandle, GL_LINK_STATUS, linkStatus,0);
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

    public void setLayout(Point layoutSize, int z){

    }
    public void computeAuto(Point size, int z) {
        if(!isCompute) {
            new Exception("Program must be compute!").printStackTrace();
            return;
        }
        GLComputeLayout glComputeLayout = mComputeLayouts.get("in");
        if(glComputeLayout == null){
            new Exception("glComputeLayout is null").printStackTrace();
            return;
        }
        glDispatchCompute(size.x/glComputeLayout.xy.x + (size.x%glComputeLayout.xy.x),
                size.y/glComputeLayout.xy.y + (size.y%glComputeLayout.xy.y),
                z/glComputeLayout.z + (z%glComputeLayout.z));
        glMemoryBarrier(GL_TEXTURE_UPDATE_BARRIER_BIT);
        glMemoryBarrier(GL_ALL_SHADER_BITS);
    }
    public void computeManual(int x,int y, int z) {
        if(!isCompute) {
            new Exception("Program must be compute!").printStackTrace();
            return;
        }
        glDispatchCompute(x, y, z);
        glMemoryBarrier(GL_TEXTURE_UPDATE_BARRIER_BIT);
        glMemoryBarrier(GL_ALL_SHADER_BITS);
    }


    public void draw() {
        mSquare.draw(vPosition());
        glFlush();
    }

    public void drawBlocks(GLTexture glTexture,Point drawsize) {
        glTexture.BufferLoad();
        drawBlocks(drawsize.x, drawsize.y);
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


    public void setTexture(String var,GLTexture tex) {
        if(tex == null) {
            new Exception("Wrong Texture:" + var).printStackTrace();
            return;
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
    public void setBufferCompute(String var,GLBuffer buff){
        setBufferCompute(var,buff,GL_SHADER_STORAGE_BUFFER);
    }
    public void setBufferCompute(String var,GLBuffer buff,int type){
        GLComputeLayout computeLayout = mComputeLayouts.get(var);
        if(computeLayout == null){
            new Exception("Wrong computeLayout:"+var).printStackTrace();
            return;
        }
        buff.BindBase(computeLayout.binding,type);
    }
    public void setTextureCompute(String var,GLTexture tex,boolean write){
        int access = GL_READ_ONLY;
        if(write) access = GL_WRITE_ONLY;
        setTextureCompute(var,tex,access);
    }
    public void setTextureCompute(String var,GLTexture tex,int access){
        if(mComputeLayouts == null){
            new Exception("Wrong mComputeLayouts:"+var).printStackTrace();
            return;
        }
        GLComputeLayout computeLayout = mComputeLayouts.get(var);
        if(computeLayout == null){
            new Exception("Wrong computeLayout:"+var).printStackTrace();
            return;
        }
        glBindImageTexture(computeLayout.binding,
                tex.mTextureID, 0, false, 0, access, tex.mFormat.getGLFormatInternal());
        checkEglError("glBindImageTexture tex.mTextureID:"+tex.mTextureID);
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

    public void setVar(String name, boolean transpose, float... vars) {
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
                glUniformMatrix3fv(address,1, transpose, vars,0);
                break;
            default:
                throw new RuntimeException("Wrong var size " + name);
        }
    }
    public void setVar(String name, float... vars) {
        setVar(name,true,vars);
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
        //for (int program : mPrograms) {
        //    glDeleteProgram(program);
        //}
    }
}
