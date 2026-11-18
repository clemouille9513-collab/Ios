package com.clem.ioslock

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Property
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

/**
 * L'écran de verrouillage façon iOS, construit une fois et posé dans une fenêtre
 * système par-dessus tout. Il est prêt avant que l'écran se rallume : aucun délai.
 */
class LockUi(
    private val ctx: Context,
    private val owner: OverlayLifecycle,
    private val host: Host
) {
    interface Host {
        fun onUnlocked()
    }

    val root = FrameLayout(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val face = FaceAuth(ctx, owner)
    private val loops = ArrayList<ObjectAnimator>()
    private var scanAnim: ObjectAnimator? = null

    private var preview = false
    private var savedCode = ""
    private var codeLen = 4
    private var codeMode = false
    private var unlocking = false
    private var torchOn = false
    private var hasWallpaper = false
    private var secureDevice = false
    private var faceOk = false
    private var consec = 0
    private var cameraOpen = false
    private var backCam = true
    private val typed = StringBuilder()
    private val dots = ArrayList<DotView>()

    private lateinit var wallLayer: FrameLayout
    private lateinit var dimView: View
    private lateinit var battery: BatteryView
    private var blurAnim: ValueAnimator? = null
    private var sensorMgr: SensorManager? = null
    private var parX = 0f
    private var parY = 0f
    private var downX = 0f
    private var axis = 0
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (!::wallLayer.isInitialized) return
            val tx = (-e.values[0] * dpf(1.5f)).coerceIn(-dpf(13f), dpf(13f))
            val ty = ((e.values[1] - 6f) * dpf(1.2f)).coerceIn(-dpf(13f), dpf(13f))
            parX += (tx - parX) * 0.1f
            parY += (ty - parY) * 0.1f
            wallLayer.translationX = parX
            wallLayer.translationY = parY
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    private lateinit var lockLayer: FrameLayout
    private lateinit var codeLayer: FrameLayout
    private lateinit var camLayer: FrameLayout
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
    private lateinit var camPreview: PreviewView
    private lateinit var camFlash: View
    private lateinit var camMsg: TextView
    private var fpViews: List<View> = emptyList()

    private var camProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    private var downY = 0f
    private var tracker: VelocityTracker? = null

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * ctx.resources.displayMetrics.density

    // ------------------------------------------------------------------ Cycle

    /** Prépare tout (écran éteint) : l'écran est déjà construit quand on le rallume. */
    fun arm(preview: Boolean) {
        this.preview = preview
        handler.removeCallbacksAndMessages(null)
        stopFace()
        stopParallax()
        blurAnim?.cancel()
        if (::camLayer.isInitialized) closeCameraNow()
        loadPrefs()
        build()
        resetState()
        prepareIntro()
        root.animate().cancel()
        root.alpha = 1f
        root.scaleX = 1f
        root.scaleY = 1f
    }

    fun abortForCall() {
        handler.removeCallbacksAndMessages(null)
        stopParallax()
        stopFace()
        if (::camLayer.isInitialized) closeCameraNow()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        stopParallax()
        blurAnim?.cancel()
        loops.forEach { it.cancel() }
        scanAnim?.cancel()
        face.release()
    }

    /** Déverrouillé de l'extérieur (empreinte reconnue par le système Samsung). */
    fun unlockExternal() {
        if (unlocking) return
        faceOk = true
        stopFace()
        closeCameraNow()
        unlock()
    }

    private fun loadPrefs() {
        savedCode = Prefs.code(ctx)
        codeLen = if (savedCode.isEmpty()) 4 else savedCode.length
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        secureDevice = km.isDeviceSecure
    }

    private fun resetState() {
        codeMode = false
        unlocking = false
        faceOk = false
        consec = 0
        typed.setLength(0)
        updateDots()
        actionText.text = "Annuler"
        codeTitle.text = "Entrez le code"
        hint.text = "Balayer vers le haut pour ouvrir"
        torchBtn.setActive(torchOn)
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

    private fun build() {
        loops.forEach { it.cancel() }
        loops.clear()
        scanAnim?.cancel()
        scanAnim = null
        root.removeAllViews()
        dots.clear()
        hasWallpaper = false

        val preset = Wallpapers.presets[Prefs.wallPreset(ctx).coerceIn(0, Wallpapers.presets.size - 1)]
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, preset.colors)

        // Le fond vit dans sa propre couche : un peu plus grand que l'écran pour le zoom de réveil et l'effet de profondeur
        wallLayer = FrameLayout(ctx)
        wallLayer.scaleX = 1.1f
        wallLayer.scaleY = 1.1f
        wallLayer.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, preset.colors)
        root.addView(wallLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        val wp = File(ctx.filesDir, "wallpaper.jpg")
        if (wp.exists()) {
            val bmp = try {
                BitmapFactory.decodeFile(wp.path)
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                hasWallpaper = true
                val iv = ImageView(ctx)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setImageBitmap(bmp)
                wallLayer.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
                val shade = View(ctx)
                shade.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x66000000, 0x00000000, 0x77000000)
                )
                wallLayer.addView(shade, FrameLayout.LayoutParams(MATCH, MATCH))
            }
        }

        // Taches lumineuses qui flottent (fonds de couleur)
        if (!hasWallpaper) {
            val g1 = GlowView(ctx, preset.glow1)
            wallLayer.addView(
                g1,
                FrameLayout.LayoutParams(dp(420), dp(420), Gravity.TOP or Gravity.END).apply {
                    topMargin = -dp(120)
                    rightMargin = -dp(160)
                }
            )
            val g2 = GlowView(ctx, preset.glow2)
            wallLayer.addView(
                g2,
                FrameLayout.LayoutParams(dp(460), dp(460), Gravity.BOTTOM or Gravity.START).apply {
                    bottomMargin = -dp(150)
                    leftMargin = -dp(170)
                }
            )
            val g3 = GlowView(ctx, preset.glow1 and 0x00FFFFFF or 0x66000000)
            wallLayer.addView(
                g3,
                FrameLayout.LayoutParams(dp(300), dp(300), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                    leftMargin = -dp(120)
                }
            )
            floatLoop(g1, View.TRANSLATION_X, -dpf(70f), 9000)
            floatLoop(g1, View.TRANSLATION_Y, dpf(60f), 11000)
            floatLoop(g2, View.TRANSLATION_X, dpf(80f), 10000)
            floatLoop(g2, View.TRANSLATION_Y, -dpf(70f), 12000)
            floatLoop(g3, View.TRANSLATION_X, dpf(260f), 14000)
            floatLoop(g3, View.TRANSLATION_Y, dpf(120f), 13000)
        }

        // Voile sombre qui apparaît derrière le clavier du code
        dimView = View(ctx)
        dimView.setBackgroundColor(0x66000000)
        dimView.alpha = 0f
        root.addView(dimView, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- Écran principal (heure, date, boutons)
        lockLayer = FrameLayout(ctx)

        header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        lockIcon = LockIconView(ctx)
        header.addView(lockIcon, LinearLayout.LayoutParams(dp(22), dp(28)))

        val date = TextClock(ctx).apply {
            format12Hour = "EEEE d MMMM"
            format24Hour = "EEEE d MMMM"
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        header.addView(date, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })

        val time = TextClock(ctx).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            setTextColor(Wallpapers.clockColors[Prefs.clockColor(ctx).coerceIn(0, Wallpapers.clockColors.size - 1)].second)
            textSize = Prefs.clockSize(ctx).toFloat()
            typeface = Typeface.create(
                Typeface.create("sans-serif", Typeface.NORMAL),
                Wallpapers.clockWeights[Prefs.clockWeight(ctx).coerceIn(0, Wallpapers.clockWeights.size - 1)].second,
                false
            )
            includeFontPadding = false
            letterSpacing = -0.02f
            gravity = Gravity.CENTER
        }
        header.addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = -dp(4) })

        lockLayer.addView(
            header,
            FrameLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(Prefs.clockTop(ctx)) }
        )

        battery = BatteryView(ctx)
        lockLayer.addView(
            battery,
            FrameLayout.LayoutParams(dp(25), dp(12), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(16)
                rightMargin = dp(28)
            }
        )
        updateBattery()

        torchBtn = ControlButton(ctx, ControlButton.TORCH) { toggleTorch() }
        camBtn = ControlButton(ctx, ControlButton.CAMERA) { openCamera() }
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

        // Empreinte : visible seulement si le Samsung a un verrouillage (sinon Android efface les empreintes)
        val fp = ArrayList<View>()
        if (secureDevice) {
            val icon = FingerprintView(ctx)
            lockLayer.addView(
                icon,
                FrameLayout.LayoutParams(dp(46), dp(46)).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(122)
                }
            )
            val label = TextView(ctx).apply {
                text = "Touche le capteur"
                textSize = 12f
                setTextColor(0xB3FFFFFF.toInt())
                gravity = Gravity.CENTER
            }
            lockLayer.addView(
                label,
                FrameLayout.LayoutParams(WRAP, WRAP).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(98)
                }
            )
            val pulse = ObjectAnimator.ofFloat(icon, View.ALPHA, 0.55f, 1f)
            pulse.duration = 1100
            pulse.repeatCount = ValueAnimator.INFINITE
            pulse.repeatMode = ValueAnimator.REVERSE
            pulse.start()
            loops.add(pulse)
            fp.add(icon)
            fp.add(label)
        }
        fpViews = fp

        hint = TextView(ctx).apply {
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
        val hintPulse = ObjectAnimator.ofFloat(hint, View.ALPHA, 0.35f, 1f)
        hintPulse.duration = 1400
        hintPulse.repeatCount = ValueAnimator.INFINITE
        hintPulse.repeatMode = ValueAnimator.REVERSE
        hintPulse.start()
        loops.add(hintPulse)

        homeBar = View(ctx).apply {
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
                    downX = e.rawX
                    axis = 0
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    tracker?.addMovement(e)
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(e)
                    val dx = e.rawX - downX
                    val dyy = downY - e.rawY
                    if (axis == 0 && (Math.abs(dx) > dp(10) || Math.abs(dyy) > dp(10))) {
                        axis = if (Math.abs(dx) > Math.abs(dyy)) 2 else 1
                    }
                    if (axis == 1) {
                        applyDrag(dyy.coerceAtLeast(0f))
                    } else if (axis == 2) {
                        val m = Math.min(0f, dx)
                        lockLayer.translationX = m * 0.6f
                        camHint(m)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(e)
                    tracker?.computeCurrentVelocity(1000)
                    val vy = tracker?.yVelocity ?: 0f
                    val vx = tracker?.xVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    val dy = downY - e.rawY
                    val dx = e.rawX - downX
                    val up = e.actionMasked == MotionEvent.ACTION_UP
                    if (axis == 2 && up && (dx < -root.width * 0.3f || (vx < -1400f && dx < -dp(40)))) {
                        openCamera()
                    } else if (axis == 1 && up &&
                        (dy > root.height * 0.2f || (vy < -1500f && dy > dp(30)))
                    ) {
                        openCode()
                    } else {
                        resetDrag()
                    }
                    axis = 0
                }
            }
            true
        }
        root.addView(lockLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // Bouton de sortie, uniquement pour l'aperçu
        if (preview) {
            val close = TextView(ctx).apply {
                text = "Fermer l'aperçu"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = GradientDrawable().apply {
                    setColor(0x55000000)
                    cornerRadius = dpf(20f)
                }
                setOnClickListener { unlock() }
            }
            root.addView(
                close,
                FrameLayout.LayoutParams(WRAP, WRAP).apply {
                    gravity = Gravity.TOP or Gravity.START
                    topMargin = dp(44)
                    leftMargin = dp(16)
                }
            )
        }

        // ---- Écran du code
        codeLayer = buildCodeLayer()
        codeLayer.visibility = View.GONE
        codeLayer.alpha = 0f
        root.addView(codeLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- Appareil photo intégré (impossible de s'échapper vers une autre appli)
        camLayer = buildCameraLayer()
        camLayer.visibility = View.GONE
        root.addView(camLayer, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun buildCodeLayer(): FrameLayout {
        val layer = FrameLayout(ctx)
        layer.isClickable = true

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        codeLock = LockIconView(ctx)
        col.addView(codeLock, LinearLayout.LayoutParams(dp(22), dp(28)))

        codeTitle = TextView(ctx).apply {
            text = "Entrez le code"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        col.addView(codeTitle, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16) })

        dotsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in 0 until codeLen) {
            val d = DotView(ctx)
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
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for ((n, l) in r) addDigit(row, n, l)
            col.addView(row, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })
        }
        val last = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        last.addView(View(ctx), keyParams())
        addDigit(last, "0", "")
        last.addView(View(ctx), keyParams())
        col.addView(last, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })

        // Centré en hauteur, comme sur l'iPhone
        layer.addView(
            col,
            FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.CENTER
                bottomMargin = dp(24)
            }
        )

        actionText = TextView(ctx).apply {
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

    private fun buildCameraLayer(): FrameLayout {
        val layer = FrameLayout(ctx)
        layer.setBackgroundColor(Color.BLACK)
        layer.isClickable = true

        camPreview = PreviewView(ctx)
        camPreview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        camPreview.scaleType = PreviewView.ScaleType.FILL_CENTER
        layer.addView(camPreview, FrameLayout.LayoutParams(MATCH, MATCH))

        camMsg = TextView(ctx).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
            visibility = View.GONE
        }
        layer.addView(camMsg, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))

        camFlash = View(ctx).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
        }
        layer.addView(camFlash, FrameLayout.LayoutParams(MATCH, MATCH))

        layer.addView(
            ShutterView(ctx) { takePhoto() },
            FrameLayout.LayoutParams(dp(76), dp(76)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(48)
            }
        )

        val close = TextView(ctx).apply {
            text = "Fermer"
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { closeCamera() }
        }
        layer.addView(
            close,
            FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = dp(20)
                bottomMargin = dp(60)
            }
        )

        val flip = TextView(ctx).apply {
            text = "Retourner"
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                backCam = !backCam
                bindCamera()
            }
        }
        layer.addView(
            flip,
            FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dp(20)
                bottomMargin = dp(60)
            }
        )
        return layer
    }

    private fun keyParams() = LinearLayout.LayoutParams(dp(78), dp(78)).apply {
        leftMargin = dp(11)
        rightMargin = dp(11)
    }

    private fun addDigit(row: LinearLayout, n: String, l: String) {
        row.addView(DigitButton(ctx, n, l) { onDigit(it) }, keyParams())
    }

    // ------------------------------------------------------------------ Animations

    /** État de départ de l'animation de réveil, posé pendant que l'écran est éteint. */
    private fun prepareIntro() {
        wallLayer.scaleX = 1.2f
        wallLayer.scaleY = 1.2f
        battery.alpha = 0f
        lockIcon.lift = 1f
        codeLock.lift = 0f
        header.alpha = 0f
        header.translationY = -dpf(18f)
        for (v in listOf<View>(torchBtn, camBtn)) {
            v.alpha = 0f
            v.scaleX = 0.5f
            v.scaleY = 0.5f
        }
        homeBar.alpha = 0f
        homeBar.translationY = dpf(30f)
        for (v in fpViews) v.alpha = 0f
    }

    /** Animation de réveil : le cadenas se referme, l'heure descend, les boutons rebondissent. */
    fun playIntro() {
        if (!::lockIcon.isInitialized || unlocking) return
        lockIcon.animateTo(0f, 420)
        updateBattery()
        wallLayer.animate().setStartDelay(0).scaleX(1.1f).scaleY(1.1f).setDuration(1000)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        battery.animate().setStartDelay(150).alpha(1f).setDuration(400).start()
        startParallax()

        header.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(520)
            .setInterpolator(DecelerateInterpolator(2f)).start()

        val btns = listOf<View>(torchBtn, camBtn)
        for ((i, v) in btns.withIndex()) {
            v.animate().setStartDelay(180L + 90L * i).alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(420).setInterpolator(OvershootInterpolator(1.6f)).start()
        }

        homeBar.animate().setStartDelay(250).translationY(0f).alpha(0.9f).setDuration(450)
            .setInterpolator(DecelerateInterpolator()).start()

        for (v in fpViews) {
            v.animate().setStartDelay(300).alpha(1f).setDuration(400).start()
        }

        // Après l'animation de réveil, on cherche ton visage
        handler.postDelayed({ startFace() }, 350)
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
            dimView.alpha = p
            codeLayer.translationY = dpf(40f) * (1f - p)
        }
    }

    private fun resetDrag() {
        lockLayer.animate().setStartDelay(0).translationX(0f).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
        camBtn.alpha = 1f
        camBtn.scaleX = 1f
        camBtn.scaleY = 1f
        if (!codeMode) dimView.animate().setStartDelay(0).alpha(0f).setDuration(200).start()
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
        blurTo(true)
        codeLayer.visibility = View.VISIBLE
        lockLayer.animate().setStartDelay(0).translationY(-dpf(80f)).alpha(0f)
            .scaleX(0.94f).scaleY(0.94f).setDuration(300)
            .setInterpolator(DecelerateInterpolator()).start()
        codeLayer.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(340)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closeCode() {
        codeMode = false
        blurTo(false)
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
            Prefs.faceEnabled(ctx) &&
            Prefs.face(ctx) != null &&
            ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun startFace() {
        if (unlocking || codeMode || faceOk || cameraOpen) return
        if (!faceReady()) return
        val ref = Prefs.face(ctx) ?: return
        val tol = Prefs.faceTol(ctx)
        consec = 0
        scanAnim?.cancel()
        scanAnim = ObjectAnimator.ofFloat(lockIcon, View.ALPHA, 0.35f, 1f).apply {
            duration = 450
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        face.start(null) { v ->
            if (!faceOk && !unlocking && !codeMode && !cameraOpen) {
                if (FaceMatch.distance(v, ref) < tol) consec++ else consec = 0
                if (consec >= 3) handler.post { onFaceSuccess() }
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
        if (faceOk || unlocking) return
        stopFace()
        faceOk = true
        buzz(30)
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
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------------ Déverrouillage

    private fun camHint(dx: Float) {
        val p = (-dx / (root.width * 0.35f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        camBtn.alpha = 1f
        camBtn.scaleX = 1f + p * 0.4f
        camBtn.scaleY = 1f + p * 0.4f
    }

    private fun updateBattery() {
        try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            battery.set(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), bm.isCharging)
        } catch (e: Exception) {
        }
    }

    private fun startParallax() {
        if (!Prefs.parallax(ctx)) return
        try {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sensorMgr = sm
            sm.registerListener(sensorListener, acc, SensorManager.SENSOR_DELAY_GAME)
        } catch (e: Exception) {
        }
    }

    private fun stopParallax() {
        try {
            sensorMgr?.unregisterListener(sensorListener)
        } catch (e: Exception) {
        }
        sensorMgr = null
    }

    /** Flou et assombrissement du fond derrière le clavier, comme sur iOS. */
    private fun blurTo(on: Boolean) {
        dimView.animate().setStartDelay(0).alpha(if (on) 1f else 0f).setDuration(320).start()
        if (Build.VERSION.SDK_INT < 31 || !Prefs.blur(ctx)) return
        blurAnim?.cancel()
        val from = if (on) 0f else 34f
        val to = if (on) 34f else 0f
        blurAnim = ValueAnimator.ofFloat(from, to).apply {
            duration = 340
            addUpdateListener {
                val r = it.animatedValue as Float
                if (r < 1f) {
                    wallLayer.setRenderEffect(null)
                } else {
                    wallLayer.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
                }
            }
            start()
        }
    }

    private fun unlock() {
        if (unlocking) return
        unlocking = true
        stopFace()
        stopParallax()
        lockLayer.animate().setStartDelay(0).translationY(-dpf(110f)).setDuration(380)
            .setInterpolator(AccelerateInterpolator(1.3f)).start()
        codeLayer.animate().setStartDelay(0).translationY(-dpf(110f)).setDuration(380)
            .setInterpolator(AccelerateInterpolator(1.3f)).start()
        val icon = if (codeMode) codeLock else lockIcon
        icon.animateTo(1f, 260)
        handler.postDelayed({
            root.animate().setStartDelay(0).scaleX(1.15f).scaleY(1.15f).alpha(0f)
                .setDuration(320).setInterpolator(AccelerateInterpolator())
                .withEndAction { host.onUnlocked() }.start()
        }, 220)
    }

    // ------------------------------------------------------------------ Lampe

    private fun toggleTorch() {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            torchBtn.setActive(torchOn)
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------------ Appareil photo intégré

    private fun openCamera() {
        if (cameraOpen || unlocking) return
        cameraOpen = true
        stopFace()
        handler.removeCallbacksAndMessages(null)
        camMsg.visibility = View.GONE
        camLayer.alpha = 1f
        camLayer.translationX = root.width.toFloat()
        camLayer.visibility = View.VISIBLE
        camLayer.animate().setStartDelay(0).translationX(0f).setDuration(320)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
        resetDrag()
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            camMsg.text = "Autorise la caméra dans l'appli « Écran iOS » pour prendre des photos ici."
            camMsg.visibility = View.VISIBLE
            return
        }
        bindCamera()
    }

    private fun bindCamera() {
        val fut = ProcessCameraProvider.getInstance(ctx)
        fut.addListener(Runnable {
            if (!cameraOpen) return@Runnable
            try {
                val p = fut.get()
                camProvider = p
                p.unbindAll()
                val pv = Preview.Builder().build()
                pv.setSurfaceProvider(camPreview.surfaceProvider)
                val ic = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = ic
                val sel = if (backCam) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                p.bindToLifecycle(owner, sel, pv, ic)
                camMsg.visibility = View.GONE
            } catch (e: Exception) {
                camMsg.text = "Impossible d'ouvrir l'appareil photo."
                camMsg.visibility = View.VISIBLE
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        val cv = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/IosLock")
            }
        }
        val opts = ImageCapture.OutputFileOptions.Builder(
            ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv
        ).build()
        ic.takePicture(
            opts,
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    camFlash.alpha = 0.9f
                    camFlash.animate().setStartDelay(0).alpha(0f).setDuration(240).start()
                    buzz(20)
                }

                override fun onError(exception: ImageCaptureException) {
                    camMsg.text = "La photo n'a pas pu être enregistrée."
                    camMsg.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun closeCamera() {
        if (!cameraOpen) return
        cameraOpen = false
        try {
            camProvider?.unbindAll()
        } catch (e: Exception) {
        }
        imageCapture = null
        camLayer.animate().setStartDelay(0).translationX(root.width.toFloat()).setDuration(280)
            .setInterpolator(AccelerateInterpolator(1.4f))
            .withEndAction { if (!cameraOpen) camLayer.visibility = View.GONE }.start()
        handler.postDelayed({ startFace() }, 300)
    }

    private fun closeCameraNow() {
        cameraOpen = false
        try {
            camProvider?.unbindAll()
        } catch (e: Exception) {
        }
        imageCapture = null
        camLayer.animate().cancel()
        camLayer.translationX = 0f
        camLayer.visibility = View.GONE
    }
}
