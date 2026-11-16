package com.clem.ioslock

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** Grosse tache lumineuse floue qui flotte doucement en arrière-plan. */
class GlowView(ctx: Context, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = minOf(w, h) / 2f
        p.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(color, color and 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(c: Canvas) {
        c.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, p)
    }
}

/** Petit cadenas iOS : se ferme au réveil, s'ouvre au déverrouillage. */
class LockIconView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    var lift: Float = 0f
        set(v) {
            field = v
            invalidate()
        }

    fun animateTo(target: Float, dur: Long = 260) {
        ObjectAnimator.ofFloat(this, "lift", lift, target).apply {
            duration = dur
            interpolator = OvershootInterpolator(1.5f)
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val l = lift.coerceAtLeast(0f)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val bodyTop = h * 0.46f
        val sw = w * 0.13f
        val r = w * 0.30f

        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.alpha = 235
        rect.set(0f, bodyTop, w, h)
        c.drawRoundRect(rect, w * 0.2f, w * 0.2f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        val top = sw / 2f + h * 0.04f - l * h * 0.10f
        path.reset()
        path.moveTo(cx - r, bodyTop)
        path.lineTo(cx - r, top + r)
        rect.set(cx - r, top, cx + r, top + 2 * r)
        path.arcTo(rect, 180f, 180f)
        path.lineTo(cx + r, bodyTop - l * h * 0.14f)
        c.drawPath(path, p)
    }
}

/** Rond du code : se remplit quand on tape un chiffre. */
class DotView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    var filled: Boolean = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
                if (v) {
                    scaleX = 1.3f
                    scaleY = 1.3f
                    animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
        }

    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) / 2f - 2f
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        c.drawCircle(width / 2f, height / 2f, r, p)
        if (filled) {
            p.style = Paint.Style.FILL
            c.drawCircle(width / 2f, height / 2f, r, p)
        }
    }
}

/** Touche du clavier : s'allume tout de suite au toucher puis s'éteint doucement (comme iOS). */
class DigitButton(
    ctx: Context,
    private val num: String,
    private val letters: String,
    private val onTap: (String) -> Unit
) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textSize = 34f * d
    }
    private val letP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.18f
        textSize = 10f * d
    }
    private var amt = 0f
    private var anim: ValueAnimator? = null

    override fun onDraw(c: Canvas) {
        fill.color = Color.WHITE
        fill.alpha = ((0.20f + 0.40f * amt) * 255).toInt()
        c.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, fill)
        val cx = width / 2f
        if (letters.isEmpty()) {
            c.drawText(num, cx, height / 2f + numP.textSize * 0.35f, numP)
        } else {
            c.drawText(num, cx, height * 0.5f, numP)
            letP.alpha = 220
            c.drawText(letters, cx, height * 0.5f + 13f * d, letP)
        }
    }

    private fun release() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(amt, 0f).apply {
            duration = 380
            addUpdateListener {
                amt = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel()
                amt = 1f
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                release()
                onTap(num)
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }
}

/** Boutons lampe torche et appareil photo : appui long avec vibration, comme sur iPhone. */
class ControlButton(
    ctx: Context,
    private val kind: Int,
    private val onActivate: () -> Unit
) : View(ctx) {
    companion object {
        const val TORCH = 0
        const val CAMERA = 1
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var active = false
    private var fired = false

    private val longPress = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        animate().setStartDelay(0).scaleX(1.12f).scaleY(1.12f).setDuration(90)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(OvershootInterpolator()).start()
            }.start()
        onActivate()
    }

    fun setActive(v: Boolean) {
        active = v
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                animate().setStartDelay(0).scaleX(0.9f).scaleY(0.9f).setDuration(120)
                    .setInterpolator(DecelerateInterpolator()).start()
                postDelayed(longPress, 350)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (!fired) {
                    animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(160)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val u = width / 58f

        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.alpha = if (active) 255 else 60
        c.drawCircle(cx, cy, width / 2f, p)

        val ic = if (active) 0xFF1A1A1A.toInt() else Color.WHITE
        p.color = ic
        if (kind == TORCH) {
            rect.set(cx - 9 * u, cy - 18 * u, cx + 9 * u, cy - 10 * u)
            c.drawRoundRect(rect, 2 * u, 2 * u, p)
            path.reset()
            path.moveTo(cx - 9 * u, cy - 10 * u)
            path.lineTo(cx + 9 * u, cy - 10 * u)
            path.lineTo(cx + 6 * u, cy - 4 * u)
            path.lineTo(cx - 6 * u, cy - 4 * u)
            path.close()
            c.drawPath(path, p)
            rect.set(cx - 6 * u, cy - 4 * u, cx + 6 * u, cy + 18 * u)
            c.drawRoundRect(rect, 3 * u, 3 * u, p)
        } else {
            rect.set(cx - 14 * u, cy - 8 * u, cx + 14 * u, cy + 13 * u)
            c.drawRoundRect(rect, 5 * u, 5 * u, p)
            rect.set(cx - 6 * u, cy - 12.5f * u, cx + 6 * u, cy - 7 * u)
            c.drawRoundRect(rect, 2 * u, 2 * u, p)
            p.color = Color.argb(170, 40, 20, 80)
            c.drawCircle(cx, cy + 2.5f * u, 6.5f * u, p)
            p.color = Color.argb(150, 255, 255, 255)
            c.drawCircle(cx, cy + 2.5f * u, 3f * u, p)
        }
    }
}
