package com.megical.easyaccess.sdk.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.jose4j.jwk.JsonWebKey
import retrofit2.Call
import retrofit2.http.*
import java.util.*

internal interface AuthApi {
    @GET
    fun discover(
        @Url discoveryUrl: String,
    ): Call<IssuerResponse>

    @GET
    fun jwks(
        @Url jwksUri: String,
    ): Call<JsonWebKeySet>

    @POST
    fun client(
        @Url clientUrl: String,
        @Body clientRequest: ClientRequest,
    ): Call<ClientResponse>

    @DELETE
    fun deleteClient(
        @Url clientDeleteUrl: String,
    ): Call<Void>

    @GET
    fun authorize(
        @Url authUrl: String,
        @Query("client_id") clientId: String,
        @Query("redirect_uri") redirectUri: String,
        @Query("state") state: String,
        @Query("nonce") nonce: String,
        @Query("code_challenge") codeChallenge: String,
        @Query("audience") audience: String,
        @Query("scope") scope: String = "openid",
        @Query("response_type") responseType: String = "code",
        @Query("code_challenge_method") codeChallengeMethod: String = "S256",
    ): Call<AuthorizeResponse>

    @POST
    fun verify(
        @Url verifyUrl: String,
        @Body verifyRequest: VerifyRequest,
    ): Call<String>

    @FormUrlEncoded
    @POST
    fun tokenRequest(
        @Url tokenUrl: String,
        @Field("grant_type") grantType: String,
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("client_assertion") clientAssertion: String,
        @Field("client_assertion_type") clientAssertionType: String,
    ): Call<TokenResponse>

    @JsonClass(generateAdapter = true)
    data class AuthorizeResponse(
        val loginCode: String,
        val sessionId: UUID,
        val lang: String?,
    )

    @JsonClass(generateAdapter = true)
    data class ClientRequest(
        val clientToken: UUID,
        val deviceId: String,
        val key: JsonWebKey,
    )

    @JsonClass(generateAdapter = true)
    data class ClientResponse(
        val clientId: String,
        val secret: String,
    )

    @JsonClass(generateAdapter = true)
    data class VerifyRequest(
        val sessionId: UUID,
        val redirect: Boolean = false,
    )

    @JsonClass(generateAdapter = true)
    data class TokenResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "expires_in") val expiresIn: Int,
        @Json(name = "id_token") val idToken: String,
        @Json(name = "scope") val scope: String,
        @Json(name = "token_type") val tokenType: String,
    )

    @JsonClass(generateAdapter = true)
    data class JsonWebKeySet(
        val keys: List<JsonWebKey>,
    )

    @JsonClass(generateAdapter = true)
    data class IssuerResponse(
        @Json(name = "issuer") val issuer: String,
        @Json(name = "authorization_endpoint") val authorizationEndpoint: String,
        @Json(name = "token_endpoint") val tokenEndpoint: String,
        @Json(name = "jwks_uri") val jwksUri: String,
        @Json(name = "subject_types_supported") val subjectTypesSupported: List<String>,
        @Json(name = "response_types_supported") val responseTypesSupported: List<String>,
        @Json(name = "claims_supported") val claimsSupported: List<String>,
        @Json(name = "grant_types_supported") val grantTypesSupported: List<String>,
        @Json(name = "response_modes_supported") val responseModesSupported: List<String>,
        @Json(name = "userinfo_endpoint") val userinfoEndpoint: String,
        @Json(name = "scopes_supported") val scopesSupported: List<String>,
        @Json(name = "token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String>,
        @Json(name = "userinfo_signing_alg_values_supported") val userinfoSigningAlgValuesSupported: List<String>,
        @Json(name = "id_token_signing_alg_values_supported") val idTokenSigningAlgValuesSupported: List<String>,
        @Json(name = "request_parameter_supported") val requestParameterSupported: Boolean,
        @Json(name = "request_uri_parameter_supported") val requestUriParameterSupported: Boolean,
        @Json(name = "require_request_uri_registration") val requireRequestUriRegistration: Boolean,
        @Json(name = "claims_parameter_supported") val claimsParameterSupported: Boolean,
        @Json(name = "revocation_endpoint") val revocationEndpoint: String,
        @Json(name = "backchannel_logout_supported") val backchannelLogoutSupported: Boolean,
        @Json(name = "backchannel_logout_session_supported") val backchannelLogoutSessionSupported: Boolean,
        @Json(name = "frontchannel_logout_supported") val frontchannelLogoutSupported: Boolean,
        @Json(name = "frontchannel_logout_session_supported") val frontchannelLogoutSessionSupported: Boolean,
        @Json(name = "end_session_endpoint") val endSessionEndpoint: String,
    )
}