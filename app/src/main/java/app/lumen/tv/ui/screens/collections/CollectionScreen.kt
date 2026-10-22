package app.lumen.tv.ui.screens.collections

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import app.lumen.tv.data.model.MovieCollection
import app.lumen.tv.ui.components.AvailabilityBadge
import app.lumen.tv.ui.components.CardShape
import app.lumen.tv.ui.components.HeroBackdrop
import app.lumen.tv.ui.components.LumenCard
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.SheetHeading
import app.lumen.tv.ui.components.SheetRow
import app.lumen.tv.ui.components.ShelfRow
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.components.TitleLogo
import app.lumen.tv.ui.components.rememberCollectionLogo
import app.lumen.tv.ui.components.availabilityColor
import app.lumen.tv.ui.components.availabilityLabel
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenLayout
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CollectionScreen(
    tmdbId: Int,
    openRequest: Boolean,
    onOpen: (DiscoverItem) -> Unit,
    viewModel: CollectionViewModel = koinViewModel(key = "collection-$tmdbId") { parametersOf(tmdbId, openRequest) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collection = state.collection
    BackHandler(enabled = state.sheetOpen) { viewModel.dismissSheet() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK || !state.sheetOpen) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) viewModel.dismissSheet()
                true
            },
    ) {
        val headerMin = maxHeight * LumenLayout.DetailHeaderMinFraction
        HeroBackdrop(url = collection?.ref?.backdropUrl)
        when {
            state.error -> Text(
                stringResource(R.string.detail_error),
                style = MaterialTheme.typography.titleLarge,
                color = LumenColors.OnSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            collection == null -> Spinner(Modifier.align(Alignment.Center))
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = LumenSpacing.XXL),
                verticalArrangement = Arrangement.spacedBy(LumenSpacing.L),
            ) {
                item(key = "header") {
                    Box(Modifier.heightIn(min = headerMin).fillMaxWidth().padding(top = LumenSpacing.XXL), contentAlignment = Alignment.BottomStart) {
                        Header(collection, state, viewModel)
                    }
                }
                item(key = "films") {
                    val entry = remember { FocusRequester() }
                    ShelfRow(title = stringResource(R.string.collection_films), entry = entry) {
                        items(collection.parts, key = { it.tmdbId }) { film ->
                            LumenCard(
                                title = film.title,
                                imageUrl = film.posterUrl,
                                shape = CardShape.Poster,
                                subtitle = film.year?.toString(),
                                captionOnFocusOnly = false,
                                onClick = { onOpen(film) },
                                badge = { AvailabilityBadge(film.availability) },
                                modifier = if (film == collection.parts.first()) Modifier.focusRequester(entry) else Modifier,
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.sheetOpen && collection != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (collection != null) RequestSheet(collection, state, viewModel)
        }
    }
}

@Composable
private fun Header(collection: MovieCollection, state: CollectionState, viewModel: CollectionViewModel) {
    val primary = remember { FocusRequester() }
    LaunchedEffect(collection.ref.tmdbId, state.sheetOpen) {
        if (!state.sheetOpen) runCatching { primary.requestFocus() }
    }
    Column(Modifier.widthIn(max = 680.dp).padding(horizontal = LumenSpacing.SafeHorizontal)) {
        TitleLogo(
            title = collection.ref.name,
            logoUrl = rememberCollectionLogo(collection.ref.tmdbId),
            style = MaterialTheme.typography.displayLarge,
            maxHeight = 110.dp,
            maxWidth = 420.dp,
        )
        Spacer(Modifier.height(LumenSpacing.M))
        Text(
            listOf(
                pluralStringResource(R.plurals.collection_films_count, collection.parts.size, collection.parts.size),
                stringResource(R.string.collection_in_library, collection.available, collection.parts.size),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelLarge,
            color = LumenColors.OnSurfaceMuted,
        )
        collection.overview?.let {
            Spacer(Modifier.height(LumenSpacing.M))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(LumenSpacing.L))
        val missing = collection.missing.size
        PillButton(
            text = when {
                state.loadingOptions || state.requesting -> stringResource(R.string.discover_requesting)
                missing == 0 -> stringResource(R.string.collection_all_requested)
                missing == collection.parts.size -> stringResource(R.string.collection_request_all)
                else -> pluralStringResource(R.plurals.collection_request_missing, missing, missing)
            },
            onClick = viewModel::openSheet,
            style = if (missing > 0) PillStyle.Primary else PillStyle.Secondary,
            enabled = missing > 0 && !state.loadingOptions && !state.requesting,
            modifier = Modifier.focusRequester(primary),
            leading = if (missing > 0) ({ Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp)) }) else null,
        )
        state.outcome?.let {
            Spacer(Modifier.height(LumenSpacing.M))
            Text(
                when (it) {
                    is CollectionOutcome.Sent -> pluralStringResource(R.plurals.collection_requested, it.count, it.count)
                    CollectionOutcome.Failed -> stringResource(R.string.discover_request_failed)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (it is CollectionOutcome.Sent) LumenColors.Available else LumenColors.Error,
            )
        }
    }
}

@Composable
private fun RequestSheet(collection: MovieCollection, state: CollectionState, viewModel: CollectionViewModel) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
        withFrameNanos { }
        listState.scrollToItem(0)
    }
    val options = state.options
    val showVersion = options?.canCustomise == true && state.has4kChoice
    val showServer = options?.canCustomise == true && state.serversForVersion.size > 1
    val profiles = if (options?.canCustomise == true) state.server?.profiles.orEmpty() else emptyList()
    val missing = collection.missing
    val firstKey = when {
        missing.size > 1 -> "films-all"
        else -> "film-${missing.firstOrNull()?.tmdbId}"
    }
    fun focusFor(key: String): Modifier = if (key == firstKey) Modifier.focusRequester(firstFocus) else Modifier

    Column(
        Modifier
            .fillMaxHeight()
            .width(420.dp)
            .padding(LumenSpacing.L)
            .background(LumenColors.Glass.copy(alpha = 0.94f), LumenShapes.Panel)
            .border(1.dp, LumenColors.GlassStroke, LumenShapes.Panel)
            .padding(LumenSpacing.M),
    ) {
        Text(
            stringResource(R.string.discover_request_options),
            style = MaterialTheme.typography.titleLarge,
            color = LumenColors.OnSurface,
            modifier = Modifier.padding(start = LumenSpacing.M, bottom = LumenSpacing.S),
        )
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(vertical = LumenSpacing.S),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SheetHeading(stringResource(R.string.collection_films)) }
            if (missing.size > 1) {
                item {
                    SheetRow(
                        label = stringResource(R.string.collection_all_films),
                        checked = state.selected.containsAll(missing.map { it.tmdbId }),
                        onClick = viewModel::toggleAll,
                        modifier = focusFor("films-all"),
                    )
                }
            }
            items(collection.parts, key = { "film-${it.tmdbId}" }) { film ->
                val enabled = film.availability == Availability.None
                SheetRow(
                    label = film.title,
                    detail = availabilityLabel(film.availability) ?: film.year?.toString(),
                    detailColor = if (enabled) LumenColors.OnSurfaceFaint else availabilityColor(film.availability),
                    checked = film.tmdbId in state.selected,
                    enabled = enabled,
                    onClick = { viewModel.toggle(film.tmdbId) },
                    modifier = focusFor("film-${film.tmdbId}"),
                )
            }
            if (showVersion) {
                item { SheetHeading(stringResource(R.string.discover_version)) }
                item { SheetRow(label = stringResource(R.string.discover_version_standard), checked = !state.want4k, onClick = { viewModel.selectVersion(false) }) }
                item { SheetRow(label = stringResource(R.string.discover_version_4k), checked = state.want4k, onClick = { viewModel.selectVersion(true) }) }
            }
            if (showServer) {
                item { SheetHeading(stringResource(R.string.discover_server)) }
                items(state.serversForVersion, key = { "server-${it.id}" }) { server ->
                    SheetRow(label = server.name, checked = server.id == state.serverId, onClick = { viewModel.selectServer(server.id) })
                }
            }
            if (profiles.isNotEmpty()) {
                item { SheetHeading(stringResource(R.string.discover_quality)) }
                items(profiles, key = { "profile-${it.id}" }) { profile ->
                    SheetRow(
                        label = profile.name,
                        detail = if (profile.id == state.server?.defaultProfileId) stringResource(R.string.discover_default) else null,
                        checked = profile.id == state.profileId,
                        onClick = { viewModel.selectProfile(profile.id) },
                    )
                }
            }
        }
        PillButton(
            text = if (state.requesting) {
                stringResource(R.string.discover_requesting)
            } else {
                pluralStringResource(R.plurals.collection_request_films, state.selected.size, state.selected.size)
            },
            onClick = viewModel::confirm,
            style = PillStyle.Primary,
            enabled = !state.requesting && state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
