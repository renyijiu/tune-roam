package app.tuneroam.radio.domain.playback

import app.tuneroam.radio.domain.model.Station

/**
 * Transport controls a native shell must provide.
 *
 * The Android app implements this with a Media3 controller bound to its
 * MediaLibraryService. A future SwiftUI shell implements it with AVPlayer and
 * reports engine events back to [PlaybackCoordinator]. This interface has no
 * platform types, so shared code can call it from either shell.
 */
interface PlaybackCommands {
    fun play(station: Station)

    fun toggle()

    fun pause()

    /** A new station from [RoamDeck]. Same as the next button on a notification or headset. */
    fun next()

    /** Back to the station played before this one. */
    fun previous()
}
