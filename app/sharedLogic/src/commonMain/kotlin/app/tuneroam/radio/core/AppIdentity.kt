package app.tuneroam.radio.core

/**
 * Code-side identity, used for the HTTP User-Agent. The on-screen name is the Android
 * string resource `app_name`. [VERSION] is generated from `tuneroam.versionName` in
 * `gradle.properties`, the same value the APK's versionName comes from.
 */
object AppIdentity {
    const val APPLICATION_ID = "app.tuneroam.radio"
    const val VERSION = AppVersion.NAME

    const val USER_AGENT = "TuneRoam/$VERSION ($APPLICATION_ID)"
}
