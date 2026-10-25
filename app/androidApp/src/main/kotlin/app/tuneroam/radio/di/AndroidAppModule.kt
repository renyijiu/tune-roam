package app.tuneroam.radio.di

import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.playback.MediaSessionCommands
import app.tuneroam.radio.presentation.CountriesViewModel
import app.tuneroam.radio.presentation.CountryStationsViewModel
import app.tuneroam.radio.presentation.FavoritesViewModel
import app.tuneroam.radio.presentation.HomeViewModel
import app.tuneroam.radio.presentation.PlayerViewModel
import app.tuneroam.radio.presentation.RoamViewModel
import app.tuneroam.radio.presentation.SettingsViewModel
import app.tuneroam.radio.presentation.TracksViewModel
import app.tuneroam.radio.snapshot.SnapshotScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Shell bindings. ViewModel classes live in sharedLogic; this module is the
 * Android half of what a SwiftUI shell will do with its own factory helpers.
 *
 * [PlaybackCommands] must be the declared type. Shared ViewModels request that
 * interface, and Koin does not treat the Media3 implementation as a substitute
 * unless it is registered as one.
 */
val androidAppModule = module {
    single { MediaSessionCommands(androidContext()) }
    single<PlaybackCommands> { get<MediaSessionCommands>() }
    single { SnapshotScheduler(androidContext(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { RoamViewModel(get(), get(), get(), get()) }
    viewModel { CountriesViewModel(get()) }
    viewModel { parameters ->
        CountryStationsViewModel(
            country = parameters.get(),
            catalog = get(),
            favorites = get(),
            commands = get(),
        )
    }
    viewModel { FavoritesViewModel(get(), get(), get()) }
    viewModel { TracksViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}
