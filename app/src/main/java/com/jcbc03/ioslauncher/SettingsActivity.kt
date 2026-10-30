package com.jcbc03.ioslauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg = Color(0xFFF2F2F7)
private val blue = Color(0xFF0A84FF)

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Inter)) { SettingsScreen(prefs) }
        }
    }

    @Composable
    private fun SettingsScreen(p: Prefs) {
        var cols by remember { mutableIntStateOf(p.cols) }
        var rows by remember { mutableIntStateOf(p.rows) }
        var pack by remember { mutableStateOf(p.iconPack) }
        var hidden by remember { mutableStateOf(p.hidden) }
        var badges by remember { mutableStateOf(p.badges) }
        var dockBlur by remember { mutableStateOf(p.dockBlur) }
        val packs = remember { IconPack.installed(this) }
        val apps = remember { loadApps(this, null) }
        LazyColumn(Modifier.fillMaxSize().background(bg).systemBarsPadding().padding(horizontal = 16.dp)) {
            item { Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(vertical = 16.dp)) }
            item { Header("HOME SCREEN") }
            item { Group {
                Stepper("Columns", cols, 3..6) { cols = it; p.cols = it }
                Stepper("Rows", rows, 4..8) { rows = it; p.rows = it }
                Toggle("Notification badges", badges) { badges = it; p.badges = it }
                Toggle("Frosted dock (wallpaper blur)", dockBlur) { dockBlur = it; p.dockBlur = it }
                Action("Reset Home Screen layout") { getSharedPreferences("layout", 0).edit().remove("v1").apply() }
            } }
            item { Header("ICON PACK") }
            item { Group {
                Choice("System icons", pack == null) { pack = null; p.iconPack = null }
                packs.forEach { (pkg, label) -> Choice(label, pack == pkg) { pack = pkg; p.iconPack = pkg } }
                if (packs.isEmpty()) Text("No ADW/Nova-compatible icon packs installed", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            } }
            item { Header("PERMISSIONS") }
            item { Group {
                Action("Default Home app") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                Action("Notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                Action("Modify system settings") { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) }
            } }
            item { Header("HIDDEN APPS") }
            items(apps, key = { it.key }) { a ->
                Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(a.icon, null, Modifier.size(30.dp).clip(SquircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, color = Color.Black, modifier = Modifier.weight(1f))
                    Switch(a.key in hidden, { on -> hidden = if (on) hidden + a.key else hidden - a.key; p.hidden = hidden })
                }
            }
            item { Text("Fonts: Inter (SIL OFL 1.1). Not affiliated with Apple.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 24.dp)) }
        }
    }
}

@Composable private fun Header(t: String) = Text(t, color = Color(0xFF6D6D72), fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp, top = 22.dp, bottom = 6.dp))

@Composable private fun Group(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White), content = content)

@Composable private fun Row1(label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color.Black, fontSize = 16.sp, modifier = Modifier.weight(1f)); trailing() }

@Composable private fun Toggle(label: String, v: Boolean, on: (Boolean) -> Unit) = Row1(label) { Switch(v, on) }
@Composable private fun Action(label: String, on: () -> Unit) = Row1(label, on) { Text("›", color = Color.Gray, fontSize = 20.sp) }
@Composable private fun Choice(label: String, sel: Boolean, on: () -> Unit) = Row1(label, on) { if (sel) Text("✓", color = blue, fontWeight = FontWeight.Bold) }
@Composable private fun Stepper(label: String, v: Int, range: IntRange, on: (Int) -> Unit) = Row1(label) {
    Text("−", color = blue, fontSize = 22.sp, modifier = Modifier.clickable { if (v > range.first) on(v - 1) }.padding(horizontal = 12.dp))
    Text("$v", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Text("+", color = blue, fontSize = 22.sp, modifier = Modifier.clickable { if (v < range.last) on(v + 1) }.padding(horizontal = 12.dp))
}
