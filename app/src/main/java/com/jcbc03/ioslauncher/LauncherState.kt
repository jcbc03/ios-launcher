package com.jcbc03.ioslauncher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LauncherState(private val ctx: Context) {
    private val store = LayoutStore(ctx)
    var apps by mutableStateOf<Map<String, AppInfo>>(emptyMap()); private set
    var pages by mutableStateOf<List<List<Item>>>(emptyList()); private set
    var dock by mutableStateOf<List<String>>(emptyList()); private set
    var editing by mutableStateOf(false)

    fun reload() {
        val list = loadApps(ctx)
        val old = apps
        apps = list.associateBy { it.key }.mapValues { old[it.key] ?: it.value }
        val saved = store.load()
        if (saved == null) {
            val prefs = listOf("dialer", "phone", "messag", "chrome", "browser", "camera")
            val d = prefs.mapNotNull { p -> list.firstOrNull { it.pkg.contains(p) }?.key }.distinct().take(4)
            dock = d
            pages = list.filter { it.key !in d }.map { Item.App(it.key) }.chunked(PER_PAGE).ifEmpty { listOf(emptyList()) }
            save()
        } else {
            val valid = apps.keys
            dock = saved.second.filter { it in valid }
            val placed = mutableSetOf<String>()
            pages = saved.first.map { page ->
                page.mapNotNull { item ->
                    when (item) {
                        is Item.App -> if (item.key in valid) { placed += item.key; item } else null
                        is Item.Folder -> item.keys.filter { it in valid }.also { placed += it }
                            .let { if (it.isEmpty()) null else item.copy(keys = it) }
                    }
                }
            }.ifEmpty { listOf(emptyList()) }
            // New apps that were never seen go to the last page (removed ones are remembered).
            val known = ctx.getSharedPreferences("layout", 0).getStringSet("known", null) ?: valid
            val fresh = valid - known - placed - dock.toSet()
            if (fresh.isNotEmpty()) addToEnd(fresh.sortedBy { apps[it]?.label?.lowercase() })
        }
        ctx.getSharedPreferences("layout", 0).edit().putStringSet("known", apps.keys.toSet()).apply()
    }

    private fun addToEnd(keys: List<String>) {
        val m = pages.map { it.toMutableList() }.toMutableList()
        keys.forEach { k ->
            if (m.isEmpty() || m.last().size >= PER_PAGE) m += mutableListOf<Item>()
            m.last() += Item.App(k)
        }
        pages = m; save()
    }

    fun save() = store.save(pages, dock)

    fun remove(page: Int, index: Int) {
        pages = pages.mapIndexed { p, l -> if (p == page) l.filterIndexed { i, _ -> i != index } else l }
            .let { ps -> ps.filterIndexed { i, l -> l.isNotEmpty() || i == 0 } }
        save()
    }

    fun removeFromDock(key: String) { dock = dock - key; save() }

    /** Drop item [from] onto item [to] on the same page: makes/extends a folder. */
    fun merge(page: Int, from: Int, to: Int) {
        if (from == to) return
        val l = pages[page].toMutableList()
        val a = l[from]; val b = l[to]
        val keysA = when (a) { is Item.App -> listOf(a.key); is Item.Folder -> a.keys }
        val merged = when (b) {
            is Item.Folder -> b.copy(keys = b.keys + keysA)
            is Item.App -> Item.Folder(suggestName(b.key, keysA.first()), listOf(b.key) + keysA)
        }
        l[to] = merged; l.removeAt(from)
        pages = pages.toMutableList().also { it[page] = l }; save()
    }

    fun move(page: Int, from: Int, to: Int) {
        val l = pages[page].toMutableList()
        val it = l.removeAt(from); l.add(to.coerceIn(0, l.size), it)
        pages = pages.toMutableList().also { p -> p[page] = l }; save()
    }

    fun renameFolder(page: Int, index: Int, name: String) {
        val f = pages[page][index] as? Item.Folder ?: return
        pages = pages.toMutableList().also { p -> p[page] = p[page].toMutableList().also { it[index] = f.copy(name = name) } }; save()
    }

    fun removeFromFolder(page: Int, index: Int, key: String) {
        val f = pages[page][index] as? Item.Folder ?: return
        val rest = f.keys - key
        val l = pages[page].toMutableList()
        when (rest.size) { 0 -> l.removeAt(index); 1 -> l[index] = Item.App(rest[0]); else -> l[index] = f.copy(keys = rest) }
        pages = pages.toMutableList().also { it[page] = l }; save()
    }

    private fun suggestName(a: String, b: String): String {
        val ca = apps[a]?.category; val cb = apps[b]?.category
        return if (ca != null && ca == cb && ca != "Other") ca else "Folder"
    }
}
