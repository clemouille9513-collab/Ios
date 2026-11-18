package com.clem.ioslock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Petit symbole d'empreinte digitale : arcs concentriques. */
class FingerprintView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val u = width / 48f
        p.strokeWidth = 2.2f * u
        var i = 0
        var r = 4f
        while (r <= 20f) {
            rect.set(cx - r * u, cy - r * u, cx + r * u, cy + r * u)
            val start = 200f - i * 12f
            val sweep = 250f + (i % 2) * 40f
            c.drawArc(rect, start, sweep, false, p)
            i++
            r += 4f
        }
        c.drawPoint(cx, cy, p)
    }
}

/** Bouton rond de l'appareil photo : se réduit au toucher. */
class ShutterView(ctx: Context, private val onShoot: () -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var down = false

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f * d
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r - 2f * d, p)
        p.style = Paint.Style.FILL
        val inner = (r - 9f * d) * (if (down) 0.86f else 1f)
        c.drawCircle(cx, cy, inner, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                down = false
                invalidate()
                onShoot()
            }
            MotionEvent.ACTION_CANCEL -> {
                down = false
                invalidate()
            }
        }
        return true
    }
}

/** Batterie façon iOS (barre d'état) : remplissage, éclair quand ça charge. */
class BatteryView(ctx: Context) : View(ctx) {
    var level = 100
    var charging = false
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun set(level: Int, charging: Boolean) {
        this.level = level.coerceIn(0, 100)
        this.charging = charging
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val bw = width - 3f * d
        val h = height.toFloat()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = 0x99FFFFFF.toInt()
        r.set(0.6f * d, 0.6f * d, bw - 0.6f * d, h - 0.6f * d)
        c.drawRoundRect(r, 4f * d, 4f * d, p)
        p.style = Paint.Style.FILL
        p.color = 0x99FFFFFF.toInt()
        r.set(bw + 0.8f * d, h * 0.36f, bw + 2.4f * d, h * 0.64f)
        c.drawRoundRect(r, d, d, p)
        p.color = when {
            charging -> 0xFF34D058.toInt()
            level <= 20 -> 0xFFFF453A.toInt()
            else -> Color.WHITE
        }
        val inner = 2.2f * d
        r.set(inner, inner, inner + (bw - 2 * inner) * level / 100f, h - inner)
        c.drawRoundRect(r, 2f * d, 2f * d, p)
    }
}
