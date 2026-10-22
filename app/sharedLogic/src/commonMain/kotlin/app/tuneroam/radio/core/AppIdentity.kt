package app.tuneroam.radio.core

/**
 * Code-side identity, used for the HTTP User-Agent. The on-screen name is the Android
 * string resource `app_name`. Keep [VERSION] in step with `versionName` in
 * `app/androidApp/build.gradle.kts`.
 */
object AppIdentity {
    const val APPLICATION_ID = "app.tuneroam.radio"
    const val VERSION = "0.2.0"

    const val USER_AGENT = "TuneRoam/$VERSION ($APPLICATION_ID)"
}
