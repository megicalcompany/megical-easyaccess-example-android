package com.megical.easyaccess.playground

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import retrofit2.HttpException
import java.util.*

class NetworkTest {
    private val playgroundRestApi = PlaygroundRestApi()

    @Test
    fun `playground healthcheck`() {
        runBlocking {
            val data = playgroundRestApi.healthcheck()
            assertThat(data.nowMs).isGreaterThan((0L))
        }
    }

    @Test
    fun `create client with invalid token`() {
        runBlocking {
            val error = try {
                playgroundRestApi.openIdClientData(UUID.randomUUID())
                null
            } catch (error: HttpException) {
                error
            }
            assertThat(error!!.code()).isEqualTo(400)
        }
    }

    @Ignore
    @Test
    fun `create client with valid token`() {
        runBlocking {
            // get test app client registration code from
            // https://playground.megical.com/easyaccess/
            val data =
                playgroundRestApi.openIdClientData(UUID.fromString("9f7c477d-5c30-4d2c-b439-78c19b83f5df"))
            assertThat(data.appId).isEqualTo("test_app")

        }
    }
}
