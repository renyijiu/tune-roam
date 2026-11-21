package app.tuneroam.radio.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * The dispatcher for blocking work (SQLite, bundled-snapshot parsing) in shared code.
 *
 * `Dispatchers.IO` is a public member on the JVM but an extension on Kotlin/Native that
 * must be imported, so shared code goes through this one name rather than repeating the
 * import (and the iOS build catches a file that forgets it).
 */
val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
