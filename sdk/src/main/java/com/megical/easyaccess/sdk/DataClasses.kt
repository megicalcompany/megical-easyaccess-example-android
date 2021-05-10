package com.megical.easyaccess.sdk

import android.net.Uri

data class Client(
    val clientId: String,
    val secret: String,
)

data class LoginData(
    val loginCode: String,
    val appLink: Uri,
    val lang: String?,
)

data class TokenSet(
    val accessToken: String,
    val idToken: String,
    val sub: String,
)

data class Metadata(
    val defaultLang: String,
    val langs: List<String>,
    val values: List<MetadataValue>,
)

data class MetadataValue(
    val key: String,
    val translations: List<Translation>,
)

data class Translation(
    val lang: String,
    val value: String,
)

enum class State(val value: String) {
    Init("init"),
    Started("started"),
    Updated("updated"),
    Debug("debug"),
    Unknown("unknown")
}

