package com.zulip.android.util;

import android.util.Log;

import com.crashlytics.android.Crashlytics;

public class ZLog {

    private ZLog() {
    }

    public static void logException(Throwable e) {
        if (!BuildHelper.shouldLogToCrashlytics()) {
            Log.e("Error", "oops", e);
        } else {
            Crashlytics.logException(e);
        }
    }

    public static void log(String message) {
        if (!BuildHelper.shouldLogToCrashlytics()) {
            Log.e("Error", message);
        } else {
            Crashlytics.log(message);
        }
    }
}
