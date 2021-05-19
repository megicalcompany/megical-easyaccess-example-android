# Megical Easy Access SDK

## Building test app



## Registering test app client:

Test app registration token can be obtained from:

https://playground.megical.com/easyaccess/

You must login using working id card.

Web-page contains app-link which you can use to open example app and automatically register client.

Registration token is one time only and it is used to fetch openId client data from test-service.

Resource returns auth-service, client data and one time client registration token for auth-service.

```
val (
    clientToken,
    authEnvUrl,
    authEnv,
    redirectUrls,
    appId,
    audience
) = playgroundRestApi.openIdClientData(testAppToken)
```

## SDK usage starts from here

### Register client with auth-service:

Use client token to register oauth client with auth-service.

- `authEnvUrl` is the auth-service url.
- `clientToken` returned from test-service. One use only. 5 minutes ttl
- `deviceId` is string. Example app uses random uuid.
https://developer.android.com/training/articles/user-data-ids#kotlin
- `keyId` is the name of keypair in keychain. Example app uses appId.

Save client data for later use.

Client registration should be done only once.

```
megicalAuthApi.registerClient(
    authEnvUrl,
    clientToken,
    deviceId,
    keyId,
    callback = object : MegicalCallback<Client> {
        override fun onSuccess(response: Client) {
            // save client data for later use
        }
        override fun onFailure(error: MegicalException) {
            // handle errors
        }
    }
)
```

### Initiate authentication:

After client is registered you can initiate authentication with auth-service.

- `authEnv` tells Megical Easy Access app which env it should connect.
- `authEnvUrl` is the auth-service url.
- `keyId` is keychain name.
- `clientId` is oauth client id. It was return when registerClient was called.
- `audience` is accessToken audience.
- `redirectUrl` is apps callback url

```
megicalAuthApi.initAuthentication(
    authEnv,
    authEnvUrl,
    keyId,
    clientId,
    audience,
    redirectUrl,
    object : MegicalCallback<LoginData> {
        override fun onSuccess(response: LoginData) {
            // save login data
        }

        override fun onFailure(error: MegicalException) {
            handle errors
        }
    }
)
```

### Handle login data:

If Megical Easy Access app is installed you can open `loginData.appLink` directly
else app needs to show loginCode and/or login qr-code.

```
private fun handleLoginData(loginData: LoginData) {
    val intent = Intent(Intent.ACTION_VIEW, loginData.appLink)
    if (intent.resolveActivity(requireActivity().packageManager) != null) {
        startActivityForResult(intent, LOGIN_ACTIVITY)
    } else {
        viewModel.fetchMetadata()
        viewModel.fetchLoginState()
        loginCodeMessage.text = loginData.loginCode

        try {
            val qrgEncoder =
                QRGEncoder(loginData.appLink.toString(), null, QRGContents.Type.TEXT, 200)
            qrCode.setImageBitmap(qrgEncoder.bitmap)
        } catch (e: WriterException) {
            Timber.e(e)
        }
    }
}
```

If Megical Easy Access app was opened with `startActivityForResult` you must implement
`onActivityResult` which calls verify:
```
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == LOGIN_ACTIVITY) {
        viewModel.verifyLoginData()
    } else {
        Timber.e("Invalid requestCode: $requestCode")
    }
}
```

```
fun verifyLoginData() {
    megicalAuthApi.verifyAuthentication(
        object : MegicalCallback<TokenSet> {
            override fun onSuccess(response: TokenSet) {
                // Returns tokenset with accessToken and idToken
            }

            override fun onFailure(error: MegicalException) {
                // handle errors
            }
        }
    )
}
```

If Megical Easy Access app is not installed in device, the app has to poll auth-service for
login state.

5 seconds should be good polling interval.

When login state is `Updated` call `megicalAuthApi.verifyAuthentication()`

```
    private fun handleLoginState(loginState: LoginState) {
        when (loginState) {
            LoginState.Init,
            LoginState.Started,
            -> {
                Thread {
                    Handler(Looper.getMainLooper())
                        .postDelayed(
                            viewModel::fetchLoginState,
                            5000
                        )
                }.start()
            }
            LoginState.Updated -> {
                viewModel.verifyLoginData()
            }
            else -> {
                Timber.e("Unhandled state")
            }
        }
    }
```

```
fun verifyLoginData() {
    megicalAuthApi.verifyAuthentication(
        object : MegicalCallback<TokenSet> {
            override fun onSuccess(response: TokenSet) {
                // handle tokens
            }

            override fun onFailure(error: MegicalException) {
                // handle errors
            }
        })
}
```

### Deregister device

Call deleteClient to destroy client from auth-service and to remove client key pair from keychain.
Delete also saved client data.

```
megicalAuthApi.deleteClient(
    authEnvUrl,
    clientId,
    keyId,
    object : MegicalCallback<Unit> {
        override fun onSuccess(response: Unit) {
            // Client was deleted
        }

        override fun onFailure(error: MegicalException) {
            // Handle error
        }
    })
```