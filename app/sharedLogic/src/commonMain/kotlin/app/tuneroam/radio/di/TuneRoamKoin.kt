package app.tuneroam.radio.di

import app.tuneroam.radio.data.local.CatalogCache
import app.tuneroam.radio.data.local.SqlFavoritesRepository
import app.tuneroam.radio.data.local.SqlHistoryRepository
import app.tuneroam.radio.data.local.SqlMirrorMemory
import app.tuneroam.radio.data.local.SqlReachabilityRepository
import app.tuneroam.radio.data.local.TuneRoamDatabase
import app.tuneroam.radio.data.remote.KtorStationConnectivity
import app.tuneroam.radio.data.remote.MirrorMemory
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.ServerDirectory
import app.tuneroam.radio.data.repository.OfflineFirstCatalog
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.domain.playback.SleepTimer
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.HistoryRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.tuneroam.radio.domain.repository.StationConnectivity
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform HTTP engine and SQL driver. Android supplies these today.
 * An iOS target would add its own actual without touching call sites.
 */
expect val platformModule: Module

private val dataModule = module {
    single { TuneRoamDatabase(get<DatabaseDriverFactory>().create()) }
    single<MirrorMemory> { SqlMirrorMemory(get()) }
    single { ServerDirectory(get(), get()) }
    single { RadioBrowserClient(get(), get()) }
    single { CatalogCache(get()) }
    single<FavoritesRepository> { SqlFavoritesRepository(get()) }
    single<ReachabilityRepository> { SqlReachabilityRepository(get()) }
    single<HistoryRepository> { SqlHistoryRepository(get()) }
    single<StationConnectivity> { KtorStationConnectivity(get()) }
    single<RadioCatalog> { OfflineFirstCatalog(get(), get()) }
    single { PlaybackCoordinator() }
    single { SimilarStations(get(), get(), get()) }
    single { SleepTimer() }
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
