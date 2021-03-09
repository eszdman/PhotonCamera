package com.particlesdevs.photoncamera.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class HttpLoader {
    public static BufferedReader readURL(String addr) throws IOException {
        URL supportedList = new URL(addr);
        HttpURLConnection conn = (HttpURLConnection) supportedList.openConnection();
        conn.setConnectTimeout(200); // timing out in a 200 ms
        return new BufferedReader(new InputStreamReader(conn.getInputStream()));
    }
}
