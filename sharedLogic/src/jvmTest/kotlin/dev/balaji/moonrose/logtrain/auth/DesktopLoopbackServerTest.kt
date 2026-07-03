package dev.balaji.moonrose.logtrain.auth

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLoopbackServerTest {
    @Test
    fun `captures the token query param and responds`() {
        var capturedToken: String? = null
        val latch = CountDownLatch(1)

        val redirectUri = DesktopLoopbackServer.start(onToken = { token ->
            capturedToken = token
            latch.countDown()
        })

        val connection = URL("$redirectUri?token=test123").openConnection() as HttpURLConnection
        assertEquals(200, connection.responseCode)
        connection.inputStream.close()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "onToken callback was not invoked in time")
        assertEquals("test123", capturedToken)
    }
}
