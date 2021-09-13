package com.particlesdevs.photoncamera.util.log;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Map;

/**
 * Customised logger utility class
 * <p>
 * Created by Vibhor on 08/Dec/2020
 */
public class Logger {
    /**
     * Prints debug log in the logcat with the name of the method which called the enclosing method
     * where this method was called
     *
     * @param tag     tag msg
     * @param message message
     */
    public static void callerLog(String tag, String message) {
        Log.d("CallerLog", tag + ": " + message + " <called by> " + getCallerClassMethodName());
    }

    /**
     * Displays a warning log in logcat along with a message, exception string
     * and link of the method where exception occurred
     *
     * @param tag     log tag
     * @param message log message
     * @param e       exception
     */
    public static void warnShort(String tag, String message, @NonNull Exception e) {
        Log.w(tag, message + "\nError: " + e.toString() + "\n\tat " + getCallerClassMethodName());
    }

    /**
     * Fetches stacktrace and formats the name and location of the method which called the enclosing method
     * where this method was called
     *
     * @return formatted String
     */
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
