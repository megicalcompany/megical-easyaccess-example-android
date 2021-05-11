package com.megical.easyaccess.playground

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.*

class PlaygroundRestApi {
    private val playgroundService: PlaygroundService = Retrofit.Builder()
        .client(
            OkHttpClient.Builder()
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.MODERN_TLS
                    )
                )
                .addNetworkInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
                .build()
        )
        .baseUrl("https://playground.megical.com/test-service/api/v1/")
        .addConverterFactory(
            MoshiConverterFactory.create(
                Moshi
                    .Builder()
                    .add(UuidAdapter())
                    .build()
            )
        )
        .build()
        .create()

    suspend fun healthcheck() = playgroundService.healthcheck()

    suspend fun openIdClientData(token: UUID) =
        playgroundService.openIdClientData(OpenIdClientDataRequest(token))

    suspend fun hello(accessToken: String) =
        playgroundService.hello("Bearer $accessToken")
}

internal interface PlaygroundService {
    @GET("healthcheck")
    suspend fun healthcheck(): HealthcheckResponse

    @POST("public/openIdClientData")
    suspend fun openIdClientData(@Body openIdClientDataRequest: OpenIdClientDataRequest): OpenIdClientDataResponse

    @GET("private/hello")
    suspend fun hello(@Header("Authorization") bearer: String): HelloResponse
}

@JsonClass(generateAdapter = true)
data class OpenIdClientDataRequest(
    val token: UUID,
)

@JsonClass(generateAdapter = true)
data class HealthcheckResponse(
    val nowMs: Long,
    val buildNumber: String,
    val sha: String,
    val branch: String,
    val buildDate: String,
)

@JsonClass(generateAdapter = true)
data class HelloResponse(
    val hello: String,
)

@JsonClass(generateAdapter = true)
data class OpenIdClientDataResponse(
    val clientToken: UUID,
    val authEnvUrl: String,
    val authEnv: String,
    val redirectUrls: List<String>,
    val appId: String,
    val audience: List<String>,
)

class UuidAdapter {
    @ToJson
    internal fun toUuid(uuid: UUID?): String? = uuid?.toString()

    @FromJson
    internal fun fromJson(uuid: String?): UUID? = UUID.fromString(uuid)
}
