package com.megical.easyaccess.sdk.utils

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import org.jose4j.jwk.JsonWebKey
import java.util.*

internal class Adapters {

    @ToJson
    internal fun jsonWebKeyToJson(value: JsonWebKey): Map<String, Any> =
        value.toParams(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)

    @FromJson
    internal fun jsonWebKeyFromJson(value: Map<String, Any>): JsonWebKey =
        JsonWebKey.Factory.newJwk(value)

    @ToJson
    internal fun uuidToJson(uuid: UUID?): String? = uuid?.toString()

    @FromJson
    internal fun uuidFromJson(uuid: String?): UUID? = UUID.fromString(uuid)

    @ToJson
    internal fun base64UrlSafeFromJson(value: Base64UrlSafe): String = value.value

    @FromJson
    internal fun base64UrlSafeFromJson(value: String): Base64UrlSafe =
        Base64UrlSafe(value)
}