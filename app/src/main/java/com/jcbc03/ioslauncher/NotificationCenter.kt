package com.jcbc03.ioslauncher

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun rememberNow(periodMs: Long = 15_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(periodMs); now = System.currentTimeMillis() } }
    return now
}

@Composable
fun BigClock() {
    val now = rememberNow()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NotificationCenter(s: LauncherState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val list by NotifStore.list.collectAsState()
    val access = remember { NotifStore.hasAccess(ctx) }
    val byPkg = remember(s.allApps) { s.allApps.associateBy { it.pkg } }
    Column(Modifier.fillMaxSize().background(Color(0x8C000000)).systemBarsPadding().padding(16.dp)) {
        Spacer(Modifier.height(24.dp))
        BigClock()
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Notification Centre", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row {
                if (list.isNotEmpty()) Pill("Clear") { NotifStore.clearAll() }
                Spacer(Modifier.width(8.dp)); Pill("Close") { onClose() }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!access) {
            Card {
                Text("Allow notification access", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Needed for Notification Centre, icon badges and media controls.", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp)); Pill("Open Settings") { ctx.openSafe(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        } else if (list.isEmpty()) Text("No Older Notifications", color = Color(0xAAFFFFFF), modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.key }) { n -> NotifCard(n, byPkg[n.pkg]) { try { n.intent?.send() } catch (_: Exception) {}; onClose() } }
        }
    }
}

@Composable
fun Pill(t: String, onClick: () -> Unit) = Text(t, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp))

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x59FFFFFF)).padding(14.dp), content = content)

@Composable
fun NotifCard(n: Notif, app: AppInfo?, onClick: () -> Unit) {
    val off = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().graphicsLayer { translationX = off.value; alpha = 1f - (abs(off.value) / 900f).coerceIn(0f, 0.8f) }
        .pointerInput(n.key) {
            detectHorizontalDragGestures(onDragEnd = {
                scope.launch { if (abs(off.value) > 220 && n.clearable) { off.animateTo(off.value * 3); NotifStore.dismiss(n) } else off.animateTo(0f, iosSpring) }
            }) { ch, d -> ch.consume(); scope.launch { off.snapTo(off.value + d) } }
        }
        .clip(RoundedCornerShape(22.dp)).background(Color(0x66FFFFFF)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (app != null) Image(app.icon, null, Modifier.size(38.dp).clip(SquircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row { Text(n.title, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), color = Color(0xFF3C3C43), fontSize = 12.sp) }
            Text(n.text, color = Color(0xFF1C1C1E), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
