package app.tuneroam.radio.di

import app.tuneroam.radio.playback.MediaSessionCommands
import app.tuneroam.radio.presentation.CountriesViewModel
import app.tuneroam.radio.presentation.CountryStationsViewModel
import app.tuneroam.radio.presentation.FavoritesViewModel
import app.tuneroam.radio.presentation.HomeViewModel
import app.tuneroam.radio.presentation.PlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Shell bindings. ViewModel classes live in sharedLogic; this module is the
 * Android half of what a SwiftUI shell will do with its own factory helpers.
 */
val androidAppModule = module {
    single { MediaSessionCommands(androidContext()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { CountriesViewModel(get()) }
    viewModel { parameters ->
        CountryStationsViewModel(
            country = parameters.get(),
            catalog = get(),
            favorites = get(),
            commands = get(),
        )
    }
    viewModel { FavoritesViewModel(get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get(), get()) }
}
