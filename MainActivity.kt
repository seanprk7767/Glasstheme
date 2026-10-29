package com.raam.glasslauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val label: String, val pkg: String, val icon: Bitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val apps = loadApps()
        setContent { Launcher(apps) { launch(it) } }
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm).toBitmap(160, 160)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun launch(app: AppEntry) {
        packageManager.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
    }
}

/** Shared wallpaper. Swap for your own bitmap; glass panels re-draw this behind themselves. */
@Composable
fun Wallpaper() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF1B1464), Color(0xFF6A2C91), Color(0xFFE0457B), Color(0xFFFFA45B)))
        )
    ) {
        Box(Modifier.offset(40.dp, 120.dp).size(260.dp).clip(RoundedCornerShape(50))
            .background(Brush.radialGradient(listOf(Color(0xAA4FC3F7), Color.Transparent))))
        Box(Modifier.align(Alignment.CenterEnd).offset(60.dp, 80.dp).size(320.dp).clip(RoundedCornerShape(50))
            .background(Brush.radialGradient(listOf(Color(0x99FFEB3B), Color.Transparent))))
    }
}

@Composable
fun Launcher(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    var drawerOpen by remember { mutableStateOf(false) }
    val dock = remember(apps) { apps.take(4) }
    BackHandler(drawerOpen) { drawerOpen = false }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var dy = 0f
            detectVerticalDragGestures(
                onDragStart = { dy = 0f },
                onDragEnd = { if (dy < -120f) drawerOpen = true },
            ) { _, d -> dy += d }
        }
    ) {
        Wallpaper()

        // Home: clock card + dock
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
            GlassPanel(RoundedCornerShape(32.dp), 32f, Modifier.fillMaxWidth().height(140.dp), { Wallpaper() }) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    val t = remember { java.text.SimpleDateFormat("HH:mm").format(java.util.Date()) }
                    Text(t, color = Color.White, fontSize = 52.sp)
                    Text("Swipe up for apps", color = Color.White.copy(0.7f), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            GlassPanel(RoundedCornerShape(36.dp), 36f, Modifier.fillMaxWidth().height(96.dp), { Wallpaper() }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    dock.forEach { AppIcon(it, showLabel = false, onLaunch) }
                }
            }
        }

        AnimatedVisibility(drawerOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Drawer(apps, onLaunch) { drawerOpen = false }
        }
    }
}

@Composable
fun Drawer(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, close: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) { apps.filter { it.label.contains(query, ignoreCase = true) } }
    Box(Modifier.fillMaxSize()) {
        GlassPanel(RoundedCornerShape(0.dp), 0f, Modifier.fillMaxSize(), { Wallpaper() }) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                GlassPanel(RoundedCornerShape(24.dp), 24f, Modifier.fillMaxWidth().height(52.dp), { Wallpaper() }) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search apps", color = Color.White.copy(0.6f), fontSize = 16.sp)
                        BasicTextField(query, { query = it }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(shown, key = { it.pkg }) { AppIcon(it, true) { a -> close(); onLaunch(a) } }
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, showLabel: Boolean, onLaunch: (AppEntry) -> Unit) {
    Column(Modifier.clickable { onLaunch(app) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon.asImageBitmap(), app.label, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
        if (showLabel) Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}
