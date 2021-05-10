package com.megical.easyaccess.sdk.utils

import java.security.SecureRandom
import android.util.Base64 as AndroidBase64

private const val BASE64_URL_SAFE_FLAGS: Int =
    AndroidBase64.NO_WRAP or AndroidBase64.URL_SAFE or AndroidBase64.NO_PADDING

internal data class Base64UrlSafe(val value: String)

internal fun ByteArray.encodeBase64UrlSafe(): Base64UrlSafe =
    Base64UrlSafe(
        AndroidBase64.encodeToString(
            this,
            BASE64_URL_SAFE_FLAGS
        ).trim()
    )

internal fun generateBase64UrlNonce(): Base64UrlSafe = ByteArray(32)
    .also { SecureRandom().nextBytes(it) }.encodeBase64UrlSafe()
