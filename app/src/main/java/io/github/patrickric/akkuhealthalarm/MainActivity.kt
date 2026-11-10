package io.github.patrickric.akkuhealthalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.LaunchedEffect
import io.github.patrickric.akkuhealthalarm.data.ThemeMode
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.service.MonitorService
import io.github.patrickric.akkuhealthalarm.ui.AppTheme
import io.github.patrickric.akkuhealthalarm.ui.HealthScreen
import io.github.patrickric.akkuhealthalarm.ui.HistoryScreen
import io.github.patrickric.akkuhealthalarm.ui.Overlay
import io.github.patrickric.akkuhealthalarm.ui.Screen
import io.github.patrickric.akkuhealthalarm.ui.SettingsScreen
import io.github.patrickric.akkuhealthalarm.ui.StartScreen
import io.github.patrickric.akkuhealthalarm.ui.rememberBatteryReading
import kotlinx.coroutines.launch

private const val WINDOW_DARK = 0xFF0F1512.toInt()
private const val WINDOW_LIGHT = 0xFFF4FAF6.toInt()

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The design is also kept in plain preferences, so the very first frame already has it.
        val ui = getSharedPreferences("ui", MODE_PRIVATE)
        val startMode = ui.getString("theme", null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.default
        applyWindowDesign(startMode)
        super.onCreate(savedInstanceState)
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val mode = settings?.themeMode ?: startMode
            LaunchedEffect(mode) {
                ui.edit().putString("theme", mode.name).apply()
                applyWindowDesign(mode)
            }
            AppTheme(mode) { AppRoot() }
        }
    }

    private fun applyWindowDesign(mode: ThemeMode) {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && night)
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable(if (dark) WINDOW_DARK else WINDOW_LIGHT))
    }

    override fun onStart() {
        super.onStart()
        MonitorService.start(this)
    }
}

private val tabs = listOf(
    Triple(Screen.START, Icons.Rounded.BatteryChargingFull, R.string.tab_start),
    Triple(Screen.HISTORY, Icons.Rounded.Timeline, R.string.history_title),
    Triple(Screen.HEALTH, Icons.Rounded.MonitorHeart, R.string.health_title),
)

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val repo = context.app.settings
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(Screen.START) }
    // Tab to return to when leaving the settings.
    var lastTab by rememberSaveable { mutableStateOf(Screen.START) }
    val reading by rememberBatteryReading()
    // Value to show in the history chart when coming from a tile on the health page.
    var chartRequest by rememberSaveable { mutableStateOf<Overlay?>(null) }
    BackHandler(enabled = screen != Screen.START) {
        screen = if (screen == Screen.SETTINGS) lastTab else Screen.START
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val s = settings ?: return@Box
        val update: ((AlarmSettings) -> AlarmSettings) -> Unit = { transform -> scope.launch { repo.update(transform) } }
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    when {
                        targetState == Screen.SETTINGS -> (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                        initialState == Screen.SETTINGS -> fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                        else -> fadeIn() togetherWith fadeOut()
                    }
                },
                label = "screen",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { target ->
                when (target) {
                    Screen.START -> StartScreen(s, reading, update) { screen = Screen.SETTINGS }
                    Screen.SETTINGS -> SettingsScreen(s, update) { screen = lastTab }
                    Screen.HISTORY -> HistoryScreen(s, chartRequest) { chartRequest = null }
                    Screen.HEALTH -> HealthScreen(reading, s, update) { overlay ->
                        chartRequest = overlay
                        screen = Screen.HISTORY
                        lastTab = Screen.HISTORY
                    }
                }
            }
            if (screen != Screen.SETTINGS) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { (tab, icon, label) ->
                        NavigationBarItem(
                            selected = screen == tab,
                            onClick = {
                                screen = tab
                                lastTab = tab
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        }
    }
}
