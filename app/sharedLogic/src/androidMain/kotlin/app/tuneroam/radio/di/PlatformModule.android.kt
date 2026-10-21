package app.tuneroam.radio.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.local.TuneRoamDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    single {
        HttpClient(OkHttp) {
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
