# Megical Easy Access SDK

## Setup sdk for your app

1. Clone sdk

```
git clone --depth=1 --branch=master https://github.com/megicalcompany/MegicalEasyAccess-SDK-Android
```

2. Add module to project 

settings.gradle:

```
include ':MegicalEasyAccess-SDK-Android'
```
 
app/build.gradle:

```
dependencies {
    implementation project(":MegicalEasyAccess-SDK-Android")
}
```


## Building test app

1. clone this repository and open it in android studio

2. clone sdk

```
git clone --depth=1 --branch=master https://github.com/megicalcompany/MegicalEasyAccess-SDK-Android
```

3. build app

```
./gradlew assemble
```

## Using test app and sdk

#### Registering test app client:

Test app registration token can be obtained from:

https://playground.hightrust.id/demo/

You must login using working id card.

Web-page contains app-link which you can use to open example app and automatically register client.

Registration token can be used only once. It is used to fetch openId client data from test-service.

Resource returns auth-service url, client data and one time client registration token for auth-service.

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

### SDK usage starts from here

#### Register client with auth-service:

Use client token to register oauth client with auth-service.

- `authEnvUrl` auth-service url.
- `clientToken` returned from test-service. One use only. 5 minutes ttl
- `deviceId` string. Example app uses random uuid. 
https://developer.android.com/training/articles/user-data-ids#kotlin
- `keyId` name of keypair in keychain. Example app uses appId.

Save client data for later use. Keypair is stored in trust zone. Other data can be public.  

Registering client should be only once when user first time starts to use app.

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

#### Initiate authentication:

After client is registered you can initiate authentication with auth-service.

- `authEnv` Megical Easy Access app uses this to determine which env to connect.
- `authEnvUrl` auth-service url.
- `keyId` keychain name.
- `clientId` oauth client id
- `audience` accessToken audience. introspect returns this audience info
- `redirectUrl` App callback url. Should be unique per app.

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

#### Handle login data:

`loginData.appLink` can be used to open Megical Easy Access if it is installed on device.
If it is not installed, app should show loginCode and/or appLink qr-code.

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

`onActivityResult` must be implemented to handle return from Megical Easy Access app.
It should call verifyAuthentication if activity was successful.

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

When login state is `Updated` call `megicalAuthApi.verifyAuthentication()`.

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

#### Deregister device

Call deleteClient to destroy client from auth-service and to remove client key pair from keychain.
Delete also saved client data.

This is optional. There shouldn't be real need to delete client from auth-service.

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