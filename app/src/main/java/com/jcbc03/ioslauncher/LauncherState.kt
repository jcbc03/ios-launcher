package com.jcbc03.ioslauncher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val DOCK = -1
const val DOCK_MAX = 4

class LauncherState(private val ctx: Context) {
    val prefs = Prefs(ctx)
    private val lp = ctx.getSharedPreferences("layout", Context.MODE_PRIVATE)
    var allApps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var apps by mutableStateOf<Map<String, AppInfo>>(emptyMap()); private set // visible (not hidden)
    var pages by mutableStateOf<List<List<Item>>>(emptyList()); private set
    var dock by mutableStateOf<List<String>>(emptyList()); private set
    var widgets by mutableStateOf<List<Int>>(emptyList()); private set
    var cols by mutableStateOf(prefs.cols); private set
    var rows by mutableStateOf(prefs.rows); private set
    var editing by mutableStateOf(false)
    val perPage get() = cols * rows
    private var packPkg: String? = "\u0000"
    private var pack: IconPack? = null

    fun reload() {
        if (packPkg != prefs.iconPack) { packPkg = prefs.iconPack; pack = IconPack.load(ctx, packPkg); allApps = emptyList() }
        val list = loadApps(ctx, pack)
        val old = allApps.associateBy { it.key }
        allApps = list.map { old[it.key] ?: it }
        val hidden = prefs.hidden
        apps = allApps.filter { it.key !in hidden }.associateBy { it.key }
        val gridChanged = cols != prefs.cols || rows != prefs.rows
        cols = prefs.cols; rows = prefs.rows
        val saved = lp.getString("v1", null)?.let { layoutFromJson(it) }
        if (saved == null) {
            val pref = listOf("dialer", "phone", "messag", "chrome", "browser", "camera")
            val d = pref.mapNotNull { p -> list.firstOrNull { it.pkg.contains(p) }?.key }.distinct().take(DOCK_MAX)
            dock = d; widgets = emptyList()
            pages = apps.values.filter { it.key !in d }.map { Item.App(it.key) }.chunked(perPage).ifEmpty { listOf(emptyList()) }
        } else {
            val valid = apps.keys
            dock = saved.dock.filter { it in valid }
            widgets = saved.widgets
            var ps = saved.pages.map { page ->
                page.mapNotNull { item ->
                    when (item) {
                        is Item.App -> item.takeIf { it.key in valid }
                        is Item.Folder -> item.keys.filter { it in valid }.let { k ->
                            when (k.size) { 0 -> null; 1 -> Item.App(k[0]); else -> item.copy(keys = k) } }
                    }
                }
            }
            if (gridChanged || ps.any { it.size > perPage }) ps = reflow(ps, perPage)
            pages = ps.ifEmpty { listOf(emptyList()) }
            val known = lp.getStringSet("known", null) ?: valid
            val placed = pages.flatten().flatMap { it.keys() }.toSet() + dock
            val fresh = valid - known - placed
            if (fresh.isNotEmpty()) addToHome(fresh.sortedBy { apps[it]?.label?.lowercase() })
        }
        lp.edit().putStringSet("known", allApps.map { it.key }.toSet()).apply()
        save()
    }

    fun save() = lp.edit().putString("v1", layoutToJson(Layout(pages, dock, widgets))).apply()

    fun onHome(key: String) = key in dock || pages.any { p -> p.any { key in it.keys() } }

    fun addToHome(keys: List<String>) {
        val m = pages.map { it.toMutableList() }.toMutableList()
        keys.filter { !onHome(it) }.forEach { k ->
            if (m.isEmpty() || m.last().size >= perPage) m += mutableListOf<Item>()
            m.last() += Item.App(k)
        }
        pages = m; save()
    }

    private fun cleanup(ps: List<List<Item>>) = ps.filterIndexed { i, l -> l.isNotEmpty() || i == 0 }

    fun remove(page: Int, index: Int) {
        pages = cleanup(pages.mapIndexed { p, l -> if (p == page) l.filterIndexed { i, _ -> i != index } else l }); save()
    }

    fun removeKeyEverywhere(key: String) {
        dock = dock - key
        pages = cleanup(pages.map { l -> l.mapNotNull { when (it) {
            is Item.App -> it.takeIf { it.key != key }
            is Item.Folder -> (it.keys - key).let { k -> when (k.size) { 0 -> null; 1 -> Item.App(k[0]); else -> it.copy(keys = k) } }
        } } }); save()
    }

    fun itemAt(c: Int, i: Int): Item? = if (c == DOCK) dock.getOrNull(i)?.let { Item.App(it) } else pages.getOrNull(c)?.getOrNull(i)

    /** Drop [from] onto occupied [to] → folder. Returns false if not allowed. */
    fun merge(fc: Int, fi: Int, tc: Int, ti: Int): Boolean {
        if (fc == tc && fi == ti) return false
        val a = itemAt(fc, fi) ?: return false
        val b = itemAt(tc, ti) ?: return false
        if (tc == DOCK || a is Item.Folder) return false
        val merged = when (b) {
            is Item.Folder -> b.copy(keys = b.keys + a.keys())
            is Item.App -> Item.Folder(suggestName(b.key, (a as Item.App).key), listOf(b.key) + a.keys())
        }
        val ps = pages.map { it.toMutableList() }.toMutableList()
        val d = dock.toMutableList()
        ps[tc][ti] = merged
        if (fc == DOCK) d.removeAt(fi) else ps[fc].removeAt(fi)
        pages = cleanup(ps); dock = d; save(); return true
    }

    /** Move [from] into slot [to] of container [tc] (page or DOCK). */
    fun move(fc: Int, fi: Int, tc: Int, ti: Int): Boolean {
        val item = itemAt(fc, fi) ?: return false
        if (tc == DOCK && item !is Item.App) return false
        if (tc == DOCK && fc != DOCK && dock.size >= DOCK_MAX) return false
        val ps = pages.map { it.toMutableList() }.toMutableList()
        while (tc >= ps.size) ps += mutableListOf<Item>()
        if (tc != DOCK && tc != fc && ps[tc].size >= perPage) return false
        val d = dock.toMutableList()
        if (fc == DOCK) d.removeAt(fi) else ps[fc].removeAt(fi)
        if (tc == DOCK) d.add(ti.coerceIn(0, d.size), (item as Item.App).key)
        else ps[tc].add(ti.coerceIn(0, ps[tc].size), item)
        pages = cleanup(ps); dock = d; save(); return true
    }

    fun addEmptyPage() { pages = pages + listOf(emptyList()) }

    fun renameFolder(page: Int, index: Int, name: String) {
        val f = pages.getOrNull(page)?.getOrNull(index) as? Item.Folder ?: return
        pages = pages.toMutableList().also { p -> p[page] = p[page].toMutableList().also { it[index] = f.copy(name = name) } }; save()
    }

    /** Takes an app out of a folder and places it on the same page (or a new one). */
    fun removeFromFolder(page: Int, index: Int, key: String) {
        val f = pages.getOrNull(page)?.getOrNull(index) as? Item.Folder ?: return
        val rest = f.keys - key
        val l = pages[page].toMutableList()
        when (rest.size) { 1 -> l[index] = Item.App(rest[0]); else -> l[index] = f.copy(keys = rest) }
        pages = pages.toMutableList().also { it[page] = l }
        addToHome(listOf(key))
    }

    fun appInstallTime(pkg: String): Long = ctx.packageManager.getPackageInfo(pkg, 0).firstInstallTime

    fun resetLayout() { lp.edit().remove("v1").apply(); reload() }

    fun addWidget(id: Int) { widgets = widgets + id; save() }
    fun removeWidget(id: Int) { widgets = widgets - id; save() }

    private fun suggestName(a: String, b: String): String {
        val ca = apps[a]?.category; val cb = apps[b]?.category
        return if (ca != null && ca == cb && ca != "Other") ca else "Folder"
    }
}
