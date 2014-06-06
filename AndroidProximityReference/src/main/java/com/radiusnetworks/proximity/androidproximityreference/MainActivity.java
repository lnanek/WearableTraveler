package com.radiusnetworks.proximity.androidproximityreference;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Map;

public class MainActivity extends Activity implements IBeaconConsumer, RangeNotifier, IBeaconDataNotifier {
    public static final String TAG = "MainActivity";

    IBeaconManager iBeaconManager;
    Map<String, TableRow> rowMap = new HashMap<String, TableRow>();

    private EditText username;
    private TextView currentLocation;
    private TextView previousLocations;

    private String previousLocationsString = "";

    private HackathonBeacon currentBeacon;

    private Handler handler = new Handler();

    private ProgressDialog progressDialog;

    private int updateCount = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        IBeaconManager.LOG_DEBUG = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        username = (EditText) findViewById(R.id.username);
        currentLocation = (TextView) findViewById(R.id.currentLocation);
        previousLocations = (TextView) findViewById(R.id.previousLocations);

        iBeaconManager = IBeaconManager.getInstanceForApplication(this.getApplicationContext());
        iBeaconManager.bind(this);
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
    public void onDestroy() {
        super.onDestroy();
        iBeaconManager.unBind(this);
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {
        for (IBeacon iBeacon : iBeacons) {
            iBeacon.requestData(this);
            Log.d(TAG, "I see an iBeacon: " + iBeacon.getProximityUuid() + "," + iBeacon.getMajor() + "," + iBeacon.getMinor());

            final HackathonBeacon foundHackathonBeacon = HackathonBeacon.findMatching(iBeacon);
            String displayString = iBeacon.getProximityUuid() + " " + iBeacon.getMajor() + " " + iBeacon.getMinor()
                    + (null == foundHackathonBeacon ? "" : "\n Hackathon beacon: " + foundHackathonBeacon.name());

            updateServerAndFields(foundHackathonBeacon);

            displayTableRow(iBeacon, displayString, false);
        }
    }

    public void updateServerAndFields(final HackathonBeacon foundHackathonBeacon) {
        Log.d(TAG, "updateServerAndFields");

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (null != foundHackathonBeacon && foundHackathonBeacon != currentBeacon) {
                    currentBeacon = foundHackathonBeacon;
                    currentLocation.setText("Current location: " + foundHackathonBeacon);
                    previousLocations.setText(previousLocations.getText() + " " + foundHackathonBeacon);
                    previousLocationsString = previousLocationsString + " " + foundHackathonBeacon;
                    Log.d(TAG, "updating server...");
                    Toast.makeText(MainActivity.this,
                            "Updating server " + (updateCount++), Toast.LENGTH_LONG).show();

                    ServerRemoteClient.updateServer(username.getText().toString(),
                            foundHackathonBeacon.name(),
                            previousLocationsString, MainActivity.this);

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

            updateServerAndFields(foundHackathonBeacon);

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
