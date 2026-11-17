package com.clem.ioslock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var statusTv: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val grey = 0xFFB5B5C8.toInt()
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFF0B0B14.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(22), dp(56), dp(22), dp(40))
        scroll.addView(col)

        fun lp(top: Int) = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

        fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) {
            val tv = TextView(this)
            tv.text = t
            tv.textSize = size
            tv.setTextColor(color)
            if (bold) tv.setTypeface(null, Typeface.BOLD)
            col.addView(tv, lp(top))
        }

        fun button(t: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = t
            b.isAllCaps = false
            b.setOnClickListener { onClick() }
            col.addView(b, lp(8))
        }

        fun slider(label: String, unit: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
            val tv = TextView(this)
            tv.text = "$label : $value$unit"
            tv.setTextColor(Color.WHITE)
            tv.textSize = 14f
            col.addView(tv, lp(12))
            val sb = SeekBar(this)
            sb.max = max - min
            sb.progress = value - min
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + min
                    tv.text = "$label : $v$unit"
                    if (fromUser) onChange(v)
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb, lp(2))
        }

        text("Écran de verrouillage iOS", 26f, Color.WHITE, true)
        text("Le look de l'écran de verrouillage de l'iPhone, sur ton Samsung.", 14f, grey, false, 6)

        // ------------------------------------------------------------ 1. Service
        text("1. Service d'accessibilité (obligatoire)", 16f, Color.WHITE, true, 28)
        text(
            "C'est lui qui pose l'écran par-dessus tout, avant même que l'écran se rallume, et qui bloque tout le reste. Il ne lit rien de ce qui s'affiche.",
            13f, grey, false, 4
        )
        statusTv = TextView(this)
        statusTv.textSize = 15f
        statusTv.setTypeface(null, Typeface.BOLD)
        col.addView(statusTv, lp(8))
        button("Ouvrir les réglages d'accessibilité") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        text(
            "Cherche « Écran iOS » dans les applis installées et active-le. Si Android refuse (« paramètre restreint ») : ouvre d'abord les infos de l'appli, touche les trois points en haut à droite, puis « Autoriser les paramètres restreints ».",
            12f, grey, false, 6
        )
        button("Ouvrir les infos de l'appli") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        button("Autoriser la caméra (visage et photos)") {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 2)
        }
        button("Désactiver l'optimisation de batterie") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        // ------------------------------------------------------------ 2. Code
        text("2. Code", 16f, Color.WHITE, true, 28)
        text(
            "Choisis un code de 4 à 6 chiffres : c'est lui qui verrouille ton téléphone. Sans code, un simple balayage déverrouille.",
            13f, grey, false, 4
        )
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.filters = arrayOf(InputFilter.LengthFilter(6))
        et.setText(Prefs.code(this))
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(grey)
        et.hint = "Code"
        col.addView(et, lp(8))
        button("Enregistrer le code") {
            val c = et.text.toString()
            if (c.isEmpty() || c.length in 4..6) {
                Prefs.setCode(this, c)
                toast("Enregistré")
            } else {
                toast("Entre 4 à 6 chiffres")
            }
        }

        // ------------------------------------------------------------ 3. Visage
        text("3. Déverrouillage par le visage", 16f, Color.WHITE, true, 28)
        text(
            "La caméra avant cherche ton visage quand tu allumes l'écran. Si elle te reconnaît, le cadenas s'ouvre et il suffit de balayer. Reconnaissance simple : moins sûre que le Face ID d'Apple. Le code reste le secours.",
            13f, grey, false, 4
        )
        button("Enregistrer mon visage") {
            if (Prefs.code(this).isEmpty()) {
                toast("Définis d'abord un code (étape 2)")
            } else {
                startActivity(Intent(this, FaceEnrollActivity::class.java))
            }
        }
        val faceSw = Switch(this)
        faceSw.text = "Déverrouiller avec le visage"
        faceSw.setTextColor(Color.WHITE)
        faceSw.isChecked = Prefs.faceEnabled(this)
        faceSw.setOnCheckedChangeListener { b, on ->
            if (on) {
                if (Prefs.code(this).isEmpty()) {
                    toast("Définis d'abord un code (étape 2)")
                    b.isChecked = false
                } else if (Prefs.face(this) == null) {
                    toast("Enregistre d'abord ton visage")
                    b.isChecked = false
                } else {
                    Prefs.setFaceEnabled(this, true)
                }
            } else {
                Prefs.setFaceEnabled(this, false)
            }
        }
        col.addView(faceSw, lp(10))
        slider("Tolérance du visage", " ‰", 20, 120, Prefs.faceTolRaw(this)) { Prefs.setFaceTolRaw(this, it) }
        text(
            "Plus la tolérance est basse, plus c'est strict. Si ton visage n'est pas reconnu, monte-la un peu ; si quelqu'un d'autre passe, baisse-la.",
            12f, grey, false, 4
        )

        // ------------------------------------------------------------ 4. Empreinte
        text("4. Empreinte digitale", 16f, Color.WHITE, true, 28)
        text(
            "Android efface les empreintes dès qu'on enlève le verrouillage Samsung, et aucune appli ne peut lire le capteur sans lui. Deux façons de faire :\n\n" +
                "• Sans verrouillage Samsung (réglage « Aucun ») : code et visage seulement.\n" +
                "• Avec un verrouillage Samsung (code + empreinte) : touche le capteur, l'écran iOS s'ouvre aussi. Le code ou le visage de l'appli laisse alors l'écran Samsung derrière, qu'il faut déverrouiller à son tour.",
            13f, grey, false, 4
        )

        // ------------------------------------------------------------ 5. Apparence
        text("5. Apparence", 16f, Color.WHITE, true, 28)
        button("Choisir le fond d'écran") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            startActivityForResult(i, 42)
        }
        button("Remettre le fond par défaut") {
            File(filesDir, "wallpaper.jpg").delete()
            toast("Fond par défaut remis")
        }
        slider("Hauteur de l'heure", " dp", 20, 260, Prefs.clockTop(this)) { Prefs.setClockTop(this, it) }
        slider("Taille de l'heure", " sp", 50, 120, Prefs.clockSize(this)) { Prefs.setClockSize(this, it) }

        // ------------------------------------------------------------ 6. Activer
        text("6. Activer", 16f, Color.WHITE, true, 28)
        val sw = Switch(this)
        sw.text = "Activer l'écran de verrouillage iOS"
        sw.setTextColor(Color.WHITE)
        sw.isChecked = Prefs.enabled(this)
        sw.setOnCheckedChangeListener { b, on ->
            if (on) {
                if (LockAccessibilityService.instance == null) {
                    toast("Active d'abord le service d'accessibilité (étape 1)")
                    b.isChecked = false
                } else {
                    Prefs.setEnabled(this, true)
                }
            } else {
                Prefs.setEnabled(this, false)
            }
        }
        col.addView(sw, lp(10))

        button("Voir l'aperçu de l'écran") {
            val s = LockAccessibilityService.instance
            if (s == null) {
                toast("Active d'abord le service d'accessibilité (étape 1)")
            } else {
                s.showPreview()
            }
        }

        text(
            "Si tu oublies ton code : redémarre le téléphone en mode sans échec (maintiens le bouton d'éteignement, puis appuie longuement sur « Éteindre » et valide). L'appli est alors désactivée et tu peux la désinstaller.",
            12f, grey, false, 24
        )

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (LockAccessibilityService.instance != null) {
            statusTv.text = "✓ Service actif"
            statusTv.setTextColor(0xFF4ADE80.toInt())
        } else {
            statusTv.text = "✗ Service inactif"
            statusTv.setTextColor(0xFFF87171.toInt())
        }
    }

    /** Copie l'image choisie en la redimensionnant et en la remettant droite. */
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 42 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 3200) sample *= 2
            val o = BitmapFactory.Options()
            o.inSampleSize = sample
            val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            if (decoded == null) {
                toast("Impossible de lire cette image")
                return
            }
            var bmp: Bitmap = decoded
            val orientation = contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val deg = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg != 0f) {
                val m = Matrix()
                m.postRotate(deg)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            FileOutputStream(File(filesDir, "wallpaper.jpg")).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            toast("Fond d'écran enregistré")
        } catch (e: Exception) {
            toast("Impossible de lire cette image")
        }
    }
}
