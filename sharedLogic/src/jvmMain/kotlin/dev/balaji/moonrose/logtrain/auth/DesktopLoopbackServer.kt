package dev.balaji.moonrose.logtrain.auth

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

// Single-shot local HTTP listener used as the OAuth redirect target for
// Desktop, where there's no OS-level custom-scheme registration to rely on.
object DesktopLoopbackServer {
    fun start(onToken: (String) -> Unit): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/callback") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            val token = query.split("&")
                .firstOrNull { it.startsWith("token=") }
                ?.substringAfter("=")

            val body = "<html><body>You can close this window.</body></html>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()

            token?.let(onToken)
            Thread { server.stop(0) }.start()
        }
        server.start()
        return "http://127.0.0.1:${server.address.port}/callback"
    }
}
