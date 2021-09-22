package com.eszdman.photonbypass;

import java.lang.reflect.Field;

public class ReflectBypass {
    static {
        System.loadLibrary("bypass");
    }
    public static native Field getDeclaredField(Class obj, String name, String sig);
    public static native Class<?> findClass(String name);
    public static String getSignature(Class<?> sig){
        return "L"+sig.getName()+";";
    }
}
