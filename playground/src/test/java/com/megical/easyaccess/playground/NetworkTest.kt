package com.megical.easyaccess.playground

import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test
import java.util.*

class NetworkTest {
    private val playgroundRestApi = PlaygroundRestApi()

    @Test
    fun `playground healthcheck`() {
        val execute = playgroundRestApi.healthcheck().execute()
        val body = execute.body()
        assertThat(body!!.nowMs).isGreaterThan((0L))
    }

    @Test
    fun `create client`() {
        val execute = playgroundRestApi.openIdClientData(UUID.randomUUID()).execute()
        assertThat(execute.isSuccessful).isFalse()
    }

    @Ignore
    @Test
    fun `create client with valid token`() {
        val execute =
            playgroundRestApi.openIdClientData(UUID.fromString("9f5a2eff-83de-4d32-854d-cee5a0cd9f5a"))
                .execute()
        assertThat(execute.isSuccessful).isTrue()
    }
}
