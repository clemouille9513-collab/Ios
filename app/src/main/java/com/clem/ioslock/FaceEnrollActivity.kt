package com.clem.ioslock

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView

/** Enregistre ton visage : 25 images de face, puis on garde la moyenne. */
class FaceEnrollActivity : ComponentActivity() {

    private val target = 25
    private val face = FaceAuth(this, this)
    private val samples = ArrayList<FloatArray>()
    private var done = false
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private lateinit var preview: PreviewView

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            begin()
        } else {
            Toast.makeText(this, "Sans la caméra, impossible d'enregistrer ton visage", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        preview = PreviewView(this)
        preview.scaleType = PreviewView.ScaleType.FILL_CENTER
        root.addView(
            preview,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        status = TextView(this)
        status.text = "Place ton visage bien en face, dans la lumière"
        status.setTextColor(Color.WHITE)
        status.textSize = 18f
        status.gravity = Gravity.CENTER
        status.setPadding(dp(24), 0, dp(24), 0)
        root.addView(
            status,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(90)
            }
        )

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = target
        root.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(50)
                leftMargin = dp(30)
                rightMargin = dp(30)
            }
        )
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            begin()
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun begin() {
        face.start(preview) { v -> onSample(v) }
    }

    private fun onSample(v: FloatArray) {
        if (done) return
        samples.add(v)
        bar.progress = samples.size
        status.text = "Ne bouge pas... ${samples.size}/$target"
        if (samples.size >= target) {
            done = true
            face.stop()
            val n = samples[0].size
            val avg = FloatArray(n)
            for (s in samples) {
                for (i in 0 until n) avg[i] += s[i] / samples.size
            }
            Prefs.setFace(this, avg)
            Toast.makeText(this, "Visage enregistré", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        face.release()
        super.onDestroy()
    }
}
