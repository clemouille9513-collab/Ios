package com.clem.ioslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Pose l'écran de verrouillage iOS dans une fenêtre d'accessibilité : elle passe au-dessus
 * de tout (applis, barre d'état, barre de navigation) et bloque tous les touchers.
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LockAccessibilityService? = null
    }

    private var controller: LockController? = null

    val ctl: LockController? get() = controller

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Le mode simple s'efface : l'accessibilité prend le relais
        stopService(Intent(this, LockOverlayService::class.java))
        controller?.stop()
        val c = LockController(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        controller = c
        c.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun cleanup() {
        if (instance === this) instance = null
        controller?.stop()
        controller = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
