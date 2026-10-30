package com.jcbc03.ioslauncher

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var cols: Int get() = p.getInt("cols", 4); set(v) = p.edit().putInt("cols", v).apply()
    var rows: Int get() = p.getInt("rows", 6); set(v) = p.edit().putInt("rows", v).apply()
    var iconPack: String? get() = p.getString("iconPack", null); set(v) = p.edit().putString("iconPack", v).apply()
    var hidden: Set<String> get() = p.getStringSet("hidden", emptySet())!!; set(v) = p.edit().putStringSet("hidden", v).apply()
    var badges: Boolean get() = p.getBoolean("badges", true); set(v) = p.edit().putBoolean("badges", v).apply()
    var onboarded: Boolean get() = p.getBoolean("onboarded", false); set(v) = p.edit().putBoolean("onboarded", v).apply()
    var dockBlur: Boolean get() = p.getBoolean("dockBlur", true); set(v) = p.edit().putBoolean("dockBlur", v).apply()
}
