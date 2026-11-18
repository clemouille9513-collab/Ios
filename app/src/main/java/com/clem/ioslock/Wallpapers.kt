package com.clem.ioslock

class WallPreset(val name: String, val colors: IntArray, val glow1: Int, val glow2: Int)

object Wallpapers {
    val presets = listOf(
        WallPreset("Aurore", intArrayOf(0xFF16215F.toInt(), 0xFF5B34C9.toInt(), 0xFFD9468A.toInt(), 0xFFFF8F5A.toInt()), 0x99FFB36B.toInt(), 0x8860A5FA.toInt()),
        WallPreset("Océan", intArrayOf(0xFF031B34.toInt(), 0xFF0B4F8A.toInt(), 0xFF1F9BC9.toInt(), 0xFF7EE0D3.toInt()), 0x9962E3E0.toInt(), 0x887C6BFF.toInt()),
        WallPreset("Coucher de soleil", intArrayOf(0xFF2B1055.toInt(), 0xFFB5179E.toInt(), 0xFFFF5D5D.toInt(), 0xFFFFC857.toInt()), 0x99FFD166.toInt(), 0x88FF5FA2.toInt()),
        WallPreset("Nuit", intArrayOf(0xFF000000.toInt(), 0xFF0B1030.toInt(), 0xFF1B2A6B.toInt(), 0xFF3B2F8F.toInt()), 0x666A7BFF.toInt(), 0x558A4BFF.toInt()),
        WallPreset("Menthe", intArrayOf(0xFF063B3A.toInt(), 0xFF0E8A6F.toInt(), 0xFF4FD1A1.toInt(), 0xFFD6F5A3.toInt()), 0x99B7F58B.toInt(), 0x8840E0D0.toInt()),
        WallPreset("Rose", intArrayOf(0xFF4A1259.toInt(), 0xFFC2399E.toInt(), 0xFFFF7EB6.toInt(), 0xFFFFD3B6.toInt()), 0x99FFC1E3.toInt(), 0x88C77DFF.toInt()),
        WallPreset("Lavande", intArrayOf(0xFF1D1B4F.toInt(), 0xFF5B5BD6.toInt(), 0xFF9F8CFF.toInt(), 0xFFE0C3FF.toInt()), 0x99C9B6FF.toInt(), 0x8873A7FF.toInt()),
        WallPreset("Graphite", intArrayOf(0xFF0E0E10.toInt(), 0xFF26272C.toInt(), 0xFF4A4C55.toInt(), 0xFF7B7E8A.toInt()), 0x55FFFFFF.toInt(), 0x44B0B8D0.toInt())
    )

    val clockColors = listOf(
        "Blanc" to 0xF2FFFFFF.toInt(),
        "Blanc doux" to 0xB3FFFFFF.toInt(),
        "Rose" to 0xFFFFC2DE.toInt(),
        "Bleu clair" to 0xFFBFE3FF.toInt(),
        "Jaune" to 0xFFFFE9A6.toInt(),
        "Vert menthe" to 0xFFC4F5DD.toInt()
    )

    val clockWeights = listOf("Fin" to 300, "Normal" to 400, "Moyen" to 500, "Gras" to 700, "Très gras" to 800)
}
