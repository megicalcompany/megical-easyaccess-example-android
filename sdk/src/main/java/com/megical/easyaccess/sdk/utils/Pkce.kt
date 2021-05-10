package com.megical.easyaccess.sdk.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * RFC7636
 */
internal class Pkce {

    val generateCodeVerifier
        get() = SecureRandom()
            .let { entropySource ->
                (0 until CODE_VERIFIER_SIZE)
                    .map {
                        UNRESERVED_URI_CHARS[
                                entropySource.nextInt(
                                    UNRESERVED_URI_CHARS.length
                                )
                        ]
                    }
                    .fold(
                        "",
                        { codeVerifier, nextChar ->
                            codeVerifier.plus(nextChar)
                        }
                    )
                    .let { CodeVerifier(it) }
            }

    data class CodeVerifier(val value: String) {
        val codeChallenge = value
            .toByteArray(Charsets.US_ASCII)
            .sha256
            .let {
                Base64.encode(
                    it,
                    BASE64_ENCODE_SETTINGS
                )
            }
            .toString(Charsets.US_ASCII)
    }

    companion object {
        private const val BASE64_ENCODE_SETTINGS =
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

        /**
         * Section 2.3 of RFC3986
         */
        private const val UNRESERVED_URI_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._~"
        private const val CODE_VERIFIER_SIZE = 128
    }

}

val ByteArray.sha256: ByteArray get() = MessageDigest.getInstance("SHA-256").digest(this)