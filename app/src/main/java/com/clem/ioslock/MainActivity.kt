package com.clem.ioslock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

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

        text("Écran de verrouillage iOS", 26f, Color.WHITE, true)
        text("Le look de l'écran de verrouillage de l'iPhone, sur ton Samsung.", 14f, grey, false, 6)

        text("1. Autorisations", 16f, Color.WHITE, true, 28)
        button("Autoriser l'affichage par-dessus les autres applis") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        button("Autoriser les notifications") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        button("Désactiver l'optimisation de batterie") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        text(
            "Dans les réglages Samsung, mets aussi l'appli en « Jamais en veille » (Batterie > Limites d'utilisation en arrière-plan).",
            12f, grey, false, 6
        )

        text("2. Code (facultatif)", 16f, Color.WHITE, true, 28)
        text(
            "Laisse vide pour utiliser le verrouillage de ton Samsung (empreinte, code...). Sinon, choisis un code de 4 à 6 chiffres.",
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

        text("3. Activer", 16f, Color.WHITE, true, 28)
        val sw = Switch(this)
        sw.text = "Activer l'écran de verrouillage iOS"
        sw.setTextColor(Color.WHITE)
        sw.isChecked = Prefs.enabled(this)
        sw.setOnCheckedChangeListener { b, on ->
            if (on) {
                if (!Settings.canDrawOverlays(this)) {
                    toast("Autorise d'abord l'affichage par-dessus les autres applis")
                    b.isChecked = false
                } else {
                    Prefs.setEnabled(this, true)
                    startForegroundService(Intent(this, LockService::class.java))
                }
            } else {
                Prefs.setEnabled(this, false)
                stopService(Intent(this, LockService::class.java))
            }
        }
        col.addView(sw, lp(10))

        button("Voir l'aperçu de l'écran") {
            startActivity(Intent(this, LockActivity::class.java).putExtra("preview", true))
        }

        setContentView(scroll)
    }
}
