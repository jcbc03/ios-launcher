package com.jcbc03.ioslauncher

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    lateinit var state: LauncherState
    lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetMgr: AppWidgetManager
    private lateinit var launcherApps: LauncherApps
    val homeSignal = mutableIntStateOf(0)
    val resumeTick = mutableIntStateOf(0)
    val resumeCount = mutableIntStateOf(0)
    val resumeScale = Animatable(1f)
    val wallpaper = mutableStateOf<ImageBitmap?>(null)
    private var launched = false
    private var pendingWidget = -1
    private val scope = MainScope()

    private val pkgCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String?, u: android.os.UserHandle?) = state.reload()
        override fun onPackageAdded(p: String?, u: android.os.UserHandle?) = state.reload()
        override fun onPackageChanged(p: String?, u: android.os.UserHandle?) = state.reload()
        override fun onPackagesAvailable(p: Array<out String>?, u: android.os.UserHandle?, r: Boolean) = state.reload()
        override fun onPackagesUnavailable(p: Array<out String>?, u: android.os.UserHandle?, r: Boolean) = state.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        state = LauncherState(this)
        widgetHost = AppWidgetHost(applicationContext, 0x10A5)
        widgetMgr = AppWidgetManager.getInstance(this)
        launcherApps = getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(pkgCallback)
        state.reload()
        setContent { LauncherRoot(this) }
    }

    private fun loadWallpaper() = scope.launch {
        wallpaper.value = withContext(Dispatchers.Default) { loadBlurredWallpaper(this@MainActivity) }
    }

    private var wallId = -2
    private fun checkWallpaper() {
        val id = try { if (Build.VERSION.SDK_INT >= 24) android.app.WallpaperManager.getInstance(this).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM) else 0 } catch (_: Exception) { -1 }
        if (id != wallId) { wallId = id; loadWallpaper() }
    }

    override fun onStart() { super.onStart(); try { widgetHost.startListening() } catch (_: Exception) {} }
    override fun onStop() { super.onStop(); try { widgetHost.stopListening() } catch (_: Exception) {} }
    override fun onDestroy() { launcherApps.unregisterCallback(pkgCallback); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); super.onDestroy() }

    override fun onResume() {
        super.onResume()
        state.reload()
        checkWallpaper()
        resumeCount.intValue++
        if (launched) { launched = false; resumeTick.intValue++ }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal.intValue++
    }


    fun launch(app: AppInfo, view: android.view.View, r: Rect) {
        val opts = ActivityOptions.makeScaleUpAnimation(view, r.left.toInt(), r.top.toInt(), r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1)).toBundle()
        try {
            launcherApps.startMainActivity(app.component, Process.myUserHandle(), android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()), opts)
            launched = true
        } catch (_: Exception) {}
    }

    // Window-level blur of what's behind (the wallpaper) while overlays are open, Android 12+.
    fun setWindowBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled) {
            try { window.setBackgroundBlurRadius(if (on) 80 else 0) } catch (_: Exception) {}
        }
    }

    // --- Shortcuts ---
    fun shortcutsFor(pkg: String): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        return try { launcherApps.getShortcuts(q, Process.myUserHandle()).orEmpty().sortedBy { it.rank } } catch (_: Exception) { emptyList() }
    }

    fun shortcutIcon(sc: ShortcutInfo): ImageBitmap? = try {
        launcherApps.getShortcutIconDrawable(sc, resources.displayMetrics.densityDpi)?.let { renderIcon(it, false, 72).asImageBitmap() }
    } catch (_: Exception) { null }

    fun startShortcut(sc: ShortcutInfo, r: Rect) {
        try { launcherApps.startShortcut(sc, android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()), null); launched = true } catch (_: Exception) {}
    }

    fun isSystemApp(pkg: String) = try { packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0 } catch (_: Exception) { true }

    fun uninstall(pkg: String) { openSafe(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }

    fun hasWallpaperAccess() = Build.VERSION.SDK_INT > 32 ||
        checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun requestWallpaperAccess() { if (Build.VERSION.SDK_INT <= 32) requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE) }
    @Deprecated("Deprecated in Java") override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        @Suppress("DEPRECATION") super.onRequestPermissionsResult(rc, p, g); if (rc == REQ_STORAGE) { wallId = -2; checkWallpaper(); resumeCount.intValue++ }
    }

    // --- Default launcher ---
    fun isDefaultLauncher(): Boolean {
        val r = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == packageName
    }

    fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION") startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE); return
            }
        }
        openSafe(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    // --- Widgets ---
    fun requestWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidget = id
        if (widgetMgr.bindAppWidgetIdIfAllowed(id, info.provider)) configureWidget(id)
        else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            @Suppress("DEPRECATION") startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureWidget(id: Int) {
        val info = widgetMgr.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try { widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null); return } catch (_: Exception) {}
        }
        state.addWidget(id)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget) ?: pendingWidget
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) configureWidget(id) else widgetHost.deleteAppWidgetId(id)
            REQ_CONFIG -> if (resultCode == RESULT_OK) state.addWidget(id) else widgetHost.deleteAppWidgetId(id)
        }
    }

    companion object { const val REQ_BIND = 11; const val REQ_CONFIG = 12; const val REQ_ROLE = 13; const val REQ_STORAGE = 14 }
}

val iosSpring = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
