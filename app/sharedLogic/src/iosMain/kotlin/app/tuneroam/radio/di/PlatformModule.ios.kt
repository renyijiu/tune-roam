package app.tuneroam.radio.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.local.TuneRoamDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS platform pieces: NSURLSession through Ktor's Darwin engine, and SQLite through
 * SQLDelight's native driver. There is no iOS app yet; this keeps the shared graph
 * complete for one (see `startDependencyInjection`).
 */
actual val platformModule: Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
    single { HttpClient(Darwin) { installRadioBrowser() } }
}

private class NativeDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver = NativeSqliteDriver(TuneRoamDatabase.Schema, "tuneroam.db")
}
