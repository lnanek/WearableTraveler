package com.radiusnetworks.proximity.androidproximityreference;

        import android.view.MotionEvent;

public interface Detector {

    public boolean onGenericMotionEvent(MotionEvent event);

}