package com.particlesdevs.photoncamera.util.log;

import android.util.Log;

import java.util.Map;

public class Logger {
    public static void callerLog(String tag, String message) {
        Log.d("CallerLog", tag + ": " + message + " <called by> " + getCallerClassMethodName());
    }

    public static String getCallerClassMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String str = null;
        for (int i = 1; i < stackTrace.length; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            if (!stackTraceElement.getClassName().equals(Logger.class.getName()) && stackTraceElement.getClassName().indexOf("java.lang.Thread") != 0) {
                if (str == null) {
                    str = stackTraceElement.getClassName();
                } else if (!str.equals(stackTraceElement.getClassName())) {
                    return String.format("%s.%s(%s:%s)",
                            stackTraceElement.getClassName(),
                            stackTraceElement.getMethodName(),
                            stackTraceElement.getFileName(),
                            stackTraceElement.getLineNumber()
                    );
                }
            }
        }
        return null;
    }

    public static String createTextFrom(Map<String, String> stringMap) {
        StringBuilder sb = new StringBuilder();
        stringMap.forEach((key, value) -> sb.append(key).append(" : ").append(value).append("\n"));
        return sb.toString();
    }

}
