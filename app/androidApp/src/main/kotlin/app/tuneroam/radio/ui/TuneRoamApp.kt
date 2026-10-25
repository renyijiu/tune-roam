package app.tuneroam.radio.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.presentation.PlayerViewModel
import app.tuneroam.radio.ui.country.CountriesScreen
import app.tuneroam.radio.ui.country.CountryStationsScreen
import app.tuneroam.radio.ui.favorites.FavoritesScreen
import app.tuneroam.radio.ui.home.HomeScreen
import app.tuneroam.radio.ui.player.MiniPlayerBar
import app.tuneroam.radio.ui.player.NowPlayingScreen
import app.tuneroam.radio.ui.player.PlayerBackdrop
import app.tuneroam.radio.ui.roam.RoamScreen
import app.tuneroam.radio.ui.settings.SettingsScreen
import app.tuneroam.radio.ui.theme.LocalArtworkVisibility
import app.tuneroam.radio.ui.theme.LocalSharedTransition
import app.tuneroam.radio.ui.theme.Starfield
import app.tuneroam.radio.ui.theme.focusRing
import app.tuneroam.radio.ui.theme.rememberWideLayout
import app.tuneroam.radio.ui.theme.usingRemote
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private object Routes {
    const val ROAM = "roam"
    const val HOME = "home"
    const val COUNTRIES = "countries"
    const val FAVORITES = "favorites"
    const val COUNTRY = "country/{name}"
    const val PLAYER = "player"
    const val SETTINGS = "settings"

    fun country(name: String) = "country/${Uri.encode(name)}"
}

/**
 * [pendingLink] is a station share link the activity received; it is opened once and
 * then reported back through [onLinkHandled].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TuneRoamApp(
    pendingLink: String? = null,
    onLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    // One player ViewModel for the mini bar, the player screen, and links, so a hint
    // raised in one place (opening a link, finding a similar station) shows in the other.
    val player: PlayerViewModel = koinViewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val coordinator = koinInject<PlaybackCoordinator>()
    val snapshot by coordinator.snapshot.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        player.openLink(link)
        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
        onLinkHandled()
    }

    val wide = rememberWideLayout()
    val remote = usingRemote()
    val railFocus = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }
    val showStars = route == null || route == Routes.HOME || route == Routes.COUNTRIES || route == Routes.ROAM
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransition provides this) {
            // Now Playing's aurora runs edge to edge, under the status bar.
            AnimatedVisibility(
                visible = route == Routes.PLAYER,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(300)),
            ) {
                PlayerBackdrop(snapshot, Modifier.fillMaxSize())
            }
            Scaffold(
                containerColor = if (route == Routes.PLAYER) Color.Transparent else MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                bottomBar = {
                    Column {
                        AnimatedVisibility(
                            visible = snapshot.station != null && route != Routes.PLAYER,
                            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(220)),
                            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
                        ) {
                            CompositionLocalProvider(LocalArtworkVisibility provides this) {
                                // Roam already has "random" of its own, so next stays in the country.
                                MiniPlayerBar(
                                    onOpen = { navController.navigate(Routes.PLAYER) },
                                    viewModel = player,
                                    onNext = if (route == Routes.ROAM) player::nextInCountry else player::next,
                                )
                            }
                        }
                        // Slides away rather than vanishing, so the screen above doesn't jump.
                        AnimatedVisibility(
                            visible = !wide && route != Routes.PLAYER,
                            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(220)),
                            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
                        ) {
                            NavigationBar {
                                Tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = tab.selected(route),
                                        onClick = { navController.openTab(tab.route) },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(stringResource(tab.label)) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Landscape keeps its height for content: the tabs move to a rail on the left,
                    // where a remote reaches them with a press to the left.
                    AnimatedVisibility(
                        visible = wide && route != Routes.PLAYER,
                        enter = slideInHorizontally(tween(320)) { -it } + fadeIn(tween(220)),
                        exit = slideOutHorizontally(tween(260)) { -it } + fadeOut(tween(200)),
                    ) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .onFocusChanged { railFocused = it.hasFocus },
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            Spacer(Modifier.weight(1f))
                            Tabs.forEach { tab ->
                                NavigationRailItem(
                                    selected = tab.selected(route),
                                    onClick = { navController.openTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.label)) },
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .focusRing(RoundedCornerShape(16.dp), outset = (-2).dp)
                                        .then(if (tab.selected(route)) Modifier.focusRequester(railFocus) else Modifier),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (showStars) Starfield()
                        NavHost(
                            navController = navController,
                            startDestination = Routes.ROAM,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = { fadeIn(tween(220, delayMillis = 60)) },
                            exitTransition = { fadeOut(tween(160)) },
                            popEnterTransition = { fadeIn(tween(220, delayMillis = 60)) },
                            popExitTransition = { fadeOut(tween(160)) },
                        ) {
                            composable(Routes.ROAM) { RoamScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) }) }
                            composable(Routes.HOME) { HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) }) }
                            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
                            composable(Routes.COUNTRIES) {
                                CountriesScreen(onCountry = { name -> navController.navigate(Routes.country(name)) })
                            }
                            composable(
                                route = Routes.COUNTRY,
                                arguments = listOf(navArgument("name") { type = NavType.StringType }),
                            ) { entry ->
                                val decoded = Uri.decode(entry.arguments?.getString("name").orEmpty())
                                CountryStationsScreen(country = decoded, onBack = { navController.popBackStack() })
                            }
                            composable(Routes.FAVORITES) { FavoritesScreen() }
                            // Now Playing rises out of the mini player and sinks back into it.
                            composable(
                                route = Routes.PLAYER,
                                enterTransition = { slideInVertically(tween(380)) { it / 3 } + fadeIn(tween(260)) },
                                popExitTransition = { slideOutVertically(tween(280)) { it / 3 } + fadeOut(tween(200)) },
                            ) {
                                CompositionLocalProvider(LocalArtworkVisibility provides this) {
                                    NowPlayingScreen(onBack = { navController.popBackStack() }, viewModel = player)
                                }
                            }
                        }
                        // On the roam screen the arrows belong to the globe, so a remote can't
                        // walk left onto the rail. Back goes there first, as on most TV apps;
                        // a second Back does what it always does. Registered after NavHost so
                        // it is asked first.
                        BackHandler(enabled = wide && remote && !railFocused && Tabs.any { it.route == route }) {
                            railFocus.requestFocus()
                        }
                    }
                }
            }
        }
    }
}

private class Tab(val route: String, val label: Int, val icon: ImageVector, val routes: Set<String> = setOf(route)) {
    fun selected(current: String?) = current in routes
}

private val Tabs = listOf(
    Tab(Routes.ROAM, R.string.tab_roam, Icons.Filled.Explore),
    Tab(Routes.HOME, R.string.tab_search, Icons.Filled.Search),
    Tab(Routes.COUNTRIES, R.string.tab_countries, Icons.Filled.Public, setOf(Routes.COUNTRIES, Routes.COUNTRY)),
    Tab(Routes.FAVORITES, R.string.tab_favorites, Icons.Filled.Favorite),
)

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
