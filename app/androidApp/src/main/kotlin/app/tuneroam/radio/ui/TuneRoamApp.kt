package app.tuneroam.radio.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import app.tuneroam.radio.ui.country.CountriesScreen
import app.tuneroam.radio.ui.country.CountryStationsScreen
import app.tuneroam.radio.ui.favorites.FavoritesScreen
import app.tuneroam.radio.ui.home.HomeScreen
import app.tuneroam.radio.ui.player.MiniPlayerBar
import app.tuneroam.radio.ui.player.NowPlayingScreen
import app.tuneroam.radio.ui.roam.RoamScreen
import app.tuneroam.radio.ui.settings.SettingsScreen
import app.tuneroam.radio.ui.theme.Starfield
import app.tuneroam.radio.presentation.PlayerViewModel
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

    val showStars = route == null || route == Routes.HOME || route == Routes.COUNTRIES || route == Routes.ROAM
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (snapshot.station != null && route != Routes.PLAYER) {
                    MiniPlayerBar(onOpen = { navController.navigate(Routes.PLAYER) }, viewModel = player)
                }
                if (route != Routes.PLAYER) {
                    NavigationBar {
                        NavItem(Routes.ROAM, R.string.tab_roam, route == Routes.ROAM, Icons.Filled.Explore, navController)
                        NavItem(Routes.HOME, R.string.tab_home, route == Routes.HOME, Icons.Filled.Home, navController)
                        NavItem(
                            Routes.COUNTRIES,
                            R.string.tab_countries,
                            route == Routes.COUNTRIES || route == Routes.COUNTRY,
                            Icons.Filled.Public,
                            navController,
                        )
                        NavItem(
                            Routes.FAVORITES,
                            R.string.tab_favorites,
                            route == Routes.FAVORITES,
                            Icons.Filled.Favorite,
                            navController,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showStars) Starfield()
            NavHost(
                navController = navController,
                startDestination = Routes.ROAM,
                modifier = Modifier.fillMaxSize(),
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
                composable(Routes.PLAYER) {
                    NowPlayingScreen(onBack = { navController.popBackStack() }, viewModel = player)
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    route: String,
    label: Int,
    selected: Boolean,
    icon: ImageVector,
    navController: NavHostController,
) {
    NavigationBarItem(
        selected = selected,
        onClick = {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}
