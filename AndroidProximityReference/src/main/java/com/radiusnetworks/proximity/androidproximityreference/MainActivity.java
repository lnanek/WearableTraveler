package com.radiusnetworks.proximity.androidproximityreference;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.os.Build;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.airportvip.app.AVIPFont;
import com.airportvip.app.AVIPFragment;
import com.airportvip.app.AVIPUser;
import com.airportvip.app.AVIPWeather;
import com.google.android.glass.media.Sounds;

import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.IBeaconData;
import com.radiusnetworks.ibeacon.IBeaconDataNotifier;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;
import com.radiusnetworks.ibeacon.client.DataProviderException;
import com.radiusnetworks.proximity.ibeacon.IBeaconManager;
import com.squareup.picasso.Picasso;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity implements IBeaconConsumer, RangeNotifier, IBeaconDataNotifier,
        TextToSpeech.OnInitListener, ServerRemoteClient.ServerRemoteClientListener {

    private static final boolean USE_SPEECH = true;

    private static final boolean LOG = false;

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
    private String nickname;
    private boolean resumed;
    private boolean resumedEver;
    private Handler timerHandler = new Handler();

    // SYSTEM VARIABLES
    private SharedPreferences AVIP_temps; // SharedPreferences objects that store settings for the application.
    private SharedPreferences.Editor AVIP_temps_editor; // SharedPreferences.Editor objects that are used for editing preferences.
    private AVIPFragment avipFrag;
    private LinearLayout fragmentDisplay, buttonDisplay;
    private Boolean showFragment = false;
    private String currentFragment = "WELCOME";

    // CLASS VARIABLES
    private AVIPUser vip;
    private AVIPWeather vipWeather;

    TextView departureTime;

    private void chooseLayout(Boolean isDebug) {

        // Use Lance's original layout
        if (isDebug) { setContentView(R.layout.activity_main); }

        // Use modified layout file and set up resources for modified layout.
        else {

            setContentView(R.layout.avip_main);

            // INITIALIZE AVIPUser & AVIPWeather class.
            vip = new AVIPUser();
            vip.initializeVIP(); // Sets the VIP dummy stats.
            vipWeather = new AVIPWeather();
            vipWeather.initializeWeather(); // Sets the dummy weather stats.

            // LAYOUT INITIALIZATION
            setUpLayout(); // Sets up the layout for the activity.
            setUpStickyBar(); // Sets up the top sticky bar.
            setUpButtons(); // Sets up the buttons.
            setUpBackground(currentFragment); // Sets up the background image.

            welcomeVIP(); // Shows the first two frames.

            // Starts countdown timer.
            countDownToFlight();
        }
    }

    // Countdown Timer.
    private void countDownToFlight() {

        new CountDownTimer(1800000, 1000) {

            public void onTick(long millisUntilFinished) {
                departureTime.setText("0:" + millisUntilFinished / 1000 / 60);
            }

            public void onFinish() {
                departureTime.setText("DEPARTED");
            }
        }.start();
    }

    public interface OnRefreshSelected {
        public void refreshFragment(boolean flag);
    }

    private OnRefreshSelected onRefreshSelected;

    /** ACTIVITY EXTENSION FUNCTIONALITY _______________________________________________________ **/

    // Refreshes the fragment contents.
    private void refreshFragment() {

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        ft.replace(R.id.avip_fragment_container, new AVIPFragment());

        //ft.replace(R.id.avip_fragment, new AVIPFragment(), "avip_fragment");
        ft.addToBackStack(null);

        int count = fm.getBackStackEntryCount();
        fm.popBackStackImmediate(count, 0);
        ft.commit();

    }

    /** LAYOUT FUNCTIONALITY _______________________________________________________ **/

    // displayFragment(): Displays/hides the fragment & button containers.
    private void displayFragment(Boolean isShow, String fragment) {

        // If the fragment is currently being displayed, the fragment is hidden.
        if (isShow == true) {
            fragmentDisplay.setVisibility(View.INVISIBLE); // Hides the fragment.
            buttonDisplay.setVisibility(View.VISIBLE);
            showFragment = false; // Indicates that the fragment is hidden.
        }

        // If the fragment is currently hidden, the fragment is displayed.
        else {
            fragmentDisplay.setVisibility(View.VISIBLE); // Displays the fragment.
            buttonDisplay.setVisibility(View.INVISIBLE);
            showFragment = true; // Indicates that the fragment is currently being displayed.
        }
    }

    private void setUpBackground(String fragment) {

        int activityBackground;

        if (fragment.equals("WELCOME")) {
            activityBackground = R.drawable.bg_parking_v2;
        }

        else {
            activityBackground = R.drawable.bg_club;
        }

        ImageView avip_background = (ImageView) findViewById(R.id.avip_background);
        Picasso.with(this).load(activityBackground).noFade().into(avip_background); // Loads the image into the ImageView object.
    }


    private void setUpLayout() {

        int activityLayout = R.layout.avip_main; // Used to reference the application layout.
        //setContentView(activityLayout); // Sets the layout for the current activity.

        // References the fragment & button containers.
        fragmentDisplay = (LinearLayout) findViewById(R.id.avip_fragment_container);
        buttonDisplay = (LinearLayout) findViewById(R.id.avip_button_subcontainer);

        // References the AVIPFragment class.
        //avipFrag = (AVIPFragment) getSupportFragmentManager().findFragmentById(R.id.avip_fragment);
    }

    private void setUpStickyBar() {

        // Loads the airline icon.
        int flightIcon = R.drawable.upper_left_logo_glass; // American Airlines Logo.
        ImageView avip_flight_icon = (ImageView) findViewById(R.id.avip_sticky_flight_icon);
        Picasso.with(this).load(flightIcon).noFade().into(avip_flight_icon); // Loads the image into the ImageView object.

        // Gate information.
        departureTime = (TextView) findViewById(R.id.avip_sticky_departure);
        TextView gateText = (TextView) findViewById(R.id.avip_sticky_gate);
        TextView flightNumber = (TextView) findViewById(R.id.avip_sticky_flight_number);

        // Sets sticky text properties.
        flightNumber.setText(vip.getFlightNumber());
        gateText.setText(vip.getGateNumber());
        departureTime.setText(vip.getEtaDeparture());

        // Sets custom font properties.
        flightNumber.setTypeface(AVIPFont.getInstance(this).getTypeFace());
        gateText.setTypeface(AVIPFont.getInstance(this).getTypeFace());
        departureTime.setTypeface(AVIPFont.getInstance(this).getTypeFace());
    }

    private void setUpButtons() {

        // Reference the button objects.
        Button flightButton = (Button) findViewById(R.id.avip_flight_button);
        Button wifiButton = (Button) findViewById(R.id.avip_wifi_button);
        Button weatherButton = (Button) findViewById(R.id.avip_weather_button);
        Button dealButton = (Button) findViewById(R.id.avip_deal_button);

        // Set custom text.
        flightButton.setTypeface(AVIPFont.getInstance(this).getTypeFace());
        wifiButton.setTypeface(AVIPFont.getInstance(this).getTypeFace());
        weatherButton.setTypeface(AVIPFont.getInstance(this).getTypeFace());
        dealButton.setTypeface(AVIPFont.getInstance(this).getTypeFace());

        flightButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                currentFragment = "FLIGHT";
                updateFragmentText(currentFragment);
                refreshFragment();
                displayFragment(showFragment, currentFragment);
            }
        });

        wifiButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                currentFragment = "WIFI";
                updateFragmentText(currentFragment);
                refreshFragment();
                displayFragment(showFragment, currentFragment);
            }
        });

        weatherButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                currentFragment = "WEATHER";
                updateFragmentText(currentFragment);
                refreshFragment();
                displayFragment(showFragment, currentFragment);
            }
        });

        dealButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                currentFragment = "WELCOME";
                updateFragmentText(currentFragment);
                refreshFragment();
                displayFragment(showFragment, currentFragment);
            }
        });

    }


    /** PHYSICAL BUTTON FUNCTIONALITY __________________________________________________________ **/

    // BACK KEY:
    // onBackPressed(): Defines the action to take when the physical back button key is pressed.
    @Override
    public void onBackPressed() {

        // Closes the fragment if currently being displayed.
        if (showFragment) {
            displayFragment(showFragment, currentFragment);
        }

        else {
            finish(); // Finishes the activity.
        }
    }

    /** ADDITIONAL FUNCTIONALITY _______________________________________________________________ **/


    private void welcomeVIP() {

        final int waitTime = 1500; // 1500 ms

        // Displays the welcome frame.
        currentFragment = "WELCOME";
        updateFragmentText(currentFragment); // Update the fragment contents.
        refreshFragment(); // Refresh the fragment.
        displayFragment(showFragment, currentFragment); // Displays the fragment.

        // Timer for the initial frame.
        new CountDownTimer(waitTime, 1000) {

            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {

                // Displays the ready frame.
                currentFragment = "READY";
                updateFragmentText(currentFragment); // Update the fragment contents.
                refreshFragment(); // Refresh the fragment.
                refreshFragment();
            }

        }.start();

        // Timer for the second frame.
        new CountDownTimer(3000, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {

                // Displays the ready frame.
                displayFragment(showFragment, currentFragment); // Displays the fragment.
            }

        }.start();
    }

    private void updateFragmentText(String fragment) {

        // Preference values.
        String avip_line_1, avip_line_2, avip_line_3, avip_weather;
        Boolean isWeather = false;

        // Sets the preferences.
        AVIP_temps = getSharedPreferences("avip_temps", Context.MODE_PRIVATE);
        AVIP_temps_editor = AVIP_temps.edit();  // Sets up shared preferences for editing.

        // Update the fragment texts.
        if (fragment.equals("WELCOME")) {
            avip_line_1 = "Welcome to " + vip.getAirportName() + " Airport,"  ;
            avip_line_2 = vip.getVipName() + ".";
            avip_line_3 = "";

            AVIP_temps_editor.putBoolean("avip_weather_enabled", false); // Disables weather icon.
        }

        // Update the fragment texts.
        else if (fragment.equals("READY")) {
            avip_line_1 = "Please get your";
            avip_line_2 = "boarding pass ready.";
            avip_line_3 = "";

            AVIP_temps_editor.putBoolean("avip_weather_enabled", false); // Disables weather icon.
        }

        else if (fragment.equals("FLIGHT")) {
            avip_line_1 = vip.getDestinationName();
            avip_line_2 = vip.getFlightNumber();
            avip_line_3 = vip.getSeatNumber();

            AVIP_temps_editor.putBoolean("avip_weather_enabled", false); // Disables weather icon.
        }

        else if (fragment.equals("WEATHER")) {
            avip_line_1 = vipWeather.getArrivalName();
            avip_line_2 = vipWeather.getArrivalTemp();
            avip_line_3 = vipWeather.getArriveCurrentWeather();

            AVIP_temps_editor.putBoolean("avip_weather_enabled", true); // Enable weather icon.
        }

        else if (fragment.equals("WIFI")) {
            avip_line_1 = "NETWORK: SAY_guest";
            avip_line_2 = "PASSWORD: sayernet";
            avip_line_3 = "";

            AVIP_temps_editor.putBoolean("avip_weather_enabled", false); // Disables weather icon.
        }

        else {
            avip_line_1 = "Welcome to " + vip.getAirportName() + " Airport,"  ;
            avip_line_2 = vip.getVipName() + ".";
            avip_line_3 = "";

            AVIP_temps_editor.putBoolean("avip_weather_enabled", false); // Disables weather icon.
        }

        AVIP_temps_editor.putString("avip_line_1", avip_line_1);
        AVIP_temps_editor.putString("avip_line_2", avip_line_2);
        AVIP_temps_editor.putString("avip_line_3", avip_line_3);
        AVIP_temps_editor.commit(); // Saves the new preferences.
    }



    // updates the background images. Modification of Lance's updateFields function.
    public void updateBackgrounds(
            ) {
        Log.d(TAG, "updateBackgrounds");



        handler.post(new Runnable() {
            @Override
            public void run() {

                    // Background image reference.
                    int activityBackground = R.drawable.transparent_tile; // BLANK
                    ImageView avip_background = (ImageView) findViewById(R.id.avip_background);

                    if (HackathonBeacon.CHECK_IN == currentBeacon) {

                        activityBackground = R.drawable.bg_checkin;
                        Picasso.with(MainActivity.this).load(activityBackground).noFade().into(avip_background); // Loads the image into the ImageView object.

                    } else if (HackathonBeacon.PARKING == currentBeacon) {

                        activityBackground = R.drawable.bg_parking_v2;
                        Picasso.with(MainActivity.this).load(activityBackground).noFade().into(avip_background); // Loads the image into the ImageView object.

                    } else if (HackathonBeacon.GATE_A22 == currentBeacon) {

                        activityBackground = R.drawable.bg_gate;
                        Picasso.with(MainActivity.this).load(activityBackground).noFade().into(avip_background); // Loads the image into the ImageView object.

                    } else if (HackathonBeacon.SECURITY == currentBeacon) {

                        activityBackground = R.drawable.bg_security; // BLANK
                        Picasso.with(MainActivity.this).load(activityBackground).noFade().into(avip_background); // Loads the image into the ImageView object.

                    } else if (HackathonBeacon.CLUB == currentBeacon) {

                        activityBackground = R.drawable.bg_club;
                        Picasso.with(MainActivity.this).load(activityBackground).noFade().into(avip_background); // Loads the image into the ImageView object.
                    }


                }

        });
    }

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
        Log.d(TAG, "onCreate hasFocus = " + hasWindowFocus());
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        screenWaker = new ScreenWaker(this);
        IBeaconManager.LOG_DEBUG = LOG;

        Log.d(TAG, "IBeaconManager.DEFAULT_FOREGROUND_BETWEEN_SCAN_PERIOD: " + IBeaconManager.DEFAULT_FOREGROUND_BETWEEN_SCAN_PERIOD);
        Log.d(TAG, "IBeaconManager.DEFAULT_FOREGROUND_SCAN_PERIOD: " + IBeaconManager.DEFAULT_FOREGROUND_SCAN_PERIOD);

        super.onCreate(savedInstanceState);

        /** HUH MODIFICATION ___________________________________________________________________ **/
        // TODO chooseLayout(false);
        //------------------------------------------------------------------------------------------
        setContentView(R.layout.activity_main);

        tts = new TextToSpeech(this, this);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        username = (EditText) findViewById(R.id.username);
        email = DeviceEmail.get(this);
        if ("lnanek@gmail.com".equals(email)) {
            username.setText("Tony Hawk");
            nickname = "Tony";
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

        resumed = true;
        resumedEver = true;
        startTimerIfResumedAndFocused();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause hasFocus = " + hasWindowFocus());

        super.onPause();
        resumed = false;
        screenWaker.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop hasFocus = " + hasWindowFocus());
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
        Log.d(TAG, "onDestroy hasFocus = " + hasWindowFocus());

        super.onDestroy();
        iBeaconManager.unBind(this);
        swipes = null;


        // Don't forget to shutdown tts!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {
        Log.d(TAG, "didRangeBeaconsInRegion");

        boolean found = false;
        for (IBeacon iBeacon : iBeacons) {
            iBeacon.requestData(this);
            Log.d(TAG, "I see an iBeacon: " + iBeacon.getProximityUuid() + "," + iBeacon.getMajor() + "," + iBeacon.getMinor());
            final HackathonBeacon foundHackathonBeacon = HackathonBeacon.findMatching(iBeacon);
            if (null != foundHackathonBeacon) {
                found = true;
                foundHackathonBeacon.proximity = HackathonBeacon.getProximityString(iBeacon.getProximity());
                foundHackathonBeacon.distanceMeters = iBeacon.getAccuracy();
                foundHackathonBeacon.lastDetectedUptimeMillis = SystemClock.uptimeMillis();
            }
        }

        updateDebugDisplay();

        /** HUH MODIFICATION **/
        //updateBackgrounds();

        if (found) {
            updateCurrentLocation();

            Log.d(TAG, "updating server " + (updateCount++));
            ServerRemoteClient.updateServer(username.getText().toString(),
                    email,
                    MainActivity.this);
        } else {
            Log.d(TAG, "nothing found. not updating server...");
        }
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
                container.setBackgroundResource(R.drawable.bg_gate2);
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

    public HackathonBeacon getNearest(final Long maxAgeAllowed) {
        final long now = SystemClock.uptimeMillis();

        HackathonBeacon closestBeacon = null;
        for( HackathonBeacon beacon : HackathonBeacon.values() ) {
            if ( null == beacon.lastDetectedUptimeMillis ) {
                continue;
            }

            final long age = now - beacon.lastDetectedUptimeMillis;
            if ( null != maxAgeAllowed && age > maxAgeAllowed ) {
                continue;
            }

            if ( null == closestBeacon ) {
                closestBeacon = beacon;
                continue;
            }

            if ( beacon.distanceMeters < closestBeacon.distanceMeters ) {
                closestBeacon = beacon;
            }
        }


        Log.d(TAG, "getNearest returning = " + closestBeacon);
        return closestBeacon;
    }

    public void updateCurrentLocation() {
        Log.d(TAG, "updateCurrentLocation");

        HackathonBeacon nearestCandidate = getNearest(new Long(15 * 1000));
        //if ( null == nearestCandidate ) {
        //    nearestCandidate = getNearest(null);
        //}
        final HackathonBeacon nearest = nearestCandidate;

        if (null == nearest ) {
            return;
        }

        final boolean sameHackathonBeacon = null != currentBeacon
                && currentBeacon == nearest;
        final boolean sameDistance = null != previousDistance
                && previousDistance.equals(nearest.distanceMeters);

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!sameHackathonBeacon || !sameDistance) {

                    if (!sameHackathonBeacon) {
                        previousLocations.setText(previousLocations.getText() + " " + nearest.name());
                        previousLocationsString = previousLocationsString + " " + nearest.name();
                        tapsThisBeacon = 0;
                        mAudioManager.playSoundEffect(Sounds.SUCCESS);
                        currentBeacon = nearest;

                        final Toast toast = Toast.makeText(MainActivity.this,
                                "Detected " + nearest.name(),
                                Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.BOTTOM, 0, 0);
                        toast.show();

                    }
                    updateBackground();

                    currentLocation.setText(
                            "Current: " + nearest
                                    + " (" + nearest.distanceMeters
                                    + " " + nearest.proximity + ")"
                    );
                    previousDistance = nearest.distanceMeters;
                }
            }
        });
    }

    @Override
    public void iBeaconDataUpdate(IBeacon iBeacon, IBeaconData iBeaconData, DataProviderException e) {
        Log.i(TAG, "iBeaconDataUpdate");
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
