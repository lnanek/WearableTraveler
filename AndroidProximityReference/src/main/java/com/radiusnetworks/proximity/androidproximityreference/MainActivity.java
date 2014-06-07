package com.radiusnetworks.proximity.androidproximityreference;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.os.Build;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.glass.media.Sounds;

import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.IBeaconData;
import com.radiusnetworks.ibeacon.IBeaconDataNotifier;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;
import com.radiusnetworks.ibeacon.client.DataProviderException;
import com.radiusnetworks.proximity.ibeacon.IBeaconManager;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements IBeaconConsumer, RangeNotifier, IBeaconDataNotifier,
        TextToSpeech.OnInitListener, ServerRemoteClient.ServerRemoteClientListener {

    private static final boolean USE_SPEECH = true;

    public static final String TAG = "MainActivity";

    private static final int DEPARTURE_DELAY_MS = 3 * 60 * 1000;

    // Loads swipe detector, separate class so loading it can be avoiding on
    // non-Glass devices
    // so that they don't crash
    private static class GlassSetup {
        private static Detector setup(final DetectorListener listener, Context context) {
            return new SwipeDetector(listener, context);
        }
    }

    IBeaconManager iBeaconManager;
    private View container;
    private View logo;
    private EditText username;
    private TextView currentLocation;
    private TextView previousLocations;
    private View debugScreen;
    private TextView countdown;
    private View contentScreen;
    private String previousLocationsString = "";
    private HackathonBeacon currentBeacon;
    private Double previousDistance;
    private Handler handler = new Handler();
    private ProgressDialog progressDialog;
    private int updateCount = 1;
    private ScreenWaker screenWaker;
    private Detector swipes;
    private int tapsThisBeacon = 0;
    private AudioManager mAudioManager;
    private Long timeStartUptimeMillis;
    private int simulatedBeaconIndex = 0;
    private TextToSpeech tts;
    private String email;
    private boolean firstDrawComplete;
    private String nickname;
    private boolean resumed;
    private boolean resumedEver;
    private Handler timerHandler = new Handler();
    private DetectorListener detectorListener = new DetectorListener() {
        @Override
        public void onSwipeDownOrBack() {
            Log.d(TAG, "onSwipeDownOrBack");
            finish();
        }

        @Override
        public void onSwipeForwardOrVolumeUp() {
            Log.d(TAG, "onSwipeForwardOrVolumeUp");

            startSimulation();
        }

        @Override
        public void onSwipeBackOrVolumeDown() {
            Log.d(TAG, "onSwipeBackOrVolumeDown");

            final int currentDebugScreenVisibility = debugScreen.getVisibility();
            if (currentDebugScreenVisibility == View.GONE) {
                contentScreen.setVisibility(View.GONE);
                debugScreen.setVisibility(View.VISIBLE);
            } else {
                contentScreen.setVisibility(View.VISIBLE);
                debugScreen.setVisibility(View.GONE);
            }
        }

        @Override
        public void onTap() {
            Log.d(TAG, "onTap");

            mAudioManager.playSoundEffect(Sounds.SELECTED);
            tapsThisBeacon++;
            updateBackground();
        }

        @Override
        public void onTwoTap() {
            Log.d(TAG, "onTwoTap");

            //startSimulation();
        }
    };

    public void startSimulation() {

        ArrayList<IBeacon> iBeacons = new ArrayList<IBeacon>();

        final HackathonBeacon simulatedBeacon = HackathonBeacon.values()[simulatedBeaconIndex];

        final Toast toast = Toast.makeText(MainActivity.this,
                "Selecting " + simulatedBeacon.name(),
                Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();


        IBeacon iBeacon1 = new IBeacon(simulatedBeacon.mUuid,
                simulatedBeacon.mMajor, simulatedBeacon.mMinor);
        simulatedBeaconIndex++;
        if (simulatedBeaconIndex >= HackathonBeacon.values().length) {
            simulatedBeaconIndex = 0;
        }


        IBeacon iBeacon2 = new IBeacon("DF7E1C79-43E9-44FF-886F-1D1F7DA6997B".toLowerCase(),
                1, 2);
        IBeacon iBeacon3 = new IBeacon("DF7E1C79-43E9-44FF-886F-1D1F7DA6997C".toLowerCase(),
                1, 3);
        IBeacon iBeacon4 = new IBeacon("DF7E1C79-43E9-44FF-886F-1D1F7DA6997D".toLowerCase(),
                1, 4);
        iBeacons.add(iBeacon1);
        iBeacons.add(iBeacon2);
        iBeacons.add(iBeacon3);
        iBeacons.add(iBeacon4);

        didRangeBeaconsInRegion(iBeacons, null);

        //IBeaconManager.setBeaconSimulator( new YourBeaconSimulatorClass() );
        //((YourBeaconSimulatorClass) IBeaconManager.getBeaconSimulator()).createTimedSimulatedBeacons();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate hasFocus = " + hasWindowFocus() + ", firstDrawComplete = " + firstDrawComplete);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        screenWaker = new ScreenWaker(this);
        IBeaconManager.LOG_DEBUG = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tts = new TextToSpeech(this, this);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        username = (EditText) findViewById(R.id.username);
        email = DeviceEmail.get(this);
        if ("lnanek@gmail.com".equals(email)) {
            username.setText("Dave Martinez");
            nickname = "Dave";
        } else {
            nickname = "Michael";
        }

        debugScreen = findViewById(R.id.debugScreen);
        contentScreen = findViewById(R.id.contentScreen);
        countdown = (TextView) findViewById(R.id.countdown);
        currentLocation = (TextView) findViewById(R.id.currentLocation);
        previousLocations = (TextView) findViewById(R.id.previousLocations);
        container = findViewById(R.id.container);
        logo = findViewById(R.id.logo);


        logo.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                MainActivity.this.onDraw();
            }
        });


        iBeaconManager = IBeaconManager.getInstanceForApplication(this.getApplicationContext());
        iBeaconManager.bind(this);

        if (Build.MODEL.toUpperCase().contains("GLASS")) {
            Log.d(TAG, "Glass detected, tracking side touch pad events...");
            swipes = GlassSetup.setup(detectorListener, this);
        } else {
            Log.d(TAG, "Not Glass: " + Build.MODEL);
        }

        updateDebugDisplay();
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    detectorListener.onSwipeForwardOrVolumeUp();

                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    detectorListener.onSwipeBackOrVolumeDown();

                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            detectorListener.onTap();

            return true;
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onIBeaconServiceConnect() {
        Region region = new Region(
                "MainActivityRanging",
                "114A4DD8-5B2F-4800-A079-BDCB21392BE9",
                null,
                null);
        try {
            iBeaconManager.startRangingBeaconsInRegion(region);
            iBeaconManager.setRangeNotifier(this);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void startTimerIfResumedAndFocused() {
        if (!resumed || !hasWindowFocus()) {
            if (null == timeStartUptimeMillis) {
                timerHandler.removeCallbacksAndMessages(null);
                timeStartUptimeMillis = SystemClock.uptimeMillis();

                timerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || null == timeStartUptimeMillis) {
                            return;
                        }

                        final long currentTime = SystemClock.uptimeMillis();
                        final long remainingMs = DEPARTURE_DELAY_MS - (currentTime - timeStartUptimeMillis);

                        if (remainingMs <= 0) {
                            countdown.setText("Departing...");
                            return;
                        }

                        final long remainingM = remainingMs / (60 * 1000);
                        final long remainingAfterM = remainingMs - (remainingM * 60 * 1000);
                        final long remainingS = remainingAfterM / 1000;

                        final String timeoutText = remainingM + ":" +
                                (remainingS > 9 ? remainingS : ("0" + remainingS));

                        countdown.setText(timeoutText);

                        timerHandler.postDelayed(this, 500);
                    }
                });
            }
        }
    }

    protected void onDraw() {
        firstDrawComplete = true;
        Log.d(TAG, "onDraw hasFocus = " + hasWindowFocus() + ", firstDrawComplete = " + firstDrawComplete);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart hasFocus = " + hasWindowFocus() + ", firstDrawComplete = " + firstDrawComplete);

        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume hasFocus = " + hasWindowFocus() + ", firstDrawComplete = " + firstDrawComplete);

        super.onResume();
        screenWaker.onResume();

        resumed = true;
        resumedEver = true;
        startTimerIfResumedAndFocused();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause hasFocus = " + hasWindowFocus() + ", firstDrawComplete = " + firstDrawComplete);

        super.onPause();
        resumed = false;
        screenWaker.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop hasFocus = " + hasWindowFocus() + ", firstDrawComplete = " + firstDrawComplete);

        super.onStop();

        if (!isFinishing() && resumedEver) {
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "onWindowFocusChanged hasFocus = " + hasFocus);

        super.onWindowFocusChanged(hasFocus);

        startTimerIfResumedAndFocused();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy hasFocus = " + hasWindowFocus() + ", firstDrawComplete = " + firstDrawComplete);

        super.onDestroy();
        iBeaconManager.unBind(this);
        swipes = null;


        // Don't forget to shutdown tts!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    //final Gson gson = new Gson();

    @Override
    public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {

        //final String json = gson.toJson(iBeacons);
        //Log.d(TAG, "didRangeBeaconsInRegion json = " + json);

        Log.d(TAG, "didRangeBeaconsInRegion");

        HackathonBeacon closestBeacon = null;
        IBeacon closestIBeacon = null;
        final List<DetectedBeacon> detectedBeaconList = new LinkedList<DetectedBeacon>();
        for (IBeacon iBeacon : iBeacons) {
            iBeacon.requestData(this);

            Log.d(TAG, "I see an iBeacon: " + iBeacon.getProximityUuid() + "," + iBeacon.getMajor() + "," + iBeacon.getMinor());

            final HackathonBeacon foundHackathonBeacon = HackathonBeacon.findMatching(iBeacon);
            if (null != foundHackathonBeacon) {

                foundHackathonBeacon.proximity = getProximityString(iBeacon.getProximity());
                foundHackathonBeacon.distanceMeters = iBeacon.getAccuracy();
                foundHackathonBeacon.lastDetectedUptimeMillis = SystemClock.uptimeMillis();

                detectedBeaconList.add(new DetectedBeacon(iBeacon));

                if (null == closestBeacon || iBeacon.getAccuracy() < closestIBeacon.getAccuracy()) {
                    closestBeacon = foundHackathonBeacon;
                    closestIBeacon = iBeacon;
                }
            }

            String displayString = iBeacon.getProximityUuid() + " " + iBeacon.getMajor() + " " + iBeacon.getMinor()
                    + (null == foundHackathonBeacon ? "" : "\n Hackathon beacon: " + foundHackathonBeacon.name());
        }

        updateDebugDisplay();

        if (null != closestBeacon && null != closestIBeacon) {
            updateFields(closestBeacon, closestIBeacon);
        }

        if (!detectedBeaconList.isEmpty()) {
            Log.d(TAG, "updating server " + (updateCount++));
            //Toast.makeText(MainActivity.this,
            //        "Updating server " + (updateCount++), Toast.LENGTH_LONG).show();
            ServerRemoteClient.updateServer(username.getText().toString(),
                    email,
                    detectedBeaconList,
                    MainActivity.this);
        } else {
            Log.d(TAG, "nothing found. not updating server...");
        }
    }

    public static String getProximityString(final int value) {
        if (value == IBeacon.PROXIMITY_FAR) {
            return "FAR";
        } else if (value == IBeacon.PROXIMITY_IMMEDIATE) {
            return "IMMEDIATE ";
        } else if (value == IBeacon.PROXIMITY_NEAR) {
            return "NEAR";
        }
        return "UNKNOWN";
    }

    public void updateBackground() {
        if (HackathonBeacon.CHECK_IN == currentBeacon) {
            if (tapsThisBeacon == 0) {
                container.setBackgroundResource(R.drawable.bg_checkin);
            } else if (tapsThisBeacon == 1) {
                container.setBackgroundResource(R.drawable.bg_checkin2);
            } else {
                container.setBackgroundResource(R.drawable.bg_checkin3);
            }

        } else if (HackathonBeacon.PARKING == currentBeacon) {
            if (tapsThisBeacon == 0) {
                container.setBackgroundResource(R.drawable.bg_parking);
            } else if (tapsThisBeacon == 1) {
                container.setBackgroundResource(R.drawable.bg_parking2);
            } else {
                container.setBackgroundResource(R.drawable.bg_parking3);
            }

        } else if (HackathonBeacon.GATE_A22 == currentBeacon) {
            if (tapsThisBeacon == 0) {
                container.setBackgroundResource(R.drawable.bg_gate);
            } else if (tapsThisBeacon == 1) {
                container.setBackgroundResource(R.drawable.bg_gate3);
            } else {
                container.setBackgroundResource(R.drawable.bg_gate3);
            }

        } else if (HackathonBeacon.SECURITY == currentBeacon) {
            if (tapsThisBeacon == 0) {
                container.setBackgroundResource(R.drawable.bg_security);
            } else if (tapsThisBeacon == 1) {
                container.setBackgroundResource(R.drawable.bg_security2);
            } else {
                container.setBackgroundResource(R.drawable.bg_security3);
            }

        } else if (HackathonBeacon.CLUB == currentBeacon) {
            if (tapsThisBeacon == 0) {
                container.setBackgroundResource(R.drawable.bg_club);
            } else if (tapsThisBeacon == 1) {
                container.setBackgroundResource(R.drawable.bg_club2);
            } else {
                container.setBackgroundResource(R.drawable.bg_club3);
            }

        }
    }

    public void updateFields(
            final HackathonBeacon foundHackathonBeacon,
            final IBeacon beacon) {
        Log.d(TAG, "updateServerAndFields");
        if (null == foundHackathonBeacon || null == beacon) {
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

                    if (!sameHackathonBeacon) {
                        previousLocations.setText(previousLocations.getText() + " " + foundHackathonBeacon);
                        previousLocationsString = previousLocationsString + " " + foundHackathonBeacon;
                        tapsThisBeacon = 0;
                        mAudioManager.playSoundEffect(Sounds.SUCCESS);
                        currentBeacon = foundHackathonBeacon;

                        final Toast toast = Toast.makeText(MainActivity.this,
                                "Detected " + foundHackathonBeacon.name(),
                                Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.BOTTOM, 0, 0);
                        toast.show();

                    }
                    updateBackground();

                    currentLocation.setText(
                            "Current: " + foundHackathonBeacon
                                    + " (" + beacon.getAccuracy()
                                    + " " + getProximityString(beacon.getProximity()) + ")"
                    );
                    previousDistance = beacon.getAccuracy();
                }
            }
        });
    }

    @Override
    public void iBeaconDataUpdate(IBeacon iBeacon, IBeaconData iBeaconData, DataProviderException e) {
        Log.i(TAG, "iBeaconDataUpdate");

        if (e != null) {
            Log.d(TAG, "data fetch error:" + e);
        }
        if (iBeaconData != null) {

            Log.d(TAG, "I have an iBeacon with data: uuid=" + iBeacon.getProximityUuid() + " major=" + iBeacon.getMajor() + " minor=" + iBeacon.getMinor() + " welcomeMessage=" + iBeaconData.get("welcomeMessage"));

            final HackathonBeacon foundHackathonBeacon = HackathonBeacon.findMatching(iBeacon);
            if ( null != foundHackathonBeacon ) {
                Log.i(TAG, "iBeaconDataUpdate foundHackathonBeacon = " + foundHackathonBeacon.name());
            }

            /*
            String displayString = iBeacon.getProximityUuid() + " " + iBeacon.getMajor() + " " + iBeacon.getMinor()
                    + (null == foundHackathonBeacon ? "" : "\n Hackathon beacon: " + foundHackathonBeacon.name())
                    + "\n" + "Welcome message:" + iBeaconData.get("welcomeMessage");

            //updateServerAndFields(foundHackathonBeacon, iBeacon);

            updateDebugDisplay(iBeacon, displayString, true);
            */
        }

    }

    private void updateDebugDisplay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TableLayout table = (TableLayout) findViewById(R.id.beacon_table);
                table.removeAllViews();

                for( HackathonBeacon hackathonBeacon : HackathonBeacon.values() ) {

                    TableRow tr = new TableRow(MainActivity.this);
                    tr = new TableRow(MainActivity.this);
                    tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
                    table.addView(tr);

                    TextView textView = new TextView(MainActivity.this);
                    if (null == hackathonBeacon.lastDetectedUptimeMillis) {
                        textView.setText(hackathonBeacon.name() + " (NOT SEEN YET)");
                    } else {
                        DecimalFormat df = new DecimalFormat("#.00");
                        String distance = df.format(hackathonBeacon.distanceMeters);

                        textView.setText(hackathonBeacon.name() + " "
                                + hackathonBeacon.proximity + " "
                                + distance + "m "
                                + "at " + hackathonBeacon.lastDetectedUptimeMillis
                        );
                    }
                    tr.addView(textView);
                }
            }
        });

    }

    public void onServerUpdated() {
        Log.d(TAG, "onServerUpdated");
    }

    public void onServerUpdateError() {
        Log.d(TAG, "onServerUpdateError");

    }

    @Override
    public void onInit(int status) {
        Log.d(TAG, "onInit");

        if (status == TextToSpeech.SUCCESS) {

            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            } else {
                //btnSpeak.setEnabled(true);
                speakOut();
            }

        } else {
            Log.e("TTS", "Initilization Failed!");
        }

    }

    private void speakOut() {
        Log.d(TAG, "speakOut");

        String text = "Welcome " + nickname;

        if (USE_SPEECH) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

}
