package app.tuneroam.radio

import android.app.Application
import app.tuneroam.radio.diagnostics.CrashLog
import app.tuneroam.radio.di.androidAppModule
import app.tuneroam.radio.di.androidSharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TuneRoamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        startKoin {
            androidContext(this@TuneRoamApplication)
            modules(androidSharedModules() + androidAppModule)
        }
    }
}
