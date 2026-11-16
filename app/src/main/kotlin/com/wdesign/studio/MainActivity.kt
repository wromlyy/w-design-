package com.wdesign.studio

import com.wdesign.ds.SafeLaunchedEffect
import com.wdesign.ds.rememberSafeScope
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wdesign.core.data.AppSettings
import com.wdesign.core.data.CrashLog
import com.wdesign.core.data.WD
import com.wdesign.core.model.DesignLink
import com.wdesign.core.model.DesignSource
import com.wdesign.ds.BarItem
import com.wdesign.ds.GlassBottomBar
import com.wdesign.ds.LocalHazeState
import com.wdesign.ds.LocalSnackbar
import com.wdesign.ds.Routes
import com.wdesign.ds.W
import com.wdesign.ds.WIcons
import com.wdesign.ds.WTheme
import com.wdesign.feature.designs.DesignsScreen
import com.wdesign.feature.designs.ImportScreen
import com.wdesign.feature.designs.PendingImport
import com.wdesign.feature.discover.DiscoverScreen
import com.wdesign.feature.discover.PresetDetailScreen
import com.wdesign.feature.home.CreateScreen
import com.wdesign.feature.home.HomeScreen
import com.wdesign.feature.lockscreen.LockScreenStudio
import com.wdesign.feature.settings.SettingsScreen
import com.wdesign.feature.xiaomilab.MtzExportScreen
import com.wdesign.feature.xiaomilab.XiaomiLabScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

class WDesignApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this) // writes filesDir/crash/last.txt, then delegates to the system handler
        WD.init(this) // lazy services only; no I/O here
    }
}

class MainActivity : ComponentActivity() {
    private val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WD.init(this)
        pendingIntent.value = intent
        setContent {
            val settings by WD.services.settings.settings.collectAsState(initial = AppSettings())
            WTheme(settings.themeMode, settings.reduceMotion, settings.reduceTransparency) {
                AppShell(pendingIntent.value) { pendingIntent.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }
}

private val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@Composable
private fun AppShell(intent: Intent?, consumed: () -> Unit) {
    val nav = rememberNavController()
    val hazeState = remember { HazeState() }
    val snackbar = remember { SnackbarHostState() }
    val reduceMotion = W.tokens.reduceMotion
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val linkInvalid = stringResource(R.string.link_invalid)
    val linkOpened = stringResource(R.string.link_imported)
    val rise = with(LocalDensity.current) { 16.dp.roundToPx() }

    SafeLaunchedEffect(intent) {
        val i = intent ?: return@SafeLaunchedEffect
        handleIntent(i, nav,
            onLink = { link ->
                val decoded = DesignLink.decode(link)
                if (decoded == null) snackbar.showSnackbar(linkInvalid) else {
                    val p = WD.services.projects.create(decoded.first, decoded.second, source = DesignSource.IMPORTED)
                    snackbar.showSnackbar(linkOpened.format(p.name))
                    nav.navigate(Routes.lockscreen(p.id))
                }
            },
        )
        consumed()
    }

    val tabs = listOf(
        BarItem(Routes.HOME, stringResource(R.string.tab_home), WIcons.Home),
        BarItem(Routes.DISCOVER, stringResource(R.string.tab_discover), WIcons.Compass),
        BarItem(Routes.CREATE, stringResource(R.string.tab_create), WIcons.Plus),
        BarItem(Routes.DESIGNS, stringResource(R.string.tab_designs), WIcons.Layers),
        BarItem(Routes.SETTINGS, stringResource(R.string.tab_settings), WIcons.Settings),
    )
    val go: (String) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { if (!nav.popBackStack()) go(Routes.HOME) }

    CompositionLocalProvider(LocalHazeState provides hazeState, LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize().background(W.colors.surface)) {
            NavHost(
                nav, startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize().haze(hazeState),
                enterTransition = { if (reduceMotion) fadeIn(tween(150)) else fadeIn(tween(280, easing = Emphasized)) + slideInVertically(tween(280, easing = Emphasized)) { rise } },
                exitTransition = { fadeOut(tween(if (reduceMotion) 150 else 200)) },
                popEnterTransition = { fadeIn(tween(if (reduceMotion) 150 else 280)) },
                popExitTransition = { fadeOut(tween(if (reduceMotion) 150 else 200)) },
            ) {
                composable(Routes.HOME) { HomeScreen(go) }
                composable(Routes.DISCOVER) { DiscoverScreen(go) }
                composable(Routes.CREATE) { CreateScreen(go) }
                composable(Routes.DESIGNS) { DesignsScreen(go, snackbar) }
                composable(Routes.SETTINGS) { SettingsScreen(go, snackbar) }
                composable(Routes.XIAOMI_LAB) { XiaomiLabScreen(go, back, snackbar) }
                composable(Routes.IMPORT) { ImportScreen(go, back, snackbar) }
                composable(Routes.PRESET, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    PresetDetailScreen(e.arguments?.getString("id").orEmpty(), go, back, snackbar)
                }
                composable("lockscreen?project={project}", arguments = listOf(navArgument("project") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                    LockScreenStudio(e.arguments?.getString("project"), go, back, snackbar)
                }
                composable("mtz?project={project}", arguments = listOf(navArgument("project") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                    MtzExportScreen(e.arguments?.getString("project"), go, back, snackbar)
                }
            }
            val route = currentRoute?.substringBefore('?')
            if (route in Routes.TABS) {
                GlassBottomBar(tabs, route, onSelect = { r ->
                    nav.navigate(r) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
                }, modifier = Modifier.align(Alignment.BottomCenter))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

private suspend fun handleIntent(i: Intent, nav: NavHostController, onLink: suspend (String) -> Unit) {
    val data: Uri? = i.data ?: if (i.action == Intent.ACTION_SEND) {
        @Suppress("DEPRECATION") (i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
    } else null
    if (data == null) return
    when {
        data.scheme == "wdesign" && data.host == "open" -> {
            val target = when (data.pathSegments.firstOrNull()) {
                "home" -> Routes.HOME
                "discover" -> Routes.DISCOVER
                "create" -> Routes.CREATE
                "designs" -> Routes.DESIGNS
                "settings" -> Routes.SETTINGS
                "xiaomilab" -> Routes.XIAOMI_LAB
                "lockscreen-demo", "studio-demo" -> Routes.lockscreen(null)
                "mtz" -> Routes.mtz(null)
                "import" -> Routes.IMPORT
                else -> Routes.HOME
            }
            nav.navigate(target) { launchSingleTop = true }
        }
        data.scheme == "wdesign" && data.host == "design" -> onLink(data.toString())
        data.scheme == "content" || data.scheme == "file" -> {
            PendingImport.uri.value = data
            nav.navigate(Routes.IMPORT) { launchSingleTop = true }
        }
    }
}
