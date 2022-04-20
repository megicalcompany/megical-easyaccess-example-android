package com.megical.easyaccess.example

import android.content.Context
import androidx.annotation.Keep
import androidx.core.content.edit
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


class SettingsRepository(context: Context) {

    companion object {
        private const val PREFERENCE_NAME = "EXAMPLE_SETTINGS"
        private const val CLIENT_DATA = "CLIENT_DATA"
    }

    private val prefs = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    private val moshi = Moshi
        .Builder()
        .build()

    private val clientDataAdapter: JsonAdapter<ClientData> =
        moshi.adapter(ClientData::class.java)
    

    var clientData: ClientData?
        set(value) {
            prefs.edit {
                value?.let { putString(CLIENT_DATA, clientDataAdapter.toJson(it)) }
                    ?: remove(CLIENT_DATA)
            }
        }
        get() {
            return prefs.getString(CLIENT_DATA, null)?.let {
                try {
                    clientDataAdapter.fromJson(it)
                } catch (error: Exception) {
                    null
                }
            }
        }

}

@Keep
@JsonClass(generateAdapter = true)
data class ClientData(
    val clientId: String,
    val appId: String,
    val audience: List<String>,
    val authEnvUrl: String,
    val authEnv: String,
)