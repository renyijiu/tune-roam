# Release builds run R8 (minify + resource shrinking). Libraries in use ship their own
# consumer rules: kotlinx.serialization keeps generated serializers, Media3 keeps what
# its session and decoders load reflectively, Ktor/OkHttp and SQLDelight need nothing.
#
# The media session classes, which car and Bluetooth hosts reach through the platform's
# MediaBrowserService binding.
-keep class androidx.media3.session.** { *; }
