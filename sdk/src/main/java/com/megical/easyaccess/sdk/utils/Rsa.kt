package com.megical.easyaccess.sdk.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey

private const val KEY_SIZE = 4096

internal class Rsa {

    fun loadOrCreateKeyPair(
        keyStore: AndroidKeyStore,
        keyAlias: String,
        newKeyPurpose: Int = PURPOSES,
    ): KeyPair =
        if (keyStore.containsKey(keyAlias)) {
            loadKeyPair(keyStore, keyAlias)
        } else {
            generateKeyPair(keyStore, keyAlias, newKeyPurpose)
        }

    fun loadKeyPair(keyStore: AndroidKeyStore, keyAlias: String): KeyPair =
        keyStore.default.let { ks ->
            KeyPair(
                ks.getCertificate(keyAlias).publicKey,
                ks.getKey(keyAlias, null) as PrivateKey
            )
        }

    private fun generateKeyPair(
        keyStore: AndroidKeyStore,
        keyAlias: String,
        keyPurpose: Int,
    ): KeyPair =
        KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            keyStore.default.type
        )
            .apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        keyPurpose
                    )
                        .setKeySize(KEY_SIZE)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .build()
                )
            }
            .generateKeyPair()

    companion object {
        private const val PURPOSES = KeyProperties.PURPOSE_SIGN
    }
}