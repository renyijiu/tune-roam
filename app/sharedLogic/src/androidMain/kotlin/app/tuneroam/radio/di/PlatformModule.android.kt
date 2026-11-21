package app.tuneroam.radio.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.local.TuneRoamDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    // One OkHttpClient for the catalog, the stream probes, and playback (the app's
    // ExoPlayer data source asks Koin for it). A probe then reaches a stream server through
    // the same TLS, redirect, and status-line handling as the player, so a station that
    // answered a probe is one the player can open too.
    single { OkHttpClient() }
    single {
        HttpClient(OkHttp) {
            engine { preconfigured = get<OkHttpClient>() }
            installRadioBrowser()
        }
    }
}

private class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {
    override fun create(): app.cash.sqldelight.db.SqlDriver =
        AndroidSqliteDriver(TuneRoamDatabase.Schema, context, "tuneroam.db")
}

fun androidSharedModules(): List<Module> = sharedModules
