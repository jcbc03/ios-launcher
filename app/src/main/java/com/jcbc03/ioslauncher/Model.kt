package com.jcbc03.ioslauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject

const val ICON_PX = 168

class AppInfo(val key: String, val label: String, val pkg: String, val category: String, private val ctx: Context, private val pack: IconPack?) {
    val component: ComponentName get() = ComponentName.unflattenFromString(key)!!
    val icon: ImageBitmap by lazy {
        val packed = pack?.drawableFor(component)
        val d = packed ?: try { ctx.packageManager.getActivityIcon(component) } catch (e: Exception) { ctx.packageManager.defaultActivityIcon }
        renderIcon(d, packed != null).asImageBitmap()
    }
}

/** Render an icon full-bleed (unmasked) so our squircle clip is the only mask. */
fun renderIcon(d: Drawable, fromPack: Boolean, size: Int = ICON_PX): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (!fromPack && d is AdaptiveIconDrawable) {
        val inset = size / 4
        d.background?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
        d.foreground?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
    } else if (!fromPack) {
        c.drawColor(android.graphics.Color.WHITE)
        val p = size / 8
        d.setBounds(p, p, size - p, size - p); d.draw(c)
    } else {
        d.setBounds(0, 0, size, size); d.draw(c)
    }
    return bmp
}

sealed class Item {
    data class App(val key: String) : Item()
    data class Folder(val name: String, val keys: List<String>) : Item()
}

fun Item.keys(): List<String> = when (this) { is Item.App -> listOf(key); is Item.Folder -> keys }

fun loadApps(ctx: Context, pack: IconPack?): List<AppInfo> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val ai = it.activityInfo
            AppInfo(ComponentName(ai.packageName, ai.name).flattenToString(), it.loadLabel(pm).toString(),
                ai.packageName, categoryOf(ai.applicationInfo), ctx, pack)
        }.sortedBy { it.label.lowercase() }
}

fun categoryOf(a: ApplicationInfo): String = when (if (Build.VERSION.SDK_INT >= 26) a.category else -1) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
    ApplicationInfo.CATEGORY_IMAGE -> "Photo & Video"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "Information & Reading"
    ApplicationInfo.CATEGORY_MAPS -> "Travel"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    else -> if (a.flags and ApplicationInfo.FLAG_SYSTEM != 0) "Utilities" else "Other"
}

/** Re-flow items into pages of [perPage]; pure, unit-tested. */
fun reflow(pages: List<List<Item>>, perPage: Int): List<List<Item>> =
    pages.flatten().chunked(perPage).ifEmpty { listOf(emptyList()) }

data class Layout(val pages: List<List<Item>>, val dock: List<String>, val widgets: List<Int>)

fun layoutToJson(l: Layout): String {
    val pa = JSONArray()
    l.pages.forEach { page ->
        val arr = JSONArray()
        page.forEach { item ->
            arr.put(when (item) {
                is Item.App -> JSONObject().put("app", item.key)
                is Item.Folder -> JSONObject().put("folder", item.name).put("keys", JSONArray(item.keys))
            })
        }
        pa.put(arr)
    }
    return JSONObject().put("pages", pa).put("dock", JSONArray(l.dock)).put("widgets", JSONArray(l.widgets)).toString()
}

fun layoutFromJson(s: String): Layout? = try {
    val o = JSONObject(s)
    val pa = o.getJSONArray("pages")
    val pages = (0 until pa.length()).map { p ->
        val arr = pa.getJSONArray(p)
        (0 until arr.length()).map { i ->
            val it = arr.getJSONObject(i)
            if (it.has("folder")) {
                val k = it.getJSONArray("keys")
                Item.Folder(it.getString("folder"), (0 until k.length()).map { j -> k.getString(j) })
            } else Item.App(it.getString("app"))
        }
    }
    val d = o.getJSONArray("dock")
    val w = o.optJSONArray("widgets") ?: JSONArray()
    Layout(pages, (0 until d.length()).map { d.getString(it) }, (0 until w.length()).map { w.getInt(it) })
} catch (e: Exception) { null }
