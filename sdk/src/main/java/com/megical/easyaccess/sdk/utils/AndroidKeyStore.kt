package com.megical.easyaccess.sdk.utils

import java.security.KeyStore
import java.security.KeyStoreException

internal class AndroidKeyStore {
    val default: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore")
            .apply { load(null) }

    fun deleteKey(keyAlias: String) = default.deleteEntry(keyAlias)

    fun containsKey(keyAlias: String) = try {
        default.containsAlias(keyAlias)
    } catch (exception: KeyStoreException) {
        false
    }
}