package app.lumen.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import app.lumen.tv.ui.theme.LumenMotion
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import android.net.Uri
import app.lumen.tv.BuildConfig
import app.lumen.tv.data.model.Trailer
import app.lumen.tv.ui.screens.player.TrailerRequest
import app.lumen.tv.ui.screens.player.LiveRequest
import app.lumen.tv.ui.screens.livetv.LiveTvScreen
import app.lumen.tv.ui.screens.trailer.TrailerActivity
import androidx.compose.ui.platform.LocalContext
import app.lumen.tv.ui.components.LocalNavFocused
import app.lumen.tv.ui.screens.detail.DetailScreen
import app.lumen.tv.ui.screens.discover.DiscoverDetailScreen
import app.lumen.tv.ui.screens.discover.DiscoverScreen
import app.lumen.tv.ui.screens.search.SearchScreen
import app.lumen.tv.ui.screens.person.PersonScreen
import app.lumen.tv.ui.screens.person.PersonRef
import app.lumen.tv.ui.screens.settings.PrivacyScreen
import app.lumen.tv.ui.screens.settings.SettingsScreen
import app.lumen.tv.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.lumen.tv.ui.screens.library.LibraryScreen
import app.lumen.tv.ui.screens.player.PlayerScreen
import java.util.UUID
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lumen.tv.ui.components.TopDestination
import app.lumen.tv.ui.components.TopNav
import app.lumen.tv.ui.components.UpdateDialog
import app.lumen.tv.ui.screens.home.HomeScreen
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.screens.onboarding.OnboardingScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun LumenRoot(viewModel: RootViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state) {
        RootState.Loading -> Box(Modifier.fillMaxSize().background(LumenColors.Background), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(160.dp))
        }
        RootState.SignedOut -> OnboardingScreen()
        RootState.SignedIn -> MainShell()
    }
}

@Composable
private fun MainShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val topDestination = TopDestination.entries.firstOrNull { it.route == backStack?.destination?.route }
    var lastTop by remember { mutableStateOf(TopDestination.Home) }
    SideEffect { topDestination?.let { lastTop = it } }
    var navFocused by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    val openItem: (MediaItem) -> Unit = { item -> navController.navigate(detailRoute(item)) }
    val openDiscover: (DiscoverItem) -> Unit = { item -> navController.navigate("discover/${item.kind.name}/${item.tmdbId}") }
    val play: (MediaItem, Long) -> Unit = { item, ticks -> navController.navigate("play/${item.id}?start=$ticks") }
    val context = LocalContext.current
    val playTrailer: (Trailer, String, String?) -> Unit = { trailer, title, backdrop ->
        when {
            trailer is Trailer.Local -> navController.navigate("play/${trailer.id}?start=0")
            trailer is Trailer.YouTube && BuildConfig.NATIVE_TRAILERS ->
                navController.navigate("trailer/${trailer.key}?title=${Uri.encode(title)}&backdrop=${Uri.encode(backdrop.orEmpty())}")
            trailer is Trailer.YouTube -> context.startActivity(TrailerActivity.intent(context, trailer.key, title))
        }
    }

    CompositionLocalProvider(LocalNavFocused provides navFocused) {
        Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
            NavHost(
                navController = navController,
                startDestination = TopDestination.Home.route,
                modifier = Modifier.focusRequester(contentFocus).focusGroup(),
                enterTransition = { fadeIn(tween(LumenMotion.Medium, delayMillis = LumenMotion.Fast / 2, easing = LumenMotion.Standard)) },
                exitTransition = { fadeOut(tween(LumenMotion.Fast)) },
                popEnterTransition = { fadeIn(tween(LumenMotion.Medium, delayMillis = LumenMotion.Fast / 2, easing = LumenMotion.Standard)) },
                popExitTransition = { fadeOut(tween(LumenMotion.Fast)) },
            ) {
                composable(TopDestination.Home.route) { HomeScreen(onOpenItem = openItem) }
                composable(TopDestination.Movies.route) { LibraryScreen(kind = MediaKind.Movie, onOpenItem = openItem) }
                composable(TopDestination.Shows.route) { LibraryScreen(kind = MediaKind.Series, onOpenItem = openItem) }
                composable(TopDestination.LiveTv.route) { LiveTvScreen(onWatch = { channel -> navController.navigate("watch/${channel.key}") }) }
                composable("watch/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                    PlayerScreen(
                        itemId = UUID(0, 0),
                        startTicks = 0L,
                        onExit = { navController.popBackStack() },
                        live = LiveRequest(entry.arguments?.getString("key").orEmpty()),
                    )
                }
                composable(TopDestination.Discover.route) { DiscoverScreen(onOpen = openDiscover) }
                composable(TopDestination.Search.route) {
                    SearchScreen(
                        onOpenItem = openItem,
                        onOpenDiscover = openDiscover,
                        onPlay = { item -> play(item, item.resumeTicks) },
                        onOpenPerson = { person ->
                            val jf = person.jellyfinId?.let { "jf=$it&" }.orEmpty()
                            navController.navigate("person?${jf}tmdb=${person.tmdbId ?: -1}")
                        },
                        onOpenView = { view ->
                            navController.navigate(view.jellyfinId?.let { "item/$it" } ?: "discover/${view.kind.name}/${view.tmdbId}")
                        },
                    )
                }
                composable(
                    route = "person?jf={jf}&tmdb={tmdb}",
                    arguments = listOf(
                        navArgument("jf") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("tmdb") {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                    ),
                ) { entry ->
                    PersonScreen(
                        ref = PersonRef(
                            jellyfinId = entry.arguments?.getString("jf")?.let(UUID::fromString),
                            tmdbId = entry.arguments?.getInt("tmdb")?.takeIf { it > 0 },
                        ),
                        onOpenItem = openItem,
                        onOpenDiscover = openDiscover,
                    )
                }
                composable(TopDestination.Settings.route) { SettingsScreen(onOpenPrivacy = { navController.navigate("privacy") }) }
                composable("privacy") { PrivacyScreen() }
                composable(
                    route = "trailer/{key}?title={title}&backdrop={backdrop}",
                    arguments = listOf(
                        navArgument("key") { type = NavType.StringType },
                        navArgument("title") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("backdrop") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    PlayerScreen(
                        itemId = UUID(0, 0),
                        startTicks = 0L,
                        onExit = { navController.popBackStack() },
                        trailer = TrailerRequest(
                            youtubeKey = entry.arguments?.getString("key").orEmpty(),
                            title = entry.arguments?.getString("title").orEmpty(),
                            backdropUrl = entry.arguments?.getString("backdrop")?.ifBlank { null },
                        ),
                    )
                }
                composable(
                    route = "discover/{kind}/{tmdbId}",
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType },
                        navArgument("tmdbId") { type = NavType.IntType },
                    ),
                ) { entry ->
                    DiscoverDetailScreen(
                        kind = DiscoverKind.valueOf(entry.arguments?.getString("kind") ?: DiscoverKind.Movie.name),
                        tmdbId = entry.arguments?.getInt("tmdbId") ?: 0,
                        onWatch = { id -> navController.navigate("item/$id") },
                        onTrailer = playTrailer,
                        onOpenPerson = { tmdb -> navController.navigate("person?tmdb=$tmdb") },
                        onOpenDiscover = { item ->
                            navController.navigate(item.jellyfinId?.let { "item/$it" } ?: "discover/${item.kind.name}/${item.tmdbId}")
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "item/{id}?episode={episode}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("episode") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    val id = UUID.fromString(entry.arguments?.getString("id"))
                    val episode = entry.arguments?.getString("episode")?.let(UUID::fromString)
                    DetailScreen(
                        itemId = id,
                        focusEpisodeId = episode,
                        onPlay = play,
                        onOpenItem = openItem,
                        onTrailer = playTrailer,
                        onOpenPerson = { personId -> navController.navigate("person?jf=$personId") },
                    )
                }
                composable(
                    route = "play/{id}?start={start}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("start") {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                    ),
                ) { entry ->
                    PlayerScreen(
                        itemId = UUID.fromString(entry.arguments?.getString("id")),
                        startTicks = entry.arguments?.getLong("start") ?: 0L,
                        onExit = { navController.popBackStack() },
                    )
                }
            }
            AnimatedVisibility(
                visible = topDestination != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopNav(
                    current = topDestination ?: lastTop,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(TopDestination.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier
                        .onFocusChanged { navFocused = it.hasFocus }
                        .onPreviewKeyEvent { event ->
                            val down = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                            down && contentFocus.requestFocus()
                        },
                )
            }
            UpdateDialog()
        }
    }
}

private fun detailRoute(item: MediaItem): String =
    if (item.kind == MediaKind.Episode && item.seriesId != null) "item/${item.seriesId}?episode=${item.id}" else "item/${item.id}"
