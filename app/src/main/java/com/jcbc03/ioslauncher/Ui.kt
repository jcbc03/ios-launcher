package com.jcbc03.ioslauncher

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ICON = 60.dp

data class Slot(val c: Int, val i: Int)

sealed class Overlay {
    data object Search : Overlay()
    data object Control : Overlay()
    data object Notifications : Overlay()
    data object Widgets : Overlay()
    data class Folder(val page: Int, val index: Int) : Overlay()
    data class LibFolder(val name: String, val apps: List<AppInfo>) : Overlay()
    data class Menu(val app: AppInfo?, val slot: Slot?, val rect: Rect) : Overlay()
    data class Remove(val slot: Slot) : Overlay()
}

/** Drag state kept outside composition-heavy paths: bounds are a plain map (no recomposition). */
class DragCtl {
    val bounds = HashMap<Slot, Rect>()
    var dockRect = Rect.Zero
    var source by mutableStateOf<Slot?>(null)
    var item by mutableStateOf<Item?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<Slot?>(null)
    var lastEdgeFlip = 0L
}

@Composable
fun LauncherRoot(act: MainActivity) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Inter)) { Launcher(act) }
}

@Composable
private fun Launcher(act: MainActivity) {
    val s = act.state
    val ctx = LocalContext.current
    val drag = remember { DragCtl() }
    val dragging = drag.source != null
    val homeCount = s.pages.size + if (dragging) 1 else 0
    val pageCount = homeCount + 2 // Today + homes + App Library
    val homeCountRef = rememberUpdatedState(homeCount)
    val pager = rememberPagerState(initialPage = 1) { pageCount }
    val scope = rememberCoroutineScope()
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var onboarding by remember { mutableStateOf(!s.prefs.onboarded) }
    val counts by NotifStore.counts.collectAsState()
    val badgesOn = s.prefs.badges
    val badge: (Item) -> Int = { if (!badgesOn) 0 else it.keys().sumOf { k -> s.apps[k]?.let { a -> counts[a.pkg] } ?: 0 } }
    val wall = act.wallpaper.value

    LaunchedEffect(act.homeSignal.intValue) {
        if (act.homeSignal.intValue == 0) return@LaunchedEffect
        when {
            overlay != null -> overlay = null
            s.editing -> s.editing = false
            else -> pager.animateScrollToPage(1)
        }
    }
    LaunchedEffect(act.resumeTick.intValue) {
        if (act.resumeTick.intValue == 0) return@LaunchedEffect
        act.resumeScale.snapTo(0.9f); act.resumeScale.animateTo(1f, iosSpring)
    }
    LaunchedEffect(overlay) { act.setWindowBlur(overlay != null) }
    BackHandler(overlay != null || s.editing) { if (overlay != null) overlay = null else s.editing = false }

    val view = LocalView.current
    val onLaunch: (AppInfo, Rect) -> Unit = { app, r -> act.launch(app, view, r) }
    val openMenu: (AppInfo?, Slot?, Rect) -> Unit = { a, sl, r -> overlay = Overlay.Menu(a, sl, r) }

    // Shared jiggle phase: read only inside graphicsLayer → no recomposition per frame.
    val jig = rememberInfiniteTransition(label = "jiggle").animateFloat(-1.7f, 1.7f,
        infiniteRepeatable(tween(130, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "j")

    val blur by animateFloatAsState(if (overlay != null) 28f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "blur")
    val density = LocalDensity.current
    val edgePx = with(density) { 28.dp.toPx() }
    val topPx = with(density) { 70.dp.toPx() }
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    fun endDrag() {
        val src = drag.source ?: return
        val h = drag.hover
        val done = if (h != null && s.itemAt(h.c, h.i) != null && h != src) s.merge(src.c, src.i, h.c, h.i) else false
        if (!done) {
            val inDock = drag.dockRect.contains(drag.pos)
            if (inDock) {
                val xs = (0 until s.dock.size).mapNotNull { drag.bounds[Slot(DOCK, it)]?.center?.x }
                s.move(src.c, src.i, DOCK, xs.count { it < drag.pos.x })
            } else {
                val page = pager.currentPage - 1
                if (page in 0 until homeCountRef.value) {
                    val near = drag.bounds.entries.filter { it.key.c == page }.minByOrNull { (it.value.center - drag.pos).getDistance() }?.key
                    if (near != null) s.move(src.c, src.i, page, near.i)
                }
            }
        }
        drag.source = null; drag.item = null; drag.hover = null
    }

    val cellGestures: (Slot, Item) -> Modifier = { slot, item ->
        Modifier.pointerInput(slot, item) {
            var start = Offset.Zero; var moved = false
            detectDragGesturesAfterLongPress(
                onDragStart = { o ->
                    start = (drag.bounds[slot]?.topLeft ?: Offset.Zero) + o; moved = false; drag.pos = start
                    if (!s.editing) openMenu((item as? Item.App)?.let { s.apps[it.key] }, slot, drag.bounds[slot] ?: Rect.Zero)
                },
                onDragEnd = { if (drag.source != null) endDrag() },
                onDragCancel = { drag.source = null; drag.item = null; drag.hover = null }
            ) { ch, d ->
                ch.consume()
                drag.pos += d
                if (drag.source == null && (drag.pos - start).getDistance() > 24f) {
                    moved = true; s.editing = true; drag.source = slot; drag.item = item; overlay = null
                }
                if (drag.source != null) {
                    val p = drag.pos
                    val page = pager.currentPage - 1
                    drag.hover = drag.bounds.entries.firstOrNull { (k, r) ->
                        k != drag.source && (k.c == page || k.c == DOCK) && s.itemAt(k.c, k.i) != null && r.deflate(r.width * 0.22f).contains(p)
                    }?.key
                    val now = System.currentTimeMillis()
                    if (now - drag.lastEdgeFlip > 750 && (p.x < edgePx || p.x > widthPx - edgePx)) {
                        val target = pager.currentPage + if (p.x < edgePx) -1 else 1
                        if (target in 1..homeCountRef.value) { drag.lastEdgeFlip = now; scope.launch { pager.animateScrollToPage(target) } }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = act.resumeScale.value; scaleY = act.resumeScale.value }
                .then(if (blur > 0.5f && Build.VERSION.SDK_INT >= 31) Modifier.blur(blur.dp) else Modifier)
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                var dragAcc by remember { mutableFloatStateOf(0f) }
                var startX by remember { mutableFloatStateOf(0f) }
                var startY by remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier.weight(1f).fillMaxWidth().pointerInput(s.editing) {
                        if (s.editing) return@pointerInput
                        detectVerticalDragGestures(onDragStart = { o -> dragAcc = 0f; startX = o.x; startY = o.y },
                            onDragEnd = {
                                if (dragAcc > 120f) overlay = when {
                                    startY < topPx && startX > size.width / 2 -> Overlay.Control
                                    startY < topPx -> Overlay.Notifications
                                    pager.currentPage in 1..homeCount -> Overlay.Search
                                    else -> null
                                }
                            }) { _, d -> dragAcc += d }
                    }
                ) {
                    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = pageCount, userScrollEnabled = !dragging) { p ->
                        when {
                            p == 0 -> TodayView(s, act.widgetHost, onAddWidget = { overlay = Overlay.Widgets }, onSearch = { overlay = Overlay.Search })
                            p <= homeCount -> HomePage(s, p - 1, drag, jig, badge, cellGestures, onLaunch,
                                openFolder = { overlay = Overlay.Folder(p - 1, it) }, onRemove = { overlay = Overlay.Remove(it) })
                            else -> AppLibrary(s, onLaunch, { a, r -> openMenu(a, null, r) }) { overlay = it }
                        }
                    }
                    if (s.editing) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("+") { scope.launch { pager.animateScrollToPage(0) }; overlay = Overlay.Widgets }
                            Pill("Settings") { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) }
                        }
                        Pill("Done") { s.editing = false }
                    }
                }
                PageDots(pageCount, pager)
                Dock(s, drag, jig, badge, wall, cellGestures, onLaunch) { overlay = Overlay.Remove(it) }
            }
        }

        // Floating dragged icon
        drag.item?.let { item ->
            val half = with(density) { (ICON / 2).toPx() }
            Box(Modifier.offset { IntOffset((drag.pos.x - half).roundToInt(), (drag.pos.y - half).roundToInt()) }
                .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.zIndex(50f)) {
                ItemIcon(s, item, showLabel = false)
            }
        }

        val o = overlay
        Over(o is Overlay.Search) { SearchOverlay(s, onLaunch, { a, r -> openMenu(a, null, r) }) { overlay = null } }
        Over(o is Overlay.Control, top = true) { ControlCenter { overlay = null } }
        Over(o is Overlay.Notifications, top = true) { NotificationCenter(s) { overlay = null } }
        Over(o is Overlay.Widgets) { WidgetPicker({ act.requestWidget(it); overlay = null }) { overlay = null } }
        Pop(o is Overlay.Folder) {
            val f = o as? Overlay.Folder
            val cur = f?.let { s.pages.getOrNull(it.page)?.getOrNull(it.index) as? Item.Folder }
            if (f != null && cur != null) FolderOverlay(cur.name, cur.keys.mapNotNull { s.apps[it] }, s.editing, wall,
                onRename = { s.renameFolder(f.page, f.index, it) }, onRemove = { s.removeFromFolder(f.page, f.index, it.key) },
                onLaunch = { a, r -> overlay = null; onLaunch(a, r) }, onMenu = { a, r -> openMenu(a, null, r) }, onClose = { overlay = null })
            else if (f != null) LaunchedEffect(Unit) { overlay = null }
        }
        Pop(o is Overlay.LibFolder) {
            (o as? Overlay.LibFolder)?.let { lf -> FolderOverlay(lf.name, lf.apps, false, wall, null, null,
                onLaunch = { a, r -> overlay = null; onLaunch(a, r) }, onMenu = { a, r -> openMenu(a, null, r) }, onClose = { overlay = null }) }
        }
        Pop(o is Overlay.Menu) { (o as? Overlay.Menu)?.let { m -> ContextMenu(s, act, m, onLaunch, editHome = { s.editing = true }) { overlay = null } } }
        Pop(o is Overlay.Remove) { (o as? Overlay.Remove)?.let { r -> RemoveSheet(s, act, r.slot) { overlay = null } } }

        if (onboarding) Onboarding(act) { onboarding = false; s.prefs.onboarded = true }
    }
}

@Composable
private fun Over(visible: Boolean, top: Boolean = false, content: @Composable () -> Unit) =
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically(spring(0.85f, Spring.StiffnessMediumLow)) { -it / if (top) 2 else 6 },
        exit = fadeOut() + slideOutVertically { -it / if (top) 2 else 6 }) { content() }

@Composable
private fun Pop(visible: Boolean, content: @Composable () -> Unit) =
    AnimatedVisibility(visible, enter = fadeIn() + scaleIn(spring(0.8f, Spring.StiffnessMediumLow), 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.7f)) { content() }

@Composable
fun AppIcon(icon: @Composable () -> Unit, label: String, size: Dp = ICON, showLabel: Boolean = true, badge: Int = 0) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(Modifier.size(size).shadow(4.dp, SquircleShape).clip(SquircleShape)) { icon() }
            if (badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).defaultMinSize(22.dp, 22.dp).clip(CircleShape)
                .background(Color(0xFFFF3B30)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(if (badge > 99) "99+" else "$badge", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size + 16.dp))
        }
    }
}

@Composable
fun ItemIcon(s: LauncherState, item: Item, showLabel: Boolean = true, badge: Int = 0) = when (item) {
    is Item.App -> s.apps[item.key]?.let { a -> AppIcon({ Image(a.icon, a.label, Modifier.fillMaxSize()) }, a.label, showLabel = showLabel, badge = badge) }
    is Item.Folder -> AppIcon({ FolderIcon(item.keys.mapNotNull { s.apps[it] }) }, item.name, showLabel = showLabel, badge = badge)
} ?: Unit

@Composable
fun FolderIcon(apps: List<AppInfo>) {
    Box(Modifier.fillMaxSize().background(glass).padding(7.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            apps.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { Image(it.icon, null, Modifier.size(12.dp).clip(SquircleShape)) } }
            }
        }
    }
}

@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "press")
    return graphicsLayer { scaleX = sc; scaleY = sc }
}

@Composable
fun HomePage(s: LauncherState, page: Int, drag: DragCtl, jig: State<Float>, badge: (Item) -> Int,
             gestures: (Slot, Item) -> Modifier, onLaunch: (AppInfo, Rect) -> Unit,
             openFolder: (Int) -> Unit, onRemove: (Slot) -> Unit) {
    val items = s.pages.getOrNull(page) ?: emptyList()
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(top = 44.dp, bottom = 8.dp)
        .pointerInput(s.editing) { detectTapGestures(onLongPress = { s.editing = true }, onTap = { s.editing = false }) },
        verticalArrangement = Arrangement.SpaceBetween) {
        for (r in 0 until s.rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                for (c in 0 until s.cols) {
                    val i = r * s.cols + c
                    val slot = Slot(page, i)
                    Box(Modifier.width(ICON + 16.dp).height(ICON + 22.dp).onGloballyPositioned { drag.bounds[slot] = it.boundsInRoot() },
                        contentAlignment = Alignment.TopCenter) {
                        val item = items.getOrNull(i) ?: return@Box
                        Cell(s, slot, item, drag, jig, badge(item), gestures, onRemove) {
                            when (item) {
                                is Item.App -> if (!s.editing) s.apps[item.key]?.let { a -> onLaunch(a, drag.bounds[slot] ?: Rect.Zero) }
                                is Item.Folder -> openFolder(i)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(s: LauncherState, slot: Slot, item: Item, drag: DragCtl, jig: State<Float>, badge: Int,
                 gestures: (Slot, Item) -> Modifier, onRemove: (Slot) -> Unit, showLabel: Boolean = true, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val isSrc = drag.source == slot
    val hoverScale by animateFloatAsState(if (drag.hover == slot) 1.18f else 1f, iosSpring, label = "h")
    val editing = s.editing
    val sign = if ((slot.i + slot.c) % 2 == 0) 1f else -1f
    Box(
        Modifier.graphicsLayer {
            rotationZ = if (editing && !isSrc) jig.value * sign else 0f
            scaleX = hoverScale; scaleY = hoverScale; alpha = if (isSrc) 0f else 1f
        }
            .pressScale(pressed && !editing)
            .pointerInput(item, editing) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onTap() })
            }
            .then(gestures(slot, item))
    ) {
        ItemIcon(s, item, showLabel, badge)
        if (editing && !isSrc) MinusBadge { onRemove(slot) }
    }
}

@Composable
fun BoxScope.MinusBadge(onClick: () -> Unit) {
    Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-6).dp).size(22.dp).clip(CircleShape)
        .background(Color(0xE6E5E5EA)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.width(10.dp).height(2.dp).background(Color.Black))
    }
}

@Composable
fun PageDots(count: Int, pager: PagerState) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            val cur = pager.currentPage
            repeat(count) { i ->
                val a by animateFloatAsState(if (i == cur) 1f else 0.4f, label = "dot")
                if (i == 0 || i == count - 1) Text(if (i == 0) "◐" else "▦", color = Color.White.copy(alpha = a), fontSize = 8.sp)
                else Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = a)))
            }
        }
    }
}

@Composable
fun Dock(s: LauncherState, drag: DragCtl, jig: State<Float>, badge: (Item) -> Int, wall: ImageBitmap?,
         gestures: (Slot, Item) -> Modifier, onLaunch: (AppInfo, Rect) -> Unit, onRemove: (Slot) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 8.dp)
        .onGloballyPositioned { drag.dockRect = it.boundsInRoot() }
        .clip(RoundedCornerShape(34.dp))
        .frosted(if (s.prefs.dockBlur) wall else null, Color(0x40FFFFFF))
        .padding(vertical = 14.dp).heightIn(min = ICON)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            s.dock.forEachIndexed { i, k ->
                val a = s.apps[k] ?: return@forEachIndexed
                val slot = Slot(DOCK, i)
                val item = Item.App(k)
                Box(Modifier.onGloballyPositioned { drag.bounds[slot] = it.boundsInRoot() }) {
                    Cell(s, slot, item, drag, jig, badge(item), gestures, onRemove, showLabel = false) {
                        if (!s.editing) onLaunch(a, drag.bounds[slot] ?: Rect.Zero)
                    }
                }
            }
        }
    }
    // Stale dock slots must not catch drops.
    SideEffect { drag.bounds.keys.removeAll { it.c == DOCK && it.i >= s.dock.size } }
}

@Composable
fun SearchField(q: String, onChange: (String) -> Unit, fr: FocusRequester? = null, placeholder: String = "Search") {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x40FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (q.isEmpty()) Text("⌕  $placeholder", color = Color(0xCCFFFFFF), fontSize = 16.sp)
        BasicTextField(q, onChange, singleLine = true, cursorBrush = SolidColor(Color.White), textStyle = fieldStyle,
            modifier = Modifier.fillMaxWidth().let { if (fr != null) it.focusRequester(fr) else it })
    }
}

@Composable
fun AppRow(a: AppInfo, onMenu: ((AppInfo, Rect) -> Unit)? = null, onLaunch: (AppInfo, Rect) -> Unit) {
    var r by remember { mutableStateOf(Rect.Zero) }
    Row(Modifier.fillMaxWidth().onGloballyPositioned { r = it.boundsInRoot() }
        .pointerInput(a) { detectTapGestures(onLongPress = { onMenu?.invoke(a, r) }, onTap = { onLaunch(a, r) }) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(a.icon, null, Modifier.size(36.dp).clip(SquircleShape))
        Spacer(Modifier.width(12.dp))
        Text(a.label, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun SearchOverlay(s: LauncherState, onLaunch: (AppInfo, Rect) -> Unit, onMenu: (AppInfo, Rect) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val res = remember(q, s.apps) {
        val all = s.apps.values.sortedBy { it.label.lowercase() }
        if (q.isBlank()) all.take(8) else all.filter { it.label.contains(q, true) }.sortedBy { if (it.label.startsWith(q, true)) 0 else 1 }
    }
    Column(Modifier.fillMaxSize().background(Color(0x99000000)).systemBarsPadding().imePadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SearchField(q, { q = it }, fr) }
            Text("Cancel", color = Color.White, modifier = Modifier.clickable { onClose() }.padding(start = 12.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (q.isBlank()) "Suggestions" else "Apps", color = Color(0xAAFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        LazyColumn { items(res, key = { it.key }) { AppRow(it, onMenu) { a, r -> onClose(); onLaunch(a, r) } } }
    }
}

@Composable
fun AppLibrary(s: LauncherState, onLaunch: (AppInfo, Rect) -> Unit, onMenu: (AppInfo, Rect) -> Unit, open: (Overlay) -> Unit) {
    var q by remember { mutableStateOf("") }
    var listMode by remember { mutableStateOf(false) }
    val all = remember(s.apps) { s.apps.values.sortedBy { it.label.lowercase() } }
    val cats = remember(all) {
        val fresh = all.sortedByDescending { a -> try { s.appInstallTime(a.pkg) } catch (_: Exception) { 0L } }.take(4)
        listOf("Recently Added" to fresh) + all.groupBy { it.category }.toList().sortedByDescending { it.second.size }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SearchField(q, { q = it }, placeholder = "App Library") }
            Text(if (listMode || q.isNotEmpty()) "Grid" else "A–Z", color = Color.White,
                modifier = Modifier.clickable { listMode = !listMode; if (!listMode) q = "" }.padding(start = 12.dp))
        }
        Spacer(Modifier.height(14.dp))
        if (listMode || q.isNotEmpty()) {
            val f = all.filter { it.label.contains(q, true) }
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0x33000000)).padding(horizontal = 14.dp)) {
                LazyColumn {
                    var last = ' '
                    f.forEach { a ->
                        val ch = a.label.firstOrNull()?.uppercaseChar() ?: '#'
                        if (ch != last) { last = ch; item(key = "h$ch") { Text("$ch", color = Color(0xAAFFFFFF), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) } }
                        item(key = a.key) { AppRow(a, onMenu, onLaunch) }
                    }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(cats, key = { it.first }) { (name, apps) -> CategoryTile(name, apps, onLaunch, onMenu) { open(Overlay.LibFolder(name, apps)) } }
            }
        }
    }
}

@Composable
fun CategoryTile(name: String, apps: List<AppInfo>, onLaunch: (AppInfo, Rect) -> Unit, onMenu: (AppInfo, Rect) -> Unit, open: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp)).background(glass).padding(12.dp)) {
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                for (r in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (c in 0..1) {
                        val idx = r * 2 + c
                        val big = !(idx == 3 && apps.size > 4)
                        val a = apps.getOrNull(idx)
                        var rect by remember { mutableStateOf(Rect.Zero) }
                        Box(Modifier.size(58.dp).onGloballyPositioned { rect = it.boundsInRoot() }, contentAlignment = Alignment.Center) {
                            if (a != null && big) Image(a.icon, a.label, Modifier.fillMaxSize().clip(SquircleShape)
                                .pointerInput(a) { detectTapGestures(onLongPress = { onMenu(a, rect) }, onTap = { onLaunch(a, rect) }) })
                            else if (a != null) Box(Modifier.fillMaxSize().clip(SquircleShape).clickable { open() }.padding(4.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    apps.drop(3).take(4).chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        row.forEach { Image(it.icon, null, Modifier.size(22.dp).clip(SquircleShape)) } } }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(name, style = labelStyle.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun FolderOverlay(name: String, apps: List<AppInfo>, editing: Boolean, wall: ImageBitmap?, onRename: ((String) -> Unit)?, onRemove: ((AppInfo) -> Unit)?,
                  onLaunch: (AppInfo, Rect) -> Unit, onMenu: (AppInfo, Rect) -> Unit, onClose: () -> Unit) {
    var title by remember(name) { mutableStateOf(name) }
    Box(Modifier.fillMaxSize().background(Color(0x33000000)).clickable(remember { MutableInteractionSource() }, null) { onRename?.invoke(title.ifBlank { "Folder" }); onClose() },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (onRename != null) BasicTextField(title, { title = it }, singleLine = true, cursorBrush = SolidColor(Color.White),
                textStyle = fieldStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), modifier = Modifier.width(300.dp))
            else Text(name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(316.dp).clip(RoundedCornerShape(36.dp)).frosted(wall, Color(0x4DFFFFFF))
                .clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp)) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(apps, key = { it.key }) { a ->
                        var r by remember { mutableStateOf(Rect.Zero) }
                        Box(Modifier.onGloballyPositioned { r = it.boundsInRoot() }
                            .pointerInput(a) { detectTapGestures(onLongPress = { onMenu(a, r) }, onTap = { onLaunch(a, r) }) },
                            contentAlignment = Alignment.TopCenter) {
                            AppIcon({ Image(a.icon, a.label, Modifier.fillMaxSize()) }, a.label)
                            if (editing && onRemove != null) MinusBadge { onRemove(a) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, glyph: String, red: Boolean = false, icon: ImageBitmap? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (red) Color(0xFFFF3B30) else Color.Black, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null) Image(icon, null, Modifier.size(22.dp).clip(SquircleShape)) else Text(glyph, color = if (red) Color(0xFFFF3B30) else Color.Black, fontSize = 16.sp)
    }
}

@Composable
fun ContextMenu(s: LauncherState, act: MainActivity, m: Overlay.Menu, onLaunch: (AppInfo, Rect) -> Unit, editHome: () -> Unit, onClose: () -> Unit) {
    val a = m.app
    val shortcuts = remember(a) { a?.let { act.shortcutsFor(it.pkg) }.orEmpty() }
    val density = LocalDensity.current
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val below = m.rect.center.y < screenH * 0.55f
    Box(Modifier.fillMaxSize().background(Color(0x40000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() }) {
        Column(Modifier.offset { IntOffset(0, if (below) m.rect.bottom.roundToInt() + 12 else 0) }
            .then(if (below) Modifier else Modifier.align(Alignment.BottomStart).padding(bottom = with(density) { (screenH - m.rect.top + 12).toDp() }))
            .padding(horizontal = 40.dp).width(260.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF2F2F2F7))) {
            shortcuts.take(4).forEach { sc ->
                MenuRow(sc.shortLabel?.toString() ?: "", "›", icon = act.shortcutIcon(sc)) { onClose(); act.startShortcut(sc, m.rect) }
            }
            if (shortcuts.isNotEmpty()) Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x14000000)))
            if (a != null && !s.onHome(a.key)) MenuRow("Add to Home Screen", "＋") { s.addToHome(listOf(a.key)); onClose() }
            if (m.slot != null) MenuRow("Edit Home Screen", "▦") { editHome(); onClose() }
            if (a != null) {
                MenuRow("App Info", "ⓘ") { onClose(); act.openSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))) }
                MenuRow("Hide App", "◌") { s.prefs.hidden = s.prefs.hidden + a.key; onClose(); s.reload() }
                if (m.slot != null) MenuRow("Remove from Home Screen", "−", red = true) { s.removeKeyEverywhere(a.key); onClose() }
                if (!act.isSystemApp(a.pkg)) MenuRow("Delete App", "🗑", red = true) { onClose(); act.uninstall(a.pkg) }
            }
        }
    }
}

@Composable
fun RemoveSheet(s: LauncherState, act: MainActivity, slot: Slot, onClose: () -> Unit) {
    val item = s.itemAt(slot.c, slot.i)
    val app = (item as? Item.App)?.let { s.apps[it.key] }
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(remember { MutableInteractionSource() }, null) { onClose() }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(280.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF2F2F2F7)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (app != null) "Remove “${app.label}”?" else "Remove folder?", color = Color.Black, fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp, modifier = Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp))
            Text("Removing from Home Screen keeps the app in App Library.", color = Color(0xFF3C3C43), fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
            if (app != null && !act.isSystemApp(app.pkg)) MenuRow("Delete App", "", red = true) { onClose(); act.uninstall(app.pkg) }
            MenuRow("Remove from Home Screen", "") {
                if (slot.c == DOCK) app?.let { s.removeKeyEverywhere(it.key) } else s.remove(slot.c, slot.i); onClose() }
            MenuRow("Cancel", "") { onClose() }
        }
    }
}

@Composable
fun Onboarding(act: MainActivity, onDone: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(act.resumeCount.intValue) { tick++ }
    val isDefault = remember(tick) { act.isDefaultLauncher() }
    val notif = remember(tick) { NotifStore.hasAccess(act) }
    val write = remember(tick) { Settings.System.canWrite(act) }
    Column(Modifier.fillMaxSize().background(Color(0xE6000000)).systemBarsPadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(30.dp))
        Text("Welcome", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("A few permissions make this feel complete. You can change them later in Settings.", color = Color(0xCCFFFFFF), fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Step("Set as default Home app", isDefault) { act.requestDefaultLauncher() }
        Step("Notifications, badges & media", notif) { act.openSafe(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        if (Build.VERSION.SDK_INT <= 32) Step("Frosted wallpaper blur (storage)", remember(tick) { act.hasWallpaperAccess() }) { act.requestWallpaperAccess() }
        Step("Brightness & rotation control", write) { act.openSafe(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${act.packageName}"))) }
        Spacer(Modifier.weight(1f))
        Text("Continue", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A84FF)).clickable { onDone() }.padding(16.dp))
    }
}

@Composable
private fun Step(label: String, done: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x26FFFFFF)).clickable(enabled = !done, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(if (done) "✓" else "Allow", color = if (done) Color(0xFF30D158) else Color(0xFF0A84FF), fontWeight = FontWeight.SemiBold)
    }
}
