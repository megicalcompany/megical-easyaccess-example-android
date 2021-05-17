package com.megical.easyaccess.sdk

import android.net.Uri
import com.megical.easyaccess.sdk.api.AuthApi
import com.megical.easyaccess.sdk.api.AuthApi.*
import com.megical.easyaccess.sdk.api.EasyAccessApi
import com.megical.easyaccess.sdk.utils.*
import com.squareup.moshi.Moshi
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.VerificationJwkSelector
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.JwtContext
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.KeyPair
import java.util.*
import retrofit2.Callback as RetrofitCallback

private const val AUTH_BASE_URL = "https://auth-prod.megical.com/"

class MegicalAuthApi {
    private val cookieJar = InMemoryCookieJar()
    private val retrofit = Retrofit.Builder()
        .client(
            OkHttpClient.Builder()
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.MODERN_TLS
                    )
                )
                .addNetworkInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.NONE
                    }
                )
                .cookieJar(cookieJar)
                .build()
        )
        .baseUrl(AUTH_BASE_URL)
        .addConverterFactory(
            MoshiConverterFactory.create(
                Moshi
                    .Builder()
                    .add(Adapters())
                    .build()
            )
        )
        .build()

    private val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    private val easyAccessApi: EasyAccessApi = retrofit.create(EasyAccessApi::class.java)

    private val keyStore: AndroidKeyStore = AndroidKeyStore()
    private val rsa: Rsa = Rsa()

    private fun <T> validateResponse(
        response: Response<T>,
        onFailure: (MegicalException) -> Unit,
    ): T? {
        val body: T? = response.body()

        if (!response.isSuccessful || body == null) {
            authState = null
            onFailure(InvalidResponse())
            return null
        }

        return body
    }

    fun registerClient(
        authEnvUrl: String,
        clientToken: UUID,
        deviceId: String,
        keyId: String,
        callback: Callback<Client>,
    ) {
        val key = try {
            keyStore.deleteKey(keyId)
            val keyPair = rsa.loadOrCreateKeyPair(
                keyStore,
                keyId
            )
            JsonWebKey.Factory.newJwk(keyPair.public).apply { use = "sig" }
        } catch (error: Exception) {
            return callback.onFailure(CouldNotCreateKeyError(error))
        }

        authApi.client("$authEnvUrl/api/v1/client", ClientRequest(clientToken, deviceId, key))
            .enqueue(object : RetrofitCallback<ClientResponse> {
                override fun onResponse(
                    call: Call<ClientResponse>,
                    response: Response<ClientResponse>,
                ) {
                    validateResponse(response, callback::onFailure)?.let { body ->
                        callback.onSuccess(Client(
                            body.clientId,
                            body.secret
                        ))
                    }
                }

                override fun onFailure(call: Call<ClientResponse>, t: Throwable) {
                    callback.onFailure(ClientError(t))
                }
            })
    }

    fun deleteClient(
        authEnvUrl: String,
        clientId: String,
        keyId: String,
        callback: Callback<Unit>,
    ) {
        keyStore.deleteKey(keyId)
        authApi.deleteClient("$authEnvUrl/api/v1/client/$clientId").enqueue(object :
            retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    callback.onSuccess(Unit)
                } else {
                    callback.onFailure(InvalidResponse())
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback.onFailure(DeleteClientError(t))
            }
        })
    }

    private data class AuthState(
        val authEnv: String,
        val authEnvUrl: String,
        val keyId: String,
        val clientId: String,
        val audience: List<String>,
        val redirectUrl: String,
        var issuer: IssuerResponse? = null,
        var sessionId: UUID? = null,
        var codeVerifier: Pkce.CodeVerifier? = null,
        var state: Base64UrlSafe? = null,
        var nonce: Base64UrlSafe? = null,
    )

    private var authState: AuthState? = null

    fun initAuthentication(
        authEnv: String,
        authEnvUrl: String,
        keyId: String,
        clientId: String,
        audience: List<String>,
        redirectUrl: String,
        callback: Callback<LoginData>,
    ) {
        cookieJar.clear()
        authState = null
        val localAuthState = AuthState(authEnv, authEnvUrl, keyId, clientId, audience, redirectUrl)

        authApi.discover("${authEnvUrl}/.well-known/openid-configuration")
            .enqueue(object : RetrofitCallback<IssuerResponse> {
                override fun onResponse(
                    call: Call<IssuerResponse>,
                    response: Response<IssuerResponse>,
                ) {
                    validateResponse(response, callback::onFailure)?.let { config ->
                        authState = localAuthState.copy(issuer = config)
                        authorize(callback)
                    }
                }

                override fun onFailure(call: Call<IssuerResponse>, t: Throwable) {
                    authState = null
                    callback.onFailure(IssuerError(t))
                }
            })
    }

    fun verifyAuthentication(callback: Callback<TokenSet>) {
        val localAuthState = authState ?: return callback.onFailure(AuthStateNullError())
        val localSessionId =
            localAuthState.sessionId ?: return callback.onFailure(SessionIdNullError())
        val state = localAuthState.state ?: return callback.onFailure(StateNullError())

        val authEnvUrl = localAuthState.authEnvUrl
        val redirectUrl = localAuthState.redirectUrl
        authApi.verify(
            "${authEnvUrl}/api/v1/auth/verifyEasyaccess",
            VerifyRequest(localSessionId, true)
        ).enqueue(object : RetrofitCallback<String> {
            override fun onResponse(
                call: Call<String>,
                response: Response<String>,
            ) {
                try {
                    if (response.code() != 302) {
                        return callback.onFailure(InvalidResponse())
                    }
                    val headers = response.headers()
                    val location = headers["Location"]

                    // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2
                    // validate callback url, code and state

                    val uri = Uri.parse(location)
                    val redirectUri = Uri.parse(redirectUrl)

                    if (uri.scheme != redirectUri.scheme
                        || (redirectUri.authority != null && uri.authority != redirectUri.authority)
                        || uri.path != redirectUri.path
                    ) {
                        throw InvalidCallbackError()
                    }

                    val returnState = uri.getQueryParameter("state")

                    if (returnState != state.value) {
                        throw InvalidStateError()
                    }

                    val code = uri.getQueryParameter("code") ?: throw CodeNotFoundError()
                    tokenRequest(code, callback)
                } catch (error: Exception) {
                    authState = null
                    callback.onFailure(VerifyError(error))
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                authState = null
                callback.onFailure(VerifyError(t))
            }
        })
    }

    fun metadata(loginCode: String, callback: Callback<Metadata>) {
        val localAuthState = authState ?: return callback.onFailure(AuthStateNullError())
        val authEnvUrl = localAuthState.authEnvUrl
        easyAccessApi.metadata("$authEnvUrl/api/v1/easyaccess/metadata/${loginCode}")
            .enqueue(object : RetrofitCallback<EasyAccessApi.MetadataResponse> {
                override fun onResponse(
                    call: Call<EasyAccessApi.MetadataResponse>,
                    response: Response<EasyAccessApi.MetadataResponse>,
                ) {
                    validateResponse(response, callback::onFailure)?.let { body ->
                        callback.onSuccess(body.toMetadata())
                    }
                }

                override fun onFailure(
                    call: Call<EasyAccessApi.MetadataResponse>,
                    t: Throwable,
                ) {
                    authState = null
                    callback.onFailure(MetadataError(t))
                }
            })
    }

    fun state(loginCode: String, callback: Callback<LoginState>) {
        val localAuthState = authState ?: return callback.onFailure(AuthStateNullError())
        val authEnvUrl = localAuthState.authEnvUrl
        easyAccessApi.state("$authEnvUrl/api/v1/easyaccess/state/${loginCode}")
            .enqueue(object : RetrofitCallback<EasyAccessApi.StateResponse> {
                override fun onResponse(
                    call: Call<EasyAccessApi.StateResponse>,
                    response: Response<EasyAccessApi.StateResponse>,
                ) {
                    validateResponse(response, callback::onFailure)?.let { body ->
                        val state =
                            LoginState.values().firstOrNull { it.value == body.state }
                                ?: LoginState.Unknown
                        callback.onSuccess(state)
                    }
                }

                override fun onFailure(
                    call: Call<EasyAccessApi.StateResponse>,
                    t: Throwable,
                ) {
                    authState = null
                    callback.onFailure(StateError(t))
                }
            })
    }

    private fun authorize(callback: Callback<LoginData>) {
        val localAuthState = authState ?: return callback.onFailure(AuthStateNullError())
        val authConfig = localAuthState.issuer ?: return callback.onFailure(IssuerNullError())
        val clientId = localAuthState.clientId
        val audience = localAuthState.audience
        val redirectUrl = localAuthState.redirectUrl
        val authEnv = localAuthState.authEnv

        val nonce = generateBase64UrlNonce()
        val state = generateBase64UrlNonce()
        val codeVerifier = Pkce().generateCodeVerifier

        authApi.authorize(
            authUrl = authConfig.authorizationEndpoint,
            clientId = clientId,
            state = state.value,
            nonce = nonce.value,
            codeChallenge = codeVerifier.codeChallenge,
            audience = audience.joinToString(" "),
            redirectUri = redirectUrl
        ).enqueue(object :
            RetrofitCallback<AuthorizeResponse> {
            override fun onResponse(
                call: Call<AuthorizeResponse>,
                response: Response<AuthorizeResponse>,
            ) {
                validateResponse(response, callback::onFailure)?.let { body ->
                    authState = localAuthState.copy(
                        sessionId = body.sessionId,
                        nonce = nonce,
                        state = state,
                        codeVerifier = codeVerifier)
                    callback.onSuccess(
                        LoginData(
                            loginCode = body.loginCode,
                            appLink = easyAccessAppLink(body.loginCode, authEnv),
                            lang = body.lang
                        )
                    )
                }
            }

            override fun onFailure(
                call: Call<AuthorizeResponse>,
                t: Throwable,
            ) {
                callback.onFailure(AuthenticationError(t))
            }
        })
    }

    private fun tokenRequest(code: String, callback: Callback<TokenSet>) {
        val localAuthState = authState ?: return callback.onFailure(AuthStateNullError())
        val authConfig = localAuthState.issuer ?: return callback.onFailure(IssuerNullError())
        val clientId = localAuthState.clientId
        val nonce = localAuthState.nonce ?: return callback.onFailure(NonceNullError())
        val keyId = localAuthState.keyId
        val redirectUrl = localAuthState.redirectUrl
        val codeVerifier =
            localAuthState.codeVerifier ?: return callback.onFailure(CodeVerifierNullError())

        val keyPair = rsa.loadKeyPair(
            keyStore,
            keyId
        )

        val clientAssertion = createClientAssertion(
            keyPair,
            clientId,
            authConfig.tokenEndpoint
        )

        authApi.tokenRequest(
            grantType = "authorization_code",
            clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            tokenUrl = authConfig.tokenEndpoint,
            codeVerifier = codeVerifier.value,
            code = code,
            clientId = clientId,
            redirectUri = redirectUrl,
            clientAssertion = clientAssertion)
            .enqueue(object : RetrofitCallback<TokenResponse> {
                override fun onResponse(
                    call: Call<TokenResponse>,
                    response: Response<TokenResponse>,
                ) {
                    validateResponse(response, callback::onFailure)?.let { body ->
                        fetchJwksAndValidateIdToken(nonce, clientId, authConfig, body, callback)
                    }
                }

                override fun onFailure(call: Call<TokenResponse>, t: Throwable) {
                    authState = null
                    callback.onFailure(TokenError(t))
                }
            })
    }

    private fun fetchJwksAndValidateIdToken(
        nonce: Base64UrlSafe,
        clientId: String,
        issuer: IssuerResponse,
        token: TokenResponse,
        callback: Callback<TokenSet>,
    ) {
        authApi.jwks(issuer.jwksUri)
            .enqueue(object : RetrofitCallback<JsonWebKeySet> {
                override fun onResponse(
                    call: Call<JsonWebKeySet>,
                    response: Response<JsonWebKeySet>,
                ) {
                    validateResponse(response, callback::onFailure)?.let { jsonWebKeySet ->
                        val jwtContext = try {
                            validateIdToken(nonce, clientId, issuer, token.idToken, jsonWebKeySet)
                        } catch (e: IdTokenValidationError) {
                            authState = null
                            return callback.onFailure(e)
                        } catch (e: Exception) {
                            authState = null
                            return callback.onFailure(UnknownError(e))
                        }

                        callback.onSuccess(TokenSet(
                            accessToken = token.accessToken,
                            expiresIn = token.expiresIn,
                            idToken = token.idToken,
                            scope = token.scope,
                            tokenType = token.tokenType,
                            sub = jwtContext.jwtClaims.subject)
                        )
                    }
                }

                override fun onFailure(
                    call: Call<JsonWebKeySet>,
                    t: Throwable,
                ) {
                    authState = null
                    callback.onFailure(JwksError(t))
                }
            })
    }

    /**
     * OpenID Connect Core 1.0 incorporating errata set 1
     * 3.1.3.7.  ID Token Validation
     *
     * Validations 1, 8, 12, 13 not implemented.
     *
     * https://bitbucket.org/b_c/jose4j/wiki/JWT%20Examples#markdown-header-two-pass-jwt-consumption
     * https://github.com/openid/AppAuth-Android/blob/1a47f749ea5300ec17454ce05b804c9578018b95/library/java/net/openid/appauth/IdToken.java#L112
     */
    private fun validateIdToken(
        nonce: Base64UrlSafe,
        clientId: String,
        authConfig: IssuerResponse,
        idToken: String,
        jsonWebKeySet: JsonWebKeySet,
    ): JwtContext {
        val jwtContext = JwtConsumerBuilder()
            .setSkipAllValidators()
            .setDisableRequireSignature()
            .setSkipSignatureVerification()
            .build()
            .process(idToken)

        // Validation 6 (TLS requirement part)
        jwtContext
            .jwtClaims
            .issuer
            .let { Uri.parse(it) }
            .let { issuer ->
                if (issuer.scheme != "https") {
                    throw IdTokenValidationError("Issuer must be an https URL")
                }
                if (issuer.host.isNullOrEmpty()) {
                    throw IdTokenValidationError("Issuer host can not be empty")
                }
                if (issuer.fragment != null || issuer.queryParameterNames.isNotEmpty()) {
                    throw IdTokenValidationError("Issuer URL should not contain query parameters or fragment components")
                }
            }

        // Validation 11
        jwtContext
            .jwtClaims
            .getClaimValue("nonce")
            .let { claimNonce ->
                if (claimNonce != nonce.value) {
                    throw IdTokenValidationError("Claim nonce does not not match sent nonce")
                }
            }

        // Validation 3 (partly, matching aud to clientId done in JwtConsumerBuilder) and 4
        // We want the token to only have this client as its audience
        jwtContext
            .jwtClaims
            .audience
            .let { claimAudience ->
                if (claimAudience.size != 1) {
                    throw IdTokenValidationError("More then one audience in claim")
                }
            }

        // Validation 5
        jwtContext
            .jwtClaims
            .getClaimValue("azp")
            .let { claimAzp ->
                if (claimAzp == clientId) {
                    throw IdTokenValidationError("Azp doest not match clientId")
                }
            }

        // Validations 2, 3, 6, 7, 9, 10
        JwtConsumerBuilder()
            .setExpectedIssuer(authConfig.issuer)
            .setVerificationKey(
                JsonWebSignature()
                    .apply {
                        compactSerialization = idToken
                    }
                    .let { VerificationJwkSelector().select(it, jsonWebKeySet.keys).key }
            )
            .setRequireExpirationTime()
            .setAllowedClockSkewInSeconds(2)
            .setRequireSubject()
            .setExpectedAudience(clientId)
            .setJwsAlgorithmConstraints(
                AlgorithmConstraints(
                    AlgorithmConstraints.ConstraintType.WHITELIST,
                    AlgorithmIdentifiers.RSA_USING_SHA256
                )
            )
            .build()
            .processContext(jwtContext)

        return jwtContext
    }

    private fun createClientAssertion(
        keyPair: KeyPair,
        clientId: String,
        tokenEndpoint: String,
    ): String =
        JwtClaims()
            .apply {
                issuer = clientId
                subject = clientId
                audience = listOf(tokenEndpoint)
//              https://github.com/dgrijalva/jwt-go/issues/314
//                setIssuedAtToNow()
                setGeneratedJwtId()
                setExpirationTimeMinutesInTheFuture(10f)
            }
            .let { claims ->
                JsonWebSignature()
                    .apply {
                        payload = claims.toJson()
                        key = keyPair.private
                        keyIdHeaderValue = clientId
                        algorithmHeaderValue = AlgorithmIdentifiers.RSA_USING_SHA256
                    }
            }
            .compactSerialization

    private fun easyAccessAppLink(loginCode: String, authEnv: String): Uri =
        Uri.parse("com.megical.easyaccess:/auth?loginCode=$loginCode&authEnv=$authEnv")


    interface Callback<T> {
        fun onSuccess(response: T)
        fun onFailure(error: MegicalException)
    }

    private fun EasyAccessApi.MetadataResponse.toMetadata(): Metadata {
        return Metadata(
            defaultLang = this.defaultLang,
            langs = this.langs,
            values = this.values.map { mv ->
                MetadataValue(
                    key = mv.key,
                    translations = mv.translations.map { t ->
                        Translation(
                            lang = t.lang,
                            value = t.value
                        )
                    })
            })
    }
}
