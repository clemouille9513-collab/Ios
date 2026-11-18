package com.clem.ioslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.enabled(context) && Prefs.simpleMode(context)) {
            LockOverlayService.start(context)
        }
    }
}
