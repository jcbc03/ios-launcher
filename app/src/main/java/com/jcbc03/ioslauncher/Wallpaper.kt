package com.jcbc03.ioslauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/** Blurred copy of the current static wallpaper, or null when the OS denies access (Android 13+, live wallpapers). */
fun loadBlurredWallpaper(ctx: Context): ImageBitmap? = try {
    val wm = WallpaperManager.getInstance(ctx)
    if (wm.wallpaperInfo != null) null else {
        @Suppress("MissingPermission") val d: Drawable? = wm.drawable
        val src = (d as? BitmapDrawable)?.bitmap
        src?.let { blurCheap(it).asImageBitmap() }
    }
} catch (_: Throwable) { null }

/** Box-filter blur via repeated down/up-scaling: done once, zero per-frame cost. */
fun blurCheap(src: Bitmap): Bitmap {
    var b = Bitmap.createScaledBitmap(src, max(1, src.width / 12), max(1, src.height / 12), true)
    b = Bitmap.createScaledBitmap(b, max(1, b.width / 2), max(1, b.height / 2), true)
    b = Bitmap.createScaledBitmap(b, max(1, src.width / 6), max(1, src.height / 6), true)
    return b
}

/** Draws the blurred wallpaper aligned to the screen behind this element (frosted glass). */
@Composable
fun Modifier.frosted(wall: ImageBitmap?, tint: Color): Modifier {
    if (wall == null) return this.drawBehind { drawRect(tint) }
    val view = LocalView.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    return this.onGloballyPositioned { pos = it.boundsInWindow().topLeft }.drawBehind {
        val sw = view.rootView.width.toFloat(); val sh = view.rootView.height.toFloat()
        val scale = max(sw / wall.width, sh / wall.height)
        val dw = wall.width * scale; val dh = wall.height * scale
        val ox = (sw - dw) / 2 - pos.x; val oy = (sh - dh) / 2 - pos.y
        drawImage(wall, IntOffset.Zero, IntSize(wall.width, wall.height), IntOffset(ox.roundToInt(), oy.roundToInt()),
            IntSize(dw.roundToInt(), dh.roundToInt()))
        drawRect(tint)
    }
}
