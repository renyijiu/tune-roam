package app.tuneroam.radio.core

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Wall-clock milliseconds. Shared code uses this instead of `System.currentTimeMillis()`
 * so `commonMain` stays free of JVM calls and an Apple target can compile it.
 */
@OptIn(ExperimentalTime::class)
fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * [runCatching] for suspend code. A cancelled coroutine stays cancelled instead of
 * being reported as a network failure (which would, for example, blame a mirror).
 */
inline fun <T> runCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
