package app.tuneroam.radio

import android.app.Application
import app.tuneroam.radio.diagnostics.CrashLog
import app.tuneroam.radio.di.androidAppModule
import app.tuneroam.radio.di.androidSharedModules
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TuneRoamApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        startKoin {
            androidContext(this@TuneRoamApplication)
            modules(androidSharedModules() + androidAppModule)
        }
    }

    // Some stations publish their logo as SVG, which Coil only decodes with this decoder.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .build()
}
