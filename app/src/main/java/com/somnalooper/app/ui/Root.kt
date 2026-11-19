package com.somnalooper.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.somnalooper.app.AppContainer
import com.somnalooper.app.R
import com.somnalooper.app.audio.AddResult
import com.somnalooper.app.audio.StopReason
import com.somnalooper.app.container
import com.somnalooper.app.ui.components.ActiveLayersSheet
import com.somnalooper.app.ui.components.MiniPlayer
import com.somnalooper.app.ui.components.ProDialog
import com.somnalooper.app.ui.focus.FocusScreen
import com.somnalooper.app.ui.mixer.MixerScreen
import com.somnalooper.app.ui.presets.PresetsScreen
import com.somnalooper.app.ui.presets.SavePresetDialog
import com.somnalooper.app.ui.settings.HistoryScreen
import com.somnalooper.app.ui.settings.SettingsScreen
import com.somnalooper.app.ui.timer.TimerScreen
import com.somnalooper.app.ui.zen.ZenScreen
import kotlinx.coroutines.launch

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }

/** UI-wide events (snackbars, Pro upsell) so every screen reacts to add-layer results the same way. */
class UiActions(
    val showMessage: (String) -> Unit,
    val showPro: () -> Unit,
) {
    fun handle(result: AddResult, limitText: String) {
        when (result) {
            AddResult.LOCKED -> showPro()
            AddResult.LIMIT -> showMessage(limitText)
            else -> Unit
        }
    }
}

val LocalUiActions = staticCompositionLocalOf<UiActions> { error("No actions") }

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("mixer", R.string.tab_mixer, Icons.Outlined.Tune),
    Tab("presets", R.string.tab_presets, Icons.Outlined.Bookmarks),
    Tab("timer", R.string.tab_timer, Icons.Outlined.Timer),
    Tab("focus", R.string.tab_focus, Icons.Outlined.CenterFocusStrong),
    Tab("settings", R.string.tab_settings, Icons.Outlined.Settings),
)

@Composable
fun SomnaRoot(activity: Activity) {
    val context = LocalContext.current
    val container = context.container
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPro by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    val actions = remember {
        UiActions(
            showMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
            showPro = { showPro = true },
        )
    }
    val engineState by container.engine.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val inZen = route == "zen"

    // Explain automatic stops once.
    LaunchedEffect(engineState.lastStopReason) {
        val msg = when (engineState.lastStopReason) {
            StopReason.MOTION -> context.getString(R.string.msg_stopped_motion)
            StopReason.BATTERY -> context.getString(R.string.msg_stopped_battery)
            StopReason.TIMER -> context.getString(R.string.msg_stopped_timer)
            else -> null
        }
        if (msg != null) snackbar.showSnackbar(msg)
    }
    LaunchedEffect(engineState.errorKey) {
        if (engineState.errorKey != null) {
            snackbar.showSnackbar(context.getString(R.string.msg_file_error))
            container.engine.clearError()
        }
    }

    CompositionLocalProvider(LocalContainer provides container, LocalUiActions provides actions) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedVisibility(!inZen, enter = fadeIn(), exit = fadeOut()) {
                    Column {
                        MiniPlayer(
                            onOpenTimer = { navigateTab(nav, "timer") },
                            onSave = { showSave = true },
                            onOpenLayers = { showLayers = true },
                        )
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = route == tab.route,
                                    onClick = { navigateTab(nav, tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(if (inZen) androidx.compose.foundation.layout.PaddingValues() else padding)) {
                NavHost(nav, startDestination = "mixer") {
                    composable("mixer") { MixerScreen() }
                    composable("presets") { PresetsScreen(onSaveCurrent = { showSave = true }) }
                    composable("timer") { TimerScreen() }
                    composable("focus") { FocusScreen(onZen = { nav.navigate("zen") }) }
                    composable("settings") { SettingsScreen(activity = activity, onHistory = { nav.navigate("history") }, onPro = { showPro = true }, onZen = { nav.navigate("zen") }) }
                    composable("history") { HistoryScreen(onBack = { nav.popBackStack() }) }
                    composable("zen") { ZenScreen(onExit = { nav.popBackStack() }) }
                }
            }
        }
        if (showPro) ProDialog(activity = activity, onDismiss = { showPro = false })
        if (showSave) SavePresetDialog(onDismiss = { showSave = false })
        if (showLayers) ActiveLayersSheet(onDismiss = { showLayers = false })
    }
}

private fun navigateTab(nav: androidx.navigation.NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
