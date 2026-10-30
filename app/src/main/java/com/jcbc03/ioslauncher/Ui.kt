package com.jcbc03.ioslauncher

import android.os.Build
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.material3.Text
import kotlinx.coroutines.launch

private val labelStyle = TextStyle(
    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
    shadow = Shadow(Color(0x66000000), Offset(0f, 1f), 3f)
)
private val ICON = 60.dp
private val glass = Color(0x40FFFFFF)

data class OpenFolder(val page: Int, val index: Int)

@Composable
fun LauncherRoot(act: MainActivity) {
    val s = act.state
    val pageCount = s.pages.size + 1 // + App Library
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf<OpenFolder?>(null) }
    var libFolder by remember { mutableStateOf<Pair<String, List<AppInfo>>?>(null) }
    val overlay = search || folder != null || libFolder != null

    LaunchedEffect(act.homeSignal.intValue) {
        if (act.homeSignal.intValue == 0) return@LaunchedEffect
        when {
            overlay -> { search = false; folder = null; libFolder = null }
            s.editing -> s.editing = false
            else -> pager.animateScrollToPage(0)
        }
    }
    LaunchedEffect(act.resumeTick.intValue) {
        if (act.resumeTick.intValue == 0) return@LaunchedEffect
        act.resumeScale.snapTo(0.92f); act.resumeScale.animateTo(1f, iosSpring)
    }
    BackHandler(overlay || s.editing) {
        if (overlay) { search = false; folder = null; libFolder = null } else s.editing = false
    }

    val blur by animateFloatAsState(if (overlay) 28f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "blur")
    val dim by animateFloatAsState(if (overlay) 0.25f else 0f, label = "dim")
    val view = LocalView.current
    val onLaunch: (AppInfo, Rect) -> Unit = { app, r ->
        act.launch(app, view, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt())
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = act.resumeScale.value; scaleY = act.resumeScale.value }
                .then(if (blur > 0.5f && Build.VERSION.SDK_INT >= 31) Modifier.blur(blur.dp) else Modifier)
                .background(Color.Black.copy(alpha = dim))
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                var dragAcc by remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier.weight(1f).fillMaxWidth().pointerInput(s.editing) {
                        if (s.editing) return@pointerInput
                        detectVerticalDragGestures(onDragStart = { dragAcc = 0f },
                            onDragEnd = { if (dragAcc > 120f && pager.currentPage < pageCount - 1) search = true }
                        ) { _, d -> dragAcc += d }
                    }
                ) {
                    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
                        if (p < s.pages.size) HomePage(s, p, onLaunch) { folder = OpenFolder(p, it) }
                        else AppLibrary(s, onLaunch) { libFolder = it }
                    }
                    if (s.editing) {
                        Text("Done", Modifier.align(Alignment.TopEnd).padding(12.dp)
                            .clip(RoundedCornerShape(50)).background(glass)
                            .clickable { s.editing = false }.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                PageDots(pageCount, pager.currentPage)
                Dock(s, onLaunch)
            }
        }

        AnimatedVisibility(search, enter = fadeIn() + slideInVertically { -it / 6 }, exit = fadeOut() + slideOutVertically { -it / 6 }) {
            SearchOverlay(s, onLaunch) { search = false }
        }
        val f = folder
        AnimatedVisibility(f != null, enter = fadeIn() + scaleIn(spring(0.8f, Spring.StiffnessMediumLow), 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
            val cur = f?.let { s.pages.getOrNull(it.page)?.getOrNull(it.index) as? Item.Folder }
            if (f != null && cur != null) FolderOverlay(cur.name, cur.keys.mapNotNull { s.apps[it] }, s.editing,
                onRename = { s.renameFolder(f.page, f.index, it) },
                onRemove = { s.removeFromFolder(f.page, f.index, it.key) },
                onLaunch = { a, r -> folder = null; onLaunch(a, r) }, onClose = { folder = null })
            else if (f != null) LaunchedEffect(Unit) { folder = null }
        }
        val lf = libFolder
        AnimatedVisibility(lf != null, enter = fadeIn() + scaleIn(spring(0.8f, Spring.StiffnessMediumLow), 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
            if (lf != null) FolderOverlay(lf.first, lf.second, false, null, null,
                onLaunch = { a, r -> libFolder = null; onLaunch(a, r) }, onClose = { libFolder = null })
        }
    }
}

@Composable
fun jiggle(on: Boolean, seed: Int): Float {
    if (!on) return 0f
    val t = rememberInfiniteTransition(label = "jiggle")
    val r by t.animateFloat(-1.6f, 1.6f, infiniteRepeatable(tween(110 + (seed % 4) * 12, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "r")
    return r
}

@Composable
fun AppIcon(icon: @Composable () -> Unit, label: String, size: Dp = ICON, showLabel: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).shadow(4.dp, SquircleShape).clip(SquircleShape)) { icon() }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size + 16.dp))
        }
    }
}

@Composable
fun AppImage(app: AppInfo) = Image(app.icon, app.label, Modifier.fillMaxSize())

@Composable
fun FolderIcon(apps: List<AppInfo>) {
    Box(Modifier.fillMaxSize().background(glass).padding(7.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            apps.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { Image(it.icon, null, Modifier.size(12.dp).clip(SquircleShape)) }
                }
            }
        }
    }
}

/** Press feedback: iOS-like spring shrink. */
@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "press")
    return graphicsLayer { scaleX = sc; scaleY = sc }
}

@Composable
fun HomePage(s: LauncherState, page: Int, onLaunch: (AppInfo, Rect) -> Unit, openFolder: (Int) -> Unit) {
    val items = s.pages.getOrNull(page) ?: emptyList()
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)
        .pointerInput(s.editing) { detectTapGestures(onLongPress = { s.editing = true }, onTap = { s.editing = false }) },
        verticalArrangement = Arrangement.SpaceBetween) {
        for (r in 0 until ROWS) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                for (c in 0 until COLS) {
                    val i = r * COLS + c
                    Box(Modifier.width(ICON + 16.dp).height(ICON + 22.dp), contentAlignment = Alignment.TopCenter) {
                        val item = items.getOrNull(i) ?: return@Box
                        val isDrag = dragging == i
                        var pressed by remember { mutableStateOf(false) }
                        val rot = jiggle(s.editing && !isDrag, i)
                        val hoverScale by animateFloatAsState(if (hover == i) 1.15f else if (isDrag) 1.1f else 1f, iosSpring, label = "h")
                        Box(
                            Modifier.zIndex(if (isDrag) 10f else 0f)
                                .onGloballyPositioned { bounds[i] = it.boundsInRoot() }
                                .graphicsLayer {
                                    rotationZ = rot; scaleX = hoverScale; scaleY = hoverScale
                                    if (isDrag) { translationX = dragOffset.x; translationY = dragOffset.y }
                                }
                                .pressScale(pressed && !s.editing)
                                .pointerInput(item, s.editing) {
                                    detectTapGestures(
                                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                                        onTap = {
                                            when (item) {
                                                is Item.App -> if (!s.editing) s.apps[item.key]?.let { a -> onLaunch(a, bounds[i] ?: Rect.Zero) }
                                                is Item.Folder -> openFolder(i)
                                            }
                                        }
                                    )
                                }
                                .pointerInput(item) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { s.editing = true; dragging = i; dragOffset = Offset.Zero },
                                        onDragEnd = {
                                            val from = dragging
                                            val target = hover
                                            if (from >= 0 && target >= 0 && target != from) s.merge(page, from, target)
                                            else if (from >= 0) {
                                                val c0 = bounds[from]?.center?.plus(dragOffset)
                                                val slot = c0?.let { p -> bounds.entries.minByOrNull { (it.value.center - p).getDistance() }?.key }
                                                if (slot != null && slot != from && (bounds[slot]!!.center - c0).getDistance() > 0) s.move(page, from, slot)
                                            }
                                            dragging = -1; hover = -1; dragOffset = Offset.Zero
                                        },
                                        onDragCancel = { dragging = -1; hover = -1; dragOffset = Offset.Zero }
                                    ) { ch, d ->
                                        ch.consume(); dragOffset += d
                                        val p = bounds[dragging]?.center?.plus(dragOffset)
                                        hover = if (p == null) -1 else bounds.entries.firstOrNull { it.key != dragging && it.key < items.size && it.value.deflate(it.value.width * 0.22f).contains(p) }?.key ?: -1
                                    }
                                }
                        ) {
                            when (item) {
                                is Item.App -> s.apps[item.key]?.let { a -> AppIcon({ AppImage(a) }, a.label) }
                                is Item.Folder -> AppIcon({ FolderIcon(item.keys.mapNotNull { s.apps[it] }) }, item.name)
                            }
                            if (s.editing && !isDrag) MinusBadge { s.remove(page, i) }
                        }
                    }
                }
            }
        }
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
fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(count) { i ->
                val a by animateFloatAsState(if (i == current) 1f else 0.4f, label = "dot")
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = a)))
            }
        }
    }
}

@Composable
fun Dock(s: LauncherState, onLaunch: (AppInfo, Rect) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 8.dp)
        .clip(RoundedCornerShape(34.dp))
        .background(Color(0x55FFFFFF))
        .padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            s.dock.forEachIndexed { i, k ->
                val a = s.apps[k] ?: return@forEachIndexed
                var r by remember { mutableStateOf(Rect.Zero) }
                var pressed by remember { mutableStateOf(false) }
                val rot = jiggle(s.editing, i + 7)
                Box(Modifier.onGloballyPositioned { r = it.boundsInRoot() }.graphicsLayer { rotationZ = rot }
                    .pressScale(pressed)
                    .pointerInput(a, s.editing) {
                        detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                            onLongPress = { s.editing = true },
                            onTap = { if (!s.editing) onLaunch(a, r) })
                    }) {
                    AppIcon({ AppImage(a) }, a.label, showLabel = false)
                    if (s.editing) MinusBadge { s.removeFromDock(k) }
                }
            }
        }
    }
}

@Composable
fun SearchField(q: String, onChange: (String) -> Unit, fr: FocusRequester? = null, placeholder: String = "Search") {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x40FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (q.isEmpty()) Text("\u2315  $placeholder", color = Color(0xCCFFFFFF), fontSize = 16.sp)
        BasicTextField(q, onChange, singleLine = true, cursorBrush = SolidColor(Color.White),
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().let { if (fr != null) it.focusRequester(fr) else it })
    }
}

@Composable
fun AppRow(a: AppInfo, onLaunch: (AppInfo, Rect) -> Unit) {
    var r by remember { mutableStateOf(Rect.Zero) }
    Row(Modifier.fillMaxWidth().onGloballyPositioned { r = it.boundsInRoot() }.clickable { onLaunch(a, r) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(a.icon, null, Modifier.size(36.dp).clip(SquircleShape))
        Spacer(Modifier.width(12.dp))
        Text(a.label, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun SearchOverlay(s: LauncherState, onLaunch: (AppInfo, Rect) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val res = remember(q, s.apps) {
        val all = s.apps.values.sortedBy { it.label.lowercase() }
        if (q.isBlank()) all.take(8) else all.filter { it.label.contains(q, true) }
            .sortedBy { if (it.label.startsWith(q, true)) 0 else 1 }
    }
    Column(Modifier.fillMaxSize().background(Color(0x99000000)).systemBarsPadding().imePadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SearchField(q, { q = it }, fr) }
            Text("Cancel", color = Color.White, modifier = Modifier.clickable { onClose() }.padding(start = 12.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (q.isBlank()) "Siri-free Suggestions" else "Apps", color = Color(0xAAFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        LazyColumn { items(res, key = { it.key }) { AppRow(it) { a, r -> onClose(); onLaunch(a, r) } } }
    }
}

@Composable
fun AppLibrary(s: LauncherState, onLaunch: (AppInfo, Rect) -> Unit, openCategory: (Pair<String, List<AppInfo>>) -> Unit) {
    var q by remember { mutableStateOf("") }
    var listMode by remember { mutableStateOf(false) }
    val all = remember(s.apps) { s.apps.values.sortedBy { it.label.lowercase() } }
    val cats = remember(all) {
        val recent = all.take(4)
        listOf("Suggestions" to recent) + all.groupBy { it.category }.toList().sortedByDescending { it.second.size }
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
                        item(key = a.key) { AppRow(a, onLaunch) }
                    }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(cats, key = { it.first }) { (name, apps) -> CategoryTile(name, apps, onLaunch) { openCategory(name to apps) } }
            }
        }
    }
}

@Composable
fun CategoryTile(name: String, apps: List<AppInfo>, onLaunch: (AppInfo, Rect) -> Unit, open: () -> Unit) {
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
                            if (a != null && big) Image(a.icon, a.label, Modifier.fillMaxSize().clip(SquircleShape).clickable { onLaunch(a, rect) })
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
fun FolderOverlay(name: String, apps: List<AppInfo>, editing: Boolean, onRename: ((String) -> Unit)?, onRemove: ((AppInfo) -> Unit)?,
                  onLaunch: (AppInfo, Rect) -> Unit, onClose: () -> Unit) {
    var title by remember(name) { mutableStateOf(name) }
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onRename?.invoke(title.ifBlank { "Folder" }); onClose() },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (onRename != null) BasicTextField(title, { title = it }, singleLine = true, cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                modifier = Modifier.width(300.dp))
            else Text(name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(316.dp).clip(RoundedCornerShape(36.dp)).background(Color(0x4DFFFFFF))
                .clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp)) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(apps, key = { it.key }) { a ->
                        var r by remember { mutableStateOf(Rect.Zero) }
                        Box(Modifier.onGloballyPositioned { r = it.boundsInRoot() }.clickable { onLaunch(a, r) }, contentAlignment = Alignment.TopCenter) {
                            AppIcon({ AppImage(a) }, a.label)
                            if (editing && onRemove != null) MinusBadge { onRemove(a) }
                        }
                    }
                }
            }
        }
    }
}
