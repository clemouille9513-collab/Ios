package com.clem.ioslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager

/** « Mode simple » : même écran, mais sans service d'accessibilité (pas de « paramètres restreints »). */
class LockOverlayService : Service() {

    companion object {
        fun canRun(c: Context): Boolean = Settings.canDrawOverlays(c)

        fun start(c: Context) {
            if (!canRun(c)) return
            try {
                val i = Intent(c, LockOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
            }
        }
    }

    private var controller: LockController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (LockAccessibilityService.instance != null || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (controller == null) {
            val c = LockController(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            controller = c
            c.start()
        }
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("lock", "Écran iOS", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "lock") else Notification.Builder(this)
        val n = b.setContentTitle("Écran iOS actif")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(7, n, type)
        } else {
            startForeground(7, n)
        }
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        super.onDestroy()
    }
}
