package com.radiusnetworks.proximity.androidproximityreference;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.IBeaconData;
import com.radiusnetworks.ibeacon.IBeaconDataNotifier;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;
import com.radiusnetworks.ibeacon.client.DataProviderException;
import com.radiusnetworks.proximity.ibeacon.IBeaconManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements IBeaconConsumer, RangeNotifier, IBeaconDataNotifier {
    public static final String TAG = "MainActivity";

    // Loads swipe detector, separate class so loading it can be avoiding on
    // non-Glass devices
    // so that they don't crash
    private static class GlassSetup {
        private static Detector setup(final DetectorListener listener, Context context) {
            return new SwipeDetector(listener, context);
        }
    }

    IBeaconManager iBeaconManager;
    Map<String, TableRow> rowMap = new HashMap<String, TableRow>();

    private View container;
    private EditText username;
    private TextView currentLocation;
    private TextView previousLocations;

    private String previousLocationsString = "";

    private HackathonBeacon currentBeacon;

    private Double previousDistance;

    private Handler handler = new Handler();

    private ProgressDialog progressDialog;

    private int updateCount = 1;

    private ScreenWaker screenWaker;

    private Detector swipes;

    private DetectorListener detectorListener = new DetectorListener() {
        @Override
        public void onSwipeDownOrBack() {
            Log.d(TAG, "onSwipeDownOrBack");
            finish();
        }

        @Override
        public void onSwipeForwardOrVolumeUp() {
            Log.d(TAG, "onSwipeForwardOrVolumeUp");
        }

        @Override
        public void onSwipeBackOrVolumeDown() {
            Log.d(TAG, "onSwipeBackOrVolumeDown");
        }

        @Override
        public void onTap() {
            Log.d(TAG, "onTap");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate hasFocus = " + hasWindowFocus());


        screenWaker = new ScreenWaker(this);
        IBeaconManager.LOG_DEBUG = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        username = (EditText) findViewById(R.id.username);
        currentLocation = (TextView) findViewById(R.id.currentLocation);
        previousLocations = (TextView) findViewById(R.id.previousLocations);
        container = findViewById(R.id.container);


        iBeaconManager = IBeaconManager.getInstanceForApplication(this.getApplicationContext());
        iBeaconManager.bind(this);

        if (Build.MODEL.toUpperCase().contains("GLASS")) {
            Log.d(TAG, "Glass detected, tracking side touch pad events...");
            swipes = GlassSetup.setup(detectorListener, this);
        } else {
            Log.d(TAG, "Not Glass: " + Build.MODEL);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        Log.d(TAG, "onGenericMotionEvent ev = " + event);
        if (null != swipes) {
            swipes.onGenericMotionEvent(event);
            return false;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void onIBeaconServiceConnect() {
        Region region = new Region("MainActivityRanging", null, null, null);
        try {
            iBeaconManager.startRangingBeaconsInRegion(region);
            iBeaconManager.setRangeNotifier(this);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart hasFocus = " + hasWindowFocus());

        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume hasFocus = " + hasWindowFocus());

        super.onResume();
        screenWaker.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause hasFocus = " + hasWindowFocus());

        super.onPause();
        screenWaker.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop hasFocus = " + hasWindowFocus());

        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "onWindowFocusChanged hasFocus = " + hasFocus);

        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy hasFocus = " + hasWindowFocus());

        super.onDestroy();
        iBeaconManager.unBind(this);
        swipes = null;
    }

    //final Gson gson = new Gson();

    @Override
    public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {

        //final String json = gson.toJson(iBeacons);
        //Log.d(TAG, "didRangeBeaconsInRegion json = " + json);

        Log.d(TAG, "didRangeBeaconsInRegion");

        HackathonBeacon closestBeacon  = null;
        IBeacon closestIBeacon = null;
        final List<DetectedBeacon> detectedBeaconList = new LinkedList<DetectedBeacon>();
        for (IBeacon iBeacon : iBeacons) {
            iBeacon.requestData(this);



            Log.d(TAG, "I see an iBeacon: " + iBeacon.getProximityUuid() + "," + iBeacon.getMajor() + "," + iBeacon.getMinor());

            final HackathonBeacon foundHackathonBeacon = HackathonBeacon.findMatching(iBeacon);
            if ( null != foundHackathonBeacon ) {
                detectedBeaconList.add(new DetectedBeacon(iBeacon));

                if ( null == closestBeacon || iBeacon.getAccuracy() < closestIBeacon.getAccuracy() ) {
                    closestBeacon = foundHackathonBeacon;
                    closestIBeacon = iBeacon;
                }
            }

            String displayString = iBeacon.getProximityUuid() + " " + iBeacon.getMajor() + " " + iBeacon.getMinor()
                    + (null == foundHackathonBeacon ? "" : "\n Hackathon beacon: " + foundHackathonBeacon.name());
            displayTableRow(iBeacon, displayString, false);
        }

        if (null != closestBeacon && null != closestIBeacon) {
            updateFields(closestBeacon, closestIBeacon);
        }

        if (!detectedBeaconList.isEmpty()) {
            Log.d(TAG, "updating server...");
            Toast.makeText(MainActivity.this,
                    "Updating server " + (updateCount++), Toast.LENGTH_LONG).show();
            ServerRemoteClient.updateServer(username.getText().toString(),
                    detectedBeaconList,
                    MainActivity.this);
        } else {
            Log.d(TAG, "nothing found. not updating server...");
        }
    }

    public static String getProximityString(final int value) {
        if (value == IBeacon.PROXIMITY_FAR ) {
            return "FAR";
        } else if (value == IBeacon.PROXIMITY_IMMEDIATE ) {
            return "IMMEDIATE ";
        } else if (value == IBeacon.PROXIMITY_NEAR ) {
            return "NEAR";
        }
        return "UNKNOWN";
    }

    public void updateFields(
            final HackathonBeacon foundHackathonBeacon,
            final IBeacon beacon) {
        Log.d(TAG, "updateServerAndFields");
        if ( null == foundHackathonBeacon || null == beacon ) {
            return;
        }

        final boolean sameHackathonBeacon = null != currentBeacon
                && currentBeacon == foundHackathonBeacon;
        final boolean sameDistance = null != previousDistance
                && previousDistance.equals(beacon.getAccuracy());

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!sameHackathonBeacon || !sameDistance) {

                    if (HackathonBeacon.CHECK_IN == foundHackathonBeacon) {
                        container.setBackgroundResource(R.drawable.bg_checkin2);

                    } else if (HackathonBeacon.PARKING == foundHackathonBeacon) {
                        container.setBackgroundResource(R.drawable.bg_parking);

                    } else if (HackathonBeacon.GATE_A22 == foundHackathonBeacon) {
                        container.setBackgroundResource(R.drawable.bg_gate);

                    } else if (HackathonBeacon.SECURITY == foundHackathonBeacon) {
                        container.setBackgroundResource(R.drawable.bg_security);

                    } else if (HackathonBeacon.CLUB == foundHackathonBeacon) {
                        container.setBackgroundResource(R.drawable.bg_club);
                    }




                    if (!sameHackathonBeacon) {
                        previousLocations.setText(previousLocations.getText() + " " + foundHackathonBeacon);
                        previousLocationsString = previousLocationsString + " " + foundHackathonBeacon;
                    }
                    currentBeacon = foundHackathonBeacon;

                    currentLocation.setText(
                            "Current location: " + foundHackathonBeacon
                            + "\nDistance: " + beacon.getAccuracy()
                                    + "\nProximity: " + getProximityString(beacon.getProximity()));
                    previousDistance = beacon.getAccuracy();
                }
            }
        });
    }

    @Override
    public void iBeaconDataUpdate(IBeacon iBeacon, IBeaconData iBeaconData, DataProviderException e) {
        if (e != null) {
            Log.d(TAG, "data fetch error:" + e);
        }
        if (iBeaconData != null) {

            Log.d(TAG, "I have an iBeacon with data: uuid=" + iBeacon.getProximityUuid() + " major=" + iBeacon.getMajor() + " minor=" + iBeacon.getMinor() + " welcomeMessage=" + iBeaconData.get("welcomeMessage"));

            final HackathonBeacon foundHackathonBeacon = HackathonBeacon.findMatching(iBeacon);
            String displayString = iBeacon.getProximityUuid() + " " + iBeacon.getMajor() + " " + iBeacon.getMinor()
                    + (null == foundHackathonBeacon ? "" : "\n Hackathon beacon: " + foundHackathonBeacon.name())
                    + "\n" + "Welcome message:" + iBeaconData.get("welcomeMessage");

            //updateServerAndFields(foundHackathonBeacon, iBeacon);

            displayTableRow(iBeacon, displayString, true);
        }
    }

    private void displayTableRow(final IBeacon iBeacon, final String displayString, final boolean updateIfExists) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TableLayout table = (TableLayout) findViewById(R.id.beacon_table);
                String key = iBeacon.getProximity() + "-" + iBeacon.getMajor() + "-" + iBeacon.getMinor();
                TableRow tr = (TableRow) rowMap.get(key);
                if (tr == null) {
                    tr = new TableRow(MainActivity.this);
                    tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
                    rowMap.put(key, tr);
                    table.addView(tr);
                } else {
                    if (updateIfExists == false) {
                        return;
                    }
                }
                tr.removeAllViews();
                TextView textView = new TextView(MainActivity.this);
                textView.setText(displayString);
                tr.addView(textView);

            }
        });

    }

    public void onServerUpdated() {
        Log.d(TAG, "onServerUpdated");
    }

    public void onServerUpdateError() {
        Log.d(TAG, "onServerUpdateError");

    }

}
