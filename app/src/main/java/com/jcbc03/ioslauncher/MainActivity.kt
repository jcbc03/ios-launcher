package com.jcbc03.ioslauncher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    lateinit var state: LauncherState
    val homeSignal = mutableIntStateOf(0)
    val resumeScale = Animatable(1f)
    val resumeTick = mutableIntStateOf(0)
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        state = LauncherState(this)
        setContent { LauncherRoot(this) }
    }

    override fun onResume() {
        super.onResume()
        state.reload()
        if (launched) { launched = false; resumeTick.intValue++ }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal.intValue++
    }

    fun launch(app: AppInfo, view: android.view.View, x: Int, y: Int, w: Int, h: Int) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName.unflattenFromString(app.key))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = ActivityOptions.makeScaleUpAnimation(view, x, y, w, h).toBundle()
        try { startActivity(i, opts); launched = true } catch (_: Exception) {}
    }
}

val iosSpring = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
