package com.jcbc03.ioslauncher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.minOf
import kotlin.math.sin

// Custom Squircle shape (superellipse)
class SquircleShape(private val radius: Float = 0.6f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            val curve = radius * minOf(width, height) / 2f

            moveTo(0f, curve)
            cubicTo(0f, 0f, 0f, 0f, curve, 0f)
            lineTo(width - curve, 0f)
            cubicTo(width, 0f, width, 0f, width, curve)
            lineTo(width, height - curve)
            cubicTo(width, height, width, height, width - curve, height)
            lineTo(curve, height)
            cubicTo(0f, height, 0f, height, 0f, height - curve)
            close()
        }
        return Outline.Generic(path)
    }
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    val componentName: ComponentName
)

data class Folder(
    val id: String,
    var name: String,
    val apps: MutableList<AppInfo> = mutableListOf()
)

data class HomeLayout(
    val pages: MutableList<MutableList<AppInfo>> = mutableListOf(),
    val dock: MutableList<AppInfo> = mutableListOf(),
    val folders: MutableList<Folder> = mutableListOf()
)

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    private lateinit var packageManager: PackageManager
    private lateinit var launcherApps: LauncherApps
    private var allApps by mutableStateOf(listOf<AppInfo>())
    private var homeLayout by mutableStateOf(HomeLayout())
    private var isJiggleMode by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var showSearch by mutableStateOf(false)
    private var showFolder by mutableStateOf<Folder?>(null)
    private var draggedApp by mutableStateOf<AppInfo?>(null)
    private var currentPage by mutableStateOf(0)

    private val PREFS_NAME = "ios_launcher_prefs"
    private val LAYOUT_KEY = "home_layout"

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        packageManager = packageManager
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        loadInstalledApps()
        loadLayout()

        // Ensure at least 4 pages, last is library
        if (homeLayout.pages.size < 3) {
            while (homeLayout.pages.size < 3) {
                homeLayout.pages.add(mutableListOf())
            }
            homeLayout.pages.add(mutableListOf()) // library page placeholder
        }

        setContent {
            IOSLauncherTheme {
                IOSLauncherUI(
                    allApps = allApps,
                    homeLayout = homeLayout,
                    isJiggleMode = isJiggleMode,
                    searchQuery = searchQuery,
                    showSearch = showSearch,
                    showFolder = showFolder,
                    draggedApp = draggedApp,
                    currentPage = currentPage,
                    onSearchChange = { searchQuery = it },
                    onSearchDismiss = { showSearch = false; searchQuery = "" },
                    onToggleJiggle = { isJiggleMode = !isJiggleMode },
                    onLaunchApp = ::launchApp,
                    onRemoveFromHome = ::removeFromHome,
                    onCreateFolder = ::createFolder,
                    onOpenFolder = { showFolder = it },
                    onFolderRename = { folder, newName -> folder.name = newName },
                    onDragStart = { draggedApp = it },
                    onDragEnd = { draggedApp = null },
                    onPageChange = { currentPage = it },
                    onSaveLayout = ::saveLayout,
                    onDockDrop = ::onDockDrop
                )
            }
        }
    }

    private fun loadInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        allApps = resolveInfos.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo
            val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
            val label = activityInfo.loadLabel(packageManager).toString()
            val iconDrawable = activityInfo.loadIcon(packageManager)
            val bitmap = drawableToBitmap(iconDrawable)
            val appInfo = packageManager.getApplicationInfo(activityInfo.packageName, 0)
            val category = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appInfo.category
            } else 0

            AppInfo(
                packageName = activityInfo.packageName,
                label = label,
                icon = bitmap,
                category = category,
                componentName = componentName
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        if (drawable is AdaptiveIconDrawable) {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = app.componentName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val options = ActivityOptions.makeScaleUpAnimation(
                window.decorView,
                0,
                0,
                window.decorView.width / 2,
                window.decorView.height / 2
            )
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e("Launcher", "Failed to launch ${app.label}", e)
        }
    }

    private fun loadLayout() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(LAYOUT_KEY, null)
        if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                // Parse pages, dock, folders - simplified for now, load default on error
            } catch (e: Exception) {
                Log.e("Launcher", "Failed to load layout", e)
            }
        }
        // Default layout: populate first 3 pages with sample apps, dock with 4
        if (homeLayout.pages.isEmpty() || homeLayout.dock.isEmpty()) {
            val popular = allApps.take(20)
            homeLayout.pages.clear()
            homeLayout.pages.add(popular.take(24).toMutableList()) // page 1
            homeLayout.pages.add(popular.drop(24).take(24).toMutableList()) // page 2
            homeLayout.pages.add(mutableListOf()) // page 3
            homeLayout.dock.addAll(popular.take(4))
        }
    }

    private fun saveLayout() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        // Simplified persistence - save pages and dock labels for demo
        val pagesArray = JSONArray()
        homeLayout.pages.forEach { page ->
            val pageArray = JSONArray()
            page.forEach { app ->
                pageArray.put(app.packageName)
            }
            pagesArray.put(pageArray)
        }
        json.put("pages", pagesArray)

        val dockArray = JSONArray()
        homeLayout.dock.forEach { app ->
            dockArray.put(app.packageName)
        }
        json.put("dock", dockArray)

        prefs.edit().putString(LAYOUT_KEY, json.toString()).apply()
    }

    private fun removeFromHome(app: AppInfo) {
        homeLayout.pages.forEach { page ->
            page.removeAll { it.packageName == app.packageName }
        }
        homeLayout.dock.removeAll { it.packageName == app.packageName }
        saveLayout()
    }

    private fun createFolder(app1: AppInfo, app2: AppInfo) {
        val folder = Folder(
            id = "${app1.packageName}_${app2.packageName}",
            name = "Folder"
        )
        folder.apps.add(app1)
        folder.apps.add(app2)
        homeLayout.folders.add(folder)

        // Remove from pages/dock
        homeLayout.pages.forEach { it.removeAll { a -> a.packageName == app1.packageName || a.packageName == app2.packageName } }
        homeLayout.dock.removeAll { it.packageName == app1.packageName || it.packageName == app2.packageName }

        saveLayout()
    }

    private fun onDockDrop(app: AppInfo) {
        if (homeLayout.dock.size < 4 && !homeLayout.dock.any { it.packageName == app.packageName }) {
            homeLayout.dock.add(app)
            saveLayout()
        }
    }

    companion object {
        const val NUM_ROWS = 6
        const val NUM_COLS = 4
    }
}

@Composable
fun IOSLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Transparent,
            surface = Color(0xB31E1E1E),
            onSurface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IOSLauncherUI(
    allApps: List<AppInfo>,
    homeLayout: HomeLayout,
    isJiggleMode: Boolean,
    searchQuery: String,
    showSearch: Boolean,
    showFolder: Folder?,
    draggedApp: AppInfo?,
    currentPage: Int,
    onSearchChange: (String) -> Unit,
    onSearchDismiss: () -> Unit,
    onToggleJiggle: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onRemoveFromHome: (AppInfo) -> Unit,
    onCreateFolder: (AppInfo, AppInfo) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onFolderRename: (Folder, String) -> Unit,
    onDragStart: (AppInfo) -> Unit,
    onDragEnd: () -> Unit,
    onPageChange: (Int) -> Unit,
    onSaveLayout: () -> Unit,
    onDockDrop: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 }) // 3 home + 1 library
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableStateOf(0f) }
    val wobbleRotation by animateFloatAsState(
        targetValue = if (isJiggleMode) 8f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "wobble"
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChange(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        offsetY += dragAmount
                        if (offsetY < -150f && !showSearch) {
                            showSearch = true // trigger via state in real impl
                        }
                    },
                    onDragEnd = { offsetY = 0f }
                )
            }
    ) {
        // Home Screen
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page < 3) {
                HomePage(
                    pageApps = homeLayout.pages.getOrElse(page) { emptyList() },
                    isJiggleMode = isJiggleMode,
                    wobbleRotation = wobbleRotation,
                    onLaunchApp = onLaunchApp,
                    onRemoveFromHome = onRemoveFromHome,
                    onDragStart = onDragStart,
                    onCreateFolder = onCreateFolder,
                    onLongPress = onToggleJiggle
                )
            } else {
                AppLibrary(
                    allApps = allApps,
                    searchQuery = searchQuery,
                    onLaunchApp = onLaunchApp,
                    onDragStart = onDragStart
                )
            }
        }

        // Page Dots
        PageDots(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )

        // Dock
        Dock(
            dockApps = homeLayout.dock,
            isJiggleMode = isJiggleMode,
            wobbleRotation = wobbleRotation,
            onLaunchApp = onLaunchApp,
            onDragStart = onDragStart,
            onDrop = onDockDrop,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Search Overlay (Spotlight)
        if (showSearch) {
            SpotlightSearch(
                allApps = allApps,
                query = searchQuery,
                onQueryChange = onSearchChange,
                onDismiss = onSearchDismiss,
                onLaunch = onLaunchApp
            )
        }

        // Folder Overlay
        showFolder?.let { folder ->
            FolderOverlay(
                folder = folder,
                onDismiss = { showFolder = null },
                onRename = onFolderRename,
                onLaunchApp = onLaunchApp
            )
        }

        // Jiggle mode Done button
        if (isJiggleMode) {
            Button(
                onClick = onToggleJiggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f))
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HomePage(
    pageApps: List<AppInfo>,
    isJiggleMode: Boolean,
    wobbleRotation: Float,
    onLaunchApp: (AppInfo) -> Unit,
    onRemoveFromHome: (AppInfo) -> Unit,
    onDragStart: (AppInfo) -> Unit,
    onCreateFolder: (AppInfo, AppInfo) -> Unit,
    onLongPress: () -> Unit
) {
    val gridApps = pageApps.take(24) // 4x6 = 24
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, bottom = 160.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(gridApps) { app ->
            AppIcon(
                app = app,
                isJiggleMode = isJiggleMode,
                wobbleRotation = wobbleRotation,
                onClick = { onLaunchApp(app) },
                onLongPress = onLongPress,
                onDragStart = { onDragStart(app) },
                showMinus = isJiggleMode,
                onRemove = { onRemoveFromHome(app) }
            )
        }
    }
}

@Composable
fun AppIcon(
    app: AppInfo,
    isJiggleMode: Boolean,
    wobbleRotation: Float,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    showMinus: Boolean = false,
    onRemove: () -> Unit = {}
) {
    val rotation by animateFloatAsState(
        targetValue = if (isJiggleMode) sin(System.currentTimeMillis() / 150.0 * PI).toFloat() * wobbleRotation else 0f,
        animationSpec = tween(80),
        label = "iconWobble"
    )

    val scale by animateFloatAsState(
        targetValue = if (isJiggleMode) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium
        )
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        onLongPress()
                    },
                    onTap = onClick
                )
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragCancel = {},
                    onDragEnd = {}
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(SquircleShape(0.65f))
                    .shadow(8.dp, SquircleShape(0.65f), spotColor = Color.Black.copy(alpha = 0.4f))
                    .background(Color.White)
            ) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(SquircleShape(0.6f)),
                    contentScale = ContentScale.Crop
                )

                if (showMinus) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .background(Color.Red, CircleShape)
                            .clickable { onRemove() }
                    ) {
                        Text(
                            text = "−",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(72.dp)
                    .shadow(2.dp, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun Dock(
    dockApps: List<AppInfo>,
    isJiggleMode: Boolean,
    wobbleRotation: Float,
    onLaunchApp: (AppInfo) -> Unit,
    onDragStart: (AppInfo) -> Unit,
    onDrop: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(bottom = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x80FFFFFF).copy(alpha = 0.25f))
            .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .padding(12.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            dockApps.forEach { app ->
                AppIcon(
                    app = app,
                    isJiggleMode = isJiggleMode,
                    wobbleRotation = wobbleRotation,
                    onClick = { onLaunchApp(app) },
                    onLongPress = {},
                    onDragStart = { onDragStart(app) },
                    showMinus = false
                )
            }
            // Empty slots for drag target
            repeat(4 - dockApps.size) {
                Box(modifier = Modifier.size(72.dp))
            }
        }
    }
}

@Composable
fun PageDots(
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(pagerState.pageCount) { index ->
            val isSelected = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 6.dp)
                    .background(
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotlightSearch(
    allApps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onLaunch: (AppInfo) -> Unit
) {
    val filtered = if (query.isBlank()) allApps else allApps.filter {
        it.label.contains(query, ignoreCase = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.6f)
                ),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filtered) { app ->
                    ListItem(
                        headlineContent = { Text(app.label, color = Color.White) },
                        leadingContent = {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(SquircleShape(0.6f))
                            )
                        },
                        modifier = Modifier
                            .clickable { onLaunch(app); onDismiss() }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppLibrary(
    allApps: List<AppInfo>,
    searchQuery: String,
    onLaunchApp: (AppInfo) -> Unit,
    onDragStart: (AppInfo) -> Unit
) {
    val categorized = allApps.groupBy { it.category }
    val queryFiltered = if (searchQuery.isBlank()) allApps else allApps.filter {
        it.label.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "App Library",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {}, // handled by parent
            placeholder = { Text("Search Apps") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                placeholderColor = Color.White.copy(alpha = 0.7f)
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Category tiles (simplified 2x2 per category)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            categorized.forEach { (cat, apps) ->
                item {
                    CategoryTile(
                        category = cat,
                        apps = apps.take(4),
                        onLaunch = onLaunchApp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("All Apps", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        LazyColumn {
            items(queryFiltered) { app ->
                ListItem(
                    headlineContent = { Text(app.label, color = Color.White) },
                    leadingContent = {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(SquircleShape(0.6f))
                        )
                    },
                    modifier = Modifier.clickable { onLaunchApp(app) }
                )
            }
        }
    }
}

@Composable
fun CategoryTile(
    category: Int,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit
) {
    val catName = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO -> "Music"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Photos"
        else -> "Other"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x40FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(catName, color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.take(4).forEach { app ->
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(SquircleShape(0.6f))
                            .clickable { onLaunch(app) }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderOverlay(
    folder: Folder,
    onDismiss: () -> Unit,
    onRename: (Folder, String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .blur(radius = 30.dp)
                .clickable(onClick = onDismiss)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .background(Color(0xFF2C2C2E), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = folder.name,
                    onValueChange = { onRename(folder, it) },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(folder.apps) { app ->
                        AppIcon(
                            app = app,
                            isJiggleMode = false,
                            wobbleRotation = 0f,
                            onClick = { onLaunchApp(app); onDismiss() },
                            onLongPress = {},
                            onDragStart = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
