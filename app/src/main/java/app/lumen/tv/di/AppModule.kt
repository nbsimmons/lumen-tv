package app.lumen.tv.di

import app.lumen.tv.data.discovery.LocalDiscovery
import app.lumen.tv.DeviceClass
import app.lumen.tv.StartupWork
import app.lumen.tv.data.jellyfin.HomeCache
import app.lumen.tv.data.collections.CollectionIndex
import app.lumen.tv.data.logos.TitleLogos
import app.lumen.tv.ui.screens.collections.CollectionViewModel
import app.lumen.tv.ui.screens.collections.CollectionsViewModel
import app.lumen.tv.data.jellyfin.JellyfinClient
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.session.SecureCipher
import app.lumen.tv.data.session.SessionStore
import app.lumen.tv.data.settings.SettingsStore
import app.lumen.tv.ui.screens.settings.SettingsViewModel
import app.lumen.tv.data.model.DiscoverKind
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.data.seerr.SeerrRepository
import app.lumen.tv.data.ratings.ImdbRatings
import app.lumen.tv.BuildConfig
import app.lumen.tv.ui.screens.discover.DiscoverDetailViewModel
import app.lumen.tv.ui.screens.discover.DiscoverViewModel
import app.lumen.tv.ui.screens.discover.SeerrConnectViewModel
import app.lumen.tv.ui.screens.search.SearchViewModel
import app.lumen.tv.ui.screens.person.PersonViewModel
import app.lumen.tv.ui.screens.person.PersonRef
import app.lumen.tv.playback.DeviceProfileFactory
import app.lumen.tv.playback.PlaybackEvents
import app.lumen.tv.playback.PlaybackRepository
import app.lumen.tv.playback.TrailerStreamResolver
import app.lumen.tv.playback.createTrailerResolver
import app.lumen.tv.data.update.UpdateRepository
import app.lumen.tv.data.update.createUpdateInstaller
import app.lumen.tv.ui.screens.player.TrailerRequest
import app.lumen.tv.ui.screens.player.LiveRequest
import app.lumen.tv.data.livetv.LiveTvRepository
import app.lumen.tv.ui.screens.livetv.LiveTvViewModel
import app.lumen.tv.ui.screens.player.PlayerViewModel
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.lumen.tv.ui.RootViewModel
import app.lumen.tv.ui.screens.detail.DetailViewModel
import app.lumen.tv.ui.screens.library.LibraryViewModel
import app.lumen.tv.ui.screens.home.HomeViewModel
import app.lumen.tv.data.ratings.MediaRatings
import app.lumen.tv.ui.screens.onboarding.OnboardingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SecureCipher(androidContext()) }
    single { SessionStore(androidContext(), get()) }
    single { SettingsStore(androidContext()) }
    single { HomeCache(androidContext()) }
    single { UpdateRepository(androidContext(), get(), createUpdateInstaller(androidContext())) }
    single { TitleLogos(get(), get()) }
    single { CollectionIndex(androidContext(), get(), get()) }
    single { StartupWork(get(), get(), get(), get()) }
    single { JellyfinClient(androidContext(), get(), get(), get()) }
    single { JellyfinRepository(get(), get()) }
    single { LocalDiscovery(get<JellyfinClient>().sdk) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single { DeviceProfileFactory(androidContext()) }
    single { PlaybackEvents() }
    single { ImdbRatings(androidContext(), BuildConfig.IMDB_DATASET && !DeviceClass.isLowMemory(androidContext())) }
    single { SeerrRepository(get(), get(), get()) }
    single { MediaRatings(get(), get()) }
    single { PlaybackRepository(get(), get(), get(), get(), get(), get()) }
    viewModel { RootViewModel(get(), get(), get(), get()) }
    viewModel { OnboardingViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { (kind: MediaKind) -> LibraryViewModel(kind, get()) }
    viewModel { params -> DetailViewModel(params.get(), params.getOrNull(), get(), get(), get(), get()) }
    createTrailerResolver()?.let { resolver -> single<TrailerStreamResolver> { resolver } }
    viewModel { params ->
        PlayerViewModel(
            androidContext(),
            params.get<UUID>(),
            params.get<Long>(),
            get(),
            get(),
            get(),
            params.getOrNull<TrailerRequest>(),
            getOrNull(),
            params.getOrNull<LiveRequest>(),
            get(),
        )
    }
    single { LiveTvRepository(androidContext(), get()) }
    viewModel { LiveTvViewModel(get(), get()) }
    viewModel { DiscoverViewModel(get()) }
    viewModel { SeerrConnectViewModel(get(), get(), get()) }
    viewModel { CollectionsViewModel(get(), get()) }
    viewModel { (tmdbId: Int, openRequest: Boolean) -> CollectionViewModel(tmdbId, openRequest, get()) }
    viewModel { (kind: DiscoverKind, tmdbId: Int) -> DiscoverDetailViewModel(kind, tmdbId, get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { (ref: PersonRef) -> PersonViewModel(ref, get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}
