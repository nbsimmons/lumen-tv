package app.lumen.tv.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import app.lumen.tv.ui.components.RoundIconButton
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.speech.RecognizerIntent
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.Availability
import app.lumen.tv.data.model.DiscoverItem
import app.lumen.tv.data.model.ExternalRatings
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.model.PersonResult
import app.lumen.tv.data.model.RecentView
import app.lumen.tv.ui.components.AvailabilityBadge
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.EdgeBringIntoViewSpec
import app.lumen.tv.ui.components.StaticBringIntoViewSpec
import app.lumen.tv.ui.components.HeroBackdrop
import app.lumen.tv.ui.components.LocalNavFocused
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.RatingBadges
import app.lumen.tv.ui.components.ShelfRow
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.components.TvKeyboard
import app.lumen.tv.ui.components.TvTextField
import app.lumen.tv.data.settings.SearchKeyboard
import androidx.compose.ui.text.input.ImeAction
import app.lumen.tv.ui.components.availabilityLabel
import app.lumen.tv.ui.components.metadataLine
import app.lumen.tv.ui.screens.discover.discoverMetadata
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val ROW_TOP = "top"
private const val ROW_RESULTS = "results"
private const val ROW_TRENDING = "trending"
private const val ROW_VIEWED = "viewed"
private val NavClearance = 88.dp
private val ListTail = 240.dp
private val TopImageMinHeight = 120.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onOpenItem: (MediaItem) -> Unit,
    onOpenDiscover: (DiscoverItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onOpenPerson: (PersonResult) -> Unit,
    onOpenView: (RecentView) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard by viewModel.keyboard.collectAsStateWithLifecycle()
    val restore = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val firstRecent = remember { FocusRequester() }
    val navFocused = LocalNavFocused.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val marginPx = with(LocalDensity.current) { LumenSpacing.SafeHorizontal.toPx() }
    val rowSpec = remember(marginPx) { EdgeBringIntoViewSpec(marginPx) }
    val listKeys = if (!state.searched) {
        listOfNotNull(
            "recent".takeIf { state.recent.isNotEmpty() },
            "viewed".takeIf { state.viewed.isNotEmpty() },
            "trending".takeIf { state.trending.isNotEmpty() },
        )
    } else {
        val nothing = state.library.isEmpty() && state.requestable.isEmpty() && state.top == null
        listOfNotNull(
            "empty".takeIf { nothing && !state.searching },
            "top".takeIf { state.top != null },
            "results".takeIf { state.library.isNotEmpty() || state.requestable.isNotEmpty() },
        )
    }

    fun Modifier.pinOnFocus(key: String): Modifier = onFocusChanged { focus ->
        if (!focus.hasFocus) return@onFocusChanged
        val index = listKeys.indexOf(key).takeIf { it >= 0 } ?: return@onFocusChanged
        scope.launch { listState.animateScrollToItem(index) }
    }
    LaunchedEffect(Unit) {
        if (navFocused) return@LaunchedEffect
        withFrameNanos { }
        if (viewModel.lastFocus != null) runCatching { restore.requestFocus() } else runCatching { fieldFocus.requestFocus() }
    }

    fun focusModifier(row: String, key: String, highlight: SearchHighlight?): Modifier {
        val last = viewModel.lastFocus
        val base = Modifier.onFocusChanged {
            if (it.isFocused) {
                viewModel.lastFocus = SearchFocus(row, key)
                highlight?.let(viewModel::highlight)
            }
        }
        return if (last?.row == row && last.key == key) base.focusRequester(restore) else base
    }

    val openLibrary: (MediaItem) -> Unit = {
        viewModel.rememberQuery()
        viewModel.rememberView(RecentView.of(it))
        onOpenItem(it)
    }
    val openDiscover: (DiscoverItem) -> Unit = {
        viewModel.rememberQuery()
        viewModel.rememberView(RecentView.of(it))
        if (it.jellyfinId != null) onOpenView(RecentView.of(it)) else onOpenDiscover(it)
    }
    val openPerson: (PersonResult) -> Unit = {
        viewModel.rememberQuery()
        onOpenPerson(it)
    }
    val play: (MediaItem) -> Unit = {
        viewModel.rememberQuery()
        onPlay(it)
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(url = state.highlight?.backdropUrl)
        Column(Modifier.fillMaxSize().padding(top = NavClearance)) {
            if (keyboard == SearchKeyboard.System) {
                Row(
                    Modifier.padding(horizontal = LumenSpacing.SafeHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M),
                ) {
                    TvTextField(
                        value = state.query,
                        onValueChange = viewModel::replaceQuery,
                        placeholder = stringResource(R.string.search_hint),
                        imeAction = ImeAction.Search,
                        width = null,
                        shape = LumenShapes.Pill,
                        containerColor = LumenColors.FieldFill,
                        focusedContainerColor = LumenColors.FieldFillFocused,
                        focusedScale = 1f,
                        placeholderColor = LumenColors.OnSurfaceMuted,
                        leading = {
                            Icon(
                                painterResource(R.drawable.ic_search),
                                contentDescription = null,
                                tint = LumenColors.OnSurfaceMuted,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailing = if (state.searching) ({ Spinner(size = 22.dp) }) else null,
                        modifier = Modifier.weight(1f).focusRequester(fieldFocus),
                    )
                    VoiceSearchButton(onResult = viewModel::replaceQuery)
                }
            } else Row(
                Modifier.fillMaxWidth().padding(horizontal = LumenSpacing.SafeHorizontal).height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = LumenColors.OnSurfaceMuted,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(LumenSpacing.M))
                Text(
                    text = state.query.ifEmpty { stringResource(R.string.search_hint) },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (state.query.isNotEmpty()) LumenColors.OnSurface else LumenColors.OnSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.searching) Spinner(size = 22.dp)
            }
            if (keyboard == SearchKeyboard.OneLine) {
                TvKeyboard(onKey = viewModel::type, onBackspace = viewModel::backspace, onClear = viewModel::clear)
            }
            Spacer(Modifier.height(LumenSpacing.S))
            CompositionLocalProvider(LocalBringIntoViewSpec provides StaticBringIntoViewSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = LumenSpacing.S, bottom = ListTail),
                    verticalArrangement = Arrangement.spacedBy(LumenSpacing.M),
                ) {
                    if (!state.searched) {
                        if (state.recent.isNotEmpty()) {
                            item(key = "recent") {
                                Column(Modifier.pinOnFocus("recent")) {
                                    SectionTitle(stringResource(R.string.search_recent))
                                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                                        LazyRow(
                                            modifier = Modifier.focusRestorer(firstRecent),
                                            contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
                                            horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S),
                                        ) {
                                            items(state.recent, key = { it }) { query ->
                                                PillButton(
                                                    text = query,
                                                    onClick = { viewModel.useRecent(query) },
                                                    modifier = if (query == state.recent.first()) Modifier.focusRequester(firstRecent) else Modifier,
                                                )
                                            }
                                            item(key = "clear-recent") {
                                                PillButton(text = stringResource(R.string.search_clear_recent), onClick = viewModel::clearRecent)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.viewed.isNotEmpty()) {
                            item(key = "viewed") {
                                val entry = remember { FocusRequester() }
                                ShelfRow(title = stringResource(R.string.search_recently_viewed), modifier = Modifier.pinOnFocus("viewed"), entry = entry) {
                                    items(state.viewed, key = { it.key }) { view ->
                                        LumenCard(
                                            title = view.title,
                                            imageUrl = view.posterUrl,
                                            shape = CardShape.Poster,
                                            onClick = {
                                                viewModel.rememberView(view)
                                                onOpenView(view)
                                            },
                                            badge = {
                                                val status = state.viewedStatus[view.key]?.takeIf { it != Availability.None }
                                                when {
                                                    status != null -> AvailabilityBadge(status)
                                                    view.jellyfinId != null -> InLibraryBadge()
                                                }
                                            },
                                            modifier = focusModifier(ROW_VIEWED, view.key, SearchHighlight.Viewed(view))
                                                .then(if (view == state.viewed.first()) Modifier.focusRequester(entry) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                        if (state.trending.isNotEmpty()) {
                            item(key = "trending") {
                                val entry = remember { FocusRequester() }
                                ShelfRow(title = stringResource(R.string.search_trending), modifier = Modifier.pinOnFocus("trending"), entry = entry) {
                                    items(state.trending, key = { "${it.kind}-${it.tmdbId}" }) { item ->
                                        LumenCard(
                                            title = item.title,
                                            imageUrl = item.posterUrl,
                                            shape = CardShape.Poster,
                                            onClick = {
                                                viewModel.rememberView(RecentView.of(item))
                                                if (item.jellyfinId != null) onOpenView(RecentView.of(item)) else onOpenDiscover(item)
                                            },
                                            badge = { AvailabilityBadge(item.availability) },
                                            modifier = focusModifier(ROW_TRENDING, "${item.kind}-${item.tmdbId}", SearchHighlight.Discover(item))
                                                .then(if (item == state.trending.first()) Modifier.focusRequester(entry) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val nothing = state.library.isEmpty() && state.requestable.isEmpty() && state.top == null
                        if (nothing && !state.searching) {
                            item(key = "empty") {
                                Text(
                                    stringResource(R.string.search_no_results, state.resultsFor),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LumenColors.OnSurfaceMuted,
                                    modifier = Modifier.padding(horizontal = LumenSpacing.SafeHorizontal),
                                )
                            }
                        }
                        state.top?.let { top ->
                            item(key = "top") {
                                TopResultCard(
                                    modifier = Modifier.pinOnFocus("top"),
                                    top = top,
                                    ratings = state.topRatings,
                                    onOpenItem = openLibrary,
                                    onOpenDiscover = openDiscover,
                                    onOpenPerson = openPerson,
                                    onPlay = play,
                                    buttonModifier = focusModifier(ROW_TOP, "primary", topHighlight(top)),
                                )
                            }
                        }
                        if (state.library.isNotEmpty() || state.requestable.isNotEmpty()) {
                            item(key = "results") {
                                val entry = remember { FocusRequester() }
                                val firstLibrary = state.library.firstOrNull()
                                val firstRequestable = state.requestable.firstOrNull().takeIf { firstLibrary == null }
                                ShelfRow(title = stringResource(R.string.search_movies_shows), modifier = Modifier.pinOnFocus("results"), entry = entry) {
                                    items(state.library, key = { "lib-${it.id}" }) { item ->
                                        LumenCard(
                                            title = item.title,
                                            imageUrl = item.posterUrl,
                                            shape = CardShape.Poster,
                                            onClick = { openLibrary(item) },
                                            badge = { InLibraryBadge() },
                                            modifier = focusModifier(ROW_RESULTS, "lib-${item.id}", SearchHighlight.Library(item))
                                                .then(if (item == firstLibrary) Modifier.focusRequester(entry) else Modifier),
                                        )
                                    }
                                    items(state.requestable, key = { "req-${it.kind}-${it.tmdbId}" }) { item ->
                                        LumenCard(
                                            title = item.title,
                                            imageUrl = item.posterUrl,
                                            shape = CardShape.Poster,
                                            onClick = { openDiscover(item) },
                                            badge = { AvailabilityBadge(item.availability) },
                                            modifier = focusModifier(ROW_RESULTS, "req-${item.kind}-${item.tmdbId}", SearchHighlight.Discover(item))
                                                .then(if (item == firstRequestable) Modifier.focusRequester(entry) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun topHighlight(top: TopResult): SearchHighlight? = when (top) {
    is TopResult.Library -> SearchHighlight.Library(top.item)
    is TopResult.Discover -> SearchHighlight.Discover(top.item)
    is TopResult.Person -> null
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = LumenColors.OnSurface,
        modifier = Modifier.padding(horizontal = LumenSpacing.SafeHorizontal),
    )
}

@Composable
private fun TopResultCard(
    top: TopResult,
    ratings: ExternalRatings?,
    onOpenItem: (MediaItem) -> Unit,
    onOpenDiscover: (DiscoverItem) -> Unit,
    onOpenPerson: (PersonResult) -> Unit,
    onPlay: (MediaItem) -> Unit,
    buttonModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = top,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        label = "top-result",
    ) { current ->
        val density = LocalDensity.current
        var textHeight by remember { mutableStateOf(TopImageMinHeight) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LumenSpacing.SafeHorizontal)
                .background(LumenColors.Glass, LumenShapes.Panel)
                .padding(LumenSpacing.M),
            verticalAlignment = Alignment.Top,
        ) {
            val image = when (current) {
                is TopResult.Library -> current.item.thumbUrl ?: current.item.backdropUrl
                is TopResult.Discover -> current.item.backdropUrl ?: current.item.posterUrl
                is TopResult.Person -> current.person.imageUrl
            }
            val frame = if (current is TopResult.Person) {
                Modifier.height(textHeight).aspectRatio(1f).clip(CircleShape)
            } else {
                Modifier.height(textHeight).aspectRatio(16f / 9f).clip(LumenShapes.Card)
            }
            Box(frame.background(LumenColors.Surface), contentAlignment = Alignment.Center) {
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(LumenSpacing.L))
            Column(Modifier.weight(1f).onSizeChanged { textHeight = maxOf(with(density) { it.height.toDp() }, TopImageMinHeight) }) {
                Text(stringResource(R.string.search_top_result), style = MaterialTheme.typography.labelMedium, color = LumenColors.Accent)
                val (title, meta, overview) = when (current) {
                    is TopResult.Library -> Triple(current.item.title, metadataLine(current.item, includeRating = ratings == null), current.item.overview)
                    is TopResult.Discover -> Triple(current.item.title, discoverMetadata(current.item, includeRating = ratings == null), current.item.overview)
                    is TopResult.Person -> Triple(current.person.name, stringResource(R.string.search_person), null)
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, color = LumenColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted, maxLines = 1) }
                ratings?.let {
                    Spacer(Modifier.height(LumenSpacing.XS))
                    RatingBadges(it)
                }
                overview?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(LumenSpacing.XS))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(LumenSpacing.M))
                Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
                    TopResultActions(current, onOpenItem, onOpenDiscover, onOpenPerson, onPlay, buttonModifier)
                }
            }
        }
    }
}

@Composable
private fun TopResultActions(
    top: TopResult,
    onOpenItem: (MediaItem) -> Unit,
    onOpenDiscover: (DiscoverItem) -> Unit,
    onOpenPerson: (PersonResult) -> Unit,
    onPlay: (MediaItem) -> Unit,
    buttonModifier: Modifier,
) {
    when (top) {
        is TopResult.Library -> {
            val playable = top.item.kind == MediaKind.Movie || top.item.kind == MediaKind.Episode
            if (playable) {
                PillButton(
                    text = stringResource(if (top.item.resumeTicks > 0) R.string.detail_resume else R.string.detail_play),
                    onClick = { onPlay(top.item) },
                    style = PillStyle.Primary,
                    modifier = buttonModifier,
                    leading = { Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
                PillButton(text = stringResource(R.string.search_details), onClick = { onOpenItem(top.item) })
            } else {
                PillButton(
                    text = stringResource(R.string.search_details),
                    onClick = { onOpenItem(top.item) },
                    style = PillStyle.Primary,
                    modifier = buttonModifier,
                )
            }
        }
        is TopResult.Discover -> PillButton(
            text = availabilityLabel(top.item.availability) ?: stringResource(R.string.discover_request),
            onClick = { onOpenDiscover(top.item) },
            style = PillStyle.Primary,
            modifier = buttonModifier,
            leading = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp)) },
        )
        is TopResult.Person -> PillButton(
            text = stringResource(R.string.search_view_person),
            onClick = { onOpenPerson(top.person) },
            style = PillStyle.Primary,
            modifier = buttonModifier,
        )
    }
}

@Composable
private fun BoxScope.InLibraryBadge() {
    Text(
        stringResource(R.string.search_in_library_badge),
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.OnFocusFill,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .background(LumenColors.OnSurface, LumenShapes.Pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun VoiceSearchButton(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val available = remember {
        context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    }
    if (!available) return
    val prompt = stringResource(R.string.search_voice_prompt)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onResult)
    }
    RoundIconButton(
        icon = R.drawable.ic_mic,
        contentDescription = stringResource(R.string.search_voice),
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            runCatching { launcher.launch(intent) }
        },
    )
}
