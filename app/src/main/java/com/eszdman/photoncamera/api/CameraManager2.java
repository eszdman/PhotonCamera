package com.eszdman.photoncamera.api;

import android.hardware.camera2.CameraManager;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.ImageView;

import org.chickenhook.restrictionbypass.RestrictionBypass;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

public class CameraManager2 {
    private static String TAG = "CameraManager2";
    Object manager = null;
    private ArrayMap<String, Integer> mDeviceStatus = new ArrayMap<>();
    private Object mLock = new Object();
    boolean exposeAux = true;
    private void connectCameraServiceLocked(){
        Log.d(TAG,"Trying to connect Service");
        try {
            Method connect = RestrictionBypass.getDeclaredMethod(manager.getClass(),"connectCameraServiceLocked");
            connect.setAccessible(true);
            connect.invoke(manager);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    public CameraManager2(CameraManager manag) {
        //manager = manag;
        Log.d(TAG,"Trying to get status");
        try {
            Class clazz = Class.forName("android.hardware.camera2.CameraManager$CameraManagerGlobal");
            Log.d(TAG,"Find class:"+clazz);
            Method getmanag = RestrictionBypass.getDeclaredMethod(clazz,"get");
            getmanag.setAccessible(true);
            manager = getmanag.invoke(null);
            Field stat = RestrictionBypass.getDeclaredField(manager.getClass(),"mDeviceStatus");
            stat.setAccessible(true);
            mDeviceStatus = (ArrayMap<String, Integer>) stat.get(manager);
            Field lock = RestrictionBypass.getDeclaredField(manager.getClass(), "mLock");
            lock.setAccessible(true);
            mLock = lock.get(manager);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    public String[] getCameraIdList() {
        String[] strArr;
        synchronized (mLock) {
            connectCameraServiceLocked();
            boolean isExposeAuxCamera = exposeAux;
            int i = 0;
            int i2 = 0;
            int i3 = 0;
            while (true) {
                if (i3 < this.mDeviceStatus.size()) {
                    if (!isExposeAuxCamera && i3 == 2) {
                        break;
                    }
                    int intValue = this.mDeviceStatus.valueAt(i3).intValue();
                    if (intValue != 0) {
                        if (intValue != 2) {
                            i++;
                        }
                    }
                    i3++;
                } else {
                    break;
                }
            }
            strArr = new String[i];
            Log.d(TAG,"Counter:"+i);
            int i4 = 0;
            while (true) {
                if (i2 < this.mDeviceStatus.size()) {
                    if (!isExposeAuxCamera && i2 == 2) {
                        break;
                    }
                    int intValue2 = mDeviceStatus.valueAt(i2).intValue();
                    if (intValue2 != 0) {
                        if (intValue2 != 2) {
                            strArr[i4] = this.mDeviceStatus.keyAt(i2);
                            Log.d(TAG,"Added:"+strArr[i4]);
                            i4++;
                        }
                    }
                    i2++;
                } else {
                    break;
                }
            }
        }
        Arrays.sort(strArr, new Comparator<String>() {
            public int compare(String str, String str2) {
                int i;
                int i2;
                try {
                    i = Integer.parseInt(str);
                } catch (NumberFormatException e) {
                    i = -1;
                }
                try {
                    i2 = Integer.parseInt(str2);
                } catch (NumberFormatException e2) {
                    i2 = -1;
                }
                if (i >= 0 && i2 >= 0) {
                    return i - i2;
                }
                if (i >= 0) {
                    return -1;
                }
                if (i2 >= 0) {
                    return 1;
                }
                return str.compareTo(str2);
            }
        });
        return strArr;
    }
}
