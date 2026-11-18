package com.clem.ioslock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * Le cœur : pose l'écran iOS dans une fenêtre système et gère allumage / extinction.
 * Utilisé soit par le service d'accessibilité (fenêtre au-dessus de tout),
 * soit par le « mode simple » (fenêtre de superposition, sans accessibilité).
 */
class LockController(private val ctx: Context, private val windowType: Int) : LockUi.Host {

    companion object {
        @Volatile
        var current: LockController? = null
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val owner = OverlayLifecycle()
    private val ui = LockUi(ctx, owner, this)
    private lateinit var params: WindowManager.LayoutParams
    private var attached = false
    private var locked = false
    private var needLock = false
    private var previewing = false
    private var modeListener: AudioManager.OnModeChangedListener? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_USER_PRESENT -> onUserPresent()
            }
        }
    }

    fun start() {
        if (attached) return
        current = this
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        ui.root.visibility = View.INVISIBLE
        try {
            wm.addView(ui.root, params)
            attached = true
        } catch (e: Exception) {
            attached = false
            return
        }

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, f)
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val l = AudioManager.OnModeChangedListener { mode -> onAudioMode(mode) }
            modeListener = l
            am.addOnModeChangedListener(ContextCompat.getMainExecutor(ctx), l)
        }
    }

    private fun callActive(): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.mode != AudioManager.MODE_NORMAL
    }

    private fun onScreenOff() {
        if (!attached || !Prefs.enabled(ctx)) return
        needLock = true
        if (callActive()) return
        armNow(false)
    }

    private fun onScreenOn() {
        if (!attached) return
        if (locked) {
            ui.playIntro()
        } else if (needLock && Prefs.enabled(ctx) && !callActive()) {
            armNow(true)
        }
    }

    /** Empreinte reconnue par le système Samsung : seulement si le téléphone a un vrai verrouillage. */
    private fun onUserPresent() {
        if (!attached || !locked || previewing) return
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isDeviceSecure) ui.unlockExternal()
    }

    private fun onAudioMode(mode: Int) {
        if (!attached || !needLock || !Prefs.enabled(ctx) || previewing) return
        if (mode == AudioManager.MODE_NORMAL) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive && !locked) armNow(true)
        } else if (locked) {
            ui.abortForCall()
            locked = false
            hideWindow()
        }
    }

    private fun armNow(playNow: Boolean) {
        owner.start()
        ui.arm(false)
        showWindow()
        locked = true
        previewing = false
        if (playNow) ui.root.post { ui.playIntro() }
    }

    fun showPreview() {
        if (!attached) return
        owner.start()
        ui.arm(true)
        showWindow()
        locked = true
        previewing = true
        ui.root.post { ui.playIntro() }
    }

    private fun showWindow() {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        ui.root.visibility = View.VISIBLE
        try {
            wm.updateViewLayout(ui.root, params)
        } catch (e: Exception) {
        }
    }

    private fun hideWindow() {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ui.root.visibility = View.INVISIBLE
        try {
            wm.updateViewLayout(ui.root, params)
        } catch (e: Exception) {
        }
    }

    override fun onUnlocked() {
        locked = false
        needLock = false
        previewing = false
        hideWindow()
        owner.stop()
    }

    fun stop() {
        if (current === this) current = null
        if (!attached) return
        attached = false
        try {
            ctx.unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val l = modeListener
            if (l != null) {
                try {
                    (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).removeOnModeChangedListener(l)
                } catch (e: Exception) {
                }
            }
        }
        try {
            wm.removeView(ui.root)
        } catch (e: Exception) {
        }
        ui.destroy()
        owner.destroy()
    }
}
