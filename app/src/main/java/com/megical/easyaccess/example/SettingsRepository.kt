package com.megical.easyaccess.example

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


const val PREFERENCE_NAME = "EXAMPLE_SETTINGS"
const val CLIENT_DATA = "CLIENT_DATA"

class SettingsRepository(context: Context) {

    private val pref: SharedPreferences =
        context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    private val editor = pref.edit()

    private val moshi = Moshi
        .Builder()
        .build()

    private val clientDataAdapter: JsonAdapter<ClientData> =
        moshi.adapter(ClientData::class.java)


    private fun String.put(clientData: ClientData?) {
        if (clientData != null) {
            editor.putString(
                this,
                clientDataAdapter.toJson(clientData)
            )
        } else {
            editor.remove(this)
        }
        editor.commit()
    }

    private fun String.getClientData(): ClientData? = pref.getString(this, null)?.let {
        try {
            clientDataAdapter.fromJson(it)
        } catch (error: Exception) {
            null
        }
    }

    fun setClientData(clientData: ClientData?) {
        CLIENT_DATA.put(clientData)
    }

    fun getClientData() = CLIENT_DATA.getClientData()

}

@JsonClass(generateAdapter = true)
data class ClientData(
    val appId: String,
    val clientId: String,
    val audience: List<String>,
    val authEnvUrl: String,
    val clientUrl: String,
)