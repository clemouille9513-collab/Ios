package com.clem.ioslock

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("ioslock", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun code(c: Context): String = sp(c).getString("code", "") ?: ""
    fun setCode(c: Context, v: String) = sp(c).edit().putString("code", v).apply()
}
