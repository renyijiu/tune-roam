package app.tuneroam.radio.di

import app.tuneroam.radio.domain.playback.PlaybackCommands
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.annotation.KoinInternalApi

@OptIn(KoinInternalApi::class)
class AndroidAppModuleTest {
    @Test
    fun playbackCommandsAreRegisteredAsTheSharedInterface() {
        val name = PlaybackCommands::class.qualifiedName!!
        val keys = androidAppModule.mappings.keys
        assertTrue(
            keys.any { it == name || it.startsWith("$name::") },
            "Shared ViewModels request PlaybackCommands. Bind the Media3 controller as that interface. Keys: $keys",
        )
    }
}
