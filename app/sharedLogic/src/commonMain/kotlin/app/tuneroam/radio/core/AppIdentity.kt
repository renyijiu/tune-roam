package app.tuneroam.radio.core

/**
 * Single code-side identity for the product.
 *
 * The on-screen name comes from the Android string resource `app_name`
 * (English in `values/`, Chinese in `values-zh/`). Keep [NAME_EN] and [NAME_ZH]
 * in step with those two strings. The HTTP User-Agent is derived only from here.
 */
object AppIdentity {
    const val NAME_EN = "Tune Roam"
    const val NAME_ZH = "随声"
    const val APPLICATION_ID = "app.tuneroam.radio"
    const val VERSION = "0.1.0"

    const val USER_AGENT = "TuneRoam/$VERSION ($APPLICATION_ID)"
}
