package com.clem.ioslock

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

object FaceMatch {
    /** Écart moyen (en proportion) entre deux empreintes de visage. Plus c'est petit, plus ça se ressemble. */
    fun distance(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 1f
        var s = 0f
        for (i in 0 until n) {
            s += abs(a[i] - b[i]) / b[i]
        }
        return s / n
    }
}

/**
 * Lit la caméra frontale, repère les points du visage (yeux, nez, bouche, joues)
 * et en tire une empreinte faite de rapports de distances.
 * Reconnaissance simple : moins fiable que le Face ID d'Apple.
 */
class FaceAuth(private val act: ComponentActivity) {

    private var detector: FaceDetector? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile
    private var running = false

    @Volatile
    private var busy = false
    private var onVector: ((FloatArray) -> Unit)? = null

    private fun det(): FaceDetector {
        val d = detector
        if (d != null) return d
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        val n = FaceDetection.getClient(opts)
        detector = n
        return n
    }

    fun start(previewView: PreviewView?, callback: (FloatArray) -> Unit) {
        stop()
        running = true
        busy = false
        onVector = callback
        val fut = ProcessCameraProvider.getInstance(act)
        fut.addListener(Runnable {
            if (!running) return@Runnable
            val p = fut.get()
            provider = p
            val ia = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            ia.setAnalyzer(exec) { proxy -> analyze(proxy) }
            analysis = ia
            try {
                p.unbindAll()
                if (previewView != null) {
                    val pv = Preview.Builder().build()
                    pv.setSurfaceProvider(previewView.surfaceProvider)
                    p.bindToLifecycle(act, CameraSelector.DEFAULT_FRONT_CAMERA, pv, ia)
                } else {
                    p.bindToLifecycle(act, CameraSelector.DEFAULT_FRONT_CAMERA, ia)
                }
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(act))
    }

    fun stop() {
        running = false
        onVector = null
        try {
            analysis?.clearAnalyzer()
            provider?.unbindAll()
        } catch (e: Exception) {
        }
        analysis = null
    }

    fun release() {
        stop()
        detector?.close()
        detector = null
    }

    private fun analyze(proxy: ImageProxy) {
        if (!running || busy) {
            proxy.close()
            return
        }
        busy = true
        val bmp = try {
            proxy.toBitmap()
        } catch (e: Exception) {
            null
        }
        val rot = proxy.imageInfo.rotationDegrees
        proxy.close()
        if (bmp == null) {
            busy = false
            return
        }
        try {
            det().process(InputImage.fromBitmap(bmp, rot))
                .addOnSuccessListener { faces -> handle(faces) }
                .addOnCompleteListener { busy = false }
        } catch (e: Exception) {
            busy = false
        }
    }

    private fun handle(faces: List<Face>) {
        if (!running) return
        val f = faces.maxByOrNull { it.boundingBox.width() } ?: return
        if (abs(f.headEulerAngleY) > 15f || abs(f.headEulerAngleZ) > 15f || abs(f.headEulerAngleX) > 15f) return
        val types = intArrayOf(
            FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.LEFT_CHEEK, FaceLandmark.RIGHT_CHEEK
        )
        val xs = FloatArray(types.size)
        val ys = FloatArray(types.size)
        for (i in types.indices) {
            val l = f.getLandmark(types[i]) ?: return
            xs[i] = l.position.x
            ys[i] = l.position.y
        }
        val eye = hypot(xs[0] - xs[1], ys[0] - ys[1])
        if (eye < 20f) return
        val v = ArrayList<Float>()
        for (i in types.indices) {
            for (j in i + 1 until types.size) {
                if (i == 0 && j == 1) continue
                v.add(hypot(xs[i] - xs[j], ys[i] - ys[j]) / eye)
            }
        }
        onVector?.invoke(v.toFloatArray())
    }
}
