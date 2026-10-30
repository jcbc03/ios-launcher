package com.jcbc03.ioslauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val widgetBg = Color(0x59FFFFFF)

@Composable
fun TodayView(s: LauncherState, host: AppWidgetHost, onAddWidget: () -> Unit, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    val mgr = remember { AppWidgetManager.getInstance(ctx) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("⌕  Search", color = Color(0xCCFFFFFF), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0x40FFFFFF)).clickable { onSearch() }.padding(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DateWidget(Modifier.weight(1f))
            BatteryWidget(Modifier.weight(1f))
        }
        s.widgets.forEach { id ->
            val info = remember(id) { mgr.getAppWidgetInfo(id) }
            if (info == null) return@forEach
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(widgetBg)) {
                val h = with(LocalDensity.current) { info.minHeight.toDp() }.coerceAtLeast(110.dp)
                AndroidView({ c -> host.createView(c.applicationContext, id, info).apply { setPadding(0, 0, 0, 0) } },
                    Modifier.fillMaxWidth().height(h).padding(6.dp))
                if (s.editing) Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp).clip(CircleShape).background(Color(0xE6E5E5EA))
                    .clickable { host.deleteAppWidgetId(id); s.removeWidget(id) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(10.dp).height(2.dp).background(Color.Black)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Pill("Edit") { s.editing = !s.editing }
            Spacer(Modifier.width(10.dp))
            Pill("+ Add Widget") { onAddWidget() }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun DateWidget(m: Modifier) {
    val now = rememberNow()
    Column(m.aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp)) {
        Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now)).uppercase(), color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(now)), color = Color.Black, fontSize = 48.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(now)), color = Color(0xFF3C3C43), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BatteryWidget(m: Modifier) {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent) {
                level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { ctx.unregisterReceiver(r) }
    }
    Column(m.aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(widgetBg).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val c = if (level in 0..20 && !charging) Color(0xFFFF3B30) else Color(0xFF30D158)
        Box(Modifier.size(70.dp).clip(CircleShape).background(Color(0x33000000)), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                drawArc(c, -90f, 360f * level.coerceAtLeast(0) / 100f, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Text(if (charging) "⚡" else "▭", color = Color.White, fontSize = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(if (level >= 0) "$level%" else "—", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WidgetPicker(onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { AppWidgetManager.getInstance(ctx).installedProviders.sortedBy { it.loadLabel(ctx.packageManager) } }
    Column(Modifier.fillMaxSize().background(Color(0xCC1C1C1E)).systemBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Pill("Done") { onClose() }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.provider.flattenToString() }) { p ->
                val icon = remember(p) { try { p.loadIcon(ctx, 0)?.let { renderIcon(it, true, 96).asImageBitmap() } } catch (_: Exception) { null } }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)).clickable { onPick(p) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    icon?.let { Image(it, null, Modifier.size(36.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.loadLabel(ctx.packageManager), color = Color.White, fontWeight = FontWeight.Medium)
                        Text(ctx.packageManager.getApplicationLabel(p.provider.packageName.let { ctx.packageManager.getApplicationInfo(it, 0) }).toString(), color = Color(0x99FFFFFF), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
