package com.jcbc03.ioslauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject

class AppInfo(val key: String, val label: String, val pkg: String, val category: String, private val ctx: Context) {
    val icon: ImageBitmap by lazy {
        val cn = ComponentName.unflattenFromString(key)!!
        val d = try { ctx.packageManager.getActivityIcon(cn) } catch (e: Exception) { ctx.packageManager.defaultActivityIcon }
        d.toBitmap(160, 160).asImageBitmap()
    }
}

sealed class Item {
    data class App(val key: String) : Item()
    data class Folder(val name: String, val keys: List<String>) : Item()
}

const val COLS = 4
const val ROWS = 6
const val PER_PAGE = COLS * ROWS

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val ai = it.activityInfo
            AppInfo(ComponentName(ai.packageName, ai.name).flattenToString(), it.loadLabel(pm).toString(),
                ai.packageName, categoryOf(ai.applicationInfo), ctx)
        }.sortedBy { it.label.lowercase() }
}

fun categoryOf(a: ApplicationInfo): String {
    if (a.flags and ApplicationInfo.FLAG_SYSTEM != 0 && android.os.Build.VERSION.SDK_INT < 26) return "Utilities"
    return when (if (android.os.Build.VERSION.SDK_INT >= 26) a.category else -1) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
        ApplicationInfo.CATEGORY_IMAGE -> "Photo & Video"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "Information & Reading"
        ApplicationInfo.CATEGORY_MAPS -> "Travel"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        else -> if (a.flags and ApplicationInfo.FLAG_SYSTEM != 0) "Utilities" else "Other"
    }
}

class LayoutStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("layout", Context.MODE_PRIVATE)

    fun load(): Pair<List<List<Item>>, List<String>>? {
        val s = prefs.getString("v1", null) ?: return null
        return try {
            val o = JSONObject(s)
            val pages = o.getJSONArray("pages").let { pa ->
                (0 until pa.length()).map { p ->
                    val arr = pa.getJSONArray(p)
                    (0 until arr.length()).map { i ->
                        val it = arr.getJSONObject(i)
                        if (it.has("folder")) {
                            val k = it.getJSONArray("keys")
                            Item.Folder(it.getString("folder"), (0 until k.length()).map { k.getString(it) })
                        } else Item.App(it.getString("app"))
                    }
                }
            }
            val d = o.getJSONArray("dock")
            pages to (0 until d.length()).map { d.getString(it) }
        } catch (e: Exception) { null }
    }

    fun save(pages: List<List<Item>>, dock: List<String>) {
        val pa = JSONArray()
        pages.forEach { page ->
            val arr = JSONArray()
            page.forEach { item ->
                arr.put(when (item) {
                    is Item.App -> JSONObject().put("app", item.key)
                    is Item.Folder -> JSONObject().put("folder", item.name).put("keys", JSONArray(item.keys))
                })
            }
            pa.put(arr)
        }
        prefs.edit().putString("v1", JSONObject().put("pages", pa).put("dock", JSONArray(dock)).toString()).apply()
    }
}
