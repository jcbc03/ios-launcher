package com.jcbc03.ioslauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val tile = Color(0x59303034)
private val on = Color(0xFF0A84FF)

fun Context.openSafe(i: Intent) = try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }

@Composable
fun ControlCenter(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val wifi = remember { ctx.applicationContext.getSystemService(WifiManager::class.java) }
    val bt = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter }
    val cam = remember { ctx.getSystemService(CameraManager::class.java) }
    val torchId = remember { try { cam.cameraIdList.firstOrNull { cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } } catch (_: Exception) { null } }
    var torch by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cb = object : CameraManager.TorchCallback() { override fun onTorchModeChanged(id: String, en: Boolean) { if (id == torchId) torch = en } }
        cam.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { cam.unregisterTorchCallback(cb) }
    }
    val canWrite = Settings.System.canWrite(ctx)
    var rotLock by remember { mutableStateOf(Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0) }
    val airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    val wifiOn = try { wifi.isWifiEnabled } catch (_: Exception) { false }
    val btOn = try { bt?.isEnabled == true } catch (_: Exception) { false }
    var bright by remember { mutableFloatStateOf(Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f) }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var vol by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()) }
    val askWrite = { ctx.openSafe(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }

    var dragY by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(remember { MutableInteractionSource() }, null) { onClose() }
            .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (dragY < -150) onClose(); dragY = 0f }) { _, d -> dragY += d } }
            .systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Connectivity block
            Column(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(tile).padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Round("✈", airplane) { ctx.openSafe(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                    Round("⇅", true, Color(0xFF30D158)) {
                        ctx.openSafe(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Round("Wi", wifiOn) { ctx.openSafe(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)) }
                    Round("Bt", btOn) { ctx.openSafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
            }
            MediaTile(Modifier.weight(1f).aspectRatio(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Square("⟳", if (rotLock) "Locked" else "Rotate", rotLock, Modifier.weight(1f)) {
                        if (!canWrite) askWrite() else {
                            rotLock = !rotLock
                            Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotLock) 0 else 1)
                        }
                    }
                    Square("☾", "Focus", false, Modifier.weight(1f)) { ctx.openSafe(Intent("android.settings.ZEN_MODE_SETTINGS")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Square("✦", "Torch", torch, Modifier.weight(1f)) {
                        torchId?.let { try { cam.setTorchMode(it, !torch) } catch (_: Exception) {} } }
                    Square("◉", "Camera", false, Modifier.weight(1f)) { onClose(); ctx.openSafe(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Square("⚙", "Settings", false, Modifier.weight(1f)) { onClose(); ctx.openSafe(Intent(Settings.ACTION_SETTINGS)) }
                    Square("▣", "Display", false, Modifier.weight(1f)) { ctx.openSafe(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                }
            }
            VSlider(bright, "☀", Modifier.weight(0.5f).height(236.dp)) { v ->
                bright = v
                if (canWrite) {
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(1, 255))
                } else act.window.attributes = act.window.attributes.apply { screenBrightness = v.coerceAtLeast(0.02f) }
            }
            VSlider(vol, "♪", Modifier.weight(0.5f).height(236.dp)) { v ->
                vol = v; audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).toInt(), 0)
            }
        }
        if (!canWrite) Text("Tap to allow brightness & rotation control", color = Color(0xCCFFFFFF), fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(tile).clickable { askWrite() }.padding(12.dp))
    }
}

@Composable
private fun Round(glyph: String, active: Boolean, color: Color = on, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(if (active) color else Color(0x40FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(glyph, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Square(glyph: String, label: String, active: Boolean, m: Modifier, onClick: () -> Unit) {
    Column(m.aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(if (active) Color.White else tile).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(glyph, color = if (active) Color.Black else Color.White, fontSize = 22.sp)
        Text(label, color = if (active) Color.Black else Color(0xCCFFFFFF), fontSize = 10.sp)
    }
}

@Composable
fun VSlider(value: Float, glyph: String, m: Modifier, onChange: (Float) -> Unit) {
    var h by remember { mutableIntStateOf(1) }
    val cur by rememberUpdatedState(value)
    Box(m.clip(RoundedCornerShape(24.dp)).background(tile)
        .onSizeChangedCompat { h = it }
        .pointerInput(Unit) {
            detectVerticalDragGestures { ch, d -> ch.consume(); onChange((cur - d / h).coerceIn(0f, 1f)) }
        }
        .pointerInput(Unit) { detectTapGestures { o -> onChange((1f - o.y / h).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(Color.White))
        Text(glyph, color = Color(0xFF8E8E93), fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
    }
}

fun Modifier.onSizeChangedCompat(f: (Int) -> Unit) = this.onSizeChanged { f(it.height.coerceAtLeast(1)) }

@Composable
private fun MediaTile(m: Modifier) {
    val ctx = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("Not Playing") }
    var artist by remember { mutableStateOf("") }
    var art by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(md: MediaMetadata?) {
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Not Playing"
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            }
            override fun onPlaybackStateChanged(s: PlaybackState?) { playing = s?.state == PlaybackState.STATE_PLAYING }
        }
        val c = try {
            ctx.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(ctx, LauncherNotificationService::class.java)).firstOrNull()
        } catch (_: SecurityException) { null }
        controller = c
        c?.let { it.registerCallback(cb); cb.onMetadataChanged(it.metadata); cb.onPlaybackStateChanged(it.playbackState) }
        onDispose { c?.unregisterCallback(cb) }
    }
    Column(m.clip(RoundedCornerShape(28.dp)).background(tile).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            art?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))); Spacer(Modifier.width(8.dp)) }
            Column {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (artist.isNotEmpty()) Text(artist, color = Color(0xAAFFFFFF), fontSize = 12.sp, maxLines = 1)
            }
        }
        if (controller == null && !NotifStore.hasAccess(ctx))
            Text("Enable notification access for media", color = Color(0xAAFFFFFF), fontSize = 11.sp,
                modifier = Modifier.clickable { ctx.openSafe(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val t = controller?.transportControls
            Text("⏮", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { t?.skipToPrevious() })
            Text(if (playing) "⏸" else "▶", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { if (playing) t?.pause() else t?.play() })
            Text("⏭", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { t?.skipToNext() })
        }
    }
}
