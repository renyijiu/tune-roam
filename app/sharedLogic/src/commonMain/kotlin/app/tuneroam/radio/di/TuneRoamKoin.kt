package app.tuneroam.radio.di

import app.tuneroam.radio.data.local.CatalogCache
import app.tuneroam.radio.data.local.SqlFavoritesRepository
import app.tuneroam.radio.data.local.TuneRoamDatabase
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.ServerDirectory
import app.tuneroam.radio.data.repository.OfflineFirstCatalog
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform HTTP engine and SQL driver. Android supplies these today.
 * An iOS target would add its own actual without touching call sites.
 */
expect val platformModule: Module

private val dataModule = module {
    single { ServerDirectory(get()) }
    single { RadioBrowserClient(get(), get()) }
    single { TuneRoamDatabase(get<DatabaseDriverFactory>().create()) }
    single { CatalogCache(get()) }
    single<FavoritesRepository> { SqlFavoritesRepository(get()) }
    single<RadioCatalog> { OfflineFirstCatalog(get(), get()) }
    single { PlaybackCoordinator() }
}

internal val sharedModules: List<Module> = listOf(dataModule, platformModule)

/**
 * Starts Koin without exposing the Koin `Module` type.
 *
 * A future SwiftUI shell calls this. The Android application does not: it needs
 * an Android context, so it starts Koin itself with [androidSharedModules].
 */
fun startDependencyInjection() {
    org.koin.core.context.startKoin {
        modules(sharedModules)
    }
}
