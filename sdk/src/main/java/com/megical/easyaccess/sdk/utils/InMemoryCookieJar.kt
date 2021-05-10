package com.megical.easyaccess.sdk.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class InMemoryCookieJar : CookieJar {
    private val cookieJar: MutableSet<Cookie> = mutableSetOf()

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookieJar
            .apply { removeAll { it.expiresAt < System.currentTimeMillis() } }
            .filter { it.matches(url) }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieJar.addAll(cookies)
    }

    fun clear() {
        cookieJar.clear()
    }
}