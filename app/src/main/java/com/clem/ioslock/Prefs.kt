package com.clem.ioslock

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("ioslock", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun code(c: Context): String = sp(c).getString("code", "") ?: ""
    fun setCode(c: Context, v: String) = sp(c).edit().putString("code", v).apply()

    fun faceEnabled(c: Context): Boolean = sp(c).getBoolean("faceEnabled", false)
    fun setFaceEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("faceEnabled", v).apply()

    fun face(c: Context): FloatArray? {
        val s = sp(c).getString("face", null) ?: return null
        return try {
            s.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }

    fun setFace(c: Context, v: FloatArray?) {
        val e = sp(c).edit()
        if (v == null) e.remove("face") else e.putString("face", v.joinToString(","))
        e.apply()
    }

    /** Tolérance de la reconnaissance, en dixièmes de pour cent (55 = 5,5 %). */
    fun faceTolRaw(c: Context): Int = sp(c).getInt("faceTol", 55)
    fun setFaceTolRaw(c: Context, v: Int) = sp(c).edit().putInt("faceTol", v).apply()
    fun faceTol(c: Context): Float = faceTolRaw(c) / 1000f

    /** Distance du haut de l'écran jusqu'au cadenas, en dp. */
    fun clockTop(c: Context): Int = sp(c).getInt("clockTop", 100)
    fun setClockTop(c: Context, v: Int) = sp(c).edit().putInt("clockTop", v).apply()

    /** Taille de l'heure, en sp. */
    fun clockSize(c: Context): Int = sp(c).getInt("clockSize", 84)
    fun setClockSize(c: Context, v: Int) = sp(c).edit().putInt("clockSize", v).apply()

    fun wallPreset(c: Context): Int = sp(c).getInt("wallPreset", 0)
    fun setWallPreset(c: Context, v: Int) = sp(c).edit().putInt("wallPreset", v).apply()

    fun clockColor(c: Context): Int = sp(c).getInt("clockColor", 0)
    fun setClockColor(c: Context, v: Int) = sp(c).edit().putInt("clockColor", v).apply()

    fun clockWeight(c: Context): Int = sp(c).getInt("clockWeight", 2)
    fun setClockWeight(c: Context, v: Int) = sp(c).edit().putInt("clockWeight", v).apply()

    fun parallax(c: Context): Boolean = sp(c).getBoolean("parallax", true)
    fun setParallax(c: Context, v: Boolean) = sp(c).edit().putBoolean("parallax", v).apply()

    fun blur(c: Context): Boolean = sp(c).getBoolean("blur", true)
    fun setBlur(c: Context, v: Boolean) = sp(c).edit().putBoolean("blur", v).apply()

    /** Mode simple : sans service d'accessibilité (superposition + service au premier plan). */
    fun simpleMode(c: Context): Boolean = sp(c).getBoolean("simpleMode", false)
    fun setSimpleMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("simpleMode", v).apply()
}
