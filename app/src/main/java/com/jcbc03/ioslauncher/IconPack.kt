package com.jcbc03.ioslauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** ADW / Nova / Apex compatible icon pack (appfilter.xml). */
class IconPack(private val ctx: Context, val pkg: String) {
    private val res: Resources = ctx.packageManager.getResourcesForApplication(pkg)
    private val map: Map<String, String> = parse()

    private fun parse(): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val id = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) res.getXml(id) else
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "utf-8") }
            val raw = HashMap<String, String>()
            parseAppFilter(parser, raw)
            raw.forEach { (k, v) -> out[ComponentName.unflattenFromString(k)?.flattenToString() ?: k] = v }
        } catch (_: Exception) {}
        return out
    }

    fun drawableFor(cn: ComponentName): Drawable? {
        val name = map[cn.flattenToString()] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: return null
        return try { res.getDrawable(id, null) } catch (_: Exception) { null }
    }

    companion object {
        private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME")

        fun installed(ctx: Context): List<Pair<String, String>> {
            val pm = ctx.packageManager
            return ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), 0) }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }
        }

        fun load(ctx: Context, pkg: String?): IconPack? = pkg?.let { try { IconPack(ctx, it) } catch (_: Exception) { null } }
    }
}

/** Parses `<item component="ComponentInfo{pkg/cls}" drawable="x"/>`. */
fun parseAppFilter(parser: XmlPullParser, out: MutableMap<String, String>) {
    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG && parser.name == "item") {
            val comp = parser.getAttributeValue(null, "component")
            val dr = parser.getAttributeValue(null, "drawable")
            if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}"))
                out[comp.substring(14, comp.length - 1)] = dr
        }
        ev = parser.next()
    }
}
