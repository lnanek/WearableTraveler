package com.radiusnetworks.proximity.androidproximityreference;

import android.util.Log;

import java.util.UUID;
import com.radiusnetworks.ibeacon.IBeacon;

/**
 * Created by lnanek on 6/6/14.
 */
public enum HackathonBeacon {

    PARKING("114A4DD8-5B2F-4800-A079-BDCB21392BE9",1700,1234),
    CHECK_IN("114A4DD8-5B2F-4800-A079-BDCB21392BE9",1000,1031),
    SECURITY("114A4DD8-5B2F-4800-A079-BDCB21392BE9",1600,1231),
    CLUB("114A4DD8-5B2F-4800-A079-BDCB21392BE9",1500,1041),
    GATE_A22("114A4DD8-5B2F-4800-A079-BDCB21392BE9",1200,1225);

    private static final String TAG = HackathonBeacon.class.getSimpleName();

    public String mUuid;

    public int mMajor;

    public int mMinor;

    public Double distanceMeters;

    public String proximity;

    public Long lastDetectedUptimeMillis;

    public String hackathonLocation;

    HackathonBeacon(final String uuid, final int major, final int minor) {
        mUuid = uuid;
        mMajor = major;
        mMinor = minor;
        hackathonLocation = name();
    }

    public static HackathonBeacon findMatching(IBeacon iBeacon) {

        final String cleanedIBeaconUuid = iBeacon
                .getProximityUuid()
                .replaceAll("-", "")
                .toUpperCase();
        Log.i(TAG, "Finding match for: " + cleanedIBeaconUuid
        + ", major = " + iBeacon.getMajor()
        + ", minor = " + iBeacon.getMinor()
        );

        for(HackathonBeacon hackathonBeacon : HackathonBeacon.values()) {
            final String cleanedHackathonUuid = hackathonBeacon.mUuid.replaceAll("-", "").toUpperCase();

            Log.i(TAG, "Comparing against: " + cleanedHackathonUuid
                            + ", major = " + hackathonBeacon.mMajor
                            + ", minor = " + hackathonBeacon.mMinor
            );

            if (cleanedHackathonUuid.equals(cleanedIBeaconUuid)
                    && hackathonBeacon.mMajor == iBeacon.getMajor()
                    && hackathonBeacon.mMinor == iBeacon.getMinor()) {
                Log.i(TAG, "Found");
                return hackathonBeacon;
            }
        }

        Log.i(TAG, "Not Found");
        return null;
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

}
