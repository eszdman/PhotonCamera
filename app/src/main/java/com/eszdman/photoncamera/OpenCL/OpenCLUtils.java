package com.eszdman.photoncamera.OpenCL;
import android.util.Log;
import android.util.Size;

import org.jocl.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import static org.jocl.CL.*;
import static org.jocl.Sizeof.*;

public class OpenCLUtils {
    String TAG = "OpenCLUtils";
    cl_mem memVar[] = null;
    public OpenCLUtils(int InNumber){
        memVar = new cl_mem[InNumber];
    }
    cl_context context;
    cl_command_queue commandQueue;
    cl_kernel kernel;
    cl_program program;
    long time;
    String name2;
    String programSource;
    int cnt = 0;
    public void CreateVar(Object[] in, Pointer in2, boolean rw){
        int size = 0;
        Class inc = in[0].getClass();
        if(inc == Short.class){
            size = Sizeof.cl_short;
        }
        if(inc == Double.class){
            size = cl_double;
        }
        long flags = 0;
        flags = CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR;
        if(rw) {flags = CL_MEM_READ_WRITE; in2 = null;}
        memVar[cnt] = clCreateBuffer(context, flags, size*in.length,in2,null);
        cnt++;
    }
    List<Object> gworksize = new ArrayList();
    ArrayList<Object> lworksize = new ArrayList();
    public void addGWork(long size){
        gworksize.add(size);
    }
    public void addLWork(long size){
        lworksize.add(size);
    }
    public void EnqueueKer(){
        long[] gwork = new long[gworksize.size()];
        long[] lwork = new long[lworksize.size()];
        for(int i =0; i<gworksize.size();i++){gwork[i] = (long)gworksize.get(i);}
        for(int i =0; i<lworksize.size();i++){lwork[i] = (long)lworksize.get(i);}
        clEnqueueNDRangeKernel(commandQueue,kernel,gworksize.size(),null,
                gwork,lwork,0,null,null);
    }
    public void ReadProg(String dir, String name){
        try {
            name2 = name;
            programSource = new Scanner(new File(dir,  name+".eszdman")).useDelimiter("\\Z").next();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
    public void InitCl(String name) {
        name2 = name;
        InitCl();
    }
    public void InitCl() {
        time = System.currentTimeMillis();
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ACCELERATOR;
        final int deviceIndex = 0;
        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);
        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];
        // Obtain a platform ID
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];
        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
        // Obtain the number of devices for the platform
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];
        // Obtain a device ID
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];
        // Create a context for the selected device
        context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device},
                null, null, null);
        // Create a command-queue for the selected device
        commandQueue =
                clCreateCommandQueue(context, device, 0, null);
        // Create the program from the source code
        program = clCreateProgramWithSource(context,
                1, new String[]{ programSource }, null, null);
        // Build the program
        clBuildProgram(program, 0, null, null, null, null);

        // Create the kernel
        kernel = clCreateKernel(program, "PCamKernel", null);
    }
    public void setArg(int num) {
        for(int i = 0; i<num; i++){
            clSetKernelArg(kernel, i,
                    Sizeof.cl_mem, Pointer.to(memVar[i]));
        }
    }
    public void ReleaseAll(int num){
        for(int i = 0; i<num; i++){
            clReleaseMemObject(memVar[i]);
        }
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
        Log.d(TAG,("Time elapsed: " + (System.currentTimeMillis()-time)+ "ms " + name2));
    }
}