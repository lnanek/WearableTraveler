package com.radiusnetworks.proximity.androidproximityreference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;

public class ServerRemoteClient {

    private static final String TAG = ServerRemoteClient.class.getSimpleName();

    private static final String URL = "http://gpop-server.com/american-airlines/post.php";

    private static final Gson gson = new Gson();

    public static final Handler uiHandler = new Handler();

    public static interface ServerRemoteClientListener {
        public void onServerUpdated();
        public void onServerUpdateError();
    }

    public static void updateServer(
            final String username,
            final String email,
            final List<DetectedBeacon> detectedBeaconList,
            final ServerRemoteClientListener activity) {
        Log.d(TAG, "updateServer");

        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                updateServerOnSecondThread(username, email, detectedBeaconList, activity);
            }
        });
        thread.start();

    }

    private static void updateServerOnSecondThread(
            final String username,
            final String email,
            final List<DetectedBeacon> detectedBeaconList,
            final ServerRemoteClientListener activity) {
        Log.d(TAG, "updateServerOnSecondThread");

        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httpMethod = new HttpPost(URL
                + "?username=" + encodeValue(username)
                + "&email=" + encodeValue(email)
        );
        HttpResponse response;
        try {

            final String json = gson.toJson(detectedBeaconList);
            final StringEntity jsonEntity = new StringEntity(json);
            httpMethod.setEntity(jsonEntity);
            httpMethod.setHeader("Content-Type", "application/json");

            Log.d(TAG, "json = " + json);


            response = httpclient.execute(httpMethod);
            Log.d(TAG, "response status: " + response.getStatusLine().toString());
            HttpEntity entity = response.getEntity();

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    activity.onServerUpdated();
                }
            });


        } catch (Exception e) {
            Log.e(TAG, "Error downloading items", e);

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    activity.onServerUpdateError();
                }
            });
        }
    }

    public static String encodeValue(final Double unescaped) {
        if (null == unescaped) {
            return "";
        }
        return encodeValue(Double.toString(unescaped));
    }

    public static String encodeValue(final String unescaped) {
        if (null == unescaped) {
            return "";
        }
        // return URIUtil.encodeWithinQuery(unescaped, "UTF-8");
        try {
            return URLEncoder.encode(unescaped, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Couldn't encode query value", e);
        }
    }

    private static String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

}
