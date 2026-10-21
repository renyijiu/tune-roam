package app.tuneroam.radio.core

import kotlinx.serialization.json.Json

val RadioJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}
