package com.clem.ioslock

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Property
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

/**
 * L'écran de verrouillage façon iOS : heure, date, cadenas, lampe, appareil photo,
 * balayage vers le haut, clavier de code, déverrouillage par le visage,
 * animations de réveil et de déverrouillage.
 */
class LockActivity : ComponentActivity() {

    private var preview = false
    private var savedCode = ""
    private var codeLen = 4
    private var codeMode = false
    private var unlocking = false
    private var torchOn = false
    private var leaving = false
    private var hasWallpaper = false
    private var faceOk = false
    private var consec = 0
    private val typed = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private val loops = ArrayList<ObjectAnimator>()
    private var scanAnim: ObjectAnimator? = null
    private val face = FaceAuth(this)

    private lateinit var root: FrameLayout
    private lateinit var lockLayer: FrameLayout
    private lateinit var codeLayer: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var lockIcon: LockIconView
    private lateinit var codeLock: LockIconView
    private lateinit var torchBtn: ControlButton
    private lateinit var camBtn: ControlButton
    private lateinit var homeBar: View
    private lateinit var hint: TextView
    private lateinit var codeTitle: TextView
    private lateinit var dotsRow: LinearLayout
    private lateinit var actionText: TextView
    private val dots = ArrayList<DotView>()

    private var downY = 0f
    private var tracker: VelocityTracker? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent.getBooleanExtra("preview", false)
        setShowWhenLocked(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val lp = window.attributes
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = lp
        hideBars()
        loadPrefs()
        buildUi()
        setContentView(root)
        root.post { playIntro() }
    }

    private fun loadPrefs() {
        savedCode = Prefs.code(this)
        codeLen = if (savedCode.isEmpty()) 4 else savedCode.length
    }

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    // ------------------------------------------------------------------ UI

    private fun floatLoop(v: View, prop: Property<View, Float>, to: Float, dur: Long) {
        val a = ObjectAnimator.ofFloat(v, prop, 0f, to)
        a.duration = dur
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.interpolator = AccelerateDecelerateInterpolator()
        a.start()
        loops.add(a)
    }

    private fun buildUi() {
        root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                0xFF16215F.toInt(), 0xFF5B34C9.toInt(),
                0xFFD9468A.toInt(), 0xFFFF8F5A.toInt()
            )
        )

        // Fond d'écran choisi dans l'appli
        val wp = File(filesDir, "wallpaper.jpg")
        if (wp.exists()) {
            val bmp = try {
                BitmapFactory.decodeFile(wp.path)
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                hasWallpaper = true
                val iv = ImageView(this)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setImageBitmap(bmp)
                root.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
                val shade = View(this)
                shade.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x66000000, 0x00000000, 0x77000000)
                )
                root.addView(shade, FrameLayout.LayoutParams(MATCH, MATCH))
            }
        }

        // Taches lumineuses qui flottent (seulement sans fond d'écran perso)
        if (!hasWallpaper) {
            val g1 = GlowView(this, 0x99FFB36B.toInt())
            root.addView(
                g1,
                FrameLayout.LayoutParams(dp(420), dp(420), Gravity.TOP or Gravity.END).apply {
                    topMargin = -dp(120)
                    rightMargin = -dp(160)
                }
            )
            val g2 = GlowView(this, 0x8860A5FA.toInt())
            root.addView(
                g2,
                FrameLayout.LayoutParams(dp(460), dp(460), Gravity.BOTTOM or Gravity.START).apply {
                    bottomMargin = -dp(150)
                    leftMargin = -dp(170)
                }
            )
            floatLoop(g1, View.TRANSLATION_X, -dpf(70f), 9000)
            floatLoop(g1, View.TRANSLATION_Y, dpf(60f), 11000)
            floatLoop(g2, View.TRANSLATION_X, dpf(80f), 10000)
            floatLoop(g2, View.TRANSLATION_Y, -dpf(70f), 12000)
        }

        // ---- Écran principal (heure, date, boutons)
        lockLayer = FrameLayout(this)

        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        lockIcon = LockIconView(this)
        header.addView(lockIcon, LinearLayout.LayoutParams(dp(22), dp(28)))

        val date = TextClock(this).apply {
            format12Hour = "EEEE d MMMM"
            format24Hour = "EEEE d MMMM"
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        header.addView(date, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })

        val time = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            setTextColor(0xF2FFFFFF.toInt())
            textSize = Prefs.clockSize(this@LockActivity).toFloat()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            letterSpacing = -0.02f
            gravity = Gravity.CENTER
        }
        header.addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = -dp(4) })

        lockLayer.addView(
            header,
            FrameLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(Prefs.clockTop(this@LockActivity)) }
        )

        torchBtn = ControlButton(this, ControlButton.TORCH) { toggleTorch() }
        camBtn = ControlButton(this, ControlButton.CAMERA) { openCamera() }
        lockLayer.addView(
            torchBtn,
            FrameLayout.LayoutParams(dp(58), dp(58)).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = dp(46)
                bottomMargin = dp(70)
            }
        )
        lockLayer.addView(
            camBtn,
            FrameLayout.LayoutParams(dp(58), dp(58)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dp(46)
                bottomMargin = dp(70)
            }
        )

        hint = TextView(this).apply {
            text = "Balayer vers le haut pour ouvrir"
            textSize = 13f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        lockLayer.addView(
            hint,
            FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(32)
            }
        )
        val pulse = ObjectAnimator.ofFloat(hint, View.ALPHA, 0.35f, 1f)
        pulse.duration = 1400
        pulse.repeatCount = ValueAnimator.INFINITE
        pulse.repeatMode = ValueAnimator.REVERSE
        pulse.start()
        loops.add(pulse)

        homeBar = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpf(3f)
            }
            alpha = 0.9f
        }
        lockLayer.addView(
            homeBar,
            FrameLayout.LayoutParams(dp(134), dp(5)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            }
        )

        lockLayer.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    tracker?.addMovement(e)
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(e)
                    applyDrag((downY - e.rawY).coerceAtLeast(0f))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(e)
                    tracker?.computeCurrentVelocity(1000)
                    val vy = tracker?.yVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    val dy = downY - e.rawY
                    val fast = vy < -1500f && dy > dp(30)
                    if (e.actionMasked == MotionEvent.ACTION_UP &&
                        (dy > root.height * 0.2f || fast)
                    ) {
                        openCode()
                    } else {
                        resetDrag()
                    }
                }
            }
            true
        }
        root.addView(lockLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- Écran du code
        codeLayer = buildCodeLayer()
        codeLayer.visibility = View.GONE
        codeLayer.alpha = 0f
        root.addView(codeLayer, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun buildCodeLayer(): FrameLayout {
        val layer = FrameLayout(this)
        layer.isClickable = true

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        codeLock = LockIconView(this)
        col.addView(codeLock, LinearLayout.LayoutParams(dp(22), dp(28)))

        codeTitle = TextView(this).apply {
            text = "Entrez le code"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        col.addView(codeTitle, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16) })

        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in 0 until codeLen) {
            val d = DotView(this)
            dots.add(d)
            dotsRow.addView(
                d,
                LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                    leftMargin = dp(10)
                    rightMargin = dp(10)
                }
            )
        }
        col.addView(dotsRow, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(26) })

        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ")
        )
        for (r in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for ((n, l) in r) addDigit(row, n, l)
            col.addView(row, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })
        }
        val last = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        last.addView(View(this), keyParams())
        addDigit(last, "0", "")
        last.addView(View(this), keyParams())
        col.addView(last, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })

        layer.addView(
            col,
            FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(80)
            }
        )

        actionText = TextView(this).apply {
            text = "Annuler"
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { onAction() }
        }
        layer.addView(
            actionText,
            FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dp(28)
                bottomMargin = dp(40)
            }
        )
        return layer
    }

    private fun keyParams() = LinearLayout.LayoutParams(dp(78), dp(78)).apply {
        leftMargin = dp(11)
        rightMargin = dp(11)
    }

    private fun addDigit(row: LinearLayout, n: String, l: String) {
        row.addView(DigitButton(this, n, l) { onDigit(it) }, keyParams())
    }

    // ------------------------------------------------------------------ Animations

    private fun playIntro() {
        codeMode = false
        unlocking = false
        faceOk = false
        handler.removeCallbacksAndMessages(null)
        stopFace()
        typed.setLength(0)
        updateDots()
        actionText.text = "Annuler"
        codeTitle.text = "Entrez le code"
        hint.text = "Balayer vers le haut pour ouvrir"

        codeLayer.animate().cancel()
        codeLayer.visibility = View.GONE
        codeLayer.alpha = 0f
        codeLayer.translationY = 0f

        root.animate().cancel()
        root.alpha = 1f
        root.scaleX = 1f
        root.scaleY = 1f

        lockLayer.animate().cancel()
        lockLayer.translationY = 0f
        lockLayer.alpha = 1f
        lockLayer.scaleX = 1f
        lockLayer.scaleY = 1f

        // Le cadenas se referme
        lockIcon.lift = 1f
        lockIcon.animateTo(0f, 420)
        codeLock.lift = 0f

        header.alpha = 0f
        header.translationY = -dpf(18f)
        header.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(520)
            .setInterpolator(DecelerateInterpolator(2f)).start()

        val btns = listOf<View>(torchBtn, camBtn)
        for ((i, v) in btns.withIndex()) {
            v.alpha = 0f
            v.scaleX = 0.5f
            v.scaleY = 0.5f
            v.animate().setStartDelay(180L + 90L * i).alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(420).setInterpolator(OvershootInterpolator(1.6f)).start()
        }

        homeBar.translationY = dpf(30f)
        homeBar.alpha = 0f
        homeBar.animate().setStartDelay(250).translationY(0f).alpha(0.9f).setDuration(450)
            .setInterpolator(DecelerateInterpolator()).start()

        // Après l'animation de réveil, on cherche ton visage
        handler.postDelayed({ startFace() }, 500)
    }

    private fun applyDrag(dy: Float) {
        val h = root.height.toFloat().coerceAtLeast(1f)
        val p = (dy / (h * 0.45f)).coerceIn(0f, 1f)
        lockLayer.translationY = -dy * 0.55f
        lockLayer.alpha = 1f - p * 0.85f
        val s = 1f - p * 0.06f
        lockLayer.scaleX = s
        lockLayer.scaleY = s
        if (savedCode.isNotEmpty() && !faceOk) {
            codeLayer.visibility = View.VISIBLE
            codeLayer.alpha = p * p
            codeLayer.translationY = dpf(40f) * (1f - p)
        }
    }

    private fun resetDrag() {
        lockLayer.animate().setStartDelay(0).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
        if (savedCode.isNotEmpty()) {
            codeLayer.animate().setStartDelay(0).alpha(0f).setDuration(200)
                .withEndAction { if (!codeMode) codeLayer.visibility = View.GONE }.start()
        }
    }

    private fun openCode() {
        if (codeMode || unlocking) return
        if (savedCode.isEmpty() || faceOk) {
            unlock()
            return
        }
        stopFace()
        codeMode = true
        codeLayer.visibility = View.VISIBLE
        lockLayer.animate().setStartDelay(0).translationY(-dpf(80f)).alpha(0f)
            .scaleX(0.94f).scaleY(0.94f).setDuration(300)
            .setInterpolator(DecelerateInterpolator()).start()
        codeLayer.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(340)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closeCode() {
        codeMode = false
        typed.setLength(0)
        updateDots()
        actionText.text = "Annuler"
        codeTitle.text = "Entrez le code"
        codeLayer.animate().setStartDelay(0).alpha(0f).translationY(dpf(40f)).setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (!codeMode) codeLayer.visibility = View.GONE }.start()
        lockLayer.animate().setStartDelay(0).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
    }

    // ------------------------------------------------------------------ Visage

    private fun faceReady(): Boolean {
        return savedCode.isNotEmpty() &&
            Prefs.faceEnabled(this) &&
            Prefs.face(this) != null &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun startFace() {
        if (unlocking || codeMode || faceOk) return
        if (!faceReady()) return
        val ref = Prefs.face(this) ?: return
        val tol = Prefs.faceTol(this)
        consec = 0
        scanAnim?.cancel()
        scanAnim = ObjectAnimator.ofFloat(lockIcon, View.ALPHA, 0.35f, 1f).apply {
            duration = 450
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        face.start(null) { v ->
            if (!faceOk && !unlocking && !codeMode) {
                if (FaceMatch.distance(v, ref) < tol) consec++ else consec = 0
                if (consec >= 3) onFaceSuccess()
            }
        }
        handler.postDelayed({ onFaceTimeout() }, 5000)
    }

    private fun stopFace() {
        scanAnim?.cancel()
        scanAnim = null
        if (::lockIcon.isInitialized) lockIcon.alpha = 1f
        face.stop()
    }

    private fun onFaceSuccess() {
        if (faceOk) return
        stopFace()
        faceOk = true
        lockIcon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        lockIcon.animateTo(1f, 300)
        hint.text = "Visage reconnu · balayer vers le haut"
    }

    private fun onFaceTimeout() {
        if (faceOk) return
        stopFace()
        hint.text = "Visage non reconnu · balayer pour utiliser le code"
        ObjectAnimator.ofFloat(
            lockIcon, View.TRANSLATION_X,
            0f, -dpf(10f), dpf(10f), -dpf(7f), dpf(7f), -dpf(3f), dpf(3f), 0f
        ).apply {
            duration = 380
            start()
        }
    }

    // ------------------------------------------------------------------ Code

    private fun onDigit(d: String) {
        if (unlocking || typed.length >= codeLen) return
        typed.append(d)
        updateDots()
        actionText.text = "Supprimer"
        if (typed.length == codeLen) {
            handler.postDelayed({ checkCode() }, 140)
        }
    }

    private fun onAction() {
        if (unlocking) return
        if (typed.isNotEmpty()) {
            typed.deleteCharAt(typed.length - 1)
            updateDots()
            if (typed.isEmpty()) actionText.text = "Annuler"
        } else {
            closeCode()
        }
    }

    private fun updateDots() {
        dots.forEachIndexed { i, d -> d.filled = i < typed.length }
    }

    private fun checkCode() {
        if (typed.toString() == savedCode) unlock() else wrongCode()
    }

    private fun wrongCode() {
        buzz(220)
        codeTitle.text = "Code incorrect"
        ObjectAnimator.ofFloat(
            dotsRow, View.TRANSLATION_X,
            0f, -dpf(24f), dpf(24f), -dpf(18f), dpf(18f), -dpf(9f), dpf(9f), 0f
        ).apply {
            duration = 420
            start()
        }
        handler.postDelayed({
            typed.setLength(0)
            updateDots()
            codeTitle.text = "Entrez le code"
            actionText.text = "Annuler"
        }, 480)
    }

    private fun buzz(ms: Long) {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------------ Déverrouillage

    private fun unlock() {
        if (unlocking) return
        unlocking = true
        stopFace()
        val icon = if (codeMode) codeLock else lockIcon
        icon.animateTo(1f, 260)
        handler.postDelayed({
            root.animate().setStartDelay(0).scaleX(1.15f).scaleY(1.15f).alpha(0f)
                .setDuration(320).setInterpolator(AccelerateInterpolator())
                .withEndAction { finishUnlock() }.start()
        }, 220)
    }

    private fun finishUnlock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (preview || !km.isKeyguardLocked) {
            closeNow()
            return
        }
        // Si le Samsung a encore un code / une empreinte, le système les demande ici.
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                closeNow()
            }

            override fun onDismissCancelled() {
                playIntro()
            }

            override fun onDismissError() {
                closeNow()
            }
        })
    }

    private fun closeNow() {
        finish()
        overridePendingTransition(0, 0)
    }

    // ------------------------------------------------------------------ Lampe & appareil photo

    private fun toggleTorch() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            torchBtn.setActive(torchOn)
        } catch (e: Exception) {
        }
    }

    private fun openCamera() {
        leaving = true
        stopFace()
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(flags))
        } catch (e: Exception) {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(flags))
            } catch (e2: Exception) {
                leaving = false
            }
        }
    }

    // ------------------------------------------------------------------ Cycle de vie

    /** Le service nous relance à chaque allumage / extinction : on rejoue l'animation de réveil. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (unlocking) return
        preview = intent.getBooleanExtra("preview", false)
        val old = codeLen
        loadPrefs()
        if (old != codeLen) {
            recreate()
            return
        }
        playIntro()
    }

    override fun onResume() {
        super.onResume()
        leaving = false
    }

    /**
     * Verrouillage strict : si on essaie de quitter l'écran (Accueil, applis récentes)
     * sans avoir mis le code ou montré son visage, l'écran revient aussitôt.
     */
    override fun onStop() {
        super.onStop()
        stopFace()
        if (isFinishing || unlocking || preview || leaving) return
        if (savedCode.isEmpty() || !Prefs.strict(this)) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.mode != AudioManager.MODE_NORMAL) return
        handler.postDelayed({ relaunch() }, 150)
    }

    private fun relaunch() {
        try {
            startActivity(
                Intent(this, LockActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (codeMode) {
            closeCode()
        } else if (preview) {
            closeNow()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        loops.forEach { it.cancel() }
        scanAnim?.cancel()
        face.release()
        super.onDestroy()
    }
}
