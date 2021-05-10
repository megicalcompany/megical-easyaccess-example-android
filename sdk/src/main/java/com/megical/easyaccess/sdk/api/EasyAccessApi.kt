package com.megical.easyaccess.sdk.api

import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

internal interface EasyAccessApi {
    @GET
    fun metadata(
        @Url url: String,
    ): Call<Metadata>

    @GET
    fun state(
        @Url url: String,
    ): Call<State>

    @JsonClass(generateAdapter = true)
    data class Metadata(
        val defaultLang: String,
        val langs: List<String>,
        val values: List<MetadataValue>,
    )

    @JsonClass(generateAdapter = true)
    data class MetadataValue(
        val key: String,
        val translations: List<Translation>,
    )

    @JsonClass(generateAdapter = true)
    data class Translation(
        val lang: String,
        val value: String,
    )

    @JsonClass(generateAdapter = true)
    data class State(
        val state: String,
    )

}
