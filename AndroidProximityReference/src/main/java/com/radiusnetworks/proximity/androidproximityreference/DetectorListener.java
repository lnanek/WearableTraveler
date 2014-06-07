package com.radiusnetworks.proximity.androidproximityreference;

public interface DetectorListener {
    void onSwipeDownOrBack();

    void onSwipeForwardOrVolumeUp();

    void onSwipeBackOrVolumeDown();

    void onTap();

    void onTwoTap();
}