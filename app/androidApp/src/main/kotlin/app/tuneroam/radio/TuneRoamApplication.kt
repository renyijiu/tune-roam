package app.tuneroam.radio

import android.app.Application
import app.tuneroam.radio.diagnostics.CrashLog
import app.tuneroam.radio.di.androidAppModule
import app.tuneroam.radio.di.androidSharedModules
import app.tuneroam.radio.snapshot.SnapshotScheduler
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TuneRoamApplication : Application(), ImageLoaderFactory {
    /** Work that lives as long as the process, such as following settings for the snapshot schedule. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        startKoin {
            androidContext(this@TuneRoamApplication)
            modules(androidSharedModules() + androidAppModule)
        }
        get<SnapshotScheduler>().start(appScope)
    }

    // Some stations publish their logo as SVG, which Coil only decodes with this decoder.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .build()
}
